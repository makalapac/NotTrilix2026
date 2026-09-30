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

/** What the model concluded about one conversation (from the pseudonymized transcript). One row per conversation. */
@Entity
@Table(name = "conversation_analyses")
public class ConversationAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 60)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Sentiment sentiment;

    @Column(name = "quality_score", nullable = false)
    private int qualityScore;

    @Column(name = "rules_followed", nullable = false)
    private boolean rulesFollowed;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(name = "knowledge_gaps", length = 2000)
    private String knowledgeGaps;

    @Column(name = "improvement_notes", length = 2000)
    private String improvementNotes;

    @Column(nullable = false)
    private int turns;

    @Column(name = "tool_calls", nullable = false)
    private int toolCalls;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "duration_ms")
    private Long durationMs;

    protected ConversationAnalysis() {
    }

    public ConversationAnalysis(String conversationId, Long customerId, Instant createdAt, String model,
                                AnalysisCategory category, AnalysisOutcome outcome, Sentiment sentiment,
                                int qualityScore, boolean rulesFollowed, String summary, String knowledgeGaps,
                                String improvementNotes, int turns, int toolCalls, Integer promptTokens,
                                Integer completionTokens, Long durationMs) {
        this.conversationId = conversationId;
        this.customerId = customerId;
        this.createdAt = createdAt;
        this.model = model;
        this.category = category;
        this.outcome = outcome;
        this.sentiment = sentiment;
        this.qualityScore = qualityScore;
        this.rulesFollowed = rulesFollowed;
        this.summary = summary;
        this.knowledgeGaps = knowledgeGaps;
        this.improvementNotes = improvementNotes;
        this.turns = turns;
        this.toolCalls = toolCalls;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.durationMs = durationMs;
    }

    /** Anonymization (customer request or retention expiry): the analysis is kept for statistics, without a link to the customer. */
    public void anonymize() {
        this.customerId = null;
    }

    public Long getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getModel() {
        return model;
    }

    public AnalysisCategory getCategory() {
        return category;
    }

    public AnalysisOutcome getOutcome() {
        return outcome;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    public int getQualityScore() {
        return qualityScore;
    }

    public boolean isRulesFollowed() {
        return rulesFollowed;
    }

    public String getSummary() {
        return summary;
    }

    public String getKnowledgeGaps() {
        return knowledgeGaps;
    }

    public String getImprovementNotes() {
        return improvementNotes;
    }

    public int getTurns() {
        return turns;
    }

    public int getToolCalls() {
        return toolCalls;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public Long getDurationMs() {
        return durationMs;
    }
}
