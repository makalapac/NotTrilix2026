package com.nottrilix.mcp;

import com.nottrilix.api.dto.DailyLimitStatus;
import com.nottrilix.api.dto.KbHit;
import com.nottrilix.api.dto.PurchasePreview;
import com.nottrilix.api.dto.PurchaseResponse;
import com.nottrilix.api.dto.RefundPreview;
import com.nottrilix.api.dto.TicketDto;
import com.nottrilix.api.dto.TransactionDto;
import com.nottrilix.api.dto.VoucherCheckDto;
import com.nottrilix.api.dto.VoucherDto;
import com.nottrilix.api.dto.WalletDto;
import com.nottrilix.auth.AuthUser;
import com.nottrilix.config.RulesProperties;
import com.nottrilix.domain.AuditEventType;
import com.nottrilix.domain.AuditOutcome;
import com.nottrilix.domain.Channel;
import com.nottrilix.domain.TransactionStatus;
import com.nottrilix.domain.VoucherStatus;
import com.nottrilix.kb.KnowledgeBaseService;
import com.nottrilix.kb.KnowledgeBaseUnavailableException;
import com.nottrilix.mcp.ConfirmationGate.PendingAction;
import com.nottrilix.service.AuditService;
import com.nottrilix.service.AuditService.AuditContext;
import com.nottrilix.service.BusinessRuleException;
import com.nottrilix.service.CatalogService;
import com.nottrilix.service.NotFoundException;
import com.nottrilix.service.ShopService;
import com.nottrilix.service.SupportService;
import com.nottrilix.service.VoucherCheckService;
import com.nottrilix.service.WalletService;
import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The system's tool boundary, exposed as an MCP server: thin adapters over the existing services.
 * No business rules live here - they are in the services and apply equally to the web shop, admin and every MCP client.
 * <ul>
 *   <li>Every tool works only with the logged-in customer's data: the customer comes from the HTTP request token
 *       ({@link McpCall}), not from tool parameters and not from the model.</li>
 *   <li>Output never contains the full activation code (only the last 4 digits) or the stock quantity.</li>
 *   <li>Purchase, refund and block go through the ConfirmationGate: the first call only proposes; execution happens
 *       only after the customer confirms in the next turn (confirmPendingAction has no parameters - the server holds
 *       the state).</li>
 *   <li>What is not here, an MCP client cannot do: wallet top-up, goodwill, other customers' data, admin actions.</li>
 * </ul>
 * The agent application, not the model, calls the last three tools (whoAmI, getPendingAction, searchKnowledgeBase).
 */
@Component
public class SupportMcpTools {

    private static final String CODE_NOTE = "Puni aktivacijski kod kupac vidi u web shopu u odjeljku Moje kupnje.";
    private static final String REDEMPTION_DATE_HINT =
            "nije u ovom popisu; zna ga samo pružatelj usluge - dohvati ga alatom checkVoucherAtProvider";
    private static final String PURCHASE = "PURCHASE";
    private static final String REFUND = "REFUND";
    private static final String BLOCK = "BLOCK";

    private final CatalogService catalogService;
    private final ShopService shopService;
    private final WalletService walletService;
    private final SupportService supportService;
    private final VoucherCheckService voucherCheckService;
    private final KnowledgeBaseService knowledgeBase;
    private final AuditService audit;
    private final ConfirmationGate gate;
    private final RulesProperties rules;
    private final Clock clock;
    private final DateTimeFormatter dates;

    public SupportMcpTools(CatalogService catalogService, ShopService shopService, WalletService walletService,
                           SupportService supportService, VoucherCheckService voucherCheckService,
                           KnowledgeBaseService knowledgeBase, AuditService audit, ConfirmationGate gate,
                           RulesProperties rules, Clock clock) {
        this.catalogService = catalogService;
        this.shopService = shopService;
        this.walletService = walletService;
        this.supportService = supportService;
        this.voucherCheckService = voucherCheckService;
        this.knowledgeBase = knowledgeBase;
        this.audit = audit;
        this.gate = gate;
        this.rules = rules;
        this.clock = clock;
        this.dates = DateTimeFormatter.ofPattern("d.M.yyyy. HH:mm").withZone(clock.getZone());
    }

