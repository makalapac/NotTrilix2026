package com.nottrilix.agent;

import com.nottrilix.api.dto.PurchasePreview;
import com.nottrilix.api.dto.PurchaseResponse;
import com.nottrilix.api.dto.TransactionDto;
import com.nottrilix.api.dto.VoucherDto;
import com.nottrilix.config.RulesProperties;
import com.nottrilix.domain.Brand;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Granica alata: bez punog koda u izlazu, radnje tek nakon potvrde, odbijanje pravila = rezultat a ne greska. */
@ExtendWith(MockitoExtension.class)
class AgentToolsTest {

    private static final String FULL_CODE = "123456789012";

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

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneId.of("Europe/Zagreb"));
    private AgentTools tools;

    @BeforeEach
    void setUp() {
        tools = new AgentTools(catalogService, shopService, walletService, supportService, voucherCheckService,
                new ConfirmationGate(new AgentProperties("Nola", 20, 10), clock),
                new RulesProperties(new BigDecimal("150"), 14, new BigDecimal("500")), clock);
    }

    private static ToolContext turn(int turn) {
        return new ToolContext(Map.of(
                AgentContext.CUSTOMER_ID, 1L,
                AgentContext.MEMORY_KEY, "c1:abc12345",
                AgentContext.TURN, turn));
    }

    private TransactionDto purchase(long id, Instant createdAt, VoucherStatus voucherStatus) {
        return new TransactionDto(id, createdAt, Channel.WEB, TransactionStatus.COMPLETED, 1L, "Ana Anić",
                8L, Brand.STEAM, "Steam Wallet 25 €", new BigDecimal("25.00"),
                new VoucherDto("STM-25-00" + id, FULL_CODE, voucherStatus, createdAt), null);
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
        List<AgentTools.PurchaseView> views = (List<AgentTools.PurchaseView>) result.data();
        assertThat(views).extracting(AgentTools.PurchaseView::codeLast4).containsOnly("9012");
        assertThat(views).extracting(AgentTools.PurchaseView::refundPossible).containsExactly(true, false, false);
        // datum iskoristenja zna samo pruzatelj: popis kupnji upucuje na provjeru umjesto da ga model pogadja
        assertThat(views.get(0).redemptionDate()).isNull();
        assertThat(views.get(2).redemptionDate()).contains("checkVoucherAtProvider");
    }

    @Test
    void purchaseIsOnlyProposedUntilCustomerHadATurn() {
        when(shopService.previewPurchase(1L, 8L)).thenReturn(new PurchasePreview(8L, "Steam Wallet 25 €",
                new BigDecimal("25.00"), new BigDecimal("115.00"), new BigDecimal("90.00")));

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
        when(shopService.previewPurchase(1L, 8L)).thenReturn(new PurchasePreview(8L, "Steam Wallet 25 €",
                new BigDecimal("25.00"), new BigDecimal("115.00"), new BigDecimal("90.00")));
        when(shopService.purchase(1L, 8L, Channel.AGENT)).thenReturn(
                new PurchaseResponse(purchase(9, clock.instant(), VoucherStatus.SOLD), new BigDecimal("90.00")));
        tools.purchaseVoucher(8L, turn(1));

        ToolResult tooEarly = tools.confirmPendingAction(turn(1));
        ToolResult done = tools.confirmPendingAction(turn(2));
        ToolResult again = tools.confirmPendingAction(turn(3));

        assertThat(tooEarly.status()).isEqualTo(ToolResult.CONFIRMATION_REQUIRED);
        assertThat(done.ok()).isTrue();
        assertThat(done.toString()).doesNotContain(FULL_CODE).contains("9012");
        assertThat(again.status()).isEqualTo("NOTHING_TO_CONFIRM");
        verify(shopService).purchase(1L, 8L, Channel.AGENT);
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
        when(shopService.previewPurchase(1L, 8L)).thenReturn(new PurchasePreview(8L, "Steam Wallet 25 €",
                new BigDecimal("25.00"), new BigDecimal("115.00"), new BigDecimal("90.00")));
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
}
