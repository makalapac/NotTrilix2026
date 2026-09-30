package com.nottrilix.provider;

/** Voucher state as seen by the service provider (A1, HT, Steam...). */
public enum ProviderVoucherStatus {
    /** Not yet activated by the provider (on our side: not sold). */
    NOT_ACTIVATED,
    /** Activated, not yet redeemed. */
    ACTIVE,
    /** Redeemed (with a date). */
    REDEEMED,
    /** Blocked at the provider - cannot be redeemed. */
    BLOCKED
}
