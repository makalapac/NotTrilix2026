package com.nottrilix.api.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Uplata kupca na vlastiti wallet (simulirano placanje karticom). Gornja granica je poslovno pravilo. */
public record TopUpRequest(@NotNull(message = "je obavezan")
                           @Positive(message = "mora biti veći od 0")
                           @Digits(integer = 8, fraction = 2, message = "najviše 2 decimale") BigDecimal amountEur) {
}
