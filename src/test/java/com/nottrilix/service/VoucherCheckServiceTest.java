package com.nottrilix.service;

import com.nottrilix.api.dto.VoucherCheckDto;
import com.nottrilix.domain.Brand;
import com.nottrilix.domain.Category;
import com.nottrilix.domain.Channel;
import com.nottrilix.domain.Customer;
import com.nottrilix.domain.Product;
import com.nottrilix.domain.Transaction;
import com.nottrilix.domain.Voucher;
import com.nottrilix.domain.VoucherStatus;
import com.nottrilix.provider.ProviderCheckResult;
import com.nottrilix.provider.ProviderGateway;
import com.nottrilix.provider.ProviderVoucherStatus;
import com.nottrilix.repo.TransactionRepository;
import com.nottrilix.repo.VoucherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Voucher check: the customer sees only their own vouchers; the message follows the provider's response. */
@ExtendWith(MockitoExtension.class)
class VoucherCheckServiceTest {

    @Mock
    private VoucherRepository voucherRepo;
    @Mock
    private TransactionRepository transactionRepo;
    @Mock
    private ProviderGateway provider;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneId.of("Europe/Zagreb"));
    private final Product steam25 = new Product(Category.GAMING, Brand.STEAM, "Steam Wallet", "STM",
            new BigDecimal("25.00"));

    private VoucherCheckService service() {
        return new VoucherCheckService(voucherRepo, transactionRepo, provider, clock);
    }

    @Test
    void customerCanCheckOwnVoucherAndGetsProviderMessage() throws Exception {
        Customer ana = customerWithId(1L);
        Voucher voucher = new Voucher(steam25, "STM-25-001", "123456789012");
        voucher.markSold(clock.instant());
        Transaction tx = new Transaction(ana, steam25, voucher, Channel.WEB, clock.instant());
        when(transactionRepo.findByVoucherSerialCompact("STM25001")).thenReturn(Optional.of(tx));
        when(provider.checkVoucher(voucher)).thenReturn(new ProviderCheckResult(
                ProviderVoucherStatus.REDEEMED, Instant.parse("2026-10-12T15:30:00Z"), "STM-CHK-ABC123", 210));

        VoucherCheckDto dto = service().checkForCustomer(1L, "STM-25-001");

        assertThat(dto.providerStatus()).isEqualTo(ProviderVoucherStatus.REDEEMED);
        assertThat(dto.localStatus()).isEqualTo(VoucherStatus.SOLD);
        assertThat(dto.providerName()).isEqualTo("Steam");
        assertThat(dto.providerReference()).isEqualTo("STM-CHK-ABC123");
        assertThat(dto.message()).isEqualTo("Bon STM-25-001 je iskorišten kod pružatelja Steam (12.10.2026. 17:30).");
    }

    @Test
    void customerCannotCheckSomeoneElsesVoucher() throws Exception {
        Customer marko = customerWithId(2L);
        Voucher voucher = new Voucher(steam25, "STM-25-001", "123456789012");
        Transaction tx = new Transaction(marko, steam25, voucher, Channel.WEB, clock.instant());
        when(transactionRepo.findByVoucherSerialCompact("STM25001")).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> service().checkForCustomer(1L, "STM-25-001"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("nije pronađen među vašim kupnjama");
        verify(provider, never()).checkVoucher(any());
    }

    @Test
    void supportGetsNotFoundForUnknownSerial() {
        when(voucherRepo.findBySerialCompact("NOPE1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().checkForSupport("NOPE-1"))
                .isInstanceOf(NotFoundException.class);
    }

    /** An entity without a database has no id; the test sets it via reflection (the same thing JPA would do). */
    private static Customer customerWithId(long id) throws Exception {
        Customer customer = new Customer("Kupac " + id, "kupac" + id + "@example.com", null, "$2a$10$hash");
        Field field = Customer.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(customer, id);
        return customer;
    }
}
