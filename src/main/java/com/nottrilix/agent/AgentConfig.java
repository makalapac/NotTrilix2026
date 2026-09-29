package com.nottrilix.agent;

import com.nottrilix.kb.KbProperties;
import com.nottrilix.service.AuditService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Agent = Spring AI ChatClient s cetiri sastojka:
 * system prompt (po pozivu, s podacima kupca) + memorija razgovora + RAG nad bazom znanja + alati (auditirani).
 */
@Configuration
public class AgentConfig {

    /** Kako se odlomci baze znanja dodaju pitanju kupca. Mora sadrzavati query i question_answer_context. */
    static final String RAG_TEMPLATE = """
            {query}

            ---------------------
            KONTEKST IZ BAZE ZNANJA NotTrilix podrške (odlomci najsličniji pitanju; neki mogu biti nevažni):
            {question_answer_context}
            ---------------------
            Pravila, rokove, limite i postupke navodi isključivo prema gornjem kontekstu. Ako kontekst ne sadrži \
            odgovor na pitanje o pravilima, reci da tu informaciju nemaš. Podatke o kupcu (kupnje, wallet, stanje \
            bona, prijave) ne traži u kontekstu nego ih dohvati alatima. Odgovor završi sadržajnom rečenicom, bez \
            ponude dodatne pomoći i bez poziva kupcu da se ponovno javi.
            """;

    @Bean
    ChatMemory chatMemory(AgentProperties props) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(props.maxHistoryMessages())
                .build();
    }

    @Bean
    ChatClient agentChatClient(ChatClient.Builder builder, VectorStore vectorStore, ChatMemory chatMemory,
                               AgentTools tools, AuditService audit, KbProperties kb) {
        ToolCallback[] auditedTools = Arrays.stream(ToolCallbacks.from(tools))
                .map(tool -> (ToolCallback) new AuditingToolCallback(tool, audit))
                .toArray(ToolCallback[]::new);

        return builder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .topK(kb.topK())
                                        .similarityThreshold(kb.similarityThreshold())
                                        .build())
                                .promptTemplate(new PromptTemplate(RAG_TEMPLATE))
                                .build())
                .defaultToolCallbacks(auditedTools)
                .build();
    }
}
