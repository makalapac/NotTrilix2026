package com.nottrilix.service;

import com.nottrilix.api.dto.VoucherCheckDto;
import com.nottrilix.domain.Brand;
import com.nottrilix.domain.Transaction;
import com.nottrilix.domain.Voucher;
import com.nottrilix.provider.ProviderCheckResult;
import com.nottrilix.provider.ProviderGateway;
import com.nottrilix.repo.TransactionRepository;
import com.nottrilix.repo.VoucherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** Provjera iskoristenosti bona kod pruzatelja usluge - za support (bilo koji bon) i za kupca (samo vlastiti bonovi). */
@Service
public class VoucherCheckService {

    private static final Logger log = LoggerFactory.getLogger(VoucherCheckService.class);

    private final VoucherRepository voucherRepo;
    private final TransactionRepository transactionRepo;
    private final ProviderGateway provider;
    private final Clock clock;
    private final DateTimeFormatter dateFormat;

    public VoucherCheckService(VoucherRepository voucherRepo, TransactionRepository transactionRepo,
                               ProviderGateway provider, Clock clock) {
        this.voucherRepo = voucherRepo;
        this.transactionRepo = transactionRepo;
        this.provider = provider;
        this.clock = clock;
        this.dateFormat = DateTimeFormatter.ofPattern("d.M.yyyy. HH:mm").withZone(clock.getZone());
    }

    @Transactional(readOnly = true)
    public VoucherCheckDto checkForSupport(String serial) {
        Voucher voucher = voucherRepo.findBySerial(serial)
                .orElseThrow(() -> new NotFoundException("Bon " + serial + " ne postoji"));
        return check(voucher);
    }

    /** Kupac smije provjeriti samo bon iz vlastite kupnje; tudji bon = kao da ne postoji. */
    @Transactional(readOnly = true)
    public VoucherCheckDto checkForCustomer(long customerId, String serial) {
        Voucher voucher = transactionRepo.findByVoucherSerial(serial)
                .filter(t -> t.getCustomer().getId().equals(customerId))
                .map(Transaction::getVoucher)
                .orElseThrow(() -> new NotFoundException("Bon " + serial + " nije pronađen među vašim kupnjama"));
        return check(voucher);
    }

    private VoucherCheckDto check(Voucher voucher) {
        Brand brand = voucher.getProduct().getBrand();
        log.info("VOUCHER CHECK request serial={} provider={} local={}", voucher.getSerial(), brand, voucher.getStatus());
        ProviderCheckResult result = provider.checkVoucher(voucher);
        String message = message(voucher, brand, result);
        log.info("VOUCHER CHECK serial={} local={} provider={}{} ref={} ({} ms)", voucher.getSerial(),
                voucher.getStatus(), result.status(),
                result.redeemedAt() == null ? "" : " redeemedAt=" + result.redeemedAt(),
                result.reference(), result.latencyMs());
        return new VoucherCheckDto(voucher.getSerial(), brand, brand.displayName(), result.status(),
                voucher.getStatus(), result.redeemedAt(), Instant.now(clock), result.reference(),
                result.latencyMs(), message);
    }

    private String message(Voucher voucher, Brand brand, ProviderCheckResult result) {
        String serial = voucher.getSerial();
        String provider = brand.displayName();
        return switch (result.status()) {
            case NOT_ACTIVATED -> "Bon %s nije aktiviran kod pružatelja %s (nije prodan).".formatted(serial, provider);
            case ACTIVE -> "Bon %s je aktivan kod pružatelja %s i još nije iskorišten.".formatted(serial, provider);
            case REDEEMED -> "Bon %s je iskorišten kod pružatelja %s (%s).".formatted(serial, provider,
                    dateFormat.format(result.redeemedAt()));
            case BLOCKED -> "Bon %s je blokiran kod pružatelja %s - ne može se iskoristiti.".formatted(serial, provider);
        };
    }
}
