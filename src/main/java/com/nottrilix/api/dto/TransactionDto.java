package com.nottrilix.api.dto;

import com.nottrilix.domain.Brand;
import com.nottrilix.domain.Channel;
import com.nottrilix.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionDto(Long id, Instant createdAt, Channel channel, TransactionStatus status,
                             Long customerId, String customerName,
                             Long productId, Brand brand, String productName, BigDecimal amountEur,
                             VoucherDto voucher, Instant refundedAt) {
}
