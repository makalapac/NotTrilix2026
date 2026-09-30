package com.nottrilix.repo;

import com.nottrilix.domain.Conversation;
import com.nottrilix.domain.ConversationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

    List<Conversation> findByStatusAndLastTurnAtBefore(ConversationStatus status, Instant before);

    List<Conversation> findByCustomerIdOrderByLastTurnAtDesc(Long customerId);

    List<Conversation> findAllByOrderByLastTurnAtDesc(Pageable pageable);

    List<Conversation> findByLastTurnAtBeforeAndStatusNot(Instant before, ConversationStatus status);
}
