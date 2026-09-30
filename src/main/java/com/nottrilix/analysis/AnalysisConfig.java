package com.nottrilix.analysis;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Model for back-office conversation analysis (Spring AI ChatClient without tools and without memory). The system still does
 * not hold the conversation with the customer - the separate agent application does; this client reads pseudonymized transcripts.
 */
@Configuration
public class AnalysisConfig {

    @Bean
    ChatClient analysisChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
