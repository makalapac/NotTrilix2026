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

/**
 * Proposed knowledge base section derived from a conversation (knowledge gap). The model proposes, a human decides:
 * only an approved suggestion enters the knowledge base. The content is generalized - no personal customer data.
 */
@Entity
@Table(name = "kb_suggestions")
public class KbSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    @Column(nullable = false, length = 500)
    private String question;

    @Column(nullable = false, length = 2000)
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SuggestionStatus status;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    protected KbSuggestion() {
    }

    public KbSuggestion(Instant createdAt, String conversationId, String question, String answer) {
        this.createdAt = createdAt;
        this.conversationId = conversationId;
        this.question = question;
        this.answer = answer;
        this.status = SuggestionStatus.PROPOSED;
    }

    /** The admin approved the (possibly corrected) text: from now on it is part of the knowledge base. */
    public void approve(String question, String answer, String decidedBy, Instant at) {
        this.question = question;
        this.answer = answer;
        this.status = SuggestionStatus.APPROVED;
        this.decidedBy = decidedBy;
        this.decidedAt = at;
    }

    public void reject(String decidedBy, Instant at) {
        this.status = SuggestionStatus.REJECTED;
        this.decidedBy = decidedBy;
        this.decidedAt = at;
    }

    /** Source of the section in vector_store; used to recognize a repeated ingest as already loaded. */
    public String source() {
        return "nauceno-" + id;
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

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public SuggestionStatus getStatus() {
        return status;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }
}
