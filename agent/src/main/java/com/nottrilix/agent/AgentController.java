package com.nottrilix.agent;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;

/**
 * Entry into the agent: customer message (text or recording) + customer token (Authorization: Bearer ...). The agent
 * does not verify the token itself: it forwards it to the system with every MCP call, and the system decides who the
 * customer is and what they may do.
 */
@RestController
public class AgentController {

    private static final String BEARER = "Bearer ";
    private static final String DEFAULT_CHANNEL = "CHAT";
    private static final String VOICE_CHANNEL = "VOICE";
    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    /** Voice turn: transcript, reply (as in chat) and speech as mp3 (base64). */
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

    /** Browser recording (webm/ogg/mp3/wav) -> transcript -> the same agent -> speech. The recording is not stored. */
    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VoiceTurnResponse voice(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                   @RequestParam("audio") MultipartFile audio,
                                   @RequestParam(value = "conversationId", required = false) String conversationId)
            throws IOException {
        return voiceService.turn(token(authorization), conversationId, audio.getBytes(), filenameOf(audio),
                VOICE_CHANNEL);
    }

    /**
     * The same turn, streamed: JSON lines (transcript, reply, audio header) and then mp3 bytes as they are synthesized.
     * Written straight to the servlet response on the request thread, with a real flush after every event and chunk
     * (StreamingResponseBody would hold the bytes in the 8 KB response buffer). The status is committed at the start,
     * so later failures travel as an error line on the stream.
     */
    @PostMapping(value = "/voice/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void voiceStream(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                            @RequestParam("audio") MultipartFile audio,
                            @RequestParam(value = "conversationId", required = false) String conversationId,
                            HttpServletResponse response) throws IOException {
        String token = token(authorization);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        voiceService.turnStreaming(token, conversationId, audio.getBytes(), filenameOf(audio), VOICE_CHANNEL,
                flushing(response));
    }

    /** An output stream whose flush() pushes the response buffer to the socket, so each event leaves immediately. */
    static OutputStream flushing(HttpServletResponse response) throws IOException {
        OutputStream out = response.getOutputStream();
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                out.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                out.flush();
                response.flushBuffer();
            }
        };
    }

    /** Nola's bridging phrase while the agent thinks (mp3, synthesized once). */
    @GetMapping(value = "/voice/filler", produces = "audio/mpeg")
    public ResponseEntity<byte[]> voiceFiller() {
        return ResponseEntity.ok()
                .contentType(AUDIO_MPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(12)))
                .body(voiceService.filler());
    }

    private static String filenameOf(MultipartFile audio) {
        return audio.getOriginalFilename() == null || audio.getOriginalFilename().isBlank()
                ? "speech.webm" : audio.getOriginalFilename();
    }

    private static String token(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)
                || authorization.substring(BEARER.length()).isBlank()) {
            throw new SystemGateway.InvalidTokenException("Nedostaje token kupca (Authorization: Bearer ...)");
        }
        return authorization.substring(BEARER.length()).trim();
    }
}
