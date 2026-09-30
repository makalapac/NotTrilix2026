package com.nottrilix.api.dto;

import java.math.BigDecimal;

/** Purchase result: the transaction with the voucher + the customer's new wallet balance. */
public record PurchaseResponse(TransactionDto transaction, BigDecimal walletBalanceEur) {
}
