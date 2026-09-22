package com.nottrilix.provider;

import com.nottrilix.domain.Voucher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fiktivni pruzatelj: "poziv" traje malo (simulirana latencija), a odgovor je deterministican -
 * izveden iz naseg statusa bona (pruzatelj i mi imamo isto stanje jer nema stvarnih dogadjaja iskoristavanja).
 * Iskoristen bon dobije datum iskoristenja i referencu provjere kakvu bi vratio pravi API.
 */
@Component
public class SimulatedProviderGateway implements ProviderGateway {

    private static final Logger log = LoggerFactory.getLogger("PROVIDER");

    private final Clock clock;
    private final int maxLatencyMs;

    public SimulatedProviderGateway(Clock clock, @Value("${nottrilix.provider.max-latency-ms:400}") int maxLatencyMs) {
        this.clock = clock;
        this.maxLatencyMs = maxLatencyMs;
    }

    @Override
    public ProviderCheckResult checkVoucher(Voucher voucher) {
        String provider = voucher.getProduct().getBrand().displayName();
        long started = System.nanoTime();
        simulateLatency();
        ProviderVoucherStatus status = switch (voucher.getStatus()) {
            case AVAILABLE -> ProviderVoucherStatus.NOT_ACTIVATED;
            case SOLD -> ProviderVoucherStatus.ACTIVE;
            case REDEEMED -> ProviderVoucherStatus.REDEEMED;
            case BLOCKED -> ProviderVoucherStatus.BLOCKED;
        };
        Instant redeemedAt = status == ProviderVoucherStatus.REDEEMED ? redeemedAt(voucher) : null;
        String reference = reference(voucher);
        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        log.info("PROVIDER {} check serial={} -> {}{} ref={} ({} ms) [simulirano]", provider, voucher.getSerial(),
                status, redeemedAt == null ? "" : " redeemedAt=" + redeemedAt, reference, latencyMs);
        return new ProviderCheckResult(status, redeemedAt, reference, latencyMs);
    }

    /** Deterministicno: iskoristen 1-72 h nakon prodaje, ovisno o serijskom broju. */
    private Instant redeemedAt(Voucher voucher) {
        Instant base = voucher.getSoldAt() != null ? voucher.getSoldAt() : Instant.now(clock);
        int hours = 1 + Math.floorMod(voucher.getSerial().hashCode(), 72);
        return base.plus(hours, ChronoUnit.HOURS);
    }

    /** Npr. STM-CHK-3F9A1C - referenca provjere kod pruzatelja. */
    private static String reference(Voucher voucher) {
        return voucher.getProduct().getSerialPrefix() + "-CHK-"
                + String.format("%06X", voucher.getSerial().hashCode() & 0xFFFFFF);
    }

    private void simulateLatency() {
        if (maxLatencyMs <= 0) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(maxLatencyMs / 2, maxLatencyMs + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
