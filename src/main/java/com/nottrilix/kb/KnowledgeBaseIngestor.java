package com.nottrilix.kb;

import com.nottrilix.api.dto.KbIngestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ingest baze znanja: classpath:kb/*.md -> odlomci (KbMarkdownParser) -> embeddinzi -> vector_store (Spring AI VectorStore).
 * Idempotentno: hash datoteke je u metapodacima svakog odlomka; nepromijenjena datoteka se preskace,
 * promijenjena se brise (po izvoru) i ponovno ucitava.
 */
@Component
public class KnowledgeBaseIngestor {

    private static final Logger log = LoggerFactory.getLogger("KB");
    private static final String KB_PATTERN = "classpath:kb/*.md";

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbc;
    private final KbProperties props;
    private final Clock clock;
    private final boolean available;
    private volatile Instant lastIngest;

    public KnowledgeBaseIngestor(VectorStore vectorStore, JdbcTemplate jdbc, KbProperties props, Clock clock,
                                 @Value("${spring.ai.openai.api-key:not-set}") String openAiApiKey) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
        this.available = openAiApiKey != null && !openAiApiKey.isBlank() && !"not-set".equals(openAiApiKey);
    }

    public boolean isAvailable() {
        return available;
    }

    public Instant lastIngest() {
        return lastIngest;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!props.ingestOnStartup()) {
            log.info("KB INGEST pri startu iskljucen (nottrilix.kb.ingest-on-startup=false)");
            return;
        }
        if (!available) {
            log.warn("KB INGEST preskocen: OPENAI_API_KEY nije postavljen - baza znanja nije dostupna");
            return;
        }
        try {
            ingest(false);
        } catch (Exception e) {
            log.error("KB INGEST neuspio: {}", e.getMessage(), e);
        }
    }

    /** @param force true = ponovno ucitaj sve datoteke bez obzira na hash */
    public synchronized KbIngestResult ingest(boolean force) throws IOException {
        if (!available) {
            throw new KnowledgeBaseUnavailableException("Baza znanja nije dostupna: OPENAI_API_KEY nije postavljen");
        }
        Resource[] files = new PathMatchingResourcePatternResolver().getResources(KB_PATTERN);
        Arrays.sort(files, Comparator.comparing(Resource::getFilename));
        log.info("KB INGEST start: {} datoteka ({}), force={}", files.length, KB_PATTERN, force);

        int added = 0;
        int skipped = 0;
        int replaced = 0;
        for (Resource file : files) {
            String source = file.getFilename();
            List<KbChunk> chunks = KbMarkdownParser.parse(source, file.getContentAsString(StandardCharsets.UTF_8));
            if (chunks.isEmpty()) {
                log.warn("KB INGEST {}: nema odjeljaka (## naslova) - preskacem", source);
                continue;
            }
            String storedHash = storedFileHash(source);
            if (!force && chunks.getFirst().fileHash().equals(storedHash)) {
                skipped++;
                log.info("KB INGEST {} nepromijenjen ({} odjeljaka) - preskacem", source, chunks.size());
                continue;
            }
            if (storedHash != null) {
                vectorStore.delete(new FilterExpressionBuilder().eq("source", source).build());
                replaced++;
            }
            List<Document> documents = new ArrayList<>();
            for (KbChunk chunk : chunks) {
                documents.add(toDocument(chunk));
            }
            vectorStore.add(documents);
            added += documents.size();
            log.info("KB INGEST {} -> {} odjeljaka ({})", source, documents.size(),
                    storedHash == null ? "novo" : "zamijenjeno");
        }
        lastIngest = Instant.now(clock);
        long total = totalChunks();
        log.info("KB INGEST gotov: {} datoteka, {} novih odjeljaka, {} nepromijenjenih datoteka, {} zamijenjenih, ukupno u bazi {}",
                files.length, added, skipped, replaced, total);
        return new KbIngestResult(files.length, added, skipped, replaced, total);
    }

    public long totalChunks() {
        Long count = jdbc.queryForObject("select count(*) from vector_store", Long.class);
        return count == null ? 0 : count;
    }

    private String storedFileHash(String source) {
        return jdbc.query("select metadata->>'file_hash' from vector_store where metadata->>'source' = ? limit 1",
                rs -> rs.next() ? rs.getString(1) : null, source);
    }

    private static Document toDocument(KbChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", chunk.source());
        metadata.put("title", chunk.title());
        metadata.put("category", chunk.category());
        metadata.put("tags", String.join(", ", chunk.tags()));
        metadata.put("audience", chunk.audience());
        metadata.put("updated", chunk.updated());
        metadata.put("section", chunk.section());
        metadata.put("chunk_index", chunk.index());
        metadata.put("file_hash", chunk.fileHash());
        return new Document(chunk.content(), metadata);
    }
}
