package com.nottrilix.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Proizvod = brend + denominacija, npr. "Steam Wallet 25 EUR". */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Brand brand;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "serial_prefix", nullable = false, length = 8)
    private String serialPrefix;

    @Column(name = "denomination_eur", nullable = false, precision = 10, scale = 2)
    private BigDecimal denominationEur;

    @Column(nullable = false)
    private boolean active = true;

    protected Product() {
    }

    public Product(Category category, Brand brand, String name, String serialPrefix, BigDecimal denominationEur) {
        this.category = category;
        this.brand = brand;
        this.name = name;
        this.serialPrefix = serialPrefix;
        this.denominationEur = denominationEur;
    }

    /** Npr. "Steam Wallet 25 €". */
    public String displayName() {
        return name + " " + denominationEur.stripTrailingZeros().toPlainString() + " €";
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public Brand getBrand() {
        return brand;
    }

    public String getName() {
        return name;
    }

    public String getSerialPrefix() {
        return serialPrefix;
    }

    public BigDecimal getDenominationEur() {
        return denominationEur;
    }

    public boolean isActive() {
        return active;
    }
}
