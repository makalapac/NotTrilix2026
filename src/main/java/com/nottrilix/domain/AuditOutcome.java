package com.nottrilix.domain;

public enum AuditOutcome {
    /** Izvrseno. */
    OK,
    /** Odbilo poslovno pravilo ili podatak ne postoji / nije kupcev. */
    DENIED,
    /** Radnja je samo predlozena; ceka potvrdu kupca u iducem turnu. */
    CONFIRMATION_REQUIRED,
    /** Neocekivana greska; radnja nije izvrsena. */
    ERROR
}
