package com.nottrilix.analysis;

import com.nottrilix.domain.Conversation;
import com.nottrilix.domain.ConversationStatus;
import com.nottrilix.repo.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Utihnuli razgovori (bez nove poruke dulje od idle-minutes) idu na analizu: po rasporedu svake minute ili rucno
 * kroz admin API. Svaki razgovor je vlastita transakcija (poziv analyze() kroz proxy); greska jednog ne zaustavlja ostale.
 */
@Component
public class AnalysisScheduler {

    private static final Logger log = LoggerFactory.getLogger("ANALYSIS");

    private final ConversationAnalyzer analyzer;
    private final ConversationRepository conversations;
    private final AnalysisProperties props;
    private final Clock clock;

    public AnalysisScheduler(ConversationAnalyzer analyzer, ConversationRepository conversations,
                             AnalysisProperties props, Clock clock) {
        this.analyzer = analyzer;
        this.conversations = conversations;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${nottrilix.analysis.poll-seconds:60}000", initialDelayString = "60000")
    public void analyzeIdleConversations() {
        if (!props.enabled()) {
            return;
        }
        List<String> done = analyzeIdle();
        if (!done.isEmpty()) {
            log.info("ANALYSIS raspored: analizirano {} razgovora {}", done.size(), done);
        }
    }

    public List<String> analyzeIdle() {
        Instant before = Instant.now(clock).minusSeconds(props.idleMinutes() * 60L);
        List<Conversation> idle = conversations.findByStatusAndLastTurnAtBefore(ConversationStatus.OPEN, before);
        List<String> done = new ArrayList<>();
        for (Conversation conversation : idle) {
            try {
                analyzer.analyze(conversation.getId());
                done.add(conversation.getId());
            } catch (RuntimeException e) {
                log.warn("ANALYSIS conv={} preskocena: {}", conversation.getId(), e.getMessage());
            }
        }
        return done;
    }
}
