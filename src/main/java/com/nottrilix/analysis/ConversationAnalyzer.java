package com.nottrilix.analysis;

import com.nottrilix.analysis.ConversationInsight.KnowledgeSuggestion;
import com.nottrilix.api.dto.AnalysisDto;
import com.nottrilix.domain.AuditEventType;
import com.nottrilix.domain.AuditOutcome;
import com.nottrilix.domain.Conversation;
import com.nottrilix.domain.ConversationAnalysis;
import com.nottrilix.domain.ConversationStatus;
import com.nottrilix.domain.KbSuggestion;
import com.nottrilix.repo.ConversationAnalysisRepository;
import com.nottrilix.repo.ConversationRepository;
import com.nottrilix.repo.KbSuggestionRepository;
import com.nottrilix.service.AuditService;
import com.nottrilix.service.AuditService.AuditContext;
import com.nottrilix.service.ConversationService;
import com.nottrilix.service.ConversationService.Transcript;
import com.nottrilix.service.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Conversation post-analysis: pseudonymized transcript -> model -> structured analysis result (summary, category, outcome,
 * sentiment, score, knowledge gaps). A back-office job over the audit trail; the customer takes no part in it.
 * Knowledge gaps become knowledge base suggestions that the admin approves (self-learning with a human in the loop).
 * analyze() is one transaction per conversation; idle conversations are processed by AnalysisScheduler (called through the proxy).
 */
@Service
public class ConversationAnalyzer {

    private static final Logger log = LoggerFactory.getLogger("ANALYSIS");
    private static final String CHANNEL = "SYSTEM";

    static final String SYSTEM_PROMPT = """
            Ti si analitičar kvalitete u korisničkoj podršci tvrtke NotTrilix (prodaja bonova A1, HT, Steam, \
            PlayStation, Xbox; kupac plaća s walleta; povrat unutar 14 dana ako bon nije iskorišten; dnevni limit \
            150 €; agent ne smije otkriti puni aktivacijski kod ni stanje zalihe; kupnja, povrat i blokada traže \
            potvrdu kupca).
            Dobivaš transkript razgovora kupca s virtualnom agenticom Nolom. Identitet kupca je zamijenjen oznakama \
            ([KUPAC], [E-MAIL], [BON], [KOD]); ne pokušavaj ga rekonstruirati i ne spominji ga.
            Retci ALAT pokazuju što je agent napravio u sustavu i s kojim ishodom, retci ZNANJE što je našao u bazi \
            znanja ("0 odlomaka iznad praga" znači da baza znanja nije imala ništa o toj temi).
            Ocijeni razgovor iskreno i konkretno, na hrvatskom:
            - summary: 1-3 rečenice, što je kupac htio i kako je završilo.
            - category: glavna tema.
            - outcome: RIJESENO ako je kupac dobio ono što je tražio ili jasan odgovor po pravilima; NERIJESENO ako \
            nije; ESKALIRANO ako je otvorena prijava za voditelja; ODUSTAO ako je kupac odustao.
            - sentiment: raspoloženje kupca na kraju.
            - qualityScore: 1-5 za rad agenta (točnost, držanje pravila, jasnoća, bez suvišnog teksta).
            - rulesFollowed: false ako je agent otkrio kod, zalihu, tuđe podatke, izvršio radnju bez potvrde ili \
            izmislio pravilo.
            - knowledgeGaps: pitanja kupca na koja agent nije imao odgovor iz baze znanja (rekao da nema informaciju, \
            ili je ZNANJE bilo prazno a pitanje se ticalo pravila, cijena, postupaka). Prazno ako nema takvih.
            - knowledgeSuggestions: za svaku rupu u znanju jedan prijedlog odlomka baze znanja: question = pitanje \
            kako bi ga kupac postavio (poopćeno, bez osobnih podataka), answer = nacrt odgovora od 1-3 rečenice u \
            stilu postojećih pravila NotTrilixa. Ako ne znaš pravu politiku tvrtke, napiši nacrt i započni ga s \
            "PROVJERITI: ". Najviše 3.
            - improvementNotes: 1-2 rečenice što bi agent mogao bolje (ili "Ništa posebno.").
            """;

    private final ChatClient chatClient;
    private final ConversationService conversationService;
    private final ConversationRepository conversations;
    private final ConversationAnalysisRepository analyses;
    private final KbSuggestionRepository suggestions;
    private final AuditService audit;
    private final AnalysisProperties props;
    private final Clock clock;
    private final BeanOutputConverter<ConversationInsight> converter = new BeanOutputConverter<>(ConversationInsight.class);

