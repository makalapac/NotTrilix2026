package com.nottrilix.api.dto;

import java.util.Map;

/** Brojke za pregled: koliko razgovora, o cemu, kako su zavrsili, kakvo je raspolozenje, prosjecna ocjena agenta. */
public record ConversationStats(long conversations, long open, long analyzed, long erased,
                                Map<String, Long> byCategory, Map<String, Long> byOutcome,
                                Map<String, Long> bySentiment, Double averageQuality, long proposedSuggestions,
                                long approvedSuggestions) {
}
