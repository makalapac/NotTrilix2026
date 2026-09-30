package com.nottrilix.api.dto;

import java.time.Instant;
import java.util.List;

/** A conversation in the customer data export (GDPR Art. 15 and 20): customer messages and agent replies. */
public record ConversationExport(String id, String channel, Instant startedAt, Instant lastTurnAt, int turns,
                                 List<TranscriptLine> messages) {
}
