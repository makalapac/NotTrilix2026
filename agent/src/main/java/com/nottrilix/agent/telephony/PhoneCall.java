package com.nottrilix.agent.telephony;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
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
    /** The conversation id the system sees for this call (see {@link #conversationIdFor}). */
    final String conversationId;
    final String from;
    final Instant startedAt;
    volatile Identity identity = Identity.unknown();
    volatile boolean bridged;
    volatile boolean mediaAttached;
    volatile boolean ended;
    final AtomicInteger turns = new AtomicInteger();

    PhoneCall(String callId, String from, Instant startedAt) {
        this.callId = callId;
        this.conversationId = conversationIdFor(callId);
        this.from = from;
        this.startedAt = startedAt;
    }

    /**
     * The system keeps conversation ids in 64 characters (chat ids are short); Infobip call ids are 68. Short ids
     * pass through unchanged, long ones become "tel-" plus a digest, stable for the whole call.
     */
    static String conversationIdFor(String callId) {
        if (callId == null || callId.length() <= 64) {
            return callId;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(callId.getBytes(StandardCharsets.UTF_8));
            return "tel-" + HexFormat.of().formatHex(digest, 0, 14);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    String firstName() {
        String name = identity.customerName();
        return name == null || name.isBlank() ? "" : name.trim().split("\\s+")[0];
    }
}
