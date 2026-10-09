package com.nottrilix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** An inbound phone call as the agent reports it: the provider's call id and the caller id (from). */
public record PhoneCallStart(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,128}") String callId,
        String from,
        String to) {
}
