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
 * Agent = Spring AI ChatClient s cetiri sastojka, isto kao u Fazi 3:
 * system prompt (po pozivu, s podacima kupca) + memorija razgovora + RAG nad bazom znanja + alati.
 * Razlika je odakle stizu alati i znanje: vise nisu u istom procesu nego ih sustav nudi preko MCP-a.
 */
@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger("AGENT");

    /** Kako se odlomci baze znanja dodaju pitanju kupca. Mora sadrzavati query i context. */
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

    /** Jedan izvor vremena (testovi ubace fiksni Clock). */
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

    /** Baza znanja je u sustavu: dohvat je MCP poziv u ime kupca, s kontekstom razgovora. */
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
                                // Prije petlje poziva alata: baza znanja se pita jednom po turnu. Sa zadanim
                                // redoslijedom (0) advisor bi bio unutar petlje i trazio ponovno pri svakom
                                // pozivu modela u istom turnu.
                                .order(ToolCallingAdvisor.DEFAULT_ORDER - 50)
                                .documentRetriever(knowledgeBase)
                                .queryAugmenter(ContextualQueryAugmenter.builder()
                                        .promptTemplate(new PromptTemplate(RAG_TEMPLATE))
                                        .allowEmptyContext(true) // bez odlomaka pitanje ide modelu kakvo jest
                                        .documentFormatter(documents -> documents.stream()
                                                .map(Document::getText)
                                                .collect(Collectors.joining("\n\n")))
                                        .build())
                                .build())
                .defaultToolCallbacks(tools)
                .build();
    }
}
