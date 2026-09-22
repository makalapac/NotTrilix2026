package com.nottrilix.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CustomerLoginRequest(@NotBlank(message = "je obavezan") @Email(message = "nije ispravan") String email,
                                   @NotBlank(message = "je obavezna") String password) {
}
