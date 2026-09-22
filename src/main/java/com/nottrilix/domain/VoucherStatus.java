package com.nottrilix.domain;

public enum VoucherStatus {
    /** Na zalihi, jos nije prodan. */
    AVAILABLE,
    /** Prodan kupcu, kod aktivan. */
    SOLD,
    /** Kupac ga je iskoristio kod providera (A1, Steam...). */
    REDEEMED,
    /** Blokiran (reklamacija, povrat, sumnja na zlouporabu). */
    BLOCKED
}
