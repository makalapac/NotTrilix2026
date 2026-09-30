package com.nottrilix.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Privola kupca (GDPR cl. 7): sto je prihvatio, kada i koju verziju teksta; povlacenje ostavlja trag. */
@Entity
@Table(name = "consents")
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsentType type;

    @Column(name = "text_version", nullable = false, length = 20)
    private String textVersion;

    @Column(name = "given_at", nullable = false)
    private Instant givenAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected Consent() {
    }

    public Consent(Long customerId, ConsentType type, String textVersion, Instant givenAt) {
        this.customerId = customerId;
        this.type = type;
        this.textVersion = textVersion;
        this.givenAt = givenAt;
    }

    public void withdraw(Instant at) {
        this.withdrawnAt = at;
    }

    public boolean isActive() {
        return withdrawnAt == null;
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public ConsentType getType() {
        return type;
    }

    public String getTextVersion() {
        return textVersion;
    }

    public Instant getGivenAt() {
        return givenAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }
}
