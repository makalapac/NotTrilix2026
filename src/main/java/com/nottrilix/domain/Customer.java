package com.nottrilix.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** Also the username for logging in to the shop. */
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    /** bcrypt; never leaves the service layer. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** Wallet balance; every change has a row in wallet_entries. Modified only through credit/debit. */
    @Column(name = "wallet_balance_eur", nullable = false, precision = 10, scale = 2)
    private BigDecimal walletBalanceEur = BigDecimal.ZERO;

    protected Customer() {
    }

    public Customer(String name, String email, String phone, String passwordHash) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
    }

    public void credit(BigDecimal amount) {
        walletBalanceEur = walletBalanceEur.add(amount);
    }

    public void debit(BigDecimal amount) {
        walletBalanceEur = walletBalanceEur.subtract(amount);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public BigDecimal getWalletBalanceEur() {
        return walletBalanceEur;
    }
}
