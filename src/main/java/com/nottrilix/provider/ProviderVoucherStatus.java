package com.nottrilix.provider;

/** Stanje bona kako ga vidi pruzatelj usluge (A1, HT, Steam...). */
public enum ProviderVoucherStatus {
    /** Pruzatelj ga jos nije aktivirao (kod nas: nije prodan). */
    NOT_ACTIVATED,
    /** Aktiviran, jos nije iskoristen. */
    ACTIVE,
    /** Iskoristen (s datumom). */
    REDEEMED,
    /** Blokiran kod pruzatelja - ne moze se iskoristiti. */
    BLOCKED
}
