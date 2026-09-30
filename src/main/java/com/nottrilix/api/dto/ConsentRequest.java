package com.nottrilix.api.dto;

import com.nottrilix.domain.ConsentType;
import jakarta.validation.constraints.NotNull;

public record ConsentRequest(@NotNull ConsentType type) {
}
