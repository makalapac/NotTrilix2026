package com.nottrilix.service;

/** The requested entity does not exist. The API maps it to HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
