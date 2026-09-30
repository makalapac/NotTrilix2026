package com.nottrilix.domain;

public enum VoucherStatus {
    /** In stock, not yet sold. */
    AVAILABLE,
    /** Sold to a customer, code active. */
    SOLD,
    /** Redeemed by the customer at the provider (A1, Steam...). */
    REDEEMED,
    /** Blocked (complaint, refund, suspected abuse). */
    BLOCKED
}
