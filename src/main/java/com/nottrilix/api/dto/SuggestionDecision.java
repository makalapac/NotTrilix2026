package com.nottrilix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Odobrenje prijedloga: konacni tekst pitanja i odgovora (admin smije ispraviti nacrt modela). */
public record SuggestionDecision(@NotBlank @Size(max = 500) String question,
                                 @NotBlank @Size(max = 2000) String answer) {
}
