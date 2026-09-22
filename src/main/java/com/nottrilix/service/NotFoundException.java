package com.nottrilix.service;

/** Trazeni entitet ne postoji. API je mapira na HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
