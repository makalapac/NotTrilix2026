package com.nottrilix.api.dto;

import java.time.Instant;

/** One line of the conversation transcript: KUPAC (customer), AGENT, ALAT (tool call with its outcome) or ZNANJE (knowledge base retrieval). */
public record TranscriptLine(int turn, String role, String text, Instant at) {
}
