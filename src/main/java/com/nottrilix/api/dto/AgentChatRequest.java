package com.nottrilix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** conversationId je opcionalan: bez njega pocinje novi razgovor, a odgovor vraca njegov id. */
public record AgentChatRequest(String conversationId,
                               @NotBlank(message = "je obavezna") @Size(max = 2000) String message) {
}
