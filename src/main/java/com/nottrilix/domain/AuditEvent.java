package com.nottrilix.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One row of the agent audit trail. No setters: a row is never modified after being written. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(nullable = false, length = 10)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private AuditEventType eventType;

    @Column(nullable = false, length = 64)
    private String operation;

    @Column(length = 1000)
    private String input;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditOutcome outcome;

    @Column(length = 2000)
    private String detail;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    protected AuditEvent() {
    }

    public AuditEvent(Instant createdAt, String conversationId, Long customerId, String customerName, String channel,
                      AuditEventType eventType, String operation, String input, AuditOutcome outcome, String detail,
                      Long durationMs, Integer promptTokens, Integer completionTokens) {
        this.createdAt = createdAt;
        this.conversationId = conversationId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.channel = channel;
        this.eventType = eventType;
        this.operation = operation;
        this.input = input;
        this.outcome = outcome;
        this.detail = detail;
        this.durationMs = durationMs;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getConversationId() {
        return conversationId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getChannel() {
        return channel;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public String getOperation() {
        return operation;
    }

    public String getInput() {
        return input;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public String getDetail() {
        return detail;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }
}
