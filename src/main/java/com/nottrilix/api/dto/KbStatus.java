package com.nottrilix.api.dto;

import java.time.Instant;
import java.util.List;

/** Stanje baze znanja: je li dostupna (OpenAI kljuc), koliko odlomaka po dokumentu, kad je zadnji ingest. */
public record KbStatus(boolean available, long chunks, List<KbSourceStatus> sources, Instant lastIngest,
                       int topK, double similarityThreshold) {
}
