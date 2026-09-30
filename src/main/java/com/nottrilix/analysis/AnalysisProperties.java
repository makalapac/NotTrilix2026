package com.nottrilix.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Post-analiza razgovora (application.yml -> nottrilix.analysis.*).
 * idleMinutes: razgovor bez nove poruke toliko minuta smatra se zavrsenim i ide na analizu.
 * maxSuggestionsPerConversation: koliko prijedloga za bazu znanja smije nastati iz jednog razgovora.
 */
@ConfigurationProperties(prefix = "nottrilix.analysis")
public record AnalysisProperties(boolean enabled, int idleMinutes, int maxSuggestionsPerConversation) {
}
