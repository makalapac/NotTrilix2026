package com.nottrilix.api.dto;

import java.math.BigDecimal;
import java.util.List;

/** Stanje walleta i povijest promjena (najnovije prve). */
public record WalletDto(BigDecimal balanceEur, List<WalletEntryDto> entries) {
}
