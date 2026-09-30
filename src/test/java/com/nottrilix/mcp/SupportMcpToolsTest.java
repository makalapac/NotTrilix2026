package com.nottrilix.mcp;

import com.nottrilix.api.dto.KbHit;
import com.nottrilix.api.dto.PurchasePreview;
import com.nottrilix.api.dto.PurchaseResponse;
import com.nottrilix.api.dto.TransactionDto;
import com.nottrilix.api.dto.VoucherDto;
import com.nottrilix.auth.AuthUser;
import com.nottrilix.auth.Realm;
import com.nottrilix.config.RulesProperties;
import com.nottrilix.domain.AuditEventType;
import com.nottrilix.domain.AuditOutcome;
import com.nottrilix.domain.Brand;
import com.nottrilix.domain.Channel;
import com.nottrilix.domain.TransactionStatus;
import com.nottrilix.domain.VoucherStatus;
import com.nottrilix.kb.KnowledgeBaseService;
import com.nottrilix.service.AuditService;
import com.nottrilix.service.AuditService.AuditContext;
import com.nottrilix.service.BusinessRuleException;
import com.nottrilix.service.CatalogService;
import com.nottrilix.service.NotFoundException;
import com.nottrilix.service.ShopService;
import com.nottrilix.service.SupportService;
import com.nottrilix.service.VoucherCheckService;
import com.nottrilix.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The tool boundary over MCP: the customer comes from the login (token), the conversation from _meta; no full code
 * in the output, actions only after confirmation, a rule denial = a result and not an error.
 */
@ExtendWith(MockitoExtension.class)
class SupportMcpToolsTest {

    private static final String FULL_CODE = "123456789012";
    private static final AuthUser ANA = new AuthUser(Realm.CUSTOMER, 1L, "Ana Anić", "ana.anic@example.com");
    private static final AuthUser MARKO = new AuthUser(Realm.CUSTOMER, 2L, "Marko Marić", "marko.maric@example.com");
    private static final AuthUser AGENT = new AuthUser(Realm.AGENT, 0L, "NotTrilix agent", "agent");

