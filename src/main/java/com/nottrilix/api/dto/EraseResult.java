package com.nottrilix.api.dto;

/** Sto je brisanje na zahtjev kupca (GDPR cl. 17) obuhvatilo. */
public record EraseResult(int deletedMessages, int anonymizedEvents, int erasedConversations,
                          int anonymizedAnalyses) {
}
