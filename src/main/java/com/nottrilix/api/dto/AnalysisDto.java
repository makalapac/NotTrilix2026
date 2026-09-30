package com.nottrilix.api.dto;

import com.nottrilix.domain.AnalysisCategory;
import com.nottrilix.domain.AnalysisOutcome;
import com.nottrilix.domain.Sentiment;

import java.time.Instant;
import java.util.List;

/** Analiza jednog razgovora (sto je model zakljucio iz pseudonimiziranog transkripta). */
public record AnalysisDto(Long id, String conversationId, Instant createdAt, String model, AnalysisCategory category,
                          AnalysisOutcome outcome, Sentiment sentiment, int qualityScore, boolean rulesFollowed,
                          String summary, List<String> knowledgeGaps, String improvementNotes, int turns,
                          int toolCalls, Integer promptTokens, Integer completionTokens, Long durationMs) {
}
