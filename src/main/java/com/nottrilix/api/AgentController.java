package com.nottrilix.api;

import com.nottrilix.agent.AgentService;
import com.nottrilix.api.dto.AgentChatRequest;
import com.nottrilix.api.dto.AgentChatResponse;
import com.nottrilix.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Agent podrske za prijavljenog kupca (uloga CUSTOMER): tekst unutra, tekst van. */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "Agent", description = "Agent korisničke podrške (Spring AI ChatClient): memorija, RAG, alati, audit")
public class AgentController {

    private static final String CHANNEL_CHAT = "CHAT";

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    @Operation(summary = "Poruka agentu; bez conversationId počinje novi razgovor")
    public AgentChatResponse chat(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AgentChatRequest request) {
        return agentService.chat(user, request.conversationId(), request.message().trim(), CHANNEL_CHAT);
    }
}
