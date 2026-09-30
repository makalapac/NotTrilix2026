package com.nottrilix.agent;

/** One tool call within a turn: outcome = OK | DENIED | CONFIRMATION_REQUIRED | ERROR. */
public record AgentToolCall(String tool, String input, String outcome, long durationMs) {
}
