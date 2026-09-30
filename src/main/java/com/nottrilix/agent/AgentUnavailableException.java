package com.nottrilix.agent;

/** The agent application is unavailable (not running, not responding or returned an error). The API maps it to HTTP 503. */
public class AgentUnavailableException extends RuntimeException {

    public AgentUnavailableException(String message) {
        super(message);
    }
}
