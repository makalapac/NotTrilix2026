package com.nottrilix.api.dto;

import java.math.BigDecimal;

/** Daily purchase limit: how much has already been bought today and how much is still available. */
public record DailyLimitStatus(BigDecimal limitEur, BigDecimal spentTodayEur, BigDecimal remainingEur) {
}
