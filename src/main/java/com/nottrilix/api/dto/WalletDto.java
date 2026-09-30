package com.nottrilix.api.dto;

import java.math.BigDecimal;
import java.util.List;

/** Wallet balance and change history (newest first). */
public record WalletDto(BigDecimal balanceEur, List<WalletEntryDto> entries) {
}
