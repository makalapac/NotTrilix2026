package com.nottrilix.agent;

/** Aplikacija agenta nije dostupna (nije pokrenuta, ne odgovara ili je vratila gresku). API je mapira na HTTP 503. */
public class AgentUnavailableException extends RuntimeException {

    public AgentUnavailableException(String message) {
        super(message);
    }
}
