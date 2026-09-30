package com.nottrilix.api.dto;

import com.nottrilix.domain.ConversationStatus;

import java.time.Instant;

/** A conversation as the admin sees it: the conversation record + analysis (null until analyzed). customerName is null after anonymization. */
public record ConversationDto(String id, Long customerId, String customerName, String channel,
                              ConversationStatus status, Instant startedAt, Instant lastTurnAt, int turns,
                              AnalysisDto analysis) {
}
