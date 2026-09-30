package com.nottrilix.api.dto;

/**
 * Voice turn: what was recognized (transcript), the agent's reply (same shape as in chat) and speech as mp3 (base64).
 * The customer's recording is not stored: it is processed and discarded.
 */
public record VoiceTurnResponse(String transcript, AgentChatResponse chat, String audioBase64, String audioMimeType,
                                long sttMs, long ttsMs, String ttsProvider) {
}
