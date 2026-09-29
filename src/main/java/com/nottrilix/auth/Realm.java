package com.nottrilix.auth;

/** Vrste korisnika sustava; svaka ima svoju prijavu i svoju ulogu (ROLE_CUSTOMER / ROLE_ADMIN / ROLE_AGENT). */
public enum Realm {
    /** Kupac - prijava e-mailom i lozinkom, koristi shop. */
    CUSTOMER,
    /** Admin/support - prijava korisnickim imenom i lozinkom, koristi admin konzolu. */
    ADMIN,
    /**
     * Aplikacija agenta (MCP klijent) - predstavlja se svojim kljucem. Smije se spojiti na MCP server i vidjeti
     * popis alata; alati rade samo u ime prijavljenog kupca, dakle tek uz kupcev token.
     */
    AGENT
}
