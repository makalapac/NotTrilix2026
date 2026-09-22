package com.nottrilix.api.dto;

import com.nottrilix.domain.TicketStatus;

import java.time.Instant;

public record TicketDto(Long id, Instant createdAt, TicketStatus status,
                        Long customerId, String customerName, Long transactionId,
                        String subject, String description, String resolution, Instant resolvedAt) {
}
