package com.nottrilix.api.dto;

/**
 * Glasovni turn: sto je prepoznato (transkript), odgovor agenta (isti oblik kao u chatu) i govor kao mp3 (base64).
 * Snimka kupca se ne pohranjuje: obradi se i odbaci.
 */
public record VoiceTurnResponse(String transcript, AgentChatResponse chat, String audioBase64, String audioMimeType,
                                long sttMs, long ttsMs, String ttsProvider) {
}
