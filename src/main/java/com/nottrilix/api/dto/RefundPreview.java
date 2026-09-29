package com.nottrilix.api.dto;

import java.math.BigDecimal;

/** Sto bi povrat napravio (pravila su vec provjerena), bez izvrsenja - za potvrdu kupca. */
public record RefundPreview(Long transactionId, String productName, BigDecimal amountEur, String voucherSerial,
                            BigDecimal walletBalanceEur, BigDecimal walletAfterEur) {
}
