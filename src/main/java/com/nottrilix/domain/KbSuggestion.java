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
 * Prijedlog odlomka baze znanja nastao iz razgovora (rupa u znanju). Model predlaze, covjek odlucuje:
 * tek odobren prijedlog ulazi u bazu znanja. Sadrzaj je poopcen - bez osobnih podataka kupca.
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

    /** Admin je (mozda ispravljen) tekst odobrio: od sada je dio baze znanja. */
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

    /** Izvor odlomka u vector_store; po njemu se ponovni ingest prepoznaje kao vec ucitan. */
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
