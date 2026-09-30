package com.nottrilix.agent;

import com.nottrilix.agent.SystemGateway.SystemUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent = Spring AI ChatClient with four ingredients, just as in Phase 3:
 * system prompt (per call, with customer data) + conversation memory + RAG over the knowledge base + tools.
 * The difference is where tools and knowledge come from: no longer the same process, the system serves them over MCP.
 */
@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger("AGENT");

    /** How knowledge base sections are added to the customer's question. Must contain query and context. */
    static final String RAG_TEMPLATE = """
            {query}

            ---------------------
            KONTEKST IZ BAZE ZNANJA NotTrilix podrške (odlomci najsličniji pitanju; neki mogu biti nevažni):
            {context}
            ---------------------
            Pravila, rokove, limite i postupke navodi isključivo prema gornjem kontekstu. Ako kontekst ne sadrži \
            odgovor na pitanje o pravilima, reci da tu informaciju nemaš. Podatke o kupcu (kupnje, wallet, stanje \
            bona, prijave) ne traži u kontekstu nego ih dohvati alatima. Odgovor završi sadržajnom rečenicom, bez \
            ponude dodatne pomoći i bez poziva kupcu da se ponovno javi.
            """;

    /** A single source of time (tests inject a fixed Clock). */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Europe/Zagreb"));
    }

    @Bean
    ChatMemory chatMemory(AgentProperties props) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(props.maxHistoryMessages())
                .build();
    }

    /** The knowledge base is in the system: retrieval is an MCP call for the customer, with conversation context. */
    @Bean
    DocumentRetriever knowledgeBaseRetriever(SystemGateway system) {
        return (Query query) -> {
            Object token = query.context().get(TurnContext.CUSTOMER_TOKEN);
            try {
                return system.searchKnowledgeBase(query.text(), token == null ? null : String.valueOf(token),
                        TurnContext.meta(query.context()));
            } catch (SystemUnavailableException e) {
                log.warn("RAG bez odlomaka: baza znanja nije dostupna ({})", e.getMessage());
                return List.of();
            }
        };
    }

    @Bean
    ChatClient agentChatClient(ChatClient.Builder builder, ChatMemory chatMemory, DocumentRetriever knowledgeBase,
                               SyncMcpToolCallbackProvider mcpTools) {
        ToolCallback[] tools = Arrays.stream(mcpTools.getToolCallbacks())
                .map(tool -> (ToolCallback) new TracingToolCallback(tool))
                .toArray(ToolCallback[]::new);
        log.info("Alati za model (preko MCP-a): {} -> {}", tools.length,
                Arrays.stream(tools).map(tool -> tool.getToolDefinition().name()).sorted().toList());

        return builder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        RetrievalAugmentationAdvisor.builder()
                                // Before the tool-calling loop: the knowledge base is queried once per turn. With
                                // the default order (0) the advisor would sit inside the loop and search again on
                                // every model call within the same turn.
                                .order(ToolCallingAdvisor.DEFAULT_ORDER - 50)
                                .documentRetriever(knowledgeBase)
                                .queryAugmenter(ContextualQueryAugmenter.builder()
                                        .promptTemplate(new PromptTemplate(RAG_TEMPLATE))
                                        .allowEmptyContext(true) // no sections: the question goes to the model as is
                                        .documentFormatter(documents -> documents.stream()
                                                .map(Document::getText)
                                                .collect(Collectors.joining("\n\n")))
                                        .build())
                                .build())
                .defaultToolCallbacks(tools)
                .build();
    }
}
