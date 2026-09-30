package com.nottrilix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Suggestion approval: the final text of the question and answer (the admin may correct the model's draft). */
public record SuggestionDecision(@NotBlank @Size(max = 500) String question,
                                 @NotBlank @Size(max = 2000) String answer) {
}
