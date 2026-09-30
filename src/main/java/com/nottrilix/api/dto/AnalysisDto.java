package com.nottrilix.api.dto;

import com.nottrilix.domain.AnalysisCategory;
import com.nottrilix.domain.AnalysisOutcome;
import com.nottrilix.domain.Sentiment;

import java.time.Instant;
import java.util.List;

/** Analysis of one conversation (what the model concluded from the pseudonymized transcript). */
public record AnalysisDto(Long id, String conversationId, Instant createdAt, String model, AnalysisCategory category,
                          AnalysisOutcome outcome, Sentiment sentiment, int qualityScore, boolean rulesFollowed,
                          String summary, List<String> knowledgeGaps, String improvementNotes, int turns,
                          int toolCalls, Integer promptTokens, Integer completionTokens, Long durationMs) {
}
