package com.nottrilix.api.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Support top-up of a customer's wallet (e.g. goodwill), with a mandatory note. */
public record AdminTopUpRequest(@NotNull(message = "je obavezan")
                                @Positive(message = "mora biti veći od 0")
                                @Digits(integer = 8, fraction = 2, message = "najviše 2 decimale") BigDecimal amountEur,
                                @NotBlank(message = "je obavezna") @Size(max = 200) String note) {
}
