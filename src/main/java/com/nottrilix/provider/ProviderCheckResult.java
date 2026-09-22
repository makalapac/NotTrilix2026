package com.nottrilix.provider;

import java.time.Instant;

/** Odgovor pruzatelja na provjeru bona. */
public record ProviderCheckResult(ProviderVoucherStatus status, Instant redeemedAt, String reference, long latencyMs) {
}
