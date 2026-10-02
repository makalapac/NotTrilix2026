package com.nottrilix.agent.telephony;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/** Control-plane state of one phone call: who called, who they are (as the system says), where we are with it. */
final class PhoneCall {

    /** The system's answer to "who is calling": a customer token for the duration of the call, or nothing. */
    record Identity(boolean identified, Long customerId, String customerName, String customerToken) {
        static Identity unknown() {
            return new Identity(false, null, null, null);
        }
    }

    final String callId;
    final String from;
    final Instant startedAt;
    volatile Identity identity = Identity.unknown();
    volatile boolean bridged;
    volatile boolean mediaAttached;
    volatile boolean ended;
    final AtomicInteger turns = new AtomicInteger();

    PhoneCall(String callId, String from, Instant startedAt) {
        this.callId = callId;
        this.from = from;
        this.startedAt = startedAt;
    }

    String firstName() {
        String name = identity.customerName();
        return name == null || name.isBlank() ? "" : name.trim().split("\\s+")[0];
    }
}