    public ConversationAnalyzer(ChatClient analysisChatClient, ConversationService conversationService,
                                ConversationRepository conversations, ConversationAnalysisRepository analyses,
                                KbSuggestionRepository suggestions, AuditService audit, AnalysisProperties props,
                                Clock clock) {
        this.chatClient = analysisChatClient;
        this.conversationService = conversationService;
        this.conversations = conversations;
        this.analyses = analyses;
        this.suggestions = suggestions;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public AnalysisDto analyze(String conversationId) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Razgovor " + conversationId + " ne postoji"));
        Transcript transcript = conversationService.transcript(conversationId).orElseThrow();
        if (transcript.turns() == 0 || conversation.getStatus() == ConversationStatus.ERASED) {
            throw new NotFoundException("Razgovor " + conversationId + " nema poruka za analizu");
        }
        String text = transcript.asText(true);
        log.info("ANALYSIS start conv={} turns={} toolCalls={} znakova={}", conversationId, transcript.turns(),
                transcript.toolCalls(), text.length());
        long start = System.nanoTime();

        ChatClientResponse response;
        ConversationInsight insight;
        try {
            response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(u -> u.text("TRANSKRIPT:\n{transcript}\n\n{format}")
                            .param("transcript", text)
                            .param("format", converter.getFormat()))
                    .call()
                    .chatClientResponse();
            String content = response.chatResponse() == null || response.chatResponse().getResult() == null ? ""
                    : String.valueOf(response.chatResponse().getResult().getOutput().getText());
            insight = converter.convert(content);
        } catch (RuntimeException e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.error("ANALYSIS conv={} neuspjela nakon {} ms: {}", conversationId, ms, e.toString());
            audit.record(new AuditContext(conversationId, conversation.getCustomerId(), null, CHANNEL),
                    AuditEventType.ANALYSIS, "analyze", null, AuditOutcome.ERROR, e.getMessage(), ms);
            throw new AnalysisFailedException("Analiza razgovora nije uspjela: " + e.getMessage());
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        if (insight == null) {
            throw new AnalysisFailedException("Model nije vratio nalaz");
        }
        Usage usage = response.chatResponse() == null ? null : response.chatResponse().getMetadata().getUsage();
        String model = response.chatResponse() == null ? "?" : String.valueOf(response.chatResponse().getMetadata().getModel());

        analyses.findByConversationId(conversationId).ifPresent(analyses::delete);
        analyses.flush();
        ConversationAnalysis analysis = analyses.save(new ConversationAnalysis(conversationId,
                conversation.getCustomerId(), Instant.now(clock), model,
                insight.category(), insight.outcome(), insight.sentiment(),
                Math.clamp(insight.qualityScore(), 1, 5), insight.rulesFollowed(),
                truncate(insight.summary(), 1000), ConversationService.gapsJson(insight.knowledgeGaps()),
                truncate(insight.improvementNotes(), 2000), transcript.turns(), transcript.toolCalls(),
                usage == null ? null : usage.getPromptTokens(), usage == null ? null : usage.getCompletionTokens(),
                ms));
        conversation.analyzed();
        int proposed = propose(conversationId, insight.knowledgeSuggestions());

        String detail = "%s / %s / %s, ocjena %d, pravila %s, rupe u znanju %d, prijedloga %d: %s".formatted(
                insight.category(), insight.outcome(), insight.sentiment(), analysis.getQualityScore(),
                insight.rulesFollowed() ? "poštovana" : "PREKRŠENA",
                insight.knowledgeGaps() == null ? 0 : insight.knowledgeGaps().size(), proposed, insight.summary());
        audit.record(new AuditContext(conversationId, conversation.getCustomerId(), null, CHANNEL),
                AuditEventType.ANALYSIS, "analyze", text.length() + " znakova transkripta (pseudonimizirano)",
                AuditOutcome.OK, detail, ms, analysis.getPromptTokens(), analysis.getCompletionTokens());
        log.info("ANALYSIS conv={} OK {} ms -> {}", conversationId, ms, detail);
        return ConversationService.toDto(analysis);
    }

    /** Knowledge gaps -> suggestions for the admin; the same suggestion from the same conversation is not duplicated. */
    private int propose(String conversationId, List<KnowledgeSuggestion> proposals) {
        if (proposals == null) {
            return 0;
        }
        int count = 0;
        for (KnowledgeSuggestion proposal : proposals) {
            if (count >= props.maxSuggestionsPerConversation() || proposal == null
                    || proposal.question() == null || proposal.question().isBlank()
                    || proposal.answer() == null || proposal.answer().isBlank()) {
                continue;
            }
            String question = truncate(proposal.question().trim(), 500);
            if (suggestions.existsByConversationIdAndQuestionIgnoreCase(conversationId, question)) {
                continue;
            }
            KbSuggestion suggestion = suggestions.save(new KbSuggestion(Instant.now(clock), conversationId, question,
                    truncate(proposal.answer().trim(), 2000)));
            count++;
            log.info("LEARN prijedlog #{} iz conv={}: '{}'", suggestion.getId(), conversationId, question);
        }
        return count;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
