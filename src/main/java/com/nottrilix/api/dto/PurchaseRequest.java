package com.nottrilix.api.dto;

import jakarta.validation.constraints.NotNull;

/** Kupac je prijavljeni korisnik (token), pa se salje samo proizvod. */
public record PurchaseRequest(@NotNull(message = "je obavezan") Long productId) {
}
