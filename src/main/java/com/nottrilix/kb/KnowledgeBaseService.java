package com.nottrilix.kb;

import com.nottrilix.api.dto.KbHit;
import com.nottrilix.api.dto.KbSourceStatus;
import com.nottrilix.api.dto.KbStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Semanticka pretraga baze znanja (Spring AI VectorStore) + stanje baze. Kasnije je koristi agent kao RAG. */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger("KB");

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbc;
    private final KnowledgeBaseIngestor ingestor;
    private final KbProperties props;

    public KnowledgeBaseService(VectorStore vectorStore, JdbcTemplate jdbc, KnowledgeBaseIngestor ingestor,
                                KbProperties props) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.ingestor = ingestor;
        this.props = props;
    }

    /** Najslicniji odlomci za upit (topK <= 0 = zadani iz konfiguracije). */
    public List<KbHit> search(String query, int topK) {
        if (!ingestor.isAvailable()) {
            throw new KnowledgeBaseUnavailableException("Baza znanja nije dostupna: OPENAI_API_KEY nije postavljen");
        }
        String q = query == null ? "" : query.trim();
        int k = topK > 0 ? topK : props.topK();
        if (q.isEmpty()) {
            log.info("KB SEARCH prazan upit -> 0 hits");
            return List.of();
        }
        List<Document> documents;
        try {
            documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(q)
                    .topK(k)
                    .similarityThreshold(props.similarityThreshold())
                    .build());
        } catch (RuntimeException e) {
            log.error("KB SEARCH q='{}' neuspjela: {}", q, e.getMessage());
            throw new KnowledgeBaseUnavailableException("Baza znanja trenutno nije dostupna: " + e.getMessage());
        }
        List<KbHit> hits = documents.stream().map(KnowledgeBaseService::toHit).toList();
        if (hits.isEmpty()) {
            log.info("KB SEARCH q='{}' topK={} threshold={} -> 0 hits", q, k, props.similarityThreshold());
        } else {
            KbHit best = hits.getFirst();
            log.info("KB SEARCH q='{}' topK={} threshold={} -> {} hits (best: {} › {} score={})",
                    q, k, props.similarityThreshold(), hits.size(), best.source(), best.section(),
                    String.format("%.3f", best.score()));
        }
        return hits;
    }

    public KbStatus status() {
        List<KbSourceStatus> sources = jdbc.query(
                "select metadata->>'source' as source, max(metadata->>'title') as title, count(*) as chunks "
                        + "from vector_store group by 1 order by 1",
                (rs, i) -> new KbSourceStatus(rs.getString("source"), rs.getString("title"), rs.getLong("chunks")));
        long total = sources.stream().mapToLong(KbSourceStatus::chunks).sum();
        log.info("KB STATUS available={} chunks={} sources={}", ingestor.isAvailable(), total, sources.size());
        return new KbStatus(ingestor.isAvailable(), total, sources, ingestor.lastIngest(),
                props.topK(), props.similarityThreshold());
    }

    private static KbHit toHit(Document document) {
        Map<String, Object> meta = document.getMetadata();
        double score = document.getScore() == null ? 0.0 : document.getScore();
        return new KbHit(str(meta, "source"), str(meta, "title"), str(meta, "section"), str(meta, "category"),
                str(meta, "tags"), score, document.getText());
    }

    private static String str(Map<String, Object> meta, String key) {
        Object value = meta.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
