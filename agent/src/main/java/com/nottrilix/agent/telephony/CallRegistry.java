package com.nottrilix.agent.telephony;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Calls in progress, by the provider's call id (control plane and media plane meet here). */
@Component
class CallRegistry {

    private final Map<String, PhoneCall> calls = new ConcurrentHashMap<>();
    private final Clock clock;

    CallRegistry(Clock clock) {
        this.clock = clock;
    }

    PhoneCall register(String callId, String from) {
        return calls.computeIfAbsent(callId, id -> new PhoneCall(id, from, Instant.now(clock)));
    }

    Optional<PhoneCall> get(String callId) {
        return Optional.ofNullable(callId == null ? null : calls.get(callId));
    }

    /**
     * The media stream reports a call id that may be the bridged leg's, not the one we answered; with one caller at a
     * time (a demo), the newest call without a media stream is the right one.
     */
    Optional<PhoneCall> newestWithoutMedia() {
        return calls.values().stream()
                .filter(call -> !call.ended && !call.mediaAttached)
                .max(Comparator.comparing(call -> call.startedAt));
    }

    void remove(String callId) {
        calls.remove(callId);
    }

    int size() {
        return calls.size();
    }
}
