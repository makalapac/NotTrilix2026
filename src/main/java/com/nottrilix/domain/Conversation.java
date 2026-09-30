package com.nottrilix.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Frame of one customer-agent conversation (turns are in audit_events). The customer is a plain column, not a relation:
 * at the customer's request the conversation is anonymized (customer_id = null), and the row is kept for statistics.
 */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(nullable = false, length = 10)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_turn_at", nullable = false)
    private Instant lastTurnAt;

    @Column(nullable = false)
    private int turns;

    protected Conversation() {
    }

    public Conversation(String id, Long customerId, String channel, Instant startedAt) {
        this.id = id;
        this.customerId = customerId;
        this.channel = channel;
        this.status = ConversationStatus.OPEN;
        this.startedAt = startedAt;
        this.lastTurnAt = startedAt;
        this.turns = 0;
    }

    /** New turn: the conversation is open again (the earlier analysis is no longer complete). */
    public void turn(Instant at, String channel) {
        this.turns++;
        this.lastTurnAt = at;
        this.channel = channel;
        if (this.status != ConversationStatus.ERASED) {
            this.status = ConversationStatus.OPEN;
        }
    }

    public void analyzed() {
        if (this.status == ConversationStatus.OPEN) {
            this.status = ConversationStatus.ANALYZED;
        }
    }

    /** Erasure at the customer's request: the link to the customer disappears, the conversation record remains without personal data. */
    public void erase() {
        this.customerId = null;
        this.status = ConversationStatus.ERASED;
    }

    public String getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getChannel() {
        return channel;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastTurnAt() {
        return lastTurnAt;
    }

    public int getTurns() {
        return turns;
    }
}
