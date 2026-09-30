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

import java.time.Instant;

/** One voucher from stock: serial number (public identifier) + activation code (the secret the customer receives). */
@Entity
@Table(name = "vouchers")
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "serial_number", nullable = false, unique = true, length = 32)
    private String serial;

    @Column(nullable = false, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherStatus status = VoucherStatus.AVAILABLE;

    @Column(name = "sold_at")
    private Instant soldAt;

    protected Voucher() {
    }

    public Voucher(Product product, String serial, String code) {
        this.product = product;
        this.serial = serial;
        this.code = code;
    }

    public void markSold(Instant at) {
        this.status = VoucherStatus.SOLD;
        this.soldAt = at;
    }

    public void block() {
        this.status = VoucherStatus.BLOCKED;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getSerial() {
        return serial;
    }

    public String getCode() {
        return code;
    }

    public VoucherStatus getStatus() {
        return status;
    }

    public Instant getSoldAt() {
        return soldAt;
    }
}