    @Mock
    private CatalogService catalogService;
    @Mock
    private ShopService shopService;
    @Mock
    private WalletService walletService;
    @Mock
    private SupportService supportService;
    @Mock
    private VoucherCheckService voucherCheckService;
    @Mock
    private KnowledgeBaseService knowledgeBase;
    @Mock
    private AuditService audit;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneId.of("Europe/Zagreb"));
    private SupportMcpTools tools;

    @BeforeEach
    void setUp() {
        tools = new SupportMcpTools(catalogService, shopService, walletService, supportService, voucherCheckService,
                knowledgeBase, audit, new ConfirmationGate(new McpProperties("test-key", 10), clock),
                new RulesProperties(new BigDecimal("150"), 14, new BigDecimal("500")), clock);
        loggedIn(ANA);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void loggedIn(AuthUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
    }

    /** The _meta the agent application sends: conversation and turn sequence number. */
    private static McpMeta turn(int turn) {
        return new McpMeta(Map.of(McpCall.CONVERSATION_ID, "abc12345", McpCall.TURN, turn, McpCall.CHANNEL, "CHAT"));
    }

    private TransactionDto purchase(long id, Instant createdAt, VoucherStatus voucherStatus) {
        return new TransactionDto(id, createdAt, Channel.WEB, TransactionStatus.COMPLETED, 1L, "Ana Anić",
                8L, Brand.STEAM, "Steam Wallet 25 €", new BigDecimal("25.00"),
                new VoucherDto("STM-25-00" + id, FULL_CODE, voucherStatus, createdAt), null);
    }

    private void steam25Preview() {
        when(shopService.previewPurchase(1L, 8L)).thenReturn(new PurchasePreview(8L, "Steam Wallet 25 €",
                new BigDecimal("25.00"), new BigDecimal("115.00"), new BigDecimal("90.00")));
    }

    @Test
    void purchasesNeverExposeFullActivationCode() {
        when(shopService.customerTransactions(1L)).thenReturn(List.of(
                purchase(1, clock.instant().minusSeconds(3 * 86400), VoucherStatus.SOLD),
                purchase(2, clock.instant().minusSeconds(20 * 86400), VoucherStatus.SOLD),
                purchase(3, clock.instant().minusSeconds(86400), VoucherStatus.REDEEMED)));

        ToolResult result = tools.getMyPurchases(turn(1));

        assertThat(result.ok()).isTrue();
        assertThat(result.toString()).doesNotContain(FULL_CODE);
        @SuppressWarnings("unchecked")
        List<SupportMcpTools.PurchaseView> views = (List<SupportMcpTools.PurchaseView>) result.data();
        assertThat(views).extracting(SupportMcpTools.PurchaseView::codeLast4).containsOnly("9012");
        assertThat(views).extracting(SupportMcpTools.PurchaseView::refundPossible)
                .containsExactly(true, false, false);
        // only the provider knows the redemption date: the purchase list points to the check instead of the model guessing
        assertThat(views.get(0).redemptionDate()).isNull();
        assertThat(views.get(2).redemptionDate()).contains("checkVoucherAtProvider");
    }

    @Test
    void customerComesFromTheTokenNotFromTheClient() {
        loggedIn(MARKO);
        when(shopService.customerTransactions(2L)).thenReturn(List.of());

        assertThat(tools.getMyPurchases(turn(1)).ok()).isTrue();

        verify(shopService).customerTransactions(2L);
    }

    @Test
    void withoutCustomerTokenToolsDoNothing() {
        loggedIn(AGENT); // the agent connected with its key, but without a customer token

        ToolResult asAgent = tools.getMyPurchases(turn(1));
        SecurityContextHolder.clearContext();
        ToolResult anonymous = tools.purchaseVoucher(8L, turn(1));

        assertThat(asAgent.status()).isEqualTo("CUSTOMER_REQUIRED");
        assertThat(anonymous.status()).isEqualTo("CUSTOMER_REQUIRED");
        verifyNoInteractions(shopService);
    }

    @Test
    void purchaseIsOnlyProposedUntilCustomerHadATurn() {
        steam25Preview();

        ToolResult proposed = tools.purchaseVoucher(8L, turn(1));
        ToolResult sameTurnAgain = tools.purchaseVoucher(8L, turn(1));

        assertThat(proposed.ok()).isFalse();
        assertThat(proposed.status()).isEqualTo(ToolResult.CONFIRMATION_REQUIRED);
        assertThat(proposed.message()).contains("Steam Wallet 25 €").contains("115").contains("90");
        assertThat(sameTurnAgain.status()).isEqualTo(ToolResult.CONFIRMATION_REQUIRED);
        verify(shopService, never()).purchase(anyLong(), anyLong(), any());
    }

    @Test
    void confirmationWithoutParametersExecutesTheProposedPurchaseThroughAgentChannel() {
        steam25Preview();
        when(shopService.purchase(1L, 8L, Channel.AGENT)).thenReturn(
                new PurchaseResponse(purchase(9, clock.instant(), VoucherStatus.SOLD), new BigDecimal("90.00")));
        tools.purchaseVoucher(8L, turn(1));

        ToolResult tooEarly = tools.confirmPendingAction(turn(1));
        ToolResult pending = tools.getPendingAction(turn(1));
        ToolResult done = tools.confirmPendingAction(turn(2));
        ToolResult again = tools.confirmPendingAction(turn(3));

        assertThat(tooEarly.status()).isEqualTo(ToolResult.CONFIRMATION_REQUIRED);
        assertThat(pending.data()).isEqualTo(new SupportMcpTools.PendingActionView(true, "PURCHASE",
                "kupnja Steam Wallet 25 €, naplata 25 € s walleta (stanje 115 € -> 90 €)"));
        assertThat(done.ok()).isTrue();
        assertThat(done.toString()).doesNotContain(FULL_CODE).contains("9012");
        assertThat(again.status()).isEqualTo("NOTHING_TO_CONFIRM");
        assertThat(tools.getPendingAction(turn(3)).data())
                .isEqualTo(new SupportMcpTools.PendingActionView(false, null, null));
        verify(shopService).purchase(1L, 8L, Channel.AGENT);
    }

    @Test
    void anotherCustomerCannotConfirmWithTheSameConversationId() {
        steam25Preview();
        tools.purchaseVoucher(8L, turn(1));

        loggedIn(MARKO);
        ToolResult markoConfirms = tools.confirmPendingAction(turn(2));
        ToolResult markoPeeks = tools.getPendingAction(turn(2));

        assertThat(markoConfirms.status()).isEqualTo("NOTHING_TO_CONFIRM");
        assertThat(markoPeeks.data()).isEqualTo(new SupportMcpTools.PendingActionView(false, null, null));
        verify(shopService, never()).purchase(anyLong(), anyLong(), any());
    }

    @Test
    void clientThatSendsNoConversationContextCannotChangeAnything() {
        McpMeta noMeta = new McpMeta(Map.of());

        ToolResult purchase = tools.purchaseVoucher(8L, noMeta);
        ToolResult confirm = tools.confirmPendingAction(null);

        assertThat(purchase.status()).isEqualTo("CONVERSATION_REQUIRED");
        assertThat(confirm.status()).isEqualTo("CONVERSATION_REQUIRED");
        verify(shopService, never()).purchase(anyLong(), anyLong(), any());
    }

    @Test
    void wrongParametersInLaterTurnDoNotExecuteAnything() {
        when(shopService.previewPurchase(1L, 5L)).thenReturn(new PurchasePreview(5L, "HT bon 10 €",
                new BigDecimal("10.00"), new BigDecimal("25.00"), new BigDecimal("15.00")));
        when(shopService.previewPurchase(1L, 3L)).thenReturn(new PurchasePreview(3L, "A1 bon 20 €",
                new BigDecimal("20.00"), new BigDecimal("25.00"), new BigDecimal("5.00")));
        tools.purchaseVoucher(5L, turn(1));

        ToolResult wrongProduct = tools.purchaseVoucher(3L, turn(2));

        assertThat(wrongProduct.status()).isEqualTo(ToolResult.CONFIRMATION_REQUIRED);
        assertThat(wrongProduct.message()).contains("A1 bon 20 €");
        verify(shopService, never()).purchase(anyLong(), anyLong(), any());
    }

    @Test
    void cancelledActionCannotBeConfirmed() {
        steam25Preview();
        tools.purchaseVoucher(8L, turn(1));

        assertThat(tools.cancelPendingAction(turn(2)).ok()).isTrue();
        assertThat(tools.confirmPendingAction(turn(3)).status()).isEqualTo("NOTHING_TO_CONFIRM");
        verify(shopService, never()).purchase(anyLong(), anyLong(), any());
    }

    @Test
    void ruleDenialIsAResultAndDoesNotAskForConfirmation() {
        when(shopService.previewPurchase(1L, 9L)).thenThrow(new BusinessRuleException("INSUFFICIENT_FUNDS",
                "Nedovoljno sredstava na walletu: stanje 25 €, cijena 50 €"));

        ToolResult first = tools.purchaseVoucher(9L, turn(1));
        ToolResult nextTurn = tools.purchaseVoucher(9L, turn(2));

        assertThat(first.ok()).isFalse();
        assertThat(first.status()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(nextTurn.status()).isEqualTo("INSUFFICIENT_FUNDS");
        verify(shopService, never()).purchase(anyLong(), anyLong(), any());
    }

    @Test
    void someoneElsesPurchaseLooksLikeItDoesNotExist() {
        when(supportService.previewRefundForCustomer(1L, 3L))
                .thenThrow(new NotFoundException("Kupnja 3 nije pronađena među vašim kupnjama"));

        ToolResult result = tools.refundPurchase(3L, turn(1));

        assertThat(result.ok()).isFalse();
        assertThat(result.status()).isEqualTo("NOT_FOUND");
        verify(supportService, never()).refundForCustomer(anyLong(), anyLong());
    }

    @Test
    void knowledgeSearchIsAuditedAsRetrieval() {
        when(knowledgeBase.search("koliki je rok za povrat", 0)).thenReturn(List.of(new KbHit(
                "05-povrat-reklamacije-i-prijave.md", "Povrat, reklamacije i prijave", "Pravila povrata novca",
                "povrat", "povrat, 14 dana", 0.59, "Povrat je moguć unutar 14 dana od kupnje.")));

        ToolResult result = tools.searchKnowledgeBase("koliki je rok za povrat", turn(3));

        assertThat(result.ok()).isTrue();
        assertThat(result.data()).isEqualTo(List.of(new SupportMcpTools.KnowledgeView(
                "05-povrat-reklamacije-i-prijave.md", "Povrat, reklamacije i prijave", "Pravila povrata novca",
                0.59, "Povrat je moguć unutar 14 dana od kupnje.")));
        verify(audit).record(eq(new AuditContext("abc12345", 1L, "Ana Anić", "CHAT")),
                eq(AuditEventType.KB_RETRIEVAL), eq("retrieve"), eq("koliki je rok za povrat"), eq(AuditOutcome.OK),
                contains("Pravila povrata novca (0.59)"), anyLong());
    }
}
