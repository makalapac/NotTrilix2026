package com.nottrilix.api.dto;

import java.math.BigDecimal;

/** Sto bi kupnja napravila (pravila su vec provjerena), bez izvrsenja - za potvrdu kupca. */
public record PurchasePreview(Long productId, String productName, BigDecimal priceEur,
                              BigDecimal walletBalanceEur, BigDecimal walletAfterEur) {
}
