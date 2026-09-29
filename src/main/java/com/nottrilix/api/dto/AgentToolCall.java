package com.nottrilix.api.dto;

/** Jedan poziv alata u turnu: outcome = OK | DENIED | CONFIRMATION_REQUIRED | ERROR. */
public record AgentToolCall(String tool, String input, String outcome, long durationMs) {
}
