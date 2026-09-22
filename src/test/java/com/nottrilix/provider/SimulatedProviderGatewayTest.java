package com.nottrilix.provider;

import com.nottrilix.domain.Brand;
import com.nottrilix.domain.Category;
import com.nottrilix.domain.Product;
import com.nottrilix.domain.Voucher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** Fiktivni pruzatelj: odgovor slijedi nase stanje bona, iskoristen bon dobije datum i referencu. */
class SimulatedProviderGatewayTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneId.of("Europe/Zagreb"));
    private final Product steam25 = new Product(Category.GAMING, Brand.STEAM, "Steam Wallet", "STM",
            new BigDecimal("25.00"));
    private final SimulatedProviderGateway gateway = new SimulatedProviderGateway(clock, 0);

    @Test
    void unsoldVoucherIsNotActivatedAtProvider() {
        Voucher voucher = new Voucher(steam25, "STM-25-001", "123456789012");

        ProviderCheckResult result = gateway.checkVoucher(voucher);

        assertThat(result.status()).isEqualTo(ProviderVoucherStatus.NOT_ACTIVATED);
        assertThat(result.redeemedAt()).isNull();
        assertThat(result.reference()).startsWith("STM-CHK-").hasSize(14);
    }

    @Test
    void soldVoucherIsActiveAtProvider() {
        Voucher voucher = new Voucher(steam25, "STM-25-002", "123456789012");
        voucher.markSold(clock.instant());

        assertThat(gateway.checkVoucher(voucher).status()).isEqualTo(ProviderVoucherStatus.ACTIVE);
    }

    @Test
    void blockedVoucherIsBlockedAtProvider() {
        Voucher voucher = new Voucher(steam25, "STM-25-003", "123456789012");
        voucher.markSold(clock.instant());
        voucher.block();

        assertThat(gateway.checkVoucher(voucher).status()).isEqualTo(ProviderVoucherStatus.BLOCKED);
    }

    @Test
    void referenceIsStableForTheSameSerial() {
        Voucher first = new Voucher(steam25, "STM-25-004", "111111111111");
        Voucher second = new Voucher(steam25, "STM-25-004", "222222222222");

        assertThat(gateway.checkVoucher(first).reference()).isEqualTo(gateway.checkVoucher(second).reference());
    }
}
