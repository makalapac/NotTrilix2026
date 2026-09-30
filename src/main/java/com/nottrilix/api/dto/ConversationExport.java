package com.nottrilix.api.dto;

import java.time.Instant;
import java.util.List;

/** Razgovor u izvozu podataka kupca (GDPR cl. 15 i 20): poruke kupca i odgovori agenta. */
public record ConversationExport(String id, String channel, Instant startedAt, Instant lastTurnAt, int turns,
                                 List<TranscriptLine> messages) {
}
