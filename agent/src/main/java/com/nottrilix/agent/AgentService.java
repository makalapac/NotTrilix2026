package com.nottrilix.agent;

import com.nottrilix.agent.SystemGateway.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * One entry point for all channels: customer text in, agent text out. Chat calls it through the shop; voice and
 * phone will put STT/TTS in front of it, and this service will not change.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger("AGENT");
    private static final Pattern CONVERSATION_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("d.M.yyyy.");
    private static final String NO_PENDING_ACTION = "nema";

    private final ChatClient chatClient;
    private final SystemGateway system;
    private final AgentProperties props;
    private final Clock clock;
    private final boolean modelConfigured;
    private final String systemPrompt;
    private final Map<String, AtomicInteger> turns = new ConcurrentHashMap<>();

    public AgentService(ChatClient agentChatClient, SystemGateway system, AgentProperties props, Clock clock,
                        @Value("${spring.ai.openai.api-key:not-set}") String openAiKey,
                        @Value("classpath:agent/system-prompt.txt") Resource systemPromptResource) {
        this.chatClient = agentChatClient;
        this.system = system;
        this.props = props;
        this.clock = clock;
        this.modelConfigured = !openAiKey.isBlank() && !"not-set".equals(openAiKey);
        try {
            this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public AgentChatResponse chat(String customerToken, String requestedConversationId, String message,
                                  String channel) {
        if (!modelConfigured) {
            throw new AgentUnavailableException("Agent nije dostupan: OPENAI_API_KEY nije postavljen");
        }
        // the system says who the customer is (by token), not the caller
        Customer customer = system.whoAmI(customerToken);
        String conversationId = conversationId(requestedConversationId);
        // the memory key includes the customer: one customer's conversation cannot be continued by another
        String memoryKey = "c" + customer.id() + ":" + conversationId;
        AtomicInteger turnCounter = turns.computeIfAbsent(memoryKey, k -> new AtomicInteger());
        // one conversation = one turn at a time (turn ordering is the basis of action confirmation)
        synchronized (turnCounter) {
            TurnContext turn = new TurnContext(customerToken, conversationId, turnCounter.incrementAndGet(),
                    channel, new AgentTrace());
            return turn(customer, memoryKey, turn, message);
        }
    }

    private AgentChatResponse turn(Customer customer, String memoryKey, TurnContext turn, String message) {
        // the system holds the conversation state: the model need not remember (or guess) what awaits confirmation
        String pending = system.pendingAction(turn);
        String pendingForPrompt = pending == null ? NO_PENDING_ACTION
                : pending + ". Ako kupac sada potvrdi, pozovi confirmPendingAction; ako odbije, pozovi "
                + "cancelPendingAction.";

        log.info("TURN {} conv={} customer={} channel={} pending='{}' msg='{}'", turn.turn(),
                turn.conversationId(), customer.id(), turn.channel(), pending == null ? "-" : pending, message);
        long start = System.nanoTime();

        ChatClientResponse response;
        try {
            response = chatClient.prompt()
                    .system(s -> s.text(systemPrompt)
                            .param("agentName", props.name())
                            .param("customerName", customer.name())
                            .param("channel", turn.channel())
                            .param("today", LocalDate.now(clock).format(TODAY))
                            .param("pendingAction", pendingForPrompt))
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryKey).params(turn.asMap()))
                    .toolContext(turn.asMap())
                    .call()
                    .chatClientResponse();
        } catch (RuntimeException e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.error("TURN {} conv={} neuspio nakon {} ms: {}", turn.turn(), turn.conversationId(), ms,
                    e.toString(), e);
            throw new AgentUnavailableException("Agent trenutno nije dostupan. Pokušajte ponovno za koji trenutak.");
        }
        long ms = (System.nanoTime() - start) / 1_000_000;

        ChatResponse chatResponse = response.chatResponse();
        String modelReply = chatResponse == null || chatResponse.getResult() == null
                ? "" : String.valueOf(chatResponse.getResult().getOutput().getText());
        String reply = ReplyStyle.withoutClosingPhrase(modelReply);
        if (reply.length() < modelReply.strip().length()) {
            log.info("TURN {} conv={} uklonjena završna fraza: '{}'", turn.turn(), turn.conversationId(),
                    modelReply.strip().substring(reply.length()).strip());
        }
        Usage usage = chatResponse == null ? null : chatResponse.getMetadata().getUsage();
        Integer promptTokens = usage == null ? null : usage.getPromptTokens();
        Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
        List<AgentSource> sources = sources(response);
        List<AgentToolCall> toolCalls = turn.trace().toolCalls();
        String pendingAfter = system.pendingAction(turn);

        log.info("TURN {} conv={} OK {} ms, alati={}, izvori={}, tokeni={}/{}, ceka potvrdu={} reply='{}'",
                turn.turn(), turn.conversationId(), ms,
                toolCalls.stream().map(t -> t.tool() + ":" + t.outcome()).toList(), sources.size(),
                promptTokens, completionTokens, pendingAfter != null, reply);
        return new AgentChatResponse(turn.conversationId(), reply, toolCalls, sources, ms, promptTokens,
                completionTokens, pendingAfter);
    }

    private static String conversationId(String requested) {
        if (requested != null && CONVERSATION_ID.matcher(requested).matches()) {
            return requested;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Knowledge base sections the RAG advisor added to the question (for showing sources). */
    private static List<AgentSource> sources(ChatClientResponse response) {
        List<AgentSource> sources = new ArrayList<>();
        if (response.context().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT) instanceof List<?> documents) {
            for (Object item : documents) {
                if (item instanceof Document doc) {
                    Map<String, Object> meta = doc.getMetadata();
                    sources.add(new AgentSource(String.valueOf(meta.getOrDefault("source", "")),
                            String.valueOf(meta.getOrDefault("title", "")),
                            String.valueOf(meta.getOrDefault("section", "")),
                            doc.getScore() == null ? 0.0 : doc.getScore()));
                }
            }
        }
        return sources;
    }
}
