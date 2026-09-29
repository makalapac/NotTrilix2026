package com.nottrilix.repo;

import com.nottrilix.domain.AuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findAllByOrderByIdDesc(Pageable pageable);

    List<AuditEvent> findByConversationIdOrderByIdDesc(String conversationId, Pageable pageable);

    List<AuditEvent> findByCustomerIdOrderByIdDesc(Long customerId, Pageable pageable);
}
