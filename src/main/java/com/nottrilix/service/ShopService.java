package com.nottrilix.service;

import com.nottrilix.api.dto.CustomerDto;
import com.nottrilix.api.dto.DtoMapper;
import com.nottrilix.api.dto.TransactionDto;
import com.nottrilix.config.RulesProperties;
import com.nottrilix.domain.Channel;
import com.nottrilix.domain.Customer;
import com.nottrilix.domain.Product;
import com.nottrilix.domain.Transaction;
import com.nottrilix.domain.TransactionStatus;
import com.nottrilix.domain.Voucher;
import com.nottrilix.domain.VoucherStatus;
import com.nottrilix.repo.CustomerRepository;
import com.nottrilix.repo.ProductRepository;
import com.nottrilix.repo.TransactionRepository;
import com.nottrilix.repo.VoucherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Prodajni kanal: kupci, kupnja bona, pregled vlastitih kupnji. Ovdje zive pravila prodaje. */
@Service
public class ShopService {

    private static final Logger log = LoggerFactory.getLogger(ShopService.class);

    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;
    private final VoucherRepository voucherRepo;
    private final TransactionRepository transactionRepo;
    private final RulesProperties rules;
    private final Clock clock;

    public ShopService(CustomerRepository customerRepo, ProductRepository productRepo, VoucherRepository voucherRepo,
                       TransactionRepository transactionRepo, RulesProperties rules, Clock clock) {
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
        this.voucherRepo = voucherRepo;
        this.transactionRepo = transactionRepo;
        this.rules = rules;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CustomerDto> customers() {
        List<CustomerDto> list = customerRepo.findAllByOrderByNameAsc().stream().map(DtoMapper::toDto).toList();
        log.info("CUSTOMERS -> {} kupaca", list.size());
        return list;
    }

    @Transactional(readOnly = true)
    public CustomerDto customer(long id) {
        Customer c = customerRepo.findById(id).orElseThrow(() -> new NotFoundException("Kupac " + id + " ne postoji"));
        log.info("CUSTOMER id={} name={}", id, c.getName());
        return DtoMapper.toDto(c);
    }

    /**
     * Kupnja bona. Pravila: proizvod aktivan, dnevni limit po kupcu, bon mora postojati na zalihi.
     * Bon se zakljucava u bazi pa dva istovremena kupca ne mogu dobiti isti kod.
     */
    @Transactional
    public TransactionDto purchase(long customerId, long productId, Channel channel) {
        log.info("PURCHASE request customer={} product={} channel={}", customerId, productId, channel);

        Customer customer = customerRepo.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Kupac " + customerId + " ne postoji"));
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new NotFoundException("Proizvod " + productId + " ne postoji"));
        if (!product.isActive()) {
            throw deny("PURCHASE", "PRODUCT_INACTIVE", "Proizvod " + product.displayName() + " nije u prodaji");
        }

        Instant now = Instant.now(clock);
        Instant startOfToday = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        BigDecimal spentToday = transactionRepo
                .sumAmountSince(customerId, TransactionStatus.COMPLETED, startOfToday)
                .orElse(BigDecimal.ZERO);
        BigDecimal price = product.getDenominationEur();
        if (spentToday.add(price).compareTo(rules.dailyLimitEur()) > 0) {
            throw deny("PURCHASE", "DAILY_LIMIT_EXCEEDED",
                    "Dnevni limit od %s € je premašen: danas već kupljeno %s €, pokušaj kupnje %s €"
                            .formatted(fmt(rules.dailyLimitEur()), fmt(spentToday), fmt(price)));
        }

        Voucher voucher = voucherRepo.findFirstByProductAndStatusOrderByIdAsc(product, VoucherStatus.AVAILABLE)
                .orElseThrow(() -> deny("PURCHASE", "OUT_OF_STOCK",
                        "Proizvod " + product.displayName() + " je trenutno rasprodan"));
        voucher.markSold(now);
        Transaction tx = transactionRepo.save(new Transaction(customer, product, voucher, channel, now));

        log.info("PURCHASE OK tx={} customer={} ({}) product={} amount={} € voucher={} channel={} spentToday={} €",
                tx.getId(), customerId, customer.getName(), product.displayName(), fmt(price),
                voucher.getSerial(), channel, fmt(spentToday.add(price)));
        return DtoMapper.toDto(tx);
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> customerTransactions(long customerId) {
        if (!customerRepo.existsById(customerId)) {
            throw new NotFoundException("Kupac " + customerId + " ne postoji");
        }
        List<TransactionDto> list = transactionRepo.findByCustomerIdWithDetails(customerId).stream()
                .map(DtoMapper::toDto).toList();
        log.info("TRANSACTIONS customer={} -> {} kupnji", customerId, list.size());
        return list;
    }

    private BusinessRuleException deny(String operation, String code, String message) {
        log.warn("{} DENIED reason={} - {}", operation, code, message);
        return new BusinessRuleException(code, message);
    }

    private static String fmt(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
