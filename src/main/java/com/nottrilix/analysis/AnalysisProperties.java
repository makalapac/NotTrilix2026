package com.nottrilix.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conversation post-analysis (application.yml -> nottrilix.analysis.*).
 * idleMinutes: a conversation with no new message for that many minutes is considered finished and goes to analysis.
 * maxSuggestionsPerConversation: how many knowledge base suggestions may arise from a single conversation.
 */
@ConfigurationProperties(prefix = "nottrilix.analysis")
public record AnalysisProperties(boolean enabled, int idleMinutes, int maxSuggestionsPerConversation) {
}
