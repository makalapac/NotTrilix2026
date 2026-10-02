package com.nottrilix.api.dto;

import jakarta.validation.constraints.NotBlank;

/** One turn of a phone call: what the caller said (transcript) and what the agent replied; the token says who the customer is. */
public record PhoneTurn(
        @NotBlank String customerToken,
        @NotBlank String message,
        @NotBlank String reply,
        long durationMs,
        Integer promptTokens,
        Integer completionTokens) {
}
