package com.nottrilix.service;

import com.nottrilix.api.dto.DtoMapper;
import com.nottrilix.api.dto.RefundPreview;
import com.nottrilix.api.dto.TicketDto;
import com.nottrilix.api.dto.TransactionDto;
import com.nottrilix.api.dto.VoucherDto;
import com.nottrilix.config.RulesProperties;
import com.nottrilix.domain.Brand;
import com.nottrilix.domain.Customer;
import com.nottrilix.domain.SupportTicket;
import com.nottrilix.domain.TicketStatus;
import com.nottrilix.domain.Transaction;
import com.nottrilix.domain.TransactionStatus;
import com.nottrilix.domain.Voucher;
import com.nottrilix.domain.VoucherStatus;
import com.nottrilix.repo.CustomerRepository;
import com.nottrilix.repo.SupportTicketRepository;
import com.nottrilix.repo.TransactionRepository;
import com.nottrilix.repo.VoucherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Admin/support: transaction overview, refund, voucher block, support tickets. Support rules live here.
 * The "...ForCustomer" methods are the same actions when requested by the customer themselves (via the agent):
 * the same rules apply, and data that does not belong to the customer behaves as if it did not exist.
 */
@Service
public class SupportService {

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);

    private final CustomerRepository customerRepo;
    private final TransactionRepository transactionRepo;
    private final VoucherRepository voucherRepo;
    private final SupportTicketRepository ticketRepo;
    private final WalletService walletService;
    private final RulesProperties rules;
    private final Clock clock;

    public SupportService(CustomerRepository customerRepo, TransactionRepository transactionRepo,
                          VoucherRepository voucherRepo, SupportTicketRepository ticketRepo,
                          WalletService walletService, RulesProperties rules, Clock clock) {
        this.customerRepo = customerRepo;
        this.transactionRepo = transactionRepo;
        this.voucherRepo = voucherRepo;
        this.ticketRepo = ticketRepo;
        this.walletService = walletService;
        this.rules = rules;
        this.clock = clock;
    }

    /** Filter for the transaction overview; every field is optional. */
    public record TransactionFilter(TransactionStatus status, Brand brand, Long customerId) {
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> transactions(TransactionFilter filter) {
        List<TransactionDto> list = transactionRepo.findAllWithDetails().stream()
                .filter(t -> filter.status() == null || t.getStatus() == filter.status())
                .filter(t -> filter.brand() == null || t.getProduct().getBrand() == filter.brand())
                .filter(t -> filter.customerId() == null || t.getCustomer().getId().equals(filter.customerId()))
                .map(DtoMapper::toDto)
                .toList();
        log.info("ADMIN TRANSACTIONS filter={} -> {} redaka", filter, list.size());
        return list;
    }

    @Transactional(readOnly = true)
    public TransactionDto transaction(long id) {
        Transaction t = transactionRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Transakcija " + id + " ne postoji"));
        log.info("ADMIN TRANSACTION tx={} customer={} voucher={} status={}",
                id, t.getCustomer().getName(), t.getVoucher().getSerial(), t.getStatus());
        return DtoMapper.toDto(t);
    }

    /**
     * Refund to the customer's wallet: only within the refund window and only if the voucher has not been redeemed.
     * The voucher is blocked (the customer already has the code).
     */
    @Transactional
    public TransactionDto refund(long id) {
        return doRefund(id, null);
    }

    /** Refund requested by the customer themselves: the purchase must be theirs. */
    @Transactional
    public TransactionDto refundForCustomer(long customerId, long id) {
        return doRefund(id, customerId);
    }

    /** Same rules as a refund, without executing it: what will happen, or why the refund is not possible. */
    @Transactional(readOnly = true)
    public RefundPreview previewRefundForCustomer(long customerId, long id) {
        Transaction t = transactionRepo.findByIdWithDetails(id)
                .filter(tx -> tx.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> notOwnPurchase(id));
        checkRefundRules(t, Instant.now(clock));
        BigDecimal balance = t.getCustomer().getWalletBalanceEur();
        log.info("REFUND PREVIEW tx={} customer={} amount={} € wallet={} € -> {} €", id, customerId,
                fmt(t.getAmountEur()), fmt(balance), fmt(balance.add(t.getAmountEur())));
        return new RefundPreview(t.getId(), t.getProduct().displayName(), t.getAmountEur(),
                t.getVoucher().getSerial(), balance, balance.add(t.getAmountEur()));
    }

    private TransactionDto doRefund(long id, Long ownerCustomerId) {
        log.info("REFUND request tx={}{}", id, ownerCustomerId == null ? "" : " customer=" + ownerCustomerId);
        // lock order: first the transaction (fresh state, a second refund waits), then the customer (wallet)
        Transaction t = transactionRepo.findByIdForUpdate(id)
                .orElseThrow(() -> ownerCustomerId == null
                        ? new NotFoundException("Transakcija " + id + " ne postoji")
                        : notOwnPurchase(id));
        if (ownerCustomerId != null && !t.getCustomer().getId().equals(ownerCustomerId)) {
            throw notOwnPurchase(id);
        }
        Customer customer = customerRepo.findByIdForUpdate(t.getCustomer().getId())
                .orElseThrow(() -> new NotFoundException("Kupac " + t.getCustomer().getId() + " ne postoji"));
        Instant now = Instant.now(clock);
        checkRefundRules(t, now);

        Voucher voucher = t.getVoucher();
        VoucherStatus before = voucher.getStatus();
        if (before == VoucherStatus.SOLD) {
            voucher.block();
        }
        t.refund(now);
        BigDecimal walletBalance = walletService.creditForRefund(customer, t, now);
        log.info("REFUND OK tx={} customer={} amount={} € -> wallet {} € voucher={} {} -> {}",
                id, customer.getName(), fmt(t.getAmountEur()), fmt(walletBalance),
                voucher.getSerial(), before, voucher.getStatus());
        return DtoMapper.toDto(t);
    }

    private void checkRefundRules(Transaction t, Instant now) {
        if (t.getStatus() == TransactionStatus.REFUNDED) {
            throw deny("REFUND", "ALREADY_REFUNDED", "Transakcija " + t.getId() + " je već vraćena");
        }
        Instant deadline = t.getCreatedAt().plus(rules.refundWindowDays(), ChronoUnit.DAYS);
        if (now.isAfter(deadline)) {
            throw deny("REFUND", "REFUND_WINDOW_EXPIRED",
                    "Rok za povrat (%d dana) je istekao za transakciju %d".formatted(rules.refundWindowDays(), t.getId()));
        }
        if (t.getVoucher().getStatus() == VoucherStatus.REDEEMED) {
            throw deny("REFUND", "VOUCHER_ALREADY_REDEEMED",
                    "Bon " + t.getVoucher().getSerial() + " je već iskorišten - povrat nije moguć");
        }
    }

    /** Voucher block (complaint, suspected abuse). A redeemed or already blocked voucher is left untouched. */
    @Transactional
    public VoucherDto blockVoucher(String serial) {
        Voucher voucher = voucherRepo.findBySerial(serial)
                .orElseThrow(() -> new NotFoundException("Bon " + serial + " ne postoji"));
        return doBlock(voucher);
    }

    /** Block requested by the customer themselves: the voucher must come from their own purchase. */
    @Transactional
    public VoucherDto blockVoucherForCustomer(long customerId, String serial) {
        return doBlock(ownVoucher(customerId, serial));
    }

    /** Same rules as a block, without executing it. */
    @Transactional(readOnly = true)
    public VoucherDto previewBlockForCustomer(long customerId, String serial) {
        Voucher voucher = ownVoucher(customerId, serial);
        checkBlockRules(voucher);
        return DtoMapper.toDto(voucher);
    }

    private VoucherDto doBlock(Voucher voucher) {
        log.info("BLOCK request voucher={}", voucher.getSerial());
        VoucherStatus before = voucher.getStatus();
        checkBlockRules(voucher);
        voucher.block();
        log.info("BLOCK OK voucher={} {} -> {}", voucher.getSerial(), before, voucher.getStatus());
        return DtoMapper.toDto(voucher);
    }

    private void checkBlockRules(Voucher voucher) {
        String serial = voucher.getSerial();
        switch (voucher.getStatus()) {
            case REDEEMED -> throw deny("BLOCK", "VOUCHER_ALREADY_REDEEMED", "Bon " + serial + " je već iskorišten");
            case BLOCKED -> throw deny("BLOCK", "ALREADY_BLOCKED", "Bon " + serial + " je već blokiran");
            case AVAILABLE, SOLD -> {
                // may be blocked
            }
        }
    }

    private Voucher ownVoucher(long customerId, String serial) {
        return transactionRepo.findByVoucherSerial(serial)
                .filter(t -> t.getCustomer().getId().equals(customerId))
                .map(Transaction::getVoucher)
                .orElseThrow(() -> new NotFoundException("Bon " + serial + " nije pronađen među vašim kupnjama"));
    }

    @Transactional(readOnly = true)
    public List<TicketDto> tickets() {
        List<TicketDto> list = ticketRepo.findAllWithDetails().stream().map(DtoMapper::toDto).toList();
        log.info("TICKETS -> {} prijava ({} otvorenih)",
                list.size(), list.stream().filter(t -> t.status() == TicketStatus.OPEN).count());
        return list;
    }

    @Transactional(readOnly = true)
    public List<TicketDto> ticketsForCustomer(long customerId) {
        List<TicketDto> list = ticketRepo.findByCustomerIdWithDetails(customerId).stream()
                .map(DtoMapper::toDto).toList();
        log.info("TICKETS customer={} -> {} prijava", customerId, list.size());
        return list;
    }

    /** New support ticket; if it references a transaction, that transaction must belong to the same customer. */
    @Transactional
    public TicketDto createTicket(long customerId, Long transactionId, String subject, String description) {
        log.info("TICKET create customer={} transaction={} subject='{}'", customerId, transactionId, subject);
        Customer customer = customerRepo.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Kupac " + customerId + " ne postoji"));
        Transaction transaction = null;
        if (transactionId != null) {
            transaction = transactionRepo.findById(transactionId)
                    .orElseThrow(() -> new NotFoundException("Transakcija " + transactionId + " ne postoji"));
            if (!transaction.getCustomer().getId().equals(customerId)) {
                throw deny("TICKET", "TRANSACTION_CUSTOMER_MISMATCH",
                        "Transakcija %d ne pripada kupcu %d".formatted(transactionId, customerId));
            }
        }
        SupportTicket ticket = ticketRepo.save(
                new SupportTicket(customer, transaction, subject, description, Instant.now(clock)));
        log.info("TICKET OK id={} customer={} ({}) transaction={}",
                ticket.getId(), customerId, customer.getName(), transactionId);
        return DtoMapper.toDto(ticket);
    }

    @Transactional
    public TicketDto resolveTicket(long id, String resolution) {
        log.info("TICKET resolve id={}", id);
        SupportTicket ticket = ticketRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Prijava " + id + " ne postoji"));
        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            throw deny("TICKET", "ALREADY_RESOLVED", "Prijava " + id + " je već riješena");
        }
        ticket.resolve(resolution, Instant.now(clock));
        log.info("TICKET RESOLVED id={} resolution='{}'", id, resolution);
        return DtoMapper.toDto(ticket);
    }

    private static NotFoundException notOwnPurchase(long id) {
        return new NotFoundException("Kupnja " + id + " nije pronađena među vašim kupnjama");
    }

    private BusinessRuleException deny(String operation, String code, String message) {
        log.warn("{} DENIED reason={} - {}", operation, code, message);
        return new BusinessRuleException(code, message);
    }

    private static String fmt(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
