package com.nottrilix.repo;

import com.nottrilix.domain.ConversationAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationAnalysisRepository extends JpaRepository<ConversationAnalysis, Long> {

    Optional<ConversationAnalysis> findByConversationId(String conversationId);

    List<ConversationAnalysis> findByCustomerId(Long customerId);

    List<ConversationAnalysis> findByConversationIdIn(List<String> conversationIds);
}
