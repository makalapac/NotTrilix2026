package com.nottrilix.api.dto;

import com.nottrilix.domain.Brand;
import com.nottrilix.domain.Category;

import java.math.BigDecimal;

/** Catalog item for customers: the customer only sees whether it is available, not how much is in stock. */
public record CatalogItem(Long productId, Category category, Brand brand, String brandName, String name,
                          BigDecimal denominationEur, boolean available) {
}