    // ---- what the client sees (views without the full code and without the stock quantity) ----

    public record CatalogView(Long productId, String brand, String name, BigDecimal denominationEur,
                              boolean available) {
    }

    /**
     * refundDeadline and refundPossible are a guide for the conversation; the refund is always verified by the service.
     * redemptionDate is never a date: for a redeemed voucher it points to the check at the provider.
     */
    public record PurchaseView(Long transactionId, String purchaseDate, String product, BigDecimal amountEur,
                               String channel, String purchaseStatus, String voucherSerial, String codeLast4,
                               String voucherStatus, String redemptionDate, String refundDeadline,
                               boolean refundPossible) {
    }

    public record WalletEntryView(String date, String type, BigDecimal amountEur, BigDecimal balanceAfterEur,
                                  String note) {
    }

    public record WalletView(BigDecimal balanceEur, BigDecimal dailyLimitEur, BigDecimal spentTodayEur,
                             BigDecimal remainingTodayEur, List<WalletEntryView> lastEntries) {
    }

    public record VoucherCheckView(String voucherSerial, String provider, String providerStatus, String redeemedAt,
                                   String providerReference, String message) {
    }

    public record PurchaseDoneView(Long transactionId, String product, BigDecimal amountEur, String voucherSerial,
                                   String codeLast4, BigDecimal walletBalanceEur, String note) {
    }

    public record RefundDoneView(Long transactionId, String product, BigDecimal refundedEur, String voucherSerial,
                                 String voucherStatus, BigDecimal walletBalanceEur) {
    }

    public record BlockDoneView(String voucherSerial, String voucherStatus) {
    }

    public record TicketView(Long ticketId, String date, String status, Long transactionId, String subject,
                             String resolution) {
    }

    public record CustomerView(Long customerId, String name, String email) {
    }

    public record PendingActionView(boolean pending, String action, String summary) {
    }

    public record KnowledgeView(String source, String title, String section, double score, String content) {
    }

    // ---- reading ----

