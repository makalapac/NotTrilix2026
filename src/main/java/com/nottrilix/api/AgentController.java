package com.nottrilix.api;

import com.nottrilix.agent.AgentChatService;
import com.nottrilix.api.dto.AgentChatRequest;
import com.nottrilix.api.dto.AgentChatResponse;
import com.nottrilix.api.dto.VoiceTurnResponse;
import com.nottrilix.auth.AuthUser;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;

/**
 * Conversation with the support agent for the logged-in customer (role CUSTOMER): text or voice in, text (and speech) out.
 * The agent is a separate application; these endpoints forward the message and the customer's token to it.
 */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "Agent", description = "Agent korisničke podrške (zasebna aplikacija; sa sustavom radi kroz MCP)")
public class AgentController {

    private static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    private final AgentChatService chatService;

    public AgentController(AgentChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    @Operation(summary = "Poruka agentu; bez conversationId počinje novi razgovor")
    public AgentChatResponse chat(@AuthenticationPrincipal AuthUser user,
                                  @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                  @Valid @RequestBody AgentChatRequest request) {
        return chatService.chat(user, authorization, request.conversationId(), request.message().trim());
    }

    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Glasovna poruka agentu (snimka iz preglednika); traži privolu VOICE. Snimka se ne pohranjuje.")
    public VoiceTurnResponse voice(@AuthenticationPrincipal AuthUser user,
                                   @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                   @RequestParam("audio") MultipartFile audio,
                                   @RequestParam(value = "conversationId", required = false) String conversationId)
            throws IOException {
        if (audio.isEmpty() || audio.getSize() > MAX_AUDIO_BYTES) {
            throw new IllegalArgumentException("Snimka je prazna ili veća od 10 MB");
        }
        String filename = audio.getOriginalFilename() == null || audio.getOriginalFilename().isBlank()
                ? "speech.webm" : audio.getOriginalFilename();
        return chatService.voice(user, authorization, conversationId, audio.getBytes(), filename,
                audio.getContentType());
    }

    /**
     * The same voice turn, streamed: JSON lines (transcript, reply, audio header), then mp3 as it is synthesized, so the
     * browser starts playing after the first chunk. Written straight to the servlet response with a flush after every
     * event (StreamingResponseBody would hold the bytes in the 8 KB buffer). Consent is checked before the first byte.
     */
    @PostMapping(value = "/voice/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Glasovna poruka sa streamingom: JSON redci (transkript, odgovor) pa mp3 kako nastaje; traži privolu VOICE")
    public void voiceStream(@AuthenticationPrincipal AuthUser user,
                            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                            @RequestParam("audio") MultipartFile audio,
                            @RequestParam(value = "conversationId", required = false) String conversationId,
                            HttpServletResponse response) throws IOException {
        if (audio.isEmpty() || audio.getSize() > MAX_AUDIO_BYTES) {
            throw new IllegalArgumentException("Snimka je prazna ili veća od 10 MB");
        }
        chatService.requireVoiceConsent(user);
        String filename = audio.getOriginalFilename() == null || audio.getOriginalFilename().isBlank()
                ? "speech.webm" : audio.getOriginalFilename();
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        chatService.voiceStream(user, authorization, conversationId, audio.getBytes(), filename,
                audio.getContentType(), flushing(response));
    }

    /** An output stream whose flush() pushes the response buffer to the socket, so each event leaves immediately. */
    private static OutputStream flushing(HttpServletResponse response) throws IOException {
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

    @GetMapping(value = "/voice/filler", produces = "audio/mpeg")
    @Operation(summary = "Kratka fraza kojom Nola premošćuje čekanje (mp3, keširano)")
    public ResponseEntity<byte[]> voiceFiller() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(12)))
                .body(chatService.voiceFiller());
    }
}
