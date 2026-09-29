package com.nottrilix.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Poruka kupca. conversationId je opcionalan (bez njega pocinje novi razgovor), channel je kanal razgovora
 * (CHAT; kasnije glas i telefon). Tko je kupac ne pise ovdje: to govori token u zaglavlju Authorization.
 */
public record AgentChatRequest(String conversationId,
                               @NotBlank(message = "je obavezna") @Size(max = 2000) String message,
                               String channel) {
}
