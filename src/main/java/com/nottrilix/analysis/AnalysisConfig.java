package com.nottrilix.analysis;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Model za back-office analizu razgovora (Spring AI ChatClient bez alata i bez memorije). Sustav i dalje ne vodi
 * razgovor s kupcem - to radi zasebna aplikacija agenta; ovaj klijent cita pseudonimizirane transkripte.
 */
@Configuration
public class AnalysisConfig {

    @Bean
    ChatClient analysisChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
