package com.nottrilix.privacy;

import com.nottrilix.api.dto.AnalysisDto;
import com.nottrilix.api.dto.ConsentDto;
import com.nottrilix.api.dto.ConversationExport;
import com.nottrilix.api.dto.CustomerDataExport;
import com.nottrilix.api.dto.EraseResult;
import com.nottrilix.api.dto.PrivacyOverview;
import com.nottrilix.api.dto.TranscriptLine;
import com.nottrilix.auth.AuthUser;
import com.nottrilix.domain.AuditEventType;
import com.nottrilix.domain.AuditOutcome;
import com.nottrilix.domain.Consent;
import com.nottrilix.domain.ConsentType;
import com.nottrilix.domain.Conversation;
import com.nottrilix.domain.ConversationAnalysis;
import com.nottrilix.repo.AuditEventRepository;
import com.nottrilix.repo.ConsentRepository;
import com.nottrilix.repo.ConversationAnalysisRepository;
import com.nottrilix.repo.ConversationRepository;
import com.nottrilix.service.AuditService;
import com.nottrilix.service.AuditService.AuditContext;
import com.nottrilix.service.ConversationService;
import com.nottrilix.service.ShopService;
import com.nottrilix.service.SupportService;
import com.nottrilix.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Prava kupca nad vlastitim podacima (GDPR): privola (cl. 7), pristup i prenosivost (cl. 15, 20), brisanje (cl. 17).
 * Sto se cuva zbog ugovora i racunovodstva (kupnje, wallet, bonovi) ostaje; sto je nastalo u razgovoru brise se.
 */
@Service
public class PrivacyService {

    private static final Logger log = LoggerFactory.getLogger("PRIVACY");
    private static final String CHANNEL = "SYSTEM";
    /** Sadrzaj razgovora: to se brise na zahtjev kupca i po isteku roka. */
    static final Set<AuditEventType> CONVERSATION_CONTENT = Set.of(AuditEventType.CHAT_TURN, AuditEventType.KB_RETRIEVAL);

    private final ConsentRepository consents;
    private final ConversationRepository conversations;
    private final ConversationAnalysisRepository analyses;
    private final AuditEventRepository auditEvents;
    private final ConversationService conversationService;
    private final ShopService shopService;
    private final WalletService walletService;
    private final SupportService supportService;
    private final AuditService audit;
    private final PrivacyProperties props;
    private final Clock clock;

