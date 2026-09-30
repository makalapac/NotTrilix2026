package com.nottrilix.analysis;

import com.nottrilix.domain.AnalysisCategory;
import com.nottrilix.domain.AnalysisOutcome;
import com.nottrilix.domain.Sentiment;

import java.util.List;

/**
 * Sto model vraca za jedan razgovor (Spring AI structured output: iz ovog recorda nastaje JSON shema koju model
 * mora postovati). Sve je poopceno: bez imena, e-maila, serijskih brojeva i kodova.
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

    /** Prijedlog odlomka baze znanja: pitanje kako bi ga kupac postavio + nacrt odgovora koji admin provjerava. */
    public record KnowledgeSuggestion(String question, String answer) {
    }
}
