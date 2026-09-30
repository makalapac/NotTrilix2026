package com.nottrilix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** Business rules from application.yml (nottrilix.rules.*). */
@ConfigurationProperties(prefix = "nottrilix.rules")
public record RulesProperties(BigDecimal dailyLimitEur, int refundWindowDays, BigDecimal maxTopUpEur) {
}
