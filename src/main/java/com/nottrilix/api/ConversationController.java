package com.nottrilix.api;

import com.nottrilix.analysis.AnalysisScheduler;
import com.nottrilix.analysis.ConversationAnalyzer;
import com.nottrilix.api.dto.AnalysisDto;
import com.nottrilix.api.dto.ConversationDetailDto;
import com.nottrilix.api.dto.ConversationDto;
import com.nottrilix.api.dto.ConversationStats;
import com.nottrilix.service.ConversationService;
import com.nottrilix.service.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Conversations and their analysis for support (role ADMIN). */
@RestController
@RequestMapping("/api/admin/conversations")
@Tag(name = "Conversations", description = "Razgovori kupaca s agentom: transkript iz audit traga, post-analiza, statistika")
public class ConversationController {

    private final ConversationService conversations;
    private final ConversationAnalyzer analyzer;
    private final AnalysisScheduler scheduler;

    public ConversationController(ConversationService conversations, ConversationAnalyzer analyzer,
                                  AnalysisScheduler scheduler) {
        this.conversations = conversations;
        this.analyzer = analyzer;
        this.scheduler = scheduler;
    }

    @GetMapping
    @Operation(summary = "Razgovori, najnoviji prvi, s analizom ako postoji")
    public List<ConversationDto> list(@RequestParam(defaultValue = "100") int limit) {
        return conversations.list(limit);
    }

    @GetMapping("/stats")
    @Operation(summary = "Brojke: teme, ishodi, raspoloženje, prosječna ocjena, prijedlozi za bazu znanja")
    public ConversationStats stats() {
        return conversations.stats();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Jedan razgovor: transkript (iz audit traga) + analiza")
    public ConversationDetailDto detail(@PathVariable String id) {
        return conversations.detail(id).orElseThrow(() -> new NotFoundException("Razgovor " + id + " ne postoji"));
    }

    @PostMapping("/{id}/analyze")
    @Operation(summary = "Analiziraj razgovor sada (ne čekajući raspored)")
    public AnalysisDto analyze(@PathVariable String id) {
        return analyzer.analyze(id);
    }

    @PostMapping("/analyze-idle")
    @Operation(summary = "Analiziraj sve razgovore koji su utihnuli (isto što radi raspored svake minute)")
    public List<String> analyzeIdle() {
        return scheduler.analyzeIdle();
    }
}
