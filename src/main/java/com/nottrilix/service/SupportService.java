package com.nottrilix.service;

import com.nottrilix.api.dto.DtoMapper;
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

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Admin/support: pregled transakcija, povrat, blokada bona, prijave. Ovdje zive pravila podrske. */
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

    /** Filter za pregled transakcija; svako polje je opcionalno. */
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
     * Povrat novca na wallet kupca: samo unutar roka i samo ako bon nije iskoristen.
     * Bon se blokira (kod je vec kod kupca).
     */
    @Transactional
    public TransactionDto refund(long id) {
        log.info("REFUND request tx={}", id);
        // redoslijed zakljucavanja: prvo transakcija (svjeze stanje, drugi povrat ceka), zatim kupac (wallet)
        Transaction t = transactionRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Transakcija " + id + " ne postoji"));
        Customer customer = customerRepo.findByIdForUpdate(t.getCustomer().getId())
                .orElseThrow(() -> new NotFoundException("Kupac " + t.getCustomer().getId() + " ne postoji"));
        if (t.getStatus() == TransactionStatus.REFUNDED) {
            throw deny("REFUND", "ALREADY_REFUNDED", "Transakcija " + id + " je već vraćena");
        }
        Instant now = Instant.now(clock);
        Instant deadline = t.getCreatedAt().plus(rules.refundWindowDays(), ChronoUnit.DAYS);
        if (now.isAfter(deadline)) {
            throw deny("REFUND", "REFUND_WINDOW_EXPIRED",
                    "Rok za povrat (%d dana) je istekao za transakciju %d".formatted(rules.refundWindowDays(), id));
        }
        Voucher voucher = t.getVoucher();
        if (voucher.getStatus() == VoucherStatus.REDEEMED) {
            throw deny("REFUND", "VOUCHER_ALREADY_REDEEMED",
                    "Bon " + voucher.getSerial() + " je već iskorišten - povrat nije moguć");
        }
        VoucherStatus before = voucher.getStatus();
        if (before == VoucherStatus.SOLD) {
            voucher.block();
        }
        t.refund(now);
        java.math.BigDecimal walletBalance = walletService.creditForRefund(customer, t, now);
        log.info("REFUND OK tx={} customer={} amount={} € -> wallet {} € voucher={} {} -> {}",
                id, customer.getName(), t.getAmountEur().stripTrailingZeros().toPlainString(),
                walletBalance.stripTrailingZeros().toPlainString(), voucher.getSerial(), before, voucher.getStatus());
        return DtoMapper.toDto(t);
    }

    /** Blokada bona (reklamacija, sumnja na zlouporabu). Iskoristen ili vec blokiran bon se ne dira. */
    @Transactional
    public VoucherDto blockVoucher(String serial) {
        log.info("BLOCK request voucher={}", serial);
        Voucher voucher = voucherRepo.findBySerial(serial)
                .orElseThrow(() -> new NotFoundException("Bon " + serial + " ne postoji"));
        VoucherStatus before = voucher.getStatus();
        switch (before) {
            case REDEEMED -> throw deny("BLOCK", "VOUCHER_ALREADY_REDEEMED", "Bon " + serial + " je već iskorišten");
            case BLOCKED -> throw deny("BLOCK", "ALREADY_BLOCKED", "Bon " + serial + " je već blokiran");
            case AVAILABLE, SOLD -> voucher.block();
        }
        log.info("BLOCK OK voucher={} {} -> {}", serial, before, voucher.getStatus());
        return DtoMapper.toDto(voucher);
    }

    @Transactional(readOnly = true)
    public List<TicketDto> tickets() {
        List<TicketDto> list = ticketRepo.findAllWithDetails().stream().map(DtoMapper::toDto).toList();
        log.info("TICKETS -> {} prijava ({} otvorenih)",
                list.size(), list.stream().filter(t -> t.status() == TicketStatus.OPEN).count());
        return list;
    }

    /** Nova prijava; ako se veze na transakciju, ona mora pripadati istom kupcu. */
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

    private BusinessRuleException deny(String operation, String code, String message) {
        log.warn("{} DENIED reason={} - {}", operation, code, message);
        return new BusinessRuleException(code, message);
    }
}
