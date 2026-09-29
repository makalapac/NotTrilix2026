package com.nottrilix.agent;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ulaz u agenta: poruka kupca + kupcev token (Authorization: Bearer ...). Agent token ne provjerava sam:
 * prosljedjuje ga sustavu uz svaki MCP poziv, a sustav odlucuje tko je kupac i sto smije.
 */
@RestController
public class AgentController {

    private static final String BEARER = "Bearer ";
    private static final String DEFAULT_CHANNEL = "CHAT";

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public AgentChatResponse chat(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                  @Valid @RequestBody AgentChatRequest request) {
        if (authorization == null || !authorization.startsWith(BEARER)
                || authorization.substring(BEARER.length()).isBlank()) {
            throw new SystemGateway.InvalidTokenException("Nedostaje token kupca (Authorization: Bearer ...)");
        }
        String channel = request.channel() == null || request.channel().isBlank() ? DEFAULT_CHANNEL
                : request.channel().trim();
        return agentService.chat(authorization.substring(BEARER.length()).trim(), request.conversationId(),
                request.message().trim(), channel);
    }
}
