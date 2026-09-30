package com.nottrilix.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** One wallet balance change (ledger entry): signed amount and the balance afterwards. */
@Entity
@Table(name = "wallet_entries")
public class WalletEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletEntryType type;

    /** + top-up / refund, - purchase. */
    @Column(name = "amount_eur", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountEur;

    @Column(name = "balance_after_eur", nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceAfterEur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(length = 200)
    private String note;

    protected WalletEntry() {
    }

    public WalletEntry(Customer customer, WalletEntryType type, BigDecimal amountEur, BigDecimal balanceAfterEur,
                       Transaction transaction, String note, Instant createdAt) {
        this.customer = customer;
        this.type = type;
        this.amountEur = amountEur;
        this.balanceAfterEur = balanceAfterEur;
        this.transaction = transaction;
        this.note = note;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public WalletEntryType getType() {
        return type;
    }

    public BigDecimal getAmountEur() {
        return amountEur;
    }

    public BigDecimal getBalanceAfterEur() {
        return balanceAfterEur;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public String getNote() {
        return note;
    }
}
