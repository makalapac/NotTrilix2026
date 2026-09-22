package com.nottrilix.service;

import com.nottrilix.api.dto.TransactionDto;
import com.nottrilix.config.RulesProperties;
import com.nottrilix.domain.Brand;
import com.nottrilix.domain.Category;
import com.nottrilix.domain.Channel;
import com.nottrilix.domain.Customer;
import com.nottrilix.domain.Product;
import com.nottrilix.domain.TransactionStatus;
import com.nottrilix.domain.Voucher;
import com.nottrilix.domain.VoucherStatus;
import com.nottrilix.repo.CustomerRepository;
import com.nottrilix.repo.ProductRepository;
import com.nottrilix.repo.TransactionRepository;
import com.nottrilix.repo.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pravila prodaje bez baze: zaliha, dnevni limit, prodaja prvog slobodnog bona. */
@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock
    private CustomerRepository customerRepo;
    @Mock
    private ProductRepository productRepo;
    @Mock
    private VoucherRepository voucherRepo;
    @Mock
    private TransactionRepository transactionRepo;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneId.of("Europe/Zagreb"));
    private final Customer ana = new Customer("Ana Anić", "ana.anic@example.com", null);
    private final Product steam25 = new Product(Category.GAMING, Brand.STEAM, "Steam Wallet", "STM",
            new BigDecimal("25.00"));

    private ShopService service;

    @BeforeEach
    void setUp() {
        service = new ShopService(customerRepo, productRepo, voucherRepo, transactionRepo,
                new RulesProperties(new BigDecimal("150"), 14), clock);
        when(customerRepo.findById(1L)).thenReturn(Optional.of(ana));
        when(productRepo.findById(7L)).thenReturn(Optional.of(steam25));
    }

    @Test
    void sellsFirstAvailableVoucher() {
        Voucher voucher = new Voucher(steam25, "STM-25-001", "123456789012");
        when(transactionRepo.sumAmountSince(eq(1L), eq(TransactionStatus.COMPLETED), any()))
                .thenReturn(Optional.empty());
        when(voucherRepo.findFirstByProductAndStatusOrderByIdAsc(steam25, VoucherStatus.AVAILABLE))
                .thenReturn(Optional.of(voucher));
        when(transactionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransactionDto dto = service.purchase(1L, 7L, Channel.WEB);

        assertThat(dto.voucher().serial()).isEqualTo("STM-25-001");
        assertThat(dto.voucher().code()).isEqualTo("123456789012");
        assertThat(dto.amountEur()).isEqualByComparingTo("25.00");
        assertThat(dto.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.SOLD);
        assertThat(voucher.getSoldAt()).isEqualTo(clock.instant());
    }

    @Test
    void deniesPurchaseWhenOutOfStock() {
        when(transactionRepo.sumAmountSince(eq(1L), eq(TransactionStatus.COMPLETED), any()))
                .thenReturn(Optional.empty());
        when(voucherRepo.findFirstByProductAndStatusOrderByIdAsc(steam25, VoucherStatus.AVAILABLE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purchase(1L, 7L, Channel.WEB))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "OUT_OF_STOCK");
        verify(transactionRepo, never()).save(any());
    }

    @Test
    void deniesPurchaseOverDailyLimit() {
        when(transactionRepo.sumAmountSince(eq(1L), eq(TransactionStatus.COMPLETED), any()))
                .thenReturn(Optional.of(new BigDecimal("130.00")));

        assertThatThrownBy(() -> service.purchase(1L, 7L, Channel.WEB))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "DAILY_LIMIT_EXCEEDED")
                .hasMessageContaining("150");
        verify(voucherRepo, never()).findFirstByProductAndStatusOrderByIdAsc(any(), any());
    }
}
