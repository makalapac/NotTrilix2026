package com.nottrilix.provider;

import com.nottrilix.domain.Voucher;

/** Integration with the service provider (A1, HT, Steam, PlayStation, Xbox). In Phase 1 there is only a simulation. */
public interface ProviderGateway {

    /** Checks the voucher state at the provider (activated / redeemed / blocked). */
    ProviderCheckResult checkVoucher(Voucher voucher);
}
