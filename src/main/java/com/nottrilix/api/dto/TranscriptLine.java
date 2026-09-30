package com.nottrilix.api.dto;

import java.time.Instant;

/** Jedan redak transkripta razgovora: KUPAC, AGENT, ALAT (poziv alata s ishodom) ili ZNANJE (dohvat iz baze znanja). */
public record TranscriptLine(int turn, String role, String text, Instant at) {
}
