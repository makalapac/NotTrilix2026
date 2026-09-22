package com.nottrilix.api.dto;

import com.nottrilix.domain.Brand;
import com.nottrilix.domain.Category;

import java.math.BigDecimal;

/** Stavka kataloga za kupce: kupac vidi samo je li dostupno, ne i koliko je na zalihi. */
public record CatalogItem(Long productId, Category category, Brand brand, String brandName, String name,
                          BigDecimal denominationEur, boolean available) {
}
