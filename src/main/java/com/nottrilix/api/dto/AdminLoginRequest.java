package com.nottrilix.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(@NotBlank(message = "je obavezno") String username,
                                @NotBlank(message = "je obavezna") String password) {
}
