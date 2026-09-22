package com.nottrilix.api.dto;

import java.math.BigDecimal;

/** Rezultat kupnje: transakcija s bonom + novo stanje walleta kupca. */
public record PurchaseResponse(TransactionDto transaction, BigDecimal walletBalanceEur) {
}
