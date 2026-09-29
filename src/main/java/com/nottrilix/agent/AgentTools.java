package com.nottrilix.agent;

import com.nottrilix.agent.ConfirmationGate.PendingAction;
import com.nottrilix.api.dto.DailyLimitStatus;
import com.nottrilix.api.dto.PurchasePreview;
import com.nottrilix.api.dto.PurchaseResponse;
import com.nottrilix.api.dto.RefundPreview;
import com.nottrilix.api.dto.TicketDto;
import com.nottrilix.api.dto.TransactionDto;
import com.nottrilix.api.dto.VoucherCheckDto;
import com.nottrilix.api.dto.VoucherDto;
import com.nottrilix.api.dto.WalletDto;
import com.nottrilix.config.RulesProperties;
import com.nottrilix.domain.Channel;
import com.nottrilix.domain.TransactionStatus;
import com.nottrilix.domain.VoucherStatus;
import com.nottrilix.service.BusinessRuleException;
import com.nottrilix.service.CatalogService;
import com.nottrilix.service.NotFoundException;
import com.nottrilix.service.ShopService;
import com.nottrilix.service.SupportService;
import com.nottrilix.service.VoucherCheckService;
import com.nottrilix.service.WalletService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Granica alata (tool boundary) agenta: tanki adapteri nad postojecim servisima. Ovdje nema poslovnih pravila -
 * pravila su u servisima i vrijede jednako za web shop, admin i agenta.
 * <ul>
 *   <li>Svaki alat radi samo s podacima prijavljenog kupca (customerId dolazi iz ToolContexta, ne od modela).</li>
 *   <li>Izlaz nikad ne sadrzi puni aktivacijski kod (samo zadnje 4 znamenke) ni kolicinu zalihe.</li>
 *   <li>Kupnja, povrat i blokada prolaze kroz ConfirmationGate: prvi poziv samo predlaze, izvrsenje tek nakon
 *       potvrde kupca u iducem turnu (confirmPendingAction bez parametara - stanje drzi server).</li>
 *   <li>Cega ovdje nema, agent ne moze: uplata na wallet, goodwill, tudji podaci, admin radnje.</li>
 * </ul>
 */
@Component
public class AgentTools {

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
    private final ConfirmationGate gate;
    private final RulesProperties rules;
    private final Clock clock;
    private final DateTimeFormatter dates;

    public AgentTools(CatalogService catalogService, ShopService shopService, WalletService walletService,
                      SupportService supportService, VoucherCheckService voucherCheckService,
                      ConfirmationGate gate, RulesProperties rules, Clock clock) {
        this.catalogService = catalogService;
        this.shopService = shopService;
        this.walletService = walletService;
        this.supportService = supportService;
        this.voucherCheckService = voucherCheckService;
        this.gate = gate;
        this.rules = rules;
        this.clock = clock;
        this.dates = DateTimeFormatter.ofPattern("d.M.yyyy. HH:mm").withZone(clock.getZone());
    }

    // ---- sto model vidi (pogledi bez punog koda i bez kolicine zalihe) ----

    public record CatalogView(Long productId, String brand, String name, BigDecimal denominationEur,
                              boolean available) {
    }

