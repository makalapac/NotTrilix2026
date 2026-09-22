package com.nottrilix.api.dto;

import com.nottrilix.domain.Brand;
import com.nottrilix.domain.Category;

import java.math.BigDecimal;

public record CatalogItem(Long productId, Category category, Brand brand, String brandName, String name,
                          BigDecimal denominationEur, long inStock) {
}
