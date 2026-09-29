package com.nottrilix.api.dto;

import java.math.BigDecimal;

/** Dnevni limit kupnje: koliko je danas vec kupljeno i koliko je jos dostupno. */
public record DailyLimitStatus(BigDecimal limitEur, BigDecimal spentTodayEur, BigDecimal remainingEur) {
}
