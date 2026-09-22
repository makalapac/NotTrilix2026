package com.nottrilix.api.dto;

import java.math.BigDecimal;

public record CustomerDto(Long id, String name, String email, String phone, BigDecimal walletBalanceEur) {
}
