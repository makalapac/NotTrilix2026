package com.nottrilix.api.dto;

import com.nottrilix.domain.SuggestionStatus;

import java.time.Instant;

/** Knowledge base section suggestion that arose from a conversation; the admin approves it (with corrections) or rejects it. */
public record KbSuggestionDto(Long id, Instant createdAt, String conversationId, String question, String answer,
                              SuggestionStatus status, Instant decidedAt, String decidedBy) {
}
