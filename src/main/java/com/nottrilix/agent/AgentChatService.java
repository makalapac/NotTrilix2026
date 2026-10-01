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

import java.io.IOException;
import java.io.OutputStream;

/**
 * The customer's conversation with the agent, as seen from the system: the message (text or voice) goes to the agent application,
 * the reply back to the customer, and the turn is recorded in the audit trail and the conversation record (for analysis and customer rights).
 * Voice requires the customer's consent. What the agent does in the system meanwhile (tools, knowledge base) comes separately, via MCP.
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

    /** Voice: the recording is forwarded to the agent and not stored; the transcript goes into the audit trail as the customer's message. */
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

    /** Consent is checked before the streaming response starts, so a refusal is still an ordinary 403. */
    public void requireVoiceConsent(AuthUser customer) {
        privacy.requireConsent(customer.id(), ConsentType.VOICE);
    }

    /**
     * Voice with streaming: the agent's events (transcript, reply) and the speech bytes pass through to the browser as
     * they arrive; the turn is recorded the moment the reply is known. The caller has already checked the consent.
     */
    public void voiceStream(AuthUser customer, String authorization, String conversationId, byte[] audio,
                            String filename, String contentType, OutputStream out) throws IOException {
        log.info("VOICE -> agent (stream) customer={} conv={} audio={} B ({})", customer.id(),
                conversationId == null ? "(novi)" : conversationId, audio.length, contentType);
        long start = System.nanoTime();
        String[] transcript = {"(glasovna poruka)"};
        agent.voiceStream(authorization, conversationId, audio, filename, contentType, out,
                new AgentClient.VoiceStreamListener() {
                    @Override
                    public void transcript(String text, long sttMs) {
                        transcript[0] = text;
                    }

                    @Override
                    public void chat(AgentChatResponse response, String ttsProvider) {
                        recordTurn(customer, CHANNEL_VOICE, transcript[0], response, System.nanoTime() - start);
                    }

                    @Override
                    public void error(String error, String message) {
                        recordFailure(customer, CHANNEL_VOICE, conversationId, transcript[0],
                                new AgentUnavailableException(message), System.nanoTime() - start);
                    }
                });
    }

    /** Nola's bridging phrase as mp3 (the agent synthesizes it once). */
    public byte[] voiceFiller() {
        return agent.voiceFiller();
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
