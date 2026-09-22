package com.nottrilix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketRequest(@NotNull(message = "je obavezan") Long customerId,
                            Long transactionId,
                            @NotBlank(message = "je obavezan") @Size(max = 200) String subject,
                            @Size(max = 2000) String description) {
}
