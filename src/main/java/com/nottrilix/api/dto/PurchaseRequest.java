package com.nottrilix.api.dto;

import jakarta.validation.constraints.NotNull;

public record PurchaseRequest(@NotNull(message = "je obavezan") Long customerId,
                              @NotNull(message = "je obavezan") Long productId) {
}
