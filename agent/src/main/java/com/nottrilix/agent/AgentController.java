package com.nottrilix.agent;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Ulaz u agenta: poruka kupca (tekst ili snimka) + kupcev token (Authorization: Bearer ...). Agent token ne provjerava
 * sam: prosljedjuje ga sustavu uz svaki MCP poziv, a sustav odlucuje tko je kupac i sto smije.
 */
@RestController
public class AgentController {

    private static final String BEARER = "Bearer ";
    private static final String DEFAULT_CHANNEL = "CHAT";
    private static final String VOICE_CHANNEL = "VOICE";

    /** Glasovni turn: transkript, odgovor (kao u chatu) i govor kao mp3 (base64). */
    public record VoiceTurnResponse(String transcript, AgentChatResponse chat, String audioBase64,
                                    String audioMimeType, long sttMs, long ttsMs, String ttsProvider) {
    }

    private final AgentService agentService;
    private final VoiceService voiceService;

    public AgentController(AgentService agentService, VoiceService voiceService) {
        this.agentService = agentService;
        this.voiceService = voiceService;
    }

    @PostMapping("/chat")
    public AgentChatResponse chat(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                  @Valid @RequestBody AgentChatRequest request) {
        String channel = request.channel() == null || request.channel().isBlank() ? DEFAULT_CHANNEL
                : request.channel().trim();
        return agentService.chat(token(authorization), request.conversationId(), request.message().trim(), channel);
    }

    /** Snimka iz preglednika (webm/ogg/mp3/wav) -> transkript -> isti agent -> govor. Snimka se ne pohranjuje. */
    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VoiceTurnResponse voice(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                   @RequestParam("audio") MultipartFile audio,
                                   @RequestParam(value = "conversationId", required = false) String conversationId)
            throws IOException {
        String filename = audio.getOriginalFilename() == null || audio.getOriginalFilename().isBlank()
                ? "speech.webm" : audio.getOriginalFilename();
        return voiceService.turn(token(authorization), conversationId, audio.getBytes(), filename, VOICE_CHANNEL);
    }

    private static String token(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)
                || authorization.substring(BEARER.length()).isBlank()) {
            throw new SystemGateway.InvalidTokenException("Nedostaje token kupca (Authorization: Bearer ...)");
        }
        return authorization.substring(BEARER.length()).trim();
    }
}
