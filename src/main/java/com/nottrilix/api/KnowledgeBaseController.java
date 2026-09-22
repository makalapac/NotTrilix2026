package com.nottrilix.api;

import com.nottrilix.api.dto.KbHit;
import com.nottrilix.api.dto.KbIngestResult;
import com.nottrilix.api.dto.KbStatus;
import com.nottrilix.kb.KnowledgeBaseIngestor;
import com.nottrilix.kb.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/** Baza znanja za podrsku (uloga ADMIN): semanticka pretraga, stanje, ponovni ingest. */
@RestController
@RequestMapping("/api/admin/kb")
@Tag(name = "Knowledge base", description = "Baza znanja podrske (RAG): pretraga odlomaka, stanje, ingest")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;
    private final KnowledgeBaseIngestor ingestor;

    public KnowledgeBaseController(KnowledgeBaseService service, KnowledgeBaseIngestor ingestor) {
        this.service = service;
        this.ingestor = ingestor;
    }

    @GetMapping("/search")
    @Operation(summary = "Semantička pretraga: najsličniji odlomci baze znanja za upit (npr. 'kod ne radi')")
    public List<KbHit> search(@RequestParam("q") String query, @RequestParam(defaultValue = "0") int topK) {
        return service.search(query, topK);
    }

    @GetMapping("/status")
    @Operation(summary = "Stanje baze znanja: dostupnost, broj odlomaka po dokumentu, zadnji ingest")
    public KbStatus status() {
        return service.status();
    }

    @PostMapping("/ingest")
    @Operation(summary = "Ponovni ingest dokumenata (force=true učitava sve, inače samo promijenjene)")
    public KbIngestResult ingest(@RequestParam(defaultValue = "true") boolean force) throws IOException {
        return ingestor.ingest(force);
    }
}
