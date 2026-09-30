package com.nottrilix.api.dto;

import java.time.Instant;
import java.util.List;

/** Knowledge base status: whether it is available (OpenAI key), how many chunks per document, when the last ingest was. */
public record KbStatus(boolean available, long chunks, List<KbSourceStatus> sources, Instant lastIngest,
                       int topK, double similarityThreshold) {
}
