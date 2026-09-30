package com.nottrilix.analysis;

import com.nottrilix.domain.AnalysisCategory;
import com.nottrilix.domain.AnalysisOutcome;
import com.nottrilix.domain.Sentiment;

import java.util.List;

/**
 * What the model returns for one conversation (Spring AI structured output: this record produces the JSON schema the model
 * must adhere to). Everything is generalized: no names, e-mails, serial numbers or codes.
 */
public record ConversationInsight(
        String summary,
        AnalysisCategory category,
        AnalysisOutcome outcome,
        Sentiment sentiment,
        int qualityScore,
        boolean rulesFollowed,
        List<String> knowledgeGaps,
        List<KnowledgeSuggestion> knowledgeSuggestions,
        String improvementNotes) {

    /** Knowledge base section suggestion: the question as a customer would ask it + a draft answer that the admin reviews. */
    public record KnowledgeSuggestion(String question, String answer) {
    }
}
