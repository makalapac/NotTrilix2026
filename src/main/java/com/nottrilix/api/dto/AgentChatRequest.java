package com.nottrilix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** conversationId is optional: without it a new conversation starts, and the response returns its id. */
public record AgentChatRequest(String conversationId,
                               @NotBlank(message = "je obavezna") @Size(max = 2000) String message) {
}
