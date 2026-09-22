package com.nottrilix.service;

import com.nottrilix.config.RulesProperties;
import com.nottrilix.domain.Brand;
import com.nottrilix.domain.Category;
import com.nottrilix.domain.Channel;
import com.nottrilix.domain.Customer;
import com.nottrilix.domain.Product;
import com.nottrilix.domain.Transaction;
import com.nottrilix.domain.Voucher;
import com.nottrilix.domain.WalletEntry;
import com.nottrilix.domain.WalletEntryType;
import com.nottrilix.repo.CustomerRepository;
import com.nottrilix.repo.WalletEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Wallet: nedovoljno sredstava, skidanje i povrat s knjizenjem, limit uplate. */
@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private CustomerRepository customerRepo;
    @Mock
    private WalletEntryRepository entryRepo;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneId.of("Europe/Zagreb"));
    private final Customer marko = new Customer("Marko Marić", "marko.maric@example.com", null, "$2a$10$hash");
    private final Product steam25 = new Product(Category.GAMING, Brand.STEAM, "Steam Wallet", "STM",
            new BigDecimal("25.00"));

    private WalletService service() {
        return new WalletService(customerRepo, entryRepo,
                new RulesProperties(new BigDecimal("150"), 14, new BigDecimal("500")), clock);
    }

    @Test
    void deniesPurchaseWhenBalanceIsTooLow() {
        marko.credit(new BigDecimal("20.00"));

        assertThatThrownBy(() -> service().requireFunds(marko, new BigDecimal("25.00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "INSUFFICIENT_FUNDS")
                .hasMessageContaining("stanje 20 €")
                .hasMessageContaining("najmanje 5 €");
    }

    @Test
    void debitAndRefundKeepBalanceAndLedgerConsistent() {
        marko.credit(new BigDecimal("100.00"));
        Voucher voucher = new Voucher(steam25, "STM-25-001", "123456789012");
        Transaction tx = new Transaction(marko, steam25, voucher, Channel.WEB, clock.instant());
        when(entryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        WalletService service = service();

        BigDecimal afterPurchase = service.debitForPurchase(marko, tx);
        BigDecimal afterRefund = service.creditForRefund(marko, tx, clock.instant());

        assertThat(afterPurchase).isEqualByComparingTo("75.00");
        assertThat(afterRefund).isEqualByComparingTo("100.00");
        assertThat(marko.getWalletBalanceEur()).isEqualByComparingTo("100.00");

        ArgumentCaptor<WalletEntry> entries = ArgumentCaptor.forClass(WalletEntry.class);
        verify(entryRepo, times(2)).save(entries.capture());
        WalletEntry purchase = entries.getAllValues().get(0);
        WalletEntry refund = entries.getAllValues().get(1);
        assertThat(purchase.getType()).isEqualTo(WalletEntryType.PURCHASE);
        assertThat(purchase.getAmountEur()).isEqualByComparingTo("-25.00");
        assertThat(purchase.getBalanceAfterEur()).isEqualByComparingTo("75.00");
        assertThat(refund.getType()).isEqualTo(WalletEntryType.REFUND);
        assertThat(refund.getAmountEur()).isEqualByComparingTo("25.00");
        assertThat(refund.getBalanceAfterEur()).isEqualByComparingTo("100.00");
        assertThat(refund.getNote()).isEqualTo("Povrat - Steam Wallet 25 €");
    }

    @Test
    void deniesTopUpOverLimit() {
        when(customerRepo.findByIdForUpdate(2L)).thenReturn(Optional.of(marko));

        assertThatThrownBy(() -> service().topUp(2L, new BigDecimal("501.00"), "test"))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "TOPUP_LIMIT_EXCEEDED");
        verify(entryRepo, never()).save(any());
        assertThat(marko.getWalletBalanceEur()).isEqualByComparingTo("0");
    }
}
