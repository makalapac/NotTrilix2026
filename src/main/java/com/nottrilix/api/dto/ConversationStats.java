package com.nottrilix.api.dto;

import java.util.Map;

/** Overview figures: how many conversations, about what, how they ended, what the sentiment is, the agent's average score. */
public record ConversationStats(long conversations, long open, long analyzed, long erased,
                                Map<String, Long> byCategory, Map<String, Long> byOutcome,
                                Map<String, Long> bySentiment, Double averageQuality, long proposedSuggestions,
                                long approvedSuggestions) {
}
