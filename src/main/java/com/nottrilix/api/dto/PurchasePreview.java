package com.nottrilix.api.dto;

import java.math.BigDecimal;

/** What the purchase would do (rules already checked), without executing it - for the customer's confirmation. */
public record PurchasePreview(Long productId, String productName, BigDecimal priceEur,
                              BigDecimal walletBalanceEur, BigDecimal walletAfterEur) {
}
