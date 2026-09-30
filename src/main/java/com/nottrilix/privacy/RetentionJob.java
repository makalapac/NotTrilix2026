package com.nottrilix.privacy;

import com.nottrilix.domain.AuditEventType;
import com.nottrilix.domain.AuditOutcome;
import com.nottrilix.domain.Conversation;
import com.nottrilix.domain.ConversationAnalysis;
import com.nottrilix.domain.ConversationStatus;
import com.nottrilix.repo.AuditEventRepository;
import com.nottrilix.repo.ConversationAnalysisRepository;
import com.nottrilix.repo.ConversationRepository;
import com.nottrilix.service.AuditService;
import com.nottrilix.service.AuditService.AuditContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Retention (GDPR Art. 5(1)(e), storage limitation): conversation content older than the retention period is deleted, while the other traces
 * and analyses remain without a link to the customer. Runs at startup and once a day; every pass leaves a trace in the audit trail.
 * The transaction is at class level: onStartup() and nightly() call run() internally (a self-invocation does not go through the proxy).
 */
@Component
@Transactional
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger("PRIVACY");

    /** What a single pass deleted or anonymized. */
    public record RetentionResult(int deletedMessages, int anonymizedEvents, int erasedConversations,
                                  int anonymizedAnalyses) {
    }

    private final AuditEventRepository auditEvents;
    private final ConversationRepository conversations;
    private final ConversationAnalysisRepository analyses;
    private final AuditService audit;
    private final PrivacyProperties props;
    private final Clock clock;

    public RetentionJob(AuditEventRepository auditEvents, ConversationRepository conversations,
                        ConversationAnalysisRepository analyses, AuditService audit, PrivacyProperties props,
                        Clock clock) {
        this.auditEvents = auditEvents;
        this.conversations = conversations;
        this.analyses = analyses;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "${nottrilix.privacy.retention-cron:0 15 3 * * *}")
    public void nightly() {
        run();
    }

    public RetentionResult run() {
        Instant now = Instant.now(clock);
        Instant conversationLimit = now.minus(Duration.ofDays(props.conversationRetentionDays()));
        Instant analysisLimit = now.minus(Duration.ofDays(props.analysisRetentionDays()));

        int deletedMessages = auditEvents.deleteByEventTypeInAndCreatedAtBefore(PrivacyService.CONVERSATION_CONTENT,
                conversationLimit);
        int anonymizedEvents = auditEvents.anonymizeCreatedBefore(conversationLimit);
        List<Conversation> expired = conversations.findByLastTurnAtBeforeAndStatusNot(conversationLimit,
                ConversationStatus.ERASED);
        expired.forEach(Conversation::erase);
        List<ConversationAnalysis> oldAnalyses = analyses.findAll().stream()
                .filter(a -> a.getCustomerId() != null && a.getCreatedAt().isBefore(analysisLimit))
                .toList();
        oldAnalyses.forEach(ConversationAnalysis::anonymize);

        RetentionResult result = new RetentionResult(deletedMessages, anonymizedEvents, expired.size(),
                oldAnalyses.size());
        if (deletedMessages + anonymizedEvents + expired.size() + oldAnalyses.size() > 0) {
            audit.record(new AuditContext(null, null, null, "SYSTEM"), AuditEventType.PRIVACY, "retention", null,
                    AuditOutcome.OK, "%d poruka obrisano, %d događaja anonimizirano, %d razgovora, %d analiza (rok %d/%d dana)"
                            .formatted(deletedMessages, anonymizedEvents, expired.size(), oldAnalyses.size(),
                                    props.conversationRetentionDays(), props.analysisRetentionDays()), null);
        }
        log.info("PRIVACY retention: rok razgovora {} dana, analiza {} dana -> {} poruka obrisano, {} događaja "
                        + "anonimizirano, {} razgovora, {} analiza", props.conversationRetentionDays(),
                props.analysisRetentionDays(), deletedMessages, anonymizedEvents, expired.size(), oldAnalyses.size());
        return result;
    }
}
