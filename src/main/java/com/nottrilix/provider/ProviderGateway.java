package com.nottrilix.provider;

import com.nottrilix.domain.Voucher;

/** Integracija prema pruzatelju usluge (A1, HT, Steam, PlayStation, Xbox). U Fazi 1 postoji samo simulacija. */
public interface ProviderGateway {

    /** Provjera stanja bona kod pruzatelja (je li aktiviran / iskoristen / blokiran). */
    ProviderCheckResult checkVoucher(Voucher voucher);
}