    public PrivacyService(ConsentRepository consents, ConversationRepository conversations,
                          ConversationAnalysisRepository analyses, AuditEventRepository auditEvents,
                          ConversationService conversationService, ShopService shopService,
                          WalletService walletService, SupportService supportService, AuditService audit,
                          PrivacyProperties props, Clock clock) {
        this.consents = consents;
        this.conversations = conversations;
        this.analyses = analyses;
        this.auditEvents = auditEvents;
        this.conversationService = conversationService;
        this.shopService = shopService;
        this.walletService = walletService;
        this.supportService = supportService;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    // ---- privola ----

    @Transactional(readOnly = true)
    public boolean hasConsent(long customerId, ConsentType type) {
        return consents.findFirstByCustomerIdAndTypeAndWithdrawnAtIsNullOrderByGivenAtDesc(customerId, type)
                .filter(c -> type != ConsentType.VOICE || props.voiceConsentVersion().equals(c.getTextVersion()))
                .isPresent();
    }

    /** Glasovni razgovor bez privole nije moguc. */
    public void requireConsent(long customerId, ConsentType type) {
        if (!hasConsent(customerId, type)) {
            throw new ConsentRequiredException("Za glasovni razgovor potrebna je privola kupca (verzija "
                    + props.voiceConsentVersion() + ")");
        }
    }

    @Transactional
    public ConsentDto giveConsent(AuthUser customer, ConsentType type) {
        if (hasConsent(customer.id(), type)) {
            return consents.findFirstByCustomerIdAndTypeAndWithdrawnAtIsNullOrderByGivenAtDesc(customer.id(), type)
                    .map(PrivacyService::toDto).orElseThrow();
        }
        Consent consent = consents.save(new Consent(customer.id(), type, props.voiceConsentVersion(),
                Instant.now(clock)));
        audit.record(context(customer), AuditEventType.PRIVACY, "consent", type.name(), AuditOutcome.OK,
                "privola dana, verzija teksta " + consent.getTextVersion(), null);
        log.info("PRIVACY consent customer={} type={} version={}", customer.id(), type, consent.getTextVersion());
        return toDto(consent);
    }

    @Transactional
    public void withdrawConsent(AuthUser customer, ConsentType type) {
        Instant now = Instant.now(clock);
        consents.findFirstByCustomerIdAndTypeAndWithdrawnAtIsNullOrderByGivenAtDesc(customer.id(), type)
                .ifPresent(consent -> {
                    consent.withdraw(now);
                    audit.record(context(customer), AuditEventType.PRIVACY, "consent", type.name(), AuditOutcome.OK,
                            "privola povucena", null);
                    log.info("PRIVACY consent withdrawn customer={} type={}", customer.id(), type);
                });
    }

    // ---- pregled, izvoz, brisanje ----

    @Transactional(readOnly = true)
    public PrivacyOverview overview(AuthUser customer) {
        List<ConsentDto> given = consents.findByCustomerIdOrderByGivenAtDesc(customer.id()).stream()
                .map(PrivacyService::toDto).toList();
        return new PrivacyOverview(props.conversationRetentionDays(), props.analysisRetentionDays(),
                props.voiceConsentVersion(), hasConsent(customer.id(), ConsentType.VOICE), given,
                conversations.findByCustomerIdOrderByLastTurnAtDesc(customer.id()).size());
    }

    /** Pravo pristupa i prenosivosti: sve o kupcu u jednom JSON-u. Izvoz se biljezi u audit. */
    @Transactional(readOnly = true)
    public CustomerDataExport export(AuthUser customer) {
        long id = customer.id();
        List<ConversationExport> conversationExports = conversations.findByCustomerIdOrderByLastTurnAtDesc(id).stream()
                .map(c -> new ConversationExport(c.getId(), c.getChannel(), c.getStartedAt(), c.getLastTurnAt(),
                        c.getTurns(), conversationService.transcript(c.getId())
                        .map(t -> t.lines().stream()
                                .filter(l -> l.role().equals("KUPAC") || l.role().equals("AGENT"))
                                .toList())
                        .orElse(List.<TranscriptLine>of())))
                .toList();
        List<AnalysisDto> analysisExports = analyses.findByCustomerId(id).stream()
                .map(ConversationService::toDto).toList();
        CustomerDataExport export = new CustomerDataExport(Instant.now(clock), shopService.customer(id),
                walletService.wallet(id), shopService.customerTransactions(id), supportService.ticketsForCustomer(id),
                conversationExports, analysisExports,
                consents.findByCustomerIdOrderByGivenAtDesc(id).stream().map(PrivacyService::toDto).toList());
        audit.record(context(customer), AuditEventType.PRIVACY, "export", null, AuditOutcome.OK,
                "%d transakcija, %d razgovora, %d analiza".formatted(export.transactions().size(),
                        conversationExports.size(), analysisExports.size()), null);
        log.info("PRIVACY export customer={} transactions={} conversations={}", id, export.transactions().size(),
                conversationExports.size());
        return export;
    }

    /**
     * Pravo na brisanje (cl. 17) za ono sto nije potrebno za ugovor: poruke razgovora se brisu, ostali tragovi
     * (pozivi alata, analize) ostaju bez veze s kupcem. Kupnje, wallet i bonovi ostaju (ugovor, racunovodstvo).
     */
    @Transactional
    public EraseResult eraseConversations(AuthUser customer) {
        long id = customer.id();
        int deletedMessages = auditEvents.deleteByCustomerIdAndEventTypeIn(id, CONVERSATION_CONTENT);
        int anonymizedEvents = auditEvents.anonymizeByCustomerId(id);
        List<Conversation> own = conversations.findByCustomerIdOrderByLastTurnAtDesc(id);
        own.forEach(Conversation::erase);
        List<ConversationAnalysis> ownAnalyses = analyses.findByCustomerId(id);
        ownAnalyses.forEach(ConversationAnalysis::anonymize);
        EraseResult result = new EraseResult(deletedMessages, anonymizedEvents, own.size(), ownAnalyses.size());
        // sam zahtjev za brisanje ostaje zapisan uz kupca: dokaz da je proveden
        audit.record(context(customer), AuditEventType.PRIVACY, "erase", null, AuditOutcome.OK,
                "%d poruka obrisano, %d događaja anonimizirano, %d razgovora, %d analiza".formatted(
                        deletedMessages, anonymizedEvents, own.size(), ownAnalyses.size()), null);
        log.info("PRIVACY erase customer={} messages={} events={} conversations={} analyses={}", id, deletedMessages,
                anonymizedEvents, own.size(), ownAnalyses.size());
        return result;
    }

    private static AuditContext context(AuthUser customer) {
        return new AuditContext(null, customer.id(), customer.name(), CHANNEL);
    }

    private static ConsentDto toDto(Consent consent) {
        return new ConsentDto(consent.getType(), consent.getTextVersion(), consent.getGivenAt(),
                consent.getWithdrawnAt());
    }
}
