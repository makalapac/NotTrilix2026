package com.nottrilix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveRequest(@NotBlank(message = "je obavezno") @Size(max = 2000) String resolution) {
}
