package com.nottrilix.agent;

/** The agent cannot answer (no OpenAI key, or the model does not respond). The API maps it to HTTP 503. */
public class AgentUnavailableException extends RuntimeException {

    public AgentUnavailableException(String message) {
        super(message);
    }
}
