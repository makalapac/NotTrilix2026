package com.nottrilix.service;

import com.nottrilix.api.dto.DtoMapper;
import com.nottrilix.api.dto.WalletDto;
import com.nottrilix.api.dto.WalletEntryDto;
import com.nottrilix.config.RulesProperties;
import com.nottrilix.domain.Customer;
import com.nottrilix.domain.Transaction;
import com.nottrilix.domain.WalletEntry;
import com.nottrilix.domain.WalletEntryType;
import com.nottrilix.repo.CustomerRepository;
import com.nottrilix.repo.WalletEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Customer wallet: balance on the customer + ledger of changes (wallet_entries). A purchase is charged to the wallet,
 * a refund goes back to the wallet. Debit/credit are called from ShopService/SupportService within their transaction.
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final CustomerRepository customerRepo;
    private final WalletEntryRepository entryRepo;
    private final RulesProperties rules;
    private final Clock clock;

    public WalletService(CustomerRepository customerRepo, WalletEntryRepository entryRepo,
                         RulesProperties rules, Clock clock) {
        this.customerRepo = customerRepo;
        this.entryRepo = entryRepo;
        this.rules = rules;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WalletDto wallet(long customerId) {
        Customer customer = customerRepo.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Kupac " + customerId + " ne postoji"));
        List<WalletEntryDto> entries = entryRepo.findByCustomerIdOrderByCreatedAtDescIdDesc(customerId).stream()
                .map(DtoMapper::toDto).toList();
        log.info("WALLET customer={} ({}) balance={} € entries={}",
                customerId, customer.getName(), fmt(customer.getWalletBalanceEur()), entries.size());
        return new WalletDto(customer.getWalletBalanceEur(), entries);
    }

    @Transactional(readOnly = true)
    public BigDecimal balance(long customerId) {
        return customerRepo.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Kupac " + customerId + " ne postoji"))
                .getWalletBalanceEur();
    }

    /** Wallet top-up (customer: simulated card payment; support: top-up with a note). */
    @Transactional
    public WalletDto topUp(long customerId, BigDecimal amount, String note) {
        log.info("WALLET TOPUP request customer={} amount={} €", customerId, fmt(amount));
        Customer customer = customerRepo.findByIdForUpdate(customerId)
                .orElseThrow(() -> new NotFoundException("Kupac " + customerId + " ne postoji"));
        if (amount.compareTo(rules.maxTopUpEur()) > 0) {
            throw deny("TOPUP", "TOPUP_LIMIT_EXCEEDED",
                    "Najveća pojedinačna uplata je %s €".formatted(fmt(rules.maxTopUpEur())));
        }
        customer.credit(amount);
        entryRepo.save(new WalletEntry(customer, WalletEntryType.TOPUP, amount, customer.getWalletBalanceEur(),
                null, note, Instant.now(clock)));
        log.info("WALLET TOPUP OK customer={} ({}) +{} € -> {} € note='{}'",
                customerId, customer.getName(), fmt(amount), fmt(customer.getWalletBalanceEur()), note);
        return wallet(customerId);
    }

    /** Check before a purchase. The customer is locked (FOR UPDATE), so the balance cannot change before the debit. */
    public void requireFunds(Customer customer, BigDecimal price) {
        BigDecimal balance = customer.getWalletBalanceEur();
        if (balance.compareTo(price) < 0) {
            throw deny("PURCHASE", "INSUFFICIENT_FUNDS",
                    "Nedovoljno sredstava na walletu: stanje %s €, cijena %s € - nadoplatite wallet za najmanje %s €"
                            .formatted(fmt(balance), fmt(price), fmt(price.subtract(balance))));
        }
    }

    /** Debits the purchase amount; called within the purchase transaction, after the transaction has been saved. */
    public BigDecimal debitForPurchase(Customer customer, Transaction tx) {
        BigDecimal amount = tx.getAmountEur();
        requireFunds(customer, amount);
        customer.debit(amount);
        entryRepo.save(new WalletEntry(customer, WalletEntryType.PURCHASE, amount.negate(),
                customer.getWalletBalanceEur(), tx, tx.getProduct().displayName(), tx.getCreatedAt()));
        log.info("WALLET DEBIT customer={} -{} € (PURCHASE tx={}) -> {} €",
                customer.getId(), fmt(amount), tx.getId(), fmt(customer.getWalletBalanceEur()));
        return customer.getWalletBalanceEur();
    }

    /** Credits the purchase amount back to the wallet; called within the refund transaction. */
    public BigDecimal creditForRefund(Customer customer, Transaction tx, Instant at) {
        BigDecimal amount = tx.getAmountEur();
        customer.credit(amount);
        entryRepo.save(new WalletEntry(customer, WalletEntryType.REFUND, amount, customer.getWalletBalanceEur(),
                tx, "Povrat - " + tx.getProduct().displayName(), at));
        log.info("WALLET CREDIT customer={} +{} € (REFUND tx={}) -> {} €",
                customer.getId(), fmt(amount), tx.getId(), fmt(customer.getWalletBalanceEur()));
        return customer.getWalletBalanceEur();
    }

    private BusinessRuleException deny(String operation, String code, String message) {
        log.warn("{} DENIED reason={} - {}", operation, code, message);
        return new BusinessRuleException(code, message);
    }

    private static String fmt(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
