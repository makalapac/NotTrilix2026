package com.nottrilix.domain;

public enum AuditEventType {
    /** Agent je pozvao alat (tool). */
    TOOL_CALL,
    /** Dohvat odlomaka iz baze znanja za pitanje kupca (RAG). */
    KB_RETRIEVAL,
    /** Jedan turn razgovora: poruka kupca i odgovor agenta. */
    CHAT_TURN,
    /** Analiza razgovora i ucenje iz nje (back-office, bez kupca u razgovoru). */
    ANALYSIS,
    /** Privatnost: privola, izvoz podataka, brisanje na zahtjev, retencija. */
    PRIVACY
}
