package com.nottrilix.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Customer message. conversationId is optional (without it a new conversation starts); channel is the conversation
 * channel (CHAT; later voice and phone). Who the customer is comes from the Authorization header token, not from here.
 */
public record AgentChatRequest(String conversationId,
                               @NotBlank(message = "je obavezna") @Size(max = 2000) String message,
                               String channel) {
}
