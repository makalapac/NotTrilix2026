package com.nottrilix.repo;

import com.nottrilix.domain.AuditEvent;
import com.nottrilix.domain.AuditEventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findAllByOrderByIdDesc(Pageable pageable);

    List<AuditEvent> findByConversationIdOrderByIdDesc(String conversationId, Pageable pageable);

    List<AuditEvent> findByCustomerIdOrderByIdDesc(Long customerId, Pageable pageable);

    /** Conversation transcript: turns and tool calls in the order they happened. */
    List<AuditEvent> findByConversationIdOrderByIdAsc(String conversationId);

    List<AuditEvent> findByCustomerIdAndEventTypeInOrderByIdAsc(Long customerId, Collection<AuditEventType> types);

    /** Erasure on customer request / retention expiry: conversation content (messages, replies, knowledge base retrievals). */
    @Modifying
    @Query("delete from AuditEvent e where e.customerId = :customerId and e.eventType in :types")
    int deleteByCustomerIdAndEventTypeIn(Long customerId, Collection<AuditEventType> types);

    @Modifying
    @Query("delete from AuditEvent e where e.eventType in :types and e.createdAt < :before")
    int deleteByEventTypeInAndCreatedAtBefore(Collection<AuditEventType> types, Instant before);

    /** Anonymization: the row stays (what the system did), but the customer's personal data is removed. */
    @Modifying
    @Query("update AuditEvent e set e.customerId = null, e.customerName = null where e.customerId = :customerId")
    int anonymizeByCustomerId(Long customerId);

    @Modifying
    @Query("update AuditEvent e set e.customerId = null, e.customerName = null "
            + "where e.customerId is not null and e.createdAt < :before")
    int anonymizeCreatedBefore(Instant before);
}
