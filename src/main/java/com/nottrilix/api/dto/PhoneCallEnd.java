package com.nottrilix.api.dto;

/** The end of a phone call: the customer token (if any) is revoked, the call is closed in the audit trail. */
public record PhoneCallEnd(String customerToken, String reason, long durationMs, int turns) {
}