    @McpTool(name = "getCatalog", description = "Katalog bonova koje NotTrilix prodaje: productId, brend, naziv, "
            + "denominacija u EUR i je li bon trenutno dostupan ili rasprodan. Koristi ga prije kupnje da nadjes "
            + "productId.",
            annotations = @McpAnnotations(title = "Katalog", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public ToolResult getCatalog() {
        return guarded(() -> ToolResult.ok(catalogService.catalog().stream()
                .map(i -> new CatalogView(i.productId(), i.brandName(), i.name(), i.denominationEur(), i.available()))
                .toList()));
    }

    @McpTool(name = "getMyPurchases", description = "Kupnje prijavljenog kupca, najnovije prve: broj transakcije, "
            + "datum, proizvod, iznos, kanal, status kupnje, serijski broj bona, zadnje 4 znamenke aktivacijskog "
            + "koda, status bona, rok za povrat i je li povrat jos moguc. Datum iskoristenja bona nije u popisu: "
            + "daje ga checkVoucherAtProvider.",
            annotations = @McpAnnotations(title = "Moje kupnje", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public ToolResult getMyPurchases(McpMeta meta) {
        return guarded(() -> {
            long customerId = call(meta).customerId();
            return ToolResult.ok(shopService.customerTransactions(customerId).stream()
                    .map(this::toPurchaseView).toList());
        });
    }

    @McpTool(name = "getMyWallet", description = "Wallet prijavljenog kupca: stanje u EUR, zadnjih 10 promjena "
            + "(uplate, kupnje, povrati) te dnevni limit kupnje: koliko je danas vec kupljeno i koliko je jos "
            + "dostupno.",
            annotations = @McpAnnotations(title = "Wallet i dnevni limit", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult getMyWallet(McpMeta meta) {
        return guarded(() -> {
            long customerId = call(meta).customerId();
            WalletDto wallet = walletService.wallet(customerId);
            DailyLimitStatus limit = shopService.dailyLimitStatus(customerId);
            List<WalletEntryView> entries = wallet.entries().stream().limit(10)
                    .map(e -> new WalletEntryView(dates.format(e.createdAt()), e.type().name(), e.amountEur(),
                            e.balanceAfterEur(), e.note()))
                    .toList();
            return ToolResult.ok(new WalletView(wallet.balanceEur(), limit.limitEur(), limit.spentTodayEur(),
                    limit.remainingEur(), entries));
        });
    }

    @McpTool(name = "checkVoucherAtProvider", description = "Provjera kod pruzatelja usluge (A1, HT, Steam, "
            + "PlayStation, Xbox) je li bon aktivan, iskoristen (s datumom) ili blokiran. Radi samo za bonove iz "
            + "kupnji prijavljenog kupca.",
            annotations = @McpAnnotations(title = "Provjera bona kod pružatelja", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = true))
    public ToolResult checkVoucherAtProvider(
            @McpToolParam(description = "Serijski broj bona, npr. STM-25-003; smije biti i bez crtica ili s razmacima, "
                    + "kako ga kupac kaže (STM25003)") String voucherSerial, McpMeta meta) {
        return guarded(() -> {
            long customerId = call(meta).customerId();
            VoucherCheckDto check = voucherCheckService.checkForCustomer(customerId, normalize(voucherSerial));
            return ToolResult.ok(new VoucherCheckView(check.serial(), check.providerName(),
                    check.providerStatus().name(),
                    check.redeemedAt() == null ? null : dates.format(check.redeemedAt()),
                    check.providerReference(), check.message()));
        });
    }

    @McpTool(name = "getMyTickets", description = "Prijave (ticketi) podrske prijavljenog kupca sa statusom i "
            + "rjesenjem.",
            annotations = @McpAnnotations(title = "Moje prijave", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public ToolResult getMyTickets(McpMeta meta) {
        return guarded(() -> {
            long customerId = call(meta).customerId();
            return ToolResult.ok(supportService.ticketsForCustomer(customerId).stream()
                    .map(this::toTicketView).toList());
        });
    }

    // ---- actions that require customer confirmation ----

    @McpTool(name = "purchaseVoucher", description = "Kupnja bona za prijavljenog kupca uz naplatu s njegovog "
            + "walleta. Pozovi odmah kad kupac zatrazi kupnju: prvi poziv nista ne kupuje, samo provjerava pravila "
            + "i vraca razlog odbijanja ili CONFIRMATION_REQUIRED sa sazetkom. Kupnja se izvrsava tek kad kupac "
            + "potvrdi i pozove se confirmPendingAction.",
            annotations = @McpAnnotations(title = "Kupnja bona (prijedlog)", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult purchaseVoucher(@McpToolParam(description = "productId iz kataloga") long productId,
                                      McpMeta meta) {
        return guarded(() -> {
            McpCall call = call(meta);
            long customerId = call.customerId();
            String conversation = call.conversationKey();
            int turn = call.turn();
            PurchasePreview preview = shopService.previewPurchase(customerId, productId);
            String summary = "kupnja %s, naplata %s € s walleta (stanje %s € -> %s €)".formatted(
                    preview.productName(), fmt(preview.priceEur()), fmt(preview.walletBalanceEur()),
                    fmt(preview.walletAfterEur()));
            if (!gate.confirmed(conversation, turn, PURCHASE, String.valueOf(productId), summary)) {
                return ToolResult.confirmationRequired(summary, preview);
            }
            return executePurchase(customerId, productId);
        });
    }

    @McpTool(name = "refundPurchase", description = "Povrat novca za kupnju prijavljenog kupca na njegov wallet; "
            + "bon se pritom blokira. Moguce samo unutar roka za povrat i ako bon nije iskoristen. Pozovi odmah kad "
            + "kupac zatrazi povrat: prvi poziv nista ne vraca, samo provjerava pravila i vraca razlog odbijanja "
            + "ili CONFIRMATION_REQUIRED sa sazetkom. Povrat se izvrsava tek kad kupac potvrdi i pozove se "
            + "confirmPendingAction.",
            annotations = @McpAnnotations(title = "Povrat novca (prijedlog)", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult refundPurchase(
            @McpToolParam(description = "Broj transakcije (kupnje) iz popisa kupnji kupca") long transactionId,
            McpMeta meta) {
        return guarded(() -> {
            McpCall call = call(meta);
            long customerId = call.customerId();
            String conversation = call.conversationKey();
            int turn = call.turn();
            RefundPreview preview = supportService.previewRefundForCustomer(customerId, transactionId);
            String summary = "povrat %s € za %s (kupnja broj %d) na wallet (stanje %s € -> %s €), bon %s se blokira"
                    .formatted(fmt(preview.amountEur()), preview.productName(), preview.transactionId(),
                            fmt(preview.walletBalanceEur()), fmt(preview.walletAfterEur()), preview.voucherSerial());
            if (!gate.confirmed(conversation, turn, REFUND, String.valueOf(transactionId), summary)) {
                return ToolResult.confirmationRequired(summary, preview);
            }
            return executeRefund(customerId, transactionId);
        });
    }

    @McpTool(name = "blockVoucher", description = "Blokada bona prijavljenog kupca, npr. kad sumnja da mu je kod "
            + "ukraden. Blokiran bon se vise ne moze iskoristiti, a blokada sama ne vraca novac. Pozovi odmah kad "
            + "kupac zatrazi blokadu: prvi poziv nista ne blokira, samo provjerava pravila i vraca razlog "
            + "odbijanja ili CONFIRMATION_REQUIRED. Blokada se izvrsava tek kad kupac potvrdi i pozove se "
            + "confirmPendingAction.",
            annotations = @McpAnnotations(title = "Blokada bona (prijedlog)", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult blockVoucher(
            @McpToolParam(description = "Serijski broj bona, npr. STM-25-003; smije biti i bez crtica ili s razmacima, "
                    + "kako ga kupac kaže (STM25003)") String voucherSerial, McpMeta meta) {
        String serial = normalize(voucherSerial);
        return guarded(() -> {
            McpCall call = call(meta);
            long customerId = call.customerId();
            String conversation = call.conversationKey();
            int turn = call.turn();
            supportService.previewBlockForCustomer(customerId, serial);
            String summary = "blokada bona %s (kod se više neće moći iskoristiti; blokada ne vraća novac)"
                    .formatted(serial);
            if (!gate.confirmed(conversation, turn, BLOCK, serial, summary)) {
                return ToolResult.confirmationRequired(summary, new BlockDoneView(serial, "jos nije blokiran"));
            }
            return executeBlock(customerId, serial);
        });
    }

    @McpTool(name = "confirmPendingAction", description = "Izvrsava radnju koja ceka potvrdu kupca: kupnju, povrat "
            + "ili blokadu predlozenu ranije u razgovoru. Pozovi kad kupac potvrdi (da, moze, potvrdjujem, slazem "
            + "se). Nema parametara: server zna koja radnja ceka i s kojim podacima.",
            annotations = @McpAnnotations(title = "Izvršenje potvrđene radnje", readOnlyHint = false,
                    destructiveHint = true, idempotentHint = false, openWorldHint = false))
    public ToolResult confirmPendingAction(McpMeta meta) {
        return guarded(() -> {
            McpCall call = call(meta);
            long customerId = call.customerId();
            String conversation = call.conversationKey();
            int turn = call.turn();
            Optional<PendingAction> pending = gate.takeConfirmable(conversation, turn);
            if (pending.isEmpty()) {
                // action proposed in this very turn: the customer has not yet had a chance to respond
                return gate.peek(conversation)
                        .map(waiting -> ToolResult.confirmationRequired(waiting.summary(), null))
                        .orElseGet(() -> ToolResult.denied("NOTHING_TO_CONFIRM", "Nema radnje koja čeka potvrdu "
                                + "kupca ili je prijedlog istekao. Ako kupac želi radnju, predloži je ponovno "
                                + "odgovarajućim alatom."));
            }
            PendingAction action = pending.get();
            ToolResult result = guarded(() -> switch (action.action()) {
                case PURCHASE -> executePurchase(customerId, Long.parseLong(action.params()));
                case REFUND -> executeRefund(customerId, Long.parseLong(action.params()));
                case BLOCK -> executeBlock(customerId, action.params());
                default -> ToolResult.denied("UNKNOWN_ACTION", "Nepoznata radnja: " + action.action());
            });
            // the tool has no parameters, so the result (and thus the audit) records exactly what was confirmed
            return new ToolResult(result.ok(), result.status(), "%s Potvrđena radnja: %s %s (%s).".formatted(
                    result.message(), action.action(), action.params(), action.summary()), result.data());
        });
    }

    @McpTool(name = "cancelPendingAction", description = "Odustajanje od radnje koja ceka potvrdu kupca. Pozovi kad "
            + "kupac odbije ili se predomisli. Nista se ne izvrsava.",
            annotations = @McpAnnotations(title = "Odustajanje od radnje", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult cancelPendingAction(McpMeta meta) {
        return guarded(() -> gate.cancel(call(meta).conversationKey())
                .map(action -> ToolResult.ok("Otkazano, ništa nije izvršeno: " + action.summary(), null))
                .orElseGet(() -> ToolResult.denied("NOTHING_TO_CANCEL", "Nema radnje koja čeka potvrdu kupca.")));
    }

    // ---- execution (always after confirmation; the services check the rules again) ----

    private ToolResult executePurchase(long customerId, long productId) {
        PurchaseResponse done = shopService.purchase(customerId, productId, Channel.AGENT);
        TransactionDto tx = done.transaction();
        return ToolResult.ok("Kupnja je izvršena.", new PurchaseDoneView(tx.id(), tx.productName(),
                tx.amountEur(), tx.voucher().serial(), last4(tx.voucher().code()), done.walletBalanceEur(),
                CODE_NOTE));
    }

    private ToolResult executeRefund(long customerId, long transactionId) {
        TransactionDto tx = supportService.refundForCustomer(customerId, transactionId);
        return ToolResult.ok("Povrat je izvršen.", new RefundDoneView(tx.id(), tx.productName(), tx.amountEur(),
                tx.voucher().serial(), tx.voucher().status().name(), walletService.balance(customerId)));
    }

    private ToolResult executeBlock(long customerId, String serial) {
        VoucherDto voucher = supportService.blockVoucherForCustomer(customerId, serial);
        return ToolResult.ok("Bon je blokiran.", new BlockDoneView(voucher.serial(), voucher.status().name()));
    }

    // ---- action without confirmation ----

    @McpTool(name = "openSupportTicket", description = "Otvaranje prijave (ticketa) podrske za prijavljenog kupca. "
            + "Koristi kad problem ne mozes rijesiti alatima ili kad treba odluka voditelja podrske (tada naslov "
            + "pocni s 'Za voditelja:').",
            annotations = @McpAnnotations(title = "Otvaranje prijave", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public ToolResult openSupportTicket(
            @McpToolParam(description = "Kratak naslov problema") String subject,
            @McpToolParam(description = "Opis: sto se dogodilo, serijski broj bona, sto je provjereno i dogovoreno")
            String description,
            @McpToolParam(description = "Broj transakcije na koju se prijava odnosi, ako postoji", required = false)
            Long transactionId,
            McpMeta meta) {
        return guarded(() -> {
            long customerId = call(meta).customerId();
            TicketDto ticket = supportService.createTicket(customerId, transactionId, truncate(subject, 200),
                    truncate(description, 2000));
            return ToolResult.ok("Prijava je otvorena.", toTicketView(ticket));
        });
    }

    // ---- tools for the agent application (not for the model) ----

    @McpTool(name = "whoAmI", description = "Za aplikaciju agenta: tko je kupac u cije ime se radi (prema tokenu).",
            annotations = @McpAnnotations(title = "Tko je kupac", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public ToolResult whoAmI(McpMeta meta) {
        return guarded(() -> {
            AuthUser customer = call(meta).customer();
            return ToolResult.ok(new CustomerView(customer.id(), customer.name(), customer.login()));
        });
    }

    @McpTool(name = "getPendingAction", description = "Za aplikaciju agenta: radnja koja u ovom razgovoru ceka "
            + "potvrdu kupca (ili da nista ne ceka).",
            annotations = @McpAnnotations(title = "Radnja koja čeka potvrdu", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult getPendingAction(McpMeta meta) {
        return guarded(() -> ToolResult.ok(gate.peek(call(meta).conversationKey())
                .map(action -> new PendingActionView(true, action.action(), action.summary()))
                .orElseGet(() -> new PendingActionView(false, null, null))));
    }

    @McpTool(name = "searchKnowledgeBase", description = "Pretraga baze znanja NotTrilix podrske: pravila, rokovi, "
            + "limiti i postupci. Vraca odlomke najslicnije upitu, s izvorom i slicnoscu 0..1.",
            annotations = @McpAnnotations(title = "Baza znanja", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public ToolResult searchKnowledgeBase(
            @McpToolParam(description = "Pitanje ili pojam, jezikom kupca") String query, McpMeta meta) {
        return guarded(() -> {
            McpCall call = call(meta);
            AuthUser customer = call.customer();
            long start = System.nanoTime();
            List<KbHit> hits;
            try {
                hits = knowledgeBase.search(query, 0);
            } catch (KnowledgeBaseUnavailableException e) {
                return ToolResult.denied("KB_UNAVAILABLE", e.getMessage());
            }
            audit.record(new AuditContext(call.conversationId(), customer.id(), customer.name(), call.channel()),
                    AuditEventType.KB_RETRIEVAL, "retrieve", query, AuditOutcome.OK,
                    hits.isEmpty() ? "0 odlomaka iznad praga" : hits.stream()
                            .map(h -> String.format(Locale.ROOT, "%s › %s (%.2f)", h.source(), h.section(),
                                    h.score()))
                            .collect(Collectors.joining("; ")),
                    (System.nanoTime() - start) / 1_000_000);
            return ToolResult.ok(hits.stream()
                    .map(h -> new KnowledgeView(h.source(), h.title(), h.section(), h.score(), h.content()))
                    .toList());
        });
    }

    // ---- helpers ----

    private static McpCall call(McpMeta meta) {
        Map<String, Object> values = meta == null ? null : meta.meta();
        return McpCall.current(values);
    }

    /** A business rule denial is not a tool error but a result: the client receives it as ok=false + reason. */
    private static ToolResult guarded(Supplier<ToolResult> action) {
        try {
            return action.get();
        } catch (BusinessRuleException e) {
            return ToolResult.denied(e.code(), e.getMessage());
        } catch (NotFoundException e) {
            return ToolResult.denied("NOT_FOUND", e.getMessage());
        } catch (McpCallException e) {
            return ToolResult.denied(e.code(), e.getMessage());
        }
    }

    private PurchaseView toPurchaseView(TransactionDto t) {
        Instant deadline = t.createdAt().plus(rules.refundWindowDays(), ChronoUnit.DAYS);
        boolean refundPossible = t.status() == TransactionStatus.COMPLETED
                && t.voucher().status() != VoucherStatus.REDEEMED
                && !Instant.now(clock).isAfter(deadline);
        String redemptionDate = t.voucher().status() == VoucherStatus.REDEEMED ? REDEMPTION_DATE_HINT : null;
        return new PurchaseView(t.id(), dates.format(t.createdAt()), t.productName(), t.amountEur(),
                t.channel().name(), t.status().name(), t.voucher().serial(), last4(t.voucher().code()),
                t.voucher().status().name(), redemptionDate, dates.format(deadline), refundPossible);
    }

    private TicketView toTicketView(TicketDto t) {
        return new TicketView(t.id(), dates.format(t.createdAt()), t.status().name(), t.transactionId(),
                t.subject(), t.resolution());
    }

    private static String last4(String code) {
        return code == null || code.length() < 4 ? "" : code.substring(code.length() - 4);
    }

    private static String normalize(String serial) {
        return serial == null ? "" : serial.trim().toUpperCase();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String fmt(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
