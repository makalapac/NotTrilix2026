package com.nottrilix.api.dto;

import com.nottrilix.domain.SuggestionStatus;

import java.time.Instant;

/** Prijedlog odlomka baze znanja nastao iz razgovora; admin ga odobrava (uz ispravke) ili odbija. */
public record KbSuggestionDto(Long id, Instant createdAt, String conversationId, String question, String answer,
                              SuggestionStatus status, Instant decidedAt, String decidedBy) {
}
