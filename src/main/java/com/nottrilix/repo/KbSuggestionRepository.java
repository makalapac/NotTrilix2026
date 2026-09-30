package com.nottrilix.repo;

import com.nottrilix.domain.KbSuggestion;
import com.nottrilix.domain.SuggestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KbSuggestionRepository extends JpaRepository<KbSuggestion, Long> {

    List<KbSuggestion> findByStatusOrderByCreatedAtDesc(SuggestionStatus status);

    List<KbSuggestion> findAllByOrderByCreatedAtDesc();

    boolean existsByConversationIdAndQuestionIgnoreCase(String conversationId, String question);
}
