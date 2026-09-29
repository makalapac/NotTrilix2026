package com.nottrilix.agent;

/** Agent nije dostupan (nema OpenAI kljuca ili model ne odgovara). API je mapira na HTTP 503. */
public class AgentUnavailableException extends RuntimeException {

    public AgentUnavailableException(String message) {
        super(message);
    }
}
