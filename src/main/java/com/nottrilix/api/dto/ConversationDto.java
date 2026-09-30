package com.nottrilix.api.dto;

import com.nottrilix.domain.ConversationStatus;

import java.time.Instant;

/** Razgovor kako ga vidi admin: okvir + analiza (null dok nije analiziran). customerName je null nakon anonimizacije. */
public record ConversationDto(String id, Long customerId, String customerName, String channel,
                              ConversationStatus status, Instant startedAt, Instant lastTurnAt, int turns,
                              AnalysisDto analysis) {
}