    /**
     * refundDeadline i refundPossible su orijentir za razgovor; povrat uvijek provjerava servis.
     * redemptionDate nikad nije datum: za iskoristen bon upucuje na provjeru kod pruzatelja.
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

    // ---- citanje ----

    @Tool(description = "Katalog bonova koje NotTrilix prodaje: productId, brend, naziv, denominacija u EUR i je li "
            + "bon trenutno dostupan ili rasprodan. Koristi ga prije kupnje da nadjes productId.")
    public ToolResult getCatalog() {
        return guarded(() -> ToolResult.ok(catalogService.catalog().stream()
                .map(i -> new CatalogView(i.productId(), i.brandName(), i.name(), i.denominationEur(), i.available()))
                .toList()));
    }

    @Tool(description = "Kupnje prijavljenog kupca, najnovije prve: broj transakcije, datum, proizvod, iznos, kanal, "
            + "status kupnje, serijski broj bona, zadnje 4 znamenke aktivacijskog koda, status bona, rok za povrat "
            + "i je li povrat jos moguc. Datum iskoristenja bona nije u popisu: daje ga checkVoucherAtProvider.")
    public ToolResult getMyPurchases(ToolContext ctx) {
        long customerId = AgentContext.customerId(ctx);
        return guarded(() -> ToolResult.ok(shopService.customerTransactions(customerId).stream()
                .map(this::toPurchaseView).toList()));
    }

    @Tool(description = "Wallet prijavljenog kupca: stanje u EUR, zadnjih 10 promjena (uplate, kupnje, povrati) te "
            + "dnevni limit kupnje: koliko je danas vec kupljeno i koliko je jos dostupno.")
    public ToolResult getMyWallet(ToolContext ctx) {
        long customerId = AgentContext.customerId(ctx);
        return guarded(() -> {
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

    @Tool(description = "Provjera kod pruzatelja usluge (A1, HT, Steam, PlayStation, Xbox) je li bon aktivan, "
            + "iskoristen (s datumom) ili blokiran. Radi samo za bonove iz kupnji prijavljenog kupca.")
    public ToolResult checkVoucherAtProvider(
            @ToolParam(description = "Serijski broj bona, npr. STM-25-003") String voucherSerial, ToolContext ctx) {
        long customerId = AgentContext.customerId(ctx);
        return guarded(() -> {
            VoucherCheckDto check = voucherCheckService.checkForCustomer(customerId, normalize(voucherSerial));
            return ToolResult.ok(new VoucherCheckView(check.serial(), check.providerName(),
                    check.providerStatus().name(),
                    check.redeemedAt() == null ? null : dates.format(check.redeemedAt()),
                    check.providerReference(), check.message()));
        });
    }

    @Tool(description = "Prijave (ticketi) podrske prijavljenog kupca sa statusom i rjesenjem.")
    public ToolResult getMyTickets(ToolContext ctx) {
        long customerId = AgentContext.customerId(ctx);
        return guarded(() -> ToolResult.ok(supportService.ticketsForCustomer(customerId).stream()
                .map(this::toTicketView).toList()));
    }

    // ---- radnje koje traze potvrdu kupca ----

    @Tool(description = "Kupnja bona za prijavljenog kupca uz naplatu s njegovog walleta. Pozovi odmah kad kupac "
            + "zatrazi kupnju: prvi poziv nista ne kupuje, samo provjerava pravila i vraca razlog odbijanja ili "
            + "CONFIRMATION_REQUIRED sa sazetkom. Kupnja se izvrsava tek kad kupac potvrdi i pozove se "
            + "confirmPendingAction.")
    public ToolResult purchaseVoucher(@ToolParam(description = "productId iz kataloga") long productId,
                                      ToolContext ctx) {
        long customerId = AgentContext.customerId(ctx);
        return guarded(() -> {
            PurchasePreview preview = shopService.previewPurchase(customerId, productId);
            String summary = "kupnja %s, naplata %s € s walleta (stanje %s € -> %s €)".formatted(
                    preview.productName(), fmt(preview.priceEur()), fmt(preview.walletBalanceEur()),
                    fmt(preview.walletAfterEur()));
            if (!gate.confirmed(AgentContext.memoryKey(ctx), AgentContext.turn(ctx), PURCHASE,
                    String.valueOf(productId), summary)) {
                return ToolResult.confirmationRequired(summary, preview);
            }
            return executePurchase(customerId, productId);
        });
    }

    @Tool(description = "Povrat novca za kupnju prijavljenog kupca na njegov wallet; bon se pritom blokira. Moguce "
            + "samo unutar roka za povrat i ako bon nije iskoristen. Pozovi odmah kad kupac zatrazi povrat: prvi "
            + "poziv nista ne vraca, samo provjerava pravila i vraca razlog odbijanja ili CONFIRMATION_REQUIRED sa "
            + "sazetkom. Povrat se izvrsava tek kad kupac potvrdi i pozove se confirmPendingAction.")
    public ToolResult refundPurchase(
            @ToolParam(description = "Broj transakcije (kupnje) iz popisa kupnji kupca") long transactionId,
            ToolContext ctx) {
        long customerId = AgentContext.customerId(ctx);
        return guarded(() -> {
            RefundPreview preview = supportService.previewRefundForCustomer(customerId, transactionId);
            String summary = "povrat %s € za %s (kupnja broj %d) na wallet (stanje %s € -> %s €), bon %s se blokira"
                    .formatted(fmt(preview.amountEur()), preview.productName(), preview.transactionId(),
                            fmt(preview.walletBalanceEur()), fmt(preview.walletAfterEur()), preview.voucherSerial());
            if (!gate.confirmed(AgentContext.memoryKey(ctx), AgentContext.turn(ctx), REFUND,
                    String.valueOf(transactionId), summary)) {
                return ToolResult.confirmationRequired(summary, preview);
            }
            return executeRefund(customerId, transactionId);
        });
    }

    @Tool(description = "Blokada bona prijavljenog kupca, npr. kad sumnja da mu je kod ukraden. Blokiran bon se vise "
            + "ne moze iskoristiti, a blokada sama ne vraca novac. Pozovi odmah kad kupac zatrazi blokadu: prvi "
            + "poziv nista ne blokira, samo provjerava pravila i vraca razlog odbijanja ili CONFIRMATION_REQUIRED. "
            + "Blokada se izvrsava tek kad kupac potvrdi i pozove se confirmPendingAction.")
    public ToolResult blockVoucher(@ToolParam(description = "Serijski broj bona, npr. STM-25-003") String voucherSerial,
                                   ToolContext ctx) {
        long customerId = AgentContext.customerId(ctx);
        String serial = normalize(voucherSerial);
        return guarded(() -> {
            supportService.previewBlockForCustomer(customerId, serial);
            String summary = "blokada bona %s (kod se više neće moći iskoristiti; blokada ne vraća novac)"
                    .formatted(serial);
            if (!gate.confirmed(AgentContext.memoryKey(ctx), AgentContext.turn(ctx), BLOCK, serial, summary)) {
                return ToolResult.confirmationRequired(summary, new BlockDoneView(serial, "jos nije blokiran"));
            }
            return executeBlock(customerId, serial);
        });
    }

    @Tool(description = "Izvrsava radnju koja ceka potvrdu kupca: kupnju, povrat ili blokadu predlozenu ranije u "
            + "razgovoru. Pozovi kad kupac potvrdi (da, moze, potvrdjujem, slazem se). Nema parametara: server "
            + "zna koja radnja ceka i s kojim podacima.")
    public ToolResult confirmPendingAction(ToolContext ctx) {
        long customerId = AgentContext.customerId(ctx);
        String conversation = AgentContext.memoryKey(ctx);
        Optional<PendingAction> pending = gate.takeConfirmable(conversation, AgentContext.turn(ctx));
        if (pending.isEmpty()) {
            // radnja predlozena u ovom istom turnu: kupac jos nije imao priliku odgovoriti
            return gate.peek(conversation)
                    .map(waiting -> ToolResult.confirmationRequired(waiting.summary(), null))
                    .orElseGet(() -> ToolResult.denied("NOTHING_TO_CONFIRM", "Nema radnje koja čeka potvrdu kupca "
                            + "ili je prijedlog istekao. Ako kupac želi radnju, predloži je ponovno "
                            + "odgovarajućim alatom."));
        }
        PendingAction action = pending.get();
        ToolResult result = guarded(() -> switch (action.action()) {
            case PURCHASE -> executePurchase(customerId, Long.parseLong(action.params()));
            case REFUND -> executeRefund(customerId, Long.parseLong(action.params()));
            case BLOCK -> executeBlock(customerId, action.params());
            default -> ToolResult.denied("UNKNOWN_ACTION", "Nepoznata radnja: " + action.action());
        });
        // alat nema parametara, pa u rezultat (i time u audit) ide sto je tocno potvrdjeno
        return new ToolResult(result.ok(), result.status(), "%s Potvrđena radnja: %s %s (%s).".formatted(
                result.message(), action.action(), action.params(), action.summary()), result.data());
    }

    @Tool(description = "Odustajanje od radnje koja ceka potvrdu kupca. Pozovi kad kupac odbije ili se predomisli. "
            + "Nista se ne izvrsava.")
    public ToolResult cancelPendingAction(ToolContext ctx) {
        return gate.cancel(AgentContext.memoryKey(ctx))
                .map(action -> ToolResult.ok("Otkazano, ništa nije izvršeno: " + action.summary(), null))
                .orElseGet(() -> ToolResult.denied("NOTHING_TO_CANCEL", "Nema radnje koja čeka potvrdu kupca."));
    }

    // ---- izvrsenje (uvijek nakon potvrde; pravila se u servisima provjeravaju ponovno) ----

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

    // ---- radnja bez potvrde ----

    @Tool(description = "Otvaranje prijave (ticketa) podrske za prijavljenog kupca. Koristi kad problem ne mozes "
            + "rijesiti alatima ili kad treba odluka voditelja podrske (tada naslov pocni s 'Za voditelja:').")
    public ToolResult openSupportTicket(
            @ToolParam(description = "Kratak naslov problema") String subject,
            @ToolParam(description = "Opis: sto se dogodilo, serijski broj bona, sto je provjereno i dogovoreno")
            String description,
            @ToolParam(description = "Broj transakcije na koju se prijava odnosi, ako postoji", required = false)
            Long transactionId,
            ToolContext ctx) {
        long customerId = AgentContext.customerId(ctx);
        return guarded(() -> {
            TicketDto ticket = supportService.createTicket(customerId, transactionId, truncate(subject, 200),
                    truncate(description, 2000));
            return ToolResult.ok("Prijava je otvorena.", toTicketView(ticket));
        });
    }

    // ---- pomocno ----

    /** Odbijanje poslovnog pravila nije greska alata nego rezultat: model ga dobiva kao ok=false + razlog. */
    private static ToolResult guarded(Supplier<ToolResult> action) {
        try {
            return action.get();
        } catch (BusinessRuleException e) {
            return ToolResult.denied(e.code(), e.getMessage());
        } catch (NotFoundException e) {
            return ToolResult.denied("NOT_FOUND", e.getMessage());
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
