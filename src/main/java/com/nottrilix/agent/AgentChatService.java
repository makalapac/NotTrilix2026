package com.nottrilix.agent;

import com.nottrilix.agent.AgentClient.TurnRequest;
import com.nottrilix.api.dto.AgentChatResponse;
import com.nottrilix.api.dto.VoiceTurnResponse;
import com.nottrilix.auth.AuthUser;
import com.nottrilix.domain.AuditEventType;
import com.nottrilix.domain.AuditOutcome;
import com.nottrilix.domain.ConsentType;
import com.nottrilix.privacy.PrivacyService;
import com.nottrilix.service.AuditService;
import com.nottrilix.service.AuditService.AuditContext;
import com.nottrilix.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Razgovor kupca s agentom, gledano iz sustava: poruka (tekst ili glas) ide aplikaciji agenta, odgovor natrag kupcu,
 * a turn se biljezi u audit trag i u okvir razgovora (za analizu i prava kupca). Glas trazi privolu kupca.
 * Sto agent pritom radi u sustavu (alati, baza znanja) dolazi zasebno, kroz MCP.
 */
@Service
public class AgentChatService {

    private static final Logger log = LoggerFactory.getLogger("AGENT");
    public static final String CHANNEL_CHAT = "CHAT";
    public static final String CHANNEL_VOICE = "VOICE";

    private final AgentClient agent;
    private final AuditService audit;
    private final ConversationService conversations;
    private final PrivacyService privacy;

    public AgentChatService(AgentClient agent, AuditService audit, ConversationService conversations,
                            PrivacyService privacy) {
        this.agent = agent;
        this.audit = audit;
        this.conversations = conversations;
        this.privacy = privacy;
    }

    public AgentChatResponse chat(AuthUser customer, String authorization, String conversationId, String message) {
        log.info("CHAT -> agent customer={} conv={} msg='{}'", customer.id(),
                conversationId == null ? "(novi)" : conversationId, message);
        long start = System.nanoTime();
        try {
            AgentChatResponse response = agent.chat(authorization,
                    new TurnRequest(conversationId, message, CHANNEL_CHAT));
            recordTurn(customer, CHANNEL_CHAT, message, response, System.nanoTime() - start);
            return response;
        } catch (AgentUnavailableException e) {
            recordFailure(customer, CHANNEL_CHAT, conversationId, message, e, System.nanoTime() - start);
            throw e;
        }
    }

    /** Glas: snimka se prosljedjuje agentu i ne pohranjuje; u trag ide transkript, kao poruka kupca. */
    public VoiceTurnResponse voice(AuthUser customer, String authorization, String conversationId, byte[] audio,
                                   String filename, String contentType) {
        privacy.requireConsent(customer.id(), ConsentType.VOICE);
        log.info("VOICE -> agent customer={} conv={} audio={} B ({})", customer.id(),
                conversationId == null ? "(novi)" : conversationId, audio.length, contentType);
        long start = System.nanoTime();
        try {
            VoiceTurnResponse response = agent.voice(authorization, conversationId, audio, filename, contentType);
            recordTurn(customer, CHANNEL_VOICE, response.transcript(), response.chat(), System.nanoTime() - start);
            return response;
        } catch (AgentUnavailableException e) {
            recordFailure(customer, CHANNEL_VOICE, conversationId, "(glasovna poruka)", e, System.nanoTime() - start);
            throw e;
        }
    }

    private void recordTurn(AuthUser customer, String channel, String message, AgentChatResponse response,
                            long nanos) {
        long ms = nanos / 1_000_000;
        audit.record(new AuditContext(response.conversationId(), customer.id(), customer.name(), channel),
                AuditEventType.CHAT_TURN, "turn", message, AuditOutcome.OK, response.reply(), ms,
                response.promptTokens(), response.completionTokens());
        conversations.recordTurn(response.conversationId(), customer.id(), channel);
        log.info("{} <- agent conv={} OK {} ms, alati={}, izvori={}, ceka potvrdu={}", channel,
                response.conversationId(), ms,
                response.toolCalls().stream().map(t -> t.tool() + ":" + t.outcome()).toList(),
                response.sources().size(), response.pendingAction() != null);
    }

    private void recordFailure(AuthUser customer, String channel, String conversationId, String message,
                               AgentUnavailableException e, long nanos) {
        audit.record(new AuditContext(conversationId, customer.id(), customer.name(), channel),
                AuditEventType.CHAT_TURN, "turn", message, AuditOutcome.ERROR, e.getMessage(), nanos / 1_000_000);
    }
}
