package com.nottrilix.domain;

public enum WalletEntryType {
    /** Wallet top-up (customer by card - simulated, or support). */
    TOPUP,
    /** Debit of the purchase amount. */
    PURCHASE,
    /** Refund of the purchase amount to the wallet. */
    REFUND
}
