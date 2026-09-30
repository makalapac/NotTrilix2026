package com.nottrilix.service;

import com.nottrilix.api.dto.CustomerDto;
import com.nottrilix.api.dto.DailyLimitStatus;
import com.nottrilix.api.dto.DtoMapper;
import com.nottrilix.api.dto.PurchasePreview;
import com.nottrilix.api.dto.PurchaseResponse;
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

/** Sales channel: customers, voucher purchase (charged to the wallet), overview of own purchases. Sales rules live here. */
@Service
public class ShopService {

    private static final Logger log = LoggerFactory.getLogger(ShopService.class);

    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;
    private final VoucherRepository voucherRepo;
    private final TransactionRepository transactionRepo;
    private final WalletService walletService;
    private final RulesProperties rules;
    private final Clock clock;

    public ShopService(CustomerRepository customerRepo, ProductRepository productRepo, VoucherRepository voucherRepo,
                       TransactionRepository transactionRepo, WalletService walletService,
                       RulesProperties rules, Clock clock) {
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
        this.voucherRepo = voucherRepo;
        this.transactionRepo = transactionRepo;
        this.walletService = walletService;
        this.rules = rules;
        this.clock = clock;
    }

    /** Product and price after the rules that do not depend on stock have passed. */
    private record RuleCheck(Product product, BigDecimal price, BigDecimal spentToday) {
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
     * Voucher purchase, charged to the wallet. Rules: product active, daily limit per customer, sufficient funds,
     * voucher in stock. Customer is locked (wallet + limit, no race), voucher is locked (same code never goes to two buyers).
     */
    @Transactional
    public PurchaseResponse purchase(long customerId, long productId, Channel channel) {
        log.info("PURCHASE request customer={} product={} channel={}", customerId, productId, channel);

        Customer customer = customerRepo.findByIdForUpdate(customerId)
                .orElseThrow(() -> new NotFoundException("Kupac " + customerId + " ne postoji"));
        RuleCheck check = checkRules(customer, customerId, productId);
        Product product = check.product();
        BigDecimal price = check.price();

        Instant now = Instant.now(clock);
        Voucher voucher = voucherRepo.findFirstByProductAndStatusOrderByIdAsc(product, VoucherStatus.AVAILABLE)
                .orElseThrow(() -> outOfStock(product));
        voucher.markSold(now);
        Transaction tx = transactionRepo.save(new Transaction(customer, product, voucher, channel, now));
        BigDecimal walletBalance = walletService.debitForPurchase(customer, tx);

        log.info("PURCHASE OK tx={} customer={} ({}) product={} amount={} € voucher={} channel={} spentToday={} € wallet={} €",
                tx.getId(), customerId, customer.getName(), product.displayName(), fmt(price),
                voucher.getSerial(), channel, fmt(check.spentToday().add(price)), fmt(walletBalance));
        return new PurchaseResponse(DtoMapper.toDto(tx), walletBalance);
    }

    /**
     * Same rules as a purchase, but without executing it: tells the customer, before confirmation, what will happen
     * (or why the purchase is not possible). Nothing is locked or modified.
     */
    @Transactional(readOnly = true)
    public PurchasePreview previewPurchase(long customerId, long productId) {
        Customer customer = customerRepo.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Kupac " + customerId + " ne postoji"));
        RuleCheck check = checkRules(customer, customerId, productId);
        if (!voucherRepo.existsByProductAndStatus(check.product(), VoucherStatus.AVAILABLE)) {
            throw outOfStock(check.product());
        }
        BigDecimal balance = customer.getWalletBalanceEur();
        log.info("PURCHASE PREVIEW customer={} product={} amount={} € wallet={} € -> {} €", customerId,
                check.product().displayName(), fmt(check.price()), fmt(balance), fmt(balance.subtract(check.price())));
        return new PurchasePreview(check.product().getId(), check.product().displayName(), check.price(),
                balance, balance.subtract(check.price()));
    }

    /** How much the customer has already bought today and how much more is allowed under the daily limit. */
    @Transactional(readOnly = true)
    public DailyLimitStatus dailyLimitStatus(long customerId) {
        BigDecimal spentToday = spentToday(customerId);
        BigDecimal remaining = rules.dailyLimitEur().subtract(spentToday).max(BigDecimal.ZERO);
        return new DailyLimitStatus(rules.dailyLimitEur(), spentToday, remaining);
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

    /** Rules that do not depend on stock: product active, daily limit, sufficient funds in the wallet. */
    private RuleCheck checkRules(Customer customer, long customerId, long productId) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new NotFoundException("Proizvod " + productId + " ne postoji"));
        if (!product.isActive()) {
            throw deny("PURCHASE", "PRODUCT_INACTIVE", "Proizvod " + product.displayName() + " nije u prodaji");
        }
        BigDecimal spentToday = spentToday(customerId);
        BigDecimal price = product.getDenominationEur();
        if (spentToday.add(price).compareTo(rules.dailyLimitEur()) > 0) {
            throw deny("PURCHASE", "DAILY_LIMIT_EXCEEDED",
                    "Dnevni limit od %s € je premašen: danas već kupljeno %s €, pokušaj kupnje %s €"
                            .formatted(fmt(rules.dailyLimitEur()), fmt(spentToday), fmt(price)));
        }
        walletService.requireFunds(customer, price);
        return new RuleCheck(product, price, spentToday);
    }

    private BigDecimal spentToday(long customerId) {
        Instant startOfToday = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        return transactionRepo.sumAmountSince(customerId, TransactionStatus.COMPLETED, startOfToday)
                .orElse(BigDecimal.ZERO);
    }

    private BusinessRuleException outOfStock(Product product) {
        return deny("PURCHASE", "OUT_OF_STOCK", "Proizvod " + product.displayName() + " je trenutno rasprodan");
    }

    private BusinessRuleException deny(String operation, String code, String message) {
        log.warn("{} DENIED reason={} - {}", operation, code, message);
        return new BusinessRuleException(code, message);
    }

    private static String fmt(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
