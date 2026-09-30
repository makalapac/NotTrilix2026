package com.nottrilix.provider;

import java.time.Instant;

/** The provider's answer to a voucher check. */
public record ProviderCheckResult(ProviderVoucherStatus status, Instant redeemedAt, String reference, long latencyMs) {
}
