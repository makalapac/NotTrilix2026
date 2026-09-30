package com.nottrilix.api.dto;

import jakarta.validation.constraints.NotNull;

/** The customer is the logged-in user (token), so only the product is sent. */
public record PurchaseRequest(@NotNull(message = "je obavezan") Long productId) {
}
