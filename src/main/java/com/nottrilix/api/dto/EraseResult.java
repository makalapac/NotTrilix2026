package com.nottrilix.api.dto;

/** What the erasure on the customer's request (GDPR Art. 17) covered. */
public record EraseResult(int deletedMessages, int anonymizedEvents, int erasedConversations,
                          int anonymizedAnalyses) {
}
