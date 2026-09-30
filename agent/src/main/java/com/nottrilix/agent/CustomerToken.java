package com.nottrilix.agent;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Token of the customer on whose behalf the agent is currently calling the system. It is bound to the call
 * (ScopedValue), not to the application: there is one MCP client and many customers, so every MCP request carries
 * the token of the one it is for. The model never sees the token.
 */
public final class CustomerToken {

    private static final ScopedValue<String> CURRENT = ScopedValue.newInstance();

    private CustomerToken() {
    }

    /** Run the action on behalf of the customer with this token (no token = on behalf of the agent). */
    public static <T> T callWith(String token, Supplier<T> action) {
        if (token == null || token.isBlank()) {
            return action.get();
        }
        return ScopedValue.where(CURRENT, token).call(action::get);
    }

    /** Customer token for the call in progress; empty if the agent calls on its own behalf (connect, tool list). */
    public static Optional<String> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }
}
