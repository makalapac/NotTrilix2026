package com.nottrilix.kb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the connection to the embedding provider warm. After a few idle minutes the first retrieval of a call took
 * about a second (a fresh connection), the next ones 150 ms; one tiny embedding every half minute keeps every first
 * turn fast. Nothing is logged on the happy path, so the console stays readable during a demo.
 */
@Component
class KnowledgeBaseWarmup {

    private static final Logger log = LoggerFactory.getLogger("KB");

    private final EmbeddingModel embeddings;
    private final KnowledgeBaseIngestor ingestor;

    KnowledgeBaseWarmup(ObjectProvider<EmbeddingModel> embeddings, KnowledgeBaseIngestor ingestor) {
        this.embeddings = embeddings.getIfAvailable();
        this.ingestor = ingestor;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT30S")
    void keepWarm() {
        if (embeddings == null || !ingestor.isAvailable()) {
            return;
        }
        try {
            long start = System.nanoTime();
            embeddings.embed("bon");
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (ms > 700) {
                log.info("KB warm-up: embedding trajao {} ms", ms);
            }
        } catch (RuntimeException e) {
            log.debug("KB warm-up preskočen: {}", e.getMessage());
        }
    }
}
