package com.nottrilix.domain;

public enum AuditEventType {
    /** Agent je pozvao alat (tool). */
    TOOL_CALL,
    /** Dohvat odlomaka iz baze znanja za pitanje kupca (RAG). */
    KB_RETRIEVAL,
    /** Jedan turn razgovora: poruka kupca i odgovor agenta. */
    CHAT_TURN
}
