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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Purchase of a single voucher. */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    @Column(name = "amount_eur", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountEur;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    protected Transaction() {
    }

    public Transaction(Customer customer, Product product, Voucher voucher, Channel channel, Instant createdAt) {
        this.customer = customer;
        this.product = product;
        this.voucher = voucher;
        this.channel = channel;
        this.createdAt = createdAt;
        this.status = TransactionStatus.COMPLETED;
        this.amountEur = product.getDenominationEur();
    }

    public void refund(Instant at) {
        this.status = TransactionStatus.REFUNDED;
        this.refundedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Channel getChannel() {
        return channel;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Product getProduct() {
        return product;
    }

    public Voucher getVoucher() {
        return voucher;
    }

    public BigDecimal getAmountEur() {
        return amountEur;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }
}
