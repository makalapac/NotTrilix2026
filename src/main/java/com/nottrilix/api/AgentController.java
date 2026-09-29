package com.nottrilix.api;

import com.nottrilix.agent.AgentChatService;
import com.nottrilix.api.dto.AgentChatRequest;
import com.nottrilix.api.dto.AgentChatResponse;
import com.nottrilix.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razgovor s agentom podrske za prijavljenog kupca (uloga CUSTOMER): tekst unutra, tekst van.
 * Agent je od Faze 4 zasebna aplikacija; ovaj endpoint joj prosljedjuje poruku i kupcev token.
 */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "Agent", description = "Agent korisničke podrške (zasebna aplikacija; sa sustavom radi kroz MCP)")
public class AgentController {

    private static final String CHANNEL_CHAT = "CHAT";

    private final AgentChatService chatService;

    public AgentController(AgentChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    @Operation(summary = "Poruka agentu; bez conversationId počinje novi razgovor")
    public AgentChatResponse chat(@AuthenticationPrincipal AuthUser user,
                                  @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                  @Valid @RequestBody AgentChatRequest request) {
        return chatService.chat(user, authorization, request.conversationId(), request.message().trim(),
                CHANNEL_CHAT);
    }
}
