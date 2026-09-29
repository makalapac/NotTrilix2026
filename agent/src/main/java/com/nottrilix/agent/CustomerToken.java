package com.nottrilix.agent;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Token kupca u cije ime agent upravo zove sustav. Vezan je uz poziv (ScopedValue), ne uz aplikaciju:
 * MCP klijent je jedan, a kupaca je mnogo, pa svaki MCP zahtjev nosi token onoga za koga se radi.
 * Model token nikad ne vidi.
 */
public final class CustomerToken {

    private static final ScopedValue<String> CURRENT = ScopedValue.newInstance();

    private CustomerToken() {
    }

    /** Izvrsi radnju u ime kupca s ovim tokenom (bez tokena = u ime agenta). */
    public static <T> T callWith(String token, Supplier<T> action) {
        if (token == null || token.isBlank()) {
            return action.get();
        }
        return ScopedValue.where(CURRENT, token).call(action::get);
    }

    /** Token kupca za poziv koji je u tijeku; prazno ako agent zove u svoje ime (spajanje, popis alata). */
    public static Optional<String> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }
}
