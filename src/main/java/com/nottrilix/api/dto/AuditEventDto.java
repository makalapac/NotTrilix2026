package com.nottrilix.api.dto;

import com.nottrilix.domain.AuditEventType;
import com.nottrilix.domain.AuditOutcome;

import java.time.Instant;

public record AuditEventDto(Long id, Instant createdAt, String conversationId, Long customerId, String customerName,
                            String channel, AuditEventType eventType, String operation, String input,
                            AuditOutcome outcome, String detail, Long durationMs, Integer promptTokens,
                            Integer completionTokens) {
}
