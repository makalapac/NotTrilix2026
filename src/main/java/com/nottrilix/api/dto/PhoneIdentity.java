package com.nottrilix.api.dto;

/**
 * Who is calling, as far as the system can tell from the caller id: a recognised customer gets a short-lived customer
 * token the agent uses for every MCP call during this call; an unknown caller gets none (no account actions).
 */
public record PhoneIdentity(boolean identified, Long customerId, String customerName, String customerToken) {

    public static PhoneIdentity unknown() {
        return new PhoneIdentity(false, null, null, null);
    }
}
