package com.nottrilix.domain;

public enum WalletEntryType {
    /** Uplata na wallet (kupac karticom - simulirano, ili podrska). */
    TOPUP,
    /** Skidanje iznosa kupnje. */
    PURCHASE,
    /** Povrat iznosa kupnje na wallet. */
    REFUND
}
