package com.nottrilix.service;

import com.nottrilix.api.dto.AuditEventDto;
import com.nottrilix.domain.AuditEvent;
import com.nottrilix.domain.AuditEventType;
import com.nottrilix.domain.AuditOutcome;
import com.nottrilix.repo.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Agent audit trail in the database (audit_events) + a mirror in the log (logger AUDIT).
 * Writes run in their own transaction (REQUIRES_NEW): the trail survives even when the business transaction is rolled back.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    /** Who, in which conversation and through which channel. */
    public record AuditContext(String conversationId, Long customerId, String customerName, String channel) {
    }

    private final AuditEventRepository repo;
    private final Clock clock;

    public AuditService(AuditEventRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditContext ctx, AuditEventType type, String operation, String input, AuditOutcome outcome,
                       String detail, Long durationMs) {
        record(ctx, type, operation, input, outcome, detail, durationMs, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditContext ctx, AuditEventType type, String operation, String input, AuditOutcome outcome,
                       String detail, Long durationMs, Integer promptTokens, Integer completionTokens) {
        String channel = ctx.channel() == null ? "?" : ctx.channel();
        repo.save(new AuditEvent(Instant.now(clock), ctx.conversationId(), ctx.customerId(), ctx.customerName(),
                channel, type, operation, truncate(input, 1000), outcome, truncate(detail, 2000), durationMs,
                promptTokens, completionTokens));
        log.info("{} {} conv={} customer={} input={} -> {}{}{}", type, operation, ctx.conversationId(),
                ctx.customerId(), truncate(input, 200), outcome,
                durationMs == null ? "" : " (" + durationMs + " ms)",
                detail == null ? "" : " | " + truncate(detail, 200));
    }

    @Transactional(readOnly = true)
    public List<AuditEventDto> events(String conversationId, Long customerId, int limit) {
        Pageable page = PageRequest.of(0, Math.clamp(limit, 1, 500));
        List<AuditEvent> events;
        if (conversationId != null && !conversationId.isBlank()) {
            events = repo.findByConversationIdOrderByIdDesc(conversationId.trim(), page);
        } else if (customerId != null) {
            events = repo.findByCustomerIdOrderByIdDesc(customerId, page);
        } else {
            events = repo.findAllByOrderByIdDesc(page);
        }
        return events.stream().map(AuditService::toDto).toList();
    }

    private static AuditEventDto toDto(AuditEvent e) {
        return new AuditEventDto(e.getId(), e.getCreatedAt(), e.getConversationId(), e.getCustomerId(),
                e.getCustomerName(), e.getChannel(), e.getEventType(), e.getOperation(), e.getInput(),
                e.getOutcome(), e.getDetail(), e.getDurationMs(), e.getPromptTokens(), e.getCompletionTokens());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String oneLine = value.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max - 1) + "…";
    }
}
