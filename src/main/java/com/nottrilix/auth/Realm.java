package com.nottrilix.auth;

/** Dvije vrste korisnika sustava; svaka ima svoju prijavu i svoju ulogu (ROLE_CUSTOMER / ROLE_ADMIN). */
public enum Realm {
    /** Kupac - prijava e-mailom i lozinkom, koristi shop. */
    CUSTOMER,
    /** Admin/support - prijava korisnickim imenom i lozinkom, koristi admin konzolu. */
    ADMIN
}
