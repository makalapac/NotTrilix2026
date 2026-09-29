package com.nottrilix.agent;

import com.nottrilix.agent.AgentClient.TurnRequest;
import com.nottrilix.api.dto.AgentChatResponse;
import com.nottrilix.auth.AuthUser;
import com.nottrilix.domain.AuditEventType;
import com.nottrilix.domain.AuditOutcome;
import com.nottrilix.service.AuditService;
import com.nottrilix.service.AuditService.AuditContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Razgovor kupca s agentom, gledano iz sustava: poruka ide aplikaciji agenta, odgovor natrag kupcu, a turn se
 * biljezi u audit trag. Sto agent pritom radi u sustavu (alati, baza znanja) dolazi zasebno, kroz MCP.
 */
@Service
public class AgentChatService {

    private static final Logger log = LoggerFactory.getLogger("AGENT");

    private final AgentClient agent;
    private final AuditService audit;

    public AgentChatService(AgentClient agent, AuditService audit) {
        this.agent = agent;
        this.audit = audit;
    }

    public AgentChatResponse chat(AuthUser customer, String authorization, String conversationId, String message,
                                  String channel) {
        log.info("CHAT -> agent customer={} ({}) conv={} channel={} msg='{}'", customer.id(), customer.name(),
                conversationId == null ? "(novi)" : conversationId, channel, message);
        long start = System.nanoTime();
        try {
            AgentChatResponse response = agent.chat(authorization, new TurnRequest(conversationId, message, channel));
            long ms = (System.nanoTime() - start) / 1_000_000;
            audit.record(new AuditContext(response.conversationId(), customer.id(), customer.name(), channel),
                    AuditEventType.CHAT_TURN, "turn", message, AuditOutcome.OK, response.reply(), ms,
                    response.promptTokens(), response.completionTokens());
            log.info("CHAT <- agent conv={} OK {} ms, alati={}, izvori={}, ceka potvrdu={}", response.conversationId(),
                    ms, response.toolCalls().stream().map(t -> t.tool() + ":" + t.outcome()).toList(),
                    response.sources().size(), response.pendingAction() != null);
            return response;
        } catch (AgentUnavailableException e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            audit.record(new AuditContext(conversationId, customer.id(), customer.name(), channel),
                    AuditEventType.CHAT_TURN, "turn", message, AuditOutcome.ERROR, e.getMessage(), ms);
            throw e;
        }
    }
}
