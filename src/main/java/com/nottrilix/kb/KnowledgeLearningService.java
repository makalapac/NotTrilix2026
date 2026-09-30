package com.nottrilix.kb;

import com.nottrilix.api.dto.KbSuggestionDto;
import com.nottrilix.auth.AuthUser;
import com.nottrilix.domain.AuditEventType;
import com.nottrilix.domain.AuditOutcome;
import com.nottrilix.domain.KbSuggestion;
import com.nottrilix.domain.SuggestionStatus;
import com.nottrilix.repo.KbSuggestionRepository;
import com.nottrilix.service.AuditService;
import com.nottrilix.service.AuditService.AuditContext;
import com.nottrilix.service.BusinessRuleException;
import com.nottrilix.service.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Self-learning with a human in the loop: a suggestion from conversation analysis becomes part of the knowledge base
 * only once an admin approves it (and corrects it if needed). The approved chunk goes straight into vector_store and
 * applies to all future conversations; on startup it is reloaded from the database (idempotent).
 */
@Service
public class KnowledgeLearningService {

    private static final Logger log = LoggerFactory.getLogger("LEARN");

    private final KbSuggestionRepository suggestions;
    private final KnowledgeBaseIngestor ingestor;
    private final AuditService audit;
    private final Clock clock;

    public KnowledgeLearningService(KbSuggestionRepository suggestions, KnowledgeBaseIngestor ingestor,
                                    AuditService audit, Clock clock) {
        this.suggestions = suggestions;
        this.ingestor = ingestor;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<KbSuggestionDto> list(SuggestionStatus status) {
        List<KbSuggestion> list = status == null ? suggestions.findAllByOrderByCreatedAtDesc()
                : suggestions.findByStatusOrderByCreatedAtDesc(status);
        return list.stream().map(KnowledgeLearningService::toDto).toList();
    }

    @Transactional
    public KbSuggestionDto approve(long id, String question, String answer, AuthUser admin) {
        KbSuggestion suggestion = suggestions.findById(id)
                .orElseThrow(() -> new NotFoundException("Prijedlog " + id + " ne postoji"));
        if (suggestion.getStatus() != SuggestionStatus.PROPOSED) {
            throw new BusinessRuleException("SUGGESTION_ALREADY_DECIDED",
                    "Prijedlog " + id + " je već odlučen (" + suggestion.getStatus() + ")");
        }
        suggestion.approve(question.trim(), answer.trim(), admin.login(), Instant.now(clock));
        ingestor.ingestLearned(suggestion, true);
        audit.record(new AuditContext(suggestion.getConversationId(), null, null, "SYSTEM"), AuditEventType.ANALYSIS,
                "learn", question.trim(), AuditOutcome.OK, "odobrio " + admin.login() + ": " + answer.trim(), null);
        log.info("LEARN #{} odobren ({}): '{}' -> u bazi znanja kao {}", id, admin.login(), suggestion.getQuestion(),
                suggestion.source());
        return toDto(suggestion);
    }

    @Transactional
    public KbSuggestionDto reject(long id, AuthUser admin) {
        KbSuggestion suggestion = suggestions.findById(id)
                .orElseThrow(() -> new NotFoundException("Prijedlog " + id + " ne postoji"));
        if (suggestion.getStatus() != SuggestionStatus.PROPOSED) {
            throw new BusinessRuleException("SUGGESTION_ALREADY_DECIDED",
                    "Prijedlog " + id + " je već odlučen (" + suggestion.getStatus() + ")");
        }
        suggestion.reject(admin.login(), Instant.now(clock));
        audit.record(new AuditContext(suggestion.getConversationId(), null, null, "SYSTEM"), AuditEventType.ANALYSIS,
                "learn", suggestion.getQuestion(), AuditOutcome.DENIED, "odbio " + admin.login(), null);
        log.info("LEARN #{} odbijen ({}): '{}'", id, admin.login(), suggestion.getQuestion());
        return toDto(suggestion);
    }

    static KbSuggestionDto toDto(KbSuggestion s) {
        return new KbSuggestionDto(s.getId(), s.getCreatedAt(), s.getConversationId(), s.getQuestion(), s.getAnswer(),
                s.getStatus(), s.getDecidedAt(), s.getDecidedBy());
    }
}
