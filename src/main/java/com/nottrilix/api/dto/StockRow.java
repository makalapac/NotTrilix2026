package com.nottrilix.api.dto;

import com.nottrilix.domain.Brand;

import java.math.BigDecimal;

public record StockRow(Long productId, Brand brand, String name, BigDecimal denominationEur, boolean active,
                       long available, long sold, long redeemed, long blocked) {
}
