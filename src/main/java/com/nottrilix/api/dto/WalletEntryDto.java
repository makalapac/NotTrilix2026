package com.nottrilix.api.dto;

import com.nottrilix.domain.WalletEntryType;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletEntryDto(Long id, Instant createdAt, WalletEntryType type, BigDecimal amountEur,
                             BigDecimal balanceAfterEur, Long transactionId, String note) {
}
