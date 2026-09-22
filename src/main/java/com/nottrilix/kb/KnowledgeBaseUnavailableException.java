package com.nottrilix.kb;

/** Baza znanja nije dostupna (nema OpenAI kljuca ili embedding servis ne odgovara). API je mapira na HTTP 503. */
public class KnowledgeBaseUnavailableException extends RuntimeException {

    public KnowledgeBaseUnavailableException(String message) {
        super(message);
    }
}
