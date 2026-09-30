package com.nottrilix.privacy;

/** Radnja trazi privolu kupca koja nije dana (npr. glasovni razgovor). API je mapira na HTTP 403 CONSENT_REQUIRED. */
public class ConsentRequiredException extends RuntimeException {

    public ConsentRequiredException(String message) {
        super(message);
    }
}
