package com.nottrilix.service;

import com.nottrilix.api.dto.AnalysisDto;
import com.nottrilix.api.dto.ConversationDetailDto;
import com.nottrilix.api.dto.ConversationDto;
import com.nottrilix.api.dto.ConversationStats;
import com.nottrilix.api.dto.TranscriptLine;
import com.nottrilix.domain.AuditEvent;
import com.nottrilix.domain.AuditOutcome;
import com.nottrilix.domain.Conversation;
import com.nottrilix.domain.ConversationAnalysis;
import com.nottrilix.domain.ConversationStatus;
import com.nottrilix.domain.Customer;
import com.nottrilix.domain.SuggestionStatus;
import com.nottrilix.privacy.Pseudonymizer;
import com.nottrilix.repo.AuditEventRepository;
import com.nottrilix.repo.ConversationAnalysisRepository;
import com.nottrilix.repo.ConversationRepository;
import com.nottrilix.repo.CustomerRepository;
import com.nottrilix.repo.KbSuggestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Conversations as records: the conversation record (conversations table) + a transcript assembled from the audit trail.
 * The analysis transcript is pseudonymized (no customer identity); the admin and customer-export transcript is the original.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger("CONVERSATION");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Conversation transcript + what we know about it (for analysis). */
    public record Transcript(String conversationId, Long customerId, String customerName, String channel,
                            List<TranscriptLine> lines, int turns, int toolCalls) {

        /** Text for the model: line by line, without the customer's identity. */
        public String asText(boolean pseudonymized) {
            StringBuilder sb = new StringBuilder();
            for (TranscriptLine line : lines) {
                String text = pseudonymized ? Pseudonymizer.pseudonymize(line.text(), customerName) : line.text();
                sb.append('[').append(line.turn()).append("] ").append(line.role()).append(": ").append(text)
                        .append('\n');
            }
            return sb.toString();
        }
    }

    private final ConversationRepository conversations;
    private final ConversationAnalysisRepository analyses;
    private final AuditEventRepository audit;
    private final CustomerRepository customers;
    private final KbSuggestionRepository suggestions;
    private final Clock clock;

    public ConversationService(ConversationRepository conversations, ConversationAnalysisRepository analyses,
                               AuditEventRepository audit, CustomerRepository customers,
                               KbSuggestionRepository suggestions, Clock clock) {
        this.conversations = conversations;
        this.analyses = analyses;
        this.audit = audit;
        this.customers = customers;
        this.suggestions = suggestions;
        this.clock = clock;
    }

    /** Every turn (chat or voice) marks the conversation; own transaction so the trail survives even if the turn fails. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTurn(String conversationId, long customerId, String channel) {
        Instant now = Instant.now(clock);
        Conversation conversation = conversations.findById(conversationId)
                .orElseGet(() -> conversations.save(new Conversation(conversationId, customerId, channel, now)));
        conversation.turn(now, channel);
        log.debug("CONVERSATION {} turn {} channel={}", conversationId, conversation.getTurns(), channel);
    }

    /** Transcript from the audit trail: KUPAC / ALAT / ZNANJE / AGENT (customer/tool/knowledge/agent) per turn, as they happened. */
    @Transactional(readOnly = true)
    public Optional<Transcript> transcript(String conversationId) {
        Conversation conversation = conversations.findById(conversationId).orElse(null);
        if (conversation == null) {
            return Optional.empty();
        }
        String customerName = conversation.getCustomerId() == null ? null
                : customers.findById(conversation.getCustomerId()).map(Customer::getName).orElse(null);
        List<TranscriptLine> lines = new ArrayList<>();
        List<TranscriptLine> pendingTools = new ArrayList<>();
        int turn = 0;
        int toolCalls = 0;
        for (AuditEvent event : audit.findByConversationIdOrderByIdAsc(conversationId)) {
            switch (event.getEventType()) {
                case TOOL_CALL -> {
                    toolCalls++;
                    pendingTools.add(new TranscriptLine(turn + 1, "ALAT",
                            event.getOperation() + " -> " + event.getOutcome()
                                    + (event.getOutcome() == AuditOutcome.OK ? ""
                                    : " (" + shorten(event.getDetail(), 160) + ")"), event.getCreatedAt()));
                }
                case KB_RETRIEVAL -> pendingTools.add(new TranscriptLine(turn + 1, "ZNANJE",
                        shorten(event.getDetail(), 160), event.getCreatedAt()));
                case CHAT_TURN -> {
                    turn++;
                    lines.add(new TranscriptLine(turn, "KUPAC", event.getInput(), event.getCreatedAt()));
                    lines.addAll(pendingTools);
                    pendingTools.clear();
                    lines.add(new TranscriptLine(turn, "AGENT",
                            event.getOutcome() == AuditOutcome.OK ? event.getDetail()
                                    : "(greška: " + event.getDetail() + ")", event.getCreatedAt()));
                }
                default -> {
                    // ANALYSIS and PRIVACY are not part of the conversation
                }
            }
        }
        return Optional.of(new Transcript(conversationId, conversation.getCustomerId(), customerName,
                conversation.getChannel(), lines, turn, toolCalls));
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> list(int limit) {
        List<Conversation> page = conversations.findAllByOrderByLastTurnAtDesc(PageRequest.of(0, Math.clamp(limit, 1, 500)));
        Map<String, ConversationAnalysis> byConversation = analyses
                .findByConversationIdIn(page.stream().map(Conversation::getId).toList()).stream()
                .collect(Collectors.toMap(ConversationAnalysis::getConversationId, Function.identity()));
        Map<Long, String> names = customerNames(page.stream().map(Conversation::getCustomerId).toList());
        return page.stream()
                .map(c -> toDto(c, names.get(c.getCustomerId()), byConversation.get(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ConversationDetailDto> detail(String conversationId) {
        return transcript(conversationId).map(t -> {
            Conversation conversation = conversations.findById(conversationId).orElseThrow();
            ConversationAnalysis analysis = analyses.findByConversationId(conversationId).orElse(null);
            return new ConversationDetailDto(toDto(conversation, t.customerName(), analysis), t.lines());
        });
    }

    @Transactional(readOnly = true)
    public ConversationStats stats() {
        List<Conversation> all = conversations.findAll();
        List<ConversationAnalysis> analyzed = analyses.findAll();
        Map<String, Long> byCategory = new TreeMap<>();
        Map<String, Long> byOutcome = new TreeMap<>();
        Map<String, Long> bySentiment = new TreeMap<>();
        for (ConversationAnalysis a : analyzed) {
            byCategory.merge(a.getCategory().name(), 1L, Long::sum);
            byOutcome.merge(a.getOutcome().name(), 1L, Long::sum);
            bySentiment.merge(a.getSentiment().name(), 1L, Long::sum);
        }
        Double averageQuality = analyzed.isEmpty() ? null
                : analyzed.stream().mapToInt(ConversationAnalysis::getQualityScore).average().orElse(0);
        return new ConversationStats(all.size(),
                all.stream().filter(c -> c.getStatus() == ConversationStatus.OPEN).count(),
                all.stream().filter(c -> c.getStatus() == ConversationStatus.ANALYZED).count(),
                all.stream().filter(c -> c.getStatus() == ConversationStatus.ERASED).count(),
                byCategory, byOutcome, bySentiment, averageQuality,
                suggestions.findByStatusOrderByCreatedAtDesc(SuggestionStatus.PROPOSED).size(),
                suggestions.findByStatusOrderByCreatedAtDesc(SuggestionStatus.APPROVED).size());
    }

    private Map<Long, String> customerNames(List<Long> ids) {
        List<Long> known = ids.stream().filter(id -> id != null).distinct().toList();
        if (known.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (Customer customer : customers.findAllById(known)) {
            names.put(customer.getId(), customer.getName());
        }
        return names;
    }

    static ConversationDto toDto(Conversation c, String customerName, ConversationAnalysis analysis) {
        return new ConversationDto(c.getId(), c.getCustomerId(), customerName, c.getChannel(), c.getStatus(),
                c.getStartedAt(), c.getLastTurnAt(), c.getTurns(), analysis == null ? null : toDto(analysis));
    }

    public static AnalysisDto toDto(ConversationAnalysis a) {
        return new AnalysisDto(a.getId(), a.getConversationId(), a.getCreatedAt(), a.getModel(), a.getCategory(),
                a.getOutcome(), a.getSentiment(), a.getQualityScore(), a.isRulesFollowed(), a.getSummary(),
                gaps(a.getKnowledgeGaps()), a.getImprovementNotes(), a.getTurns(), a.getToolCalls(),
                a.getPromptTokens(), a.getCompletionTokens(), a.getDurationMs());
    }

    /** Knowledge gaps are stored in the database as a JSON list. */
    public static List<String> gaps(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, JSON.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JacksonException e) {
            return Collections.singletonList(json);
        }
    }

    public static String gapsJson(List<String> gaps) {
        try {
            return gaps == null || gaps.isEmpty() ? null : JSON.writeValueAsString(gaps);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static String shorten(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
