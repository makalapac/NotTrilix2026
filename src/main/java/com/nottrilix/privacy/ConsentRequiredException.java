package com.nottrilix.privacy;

/** The action requires a customer consent that has not been given (e.g. voice conversation). The API maps it to HTTP 403 CONSENT_REQUIRED. */
public class ConsentRequiredException extends RuntimeException {

    public ConsentRequiredException(String message) {
        super(message);
    }
}
