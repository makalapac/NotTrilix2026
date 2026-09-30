package com.nottrilix.api;

import com.nottrilix.api.dto.KbHit;
import com.nottrilix.api.dto.KbIngestResult;
import com.nottrilix.api.dto.KbStatus;
import com.nottrilix.api.dto.KbSuggestionDto;
import com.nottrilix.api.dto.SuggestionDecision;
import com.nottrilix.auth.AuthUser;
import com.nottrilix.domain.SuggestionStatus;
import com.nottrilix.kb.KnowledgeBaseIngestor;
import com.nottrilix.kb.KnowledgeBaseService;
import com.nottrilix.kb.KnowledgeLearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/** Baza znanja za podrsku (uloga ADMIN): semanticka pretraga, stanje, ponovni ingest, prijedlozi iz razgovora (samoucenje). */
@RestController
@RequestMapping("/api/admin/kb")
@Tag(name = "Knowledge base", description = "Baza znanja podrske (RAG): pretraga odlomaka, stanje, ingest")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;
    private final KnowledgeBaseIngestor ingestor;
    private final KnowledgeLearningService learning;

    public KnowledgeBaseController(KnowledgeBaseService service, KnowledgeBaseIngestor ingestor,
                                   KnowledgeLearningService learning) {
        this.service = service;
        this.ingestor = ingestor;
        this.learning = learning;
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

    @GetMapping("/suggestions")
    @Operation(summary = "Prijedlozi za bazu znanja nastali iz analize razgovora (samoučenje); bez statusa = svi")
    public List<KbSuggestionDto> suggestions(@RequestParam(required = false) SuggestionStatus status) {
        return learning.list(status);
    }

    @PostMapping("/suggestions/{id}/approve")
    @Operation(summary = "Odobri prijedlog (uz moguće ispravke teksta): odmah ulazi u bazu znanja")
    public KbSuggestionDto approve(@PathVariable long id, @Valid @RequestBody SuggestionDecision decision,
                                   @AuthenticationPrincipal AuthUser admin) {
        return learning.approve(id, decision.question(), decision.answer(), admin);
    }

    @PostMapping("/suggestions/{id}/reject")
    @Operation(summary = "Odbij prijedlog")
    public KbSuggestionDto reject(@PathVariable long id, @AuthenticationPrincipal AuthUser admin) {
        return learning.reject(id, admin);
    }
}
