package com.nottrilix.api;

import com.nottrilix.agent.AgentChatService;
import com.nottrilix.api.dto.AgentChatRequest;
import com.nottrilix.api.dto.AgentChatResponse;
import com.nottrilix.api.dto.VoiceTurnResponse;
import com.nottrilix.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Razgovor s agentom podrske za prijavljenog kupca (uloga CUSTOMER): tekst ili glas unutra, tekst (i govor) van.
 * Agent je zasebna aplikacija; ovi endpointi joj prosljedjuju poruku i kupcev token.
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
}
