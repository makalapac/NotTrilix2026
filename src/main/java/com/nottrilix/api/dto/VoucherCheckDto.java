package com.nottrilix.api.dto;

import com.nottrilix.domain.Brand;
import com.nottrilix.domain.VoucherStatus;
import com.nottrilix.provider.ProviderVoucherStatus;

import java.time.Instant;

/** Rezultat provjere bona kod pruzatelja usluge, uz nase lokalno stanje za usporedbu. */
public record VoucherCheckDto(String serial, Brand brand, String providerName,
                              ProviderVoucherStatus providerStatus, VoucherStatus localStatus,
                              Instant redeemedAt, Instant checkedAt, String providerReference, long latencyMs,
                              String message) {
}
