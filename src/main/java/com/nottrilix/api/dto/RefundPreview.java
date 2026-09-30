package com.nottrilix.api.dto;

import java.math.BigDecimal;

/** What the refund would do (rules already checked), without executing it - for the customer's confirmation. */
public record RefundPreview(Long transactionId, String productName, BigDecimal amountEur, String voucherSerial,
                            BigDecimal walletBalanceEur, BigDecimal walletAfterEur) {
}
