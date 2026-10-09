package com.nottrilix.agent.telephony;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The control plane of a phone call, as Infobip reports it (webhooks; demo: no signature check). CALL_RECEIVED: the
 * system says who is calling and the call is answered; CALL_ESTABLISHED: the call is bridged to our media WebSocket;
 * CALL_FINISHED / CALL_FAILED: the call is closed in the system. Only calls we answered are bridged, so the events of
 * the bridged leg itself are ignored. Events depend on the subscriptions in the Infobip portal, so after answering
 * the call state is also polled for a few seconds and the call is bridged as soon as it is ESTABLISHED, event or not.
 */
@RestController
@RequestMapping("/infobip")
class InfobipWebhookController {

    private static final Logger log = LoggerFactory.getLogger("TELEPHONY");
    private static final String MEDIA_CONFIG_NAME = "nottrilix-media";
    /** Seconds after answering at which the call state is checked when no CALL_ESTABLISHED event has arrived. */
    private static final List<Integer> BRIDGE_CHECKS_SECONDS = List.of(2, 4, 7);

    private final InfobipCallsClient infobip;
    private final CallRegistry registry;
    private final PhoneGateway gateway;
    private final TelephonyProperties props;
    private final Clock clock;
    private volatile String wsConfigId;
    private final ScheduledExecutorService checks = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "telephony-bridge-check");
        t.setDaemon(true);
        return t;
    });

    InfobipWebhookController(InfobipCallsClient infobip, CallRegistry registry, PhoneGateway gateway,
                             TelephonyProperties props, Clock clock) {
        this.infobip = infobip;
        this.registry = registry;
        this.gateway = gateway;
        this.props = props;
        this.clock = clock;
        this.wsConfigId = props.websocketEndpointConfigId();
    }

    /** At startup: point Infobip's media-stream config at this run's public URL and say which webhooks to configure. */
    @EventListener(ApplicationReadyEvent.class)
    public void publish() {
        String wss = props.mediaWebSocketUrl();
        if (!props.enabled()) {
            log.info("TELEPHONY iskljucen");
            return;
        }
        if (!infobip.configured() || wss == null) {
            log.info("TELEPHONY bez Infobipa ili bez javnog URL-a: samo simulator (scripts/phone_sim.py -> ws://localhost:8090/infobip/media)");
            return;
        }
        try {
            wsConfigId = wsConfigId == null || wsConfigId.isBlank()
                    ? infobip.createMediaConfig(MEDIA_CONFIG_NAME, wss, props.sampleRate())
                    : infobip.updateMediaConfig(wsConfigId, MEDIA_CONFIG_NAME, wss, props.sampleRate());
            String base = props.publicUrl().trim().replaceFirst("/+$", "");
            log.info("TELEPHONY spreman: media WebSocket {} (config {}); webhookovi u Infobip portalu: {}/infobip/call-received i {}/infobip/event",
                    wss, wsConfigId, base, base);
        } catch (RuntimeException e) {
            log.error("TELEPHONY media-stream config nije postavljen: {}", e.getMessage());
        }
    }

    /** Inbound call (receive URL): identify the caller, answer. Always 200, so the provider does not retry. */
    @PostMapping("/call-received")
    public Map<String, Object> callReceived(@RequestBody Map<String, Object> body) {
        String callId = extract(body, "callId");
        if (callId == null) {
            log.warn("TELEPHONY CALL_RECEIVED bez callId: {}", body);
            return Map.of("ok", false);
        }
        PhoneCall call = received(callId, extract(body, "from"), extract(body, "to"));
        return Map.of("ok", true, "identified", call.identity.identified());
    }

    /** Call lifecycle events (event URL). */
    @PostMapping("/event")
    public Map<String, Object> event(@RequestBody Map<String, Object> body) {
        String type = extract(body, "type");
        String callId = extract(body, "callId");
        log.info("TELEPHONY event {} call={}", type, callId);
        if (type != null && (type.endsWith("FINISHED") || type.endsWith("FAILED"))) {
            String details = String.valueOf(body.get("properties"));   // why Infobip ended the call
            log.info("TELEPHONY event {} detalji: {}", type, details.length() > 700 ? details.substring(0, 700) + "…" : details);
        }
        if (callId == null) {
            return Map.of("ok", false);
        }
        switch (type == null ? "" : type) {
            case "CALL_RECEIVED" -> received(callId, extract(body, "from"), extract(body, "to"));
            case "CALL_ESTABLISHED" -> registry.get(callId).ifPresentOrElse(this::bridge,
                    () -> log.info("CALL {} established, ali nije poziv koji smo javili (most ili tuđi poziv): ignoriram", callId));
            case "CALL_FINISHED", "CALL_FAILED", "CALL_HANGUP" -> registry.get(callId).ifPresent(call -> end(call, type));
            default -> {
                // other events are not interesting
            }
        }
        return Map.of("ok", true);
    }

    /** Manual setup: register a public wss URL as the media-stream config (when there is no public URL at startup). */
    @PostMapping("/setup")
    public Map<String, String> setup(@RequestParam("wss") String wssUrl) {
        wsConfigId = wsConfigId == null || wsConfigId.isBlank()
                ? infobip.createMediaConfig(MEDIA_CONFIG_NAME, wssUrl, props.sampleRate())
                : infobip.updateMediaConfig(wsConfigId, MEDIA_CONFIG_NAME, wssUrl, props.sampleRate());
        return Map.of("websocketEndpointConfigId", wsConfigId, "wssUrl", wssUrl);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", props.enabled());
        status.put("infobipConfigured", infobip.configured());
        status.put("publicUrl", props.publicUrl() == null ? "" : props.publicUrl());
        status.put("mediaWebSocketUrl", props.mediaWebSocketUrl() == null ? "" : props.mediaWebSocketUrl());
        status.put("websocketEndpointConfigId", wsConfigId == null ? "" : wsConfigId);
        status.put("sampleRate", props.sampleRate());
        status.put("activeCalls", registry.size());
        return status;
    }

    private PhoneCall received(String callId, String from, String to) {
        PhoneCall call = registry.register(callId, from);
        call.identity = gateway.identify(call, from, to);
        log.info("CALL {} dolazni poziv (razgovor {}), pozivatelj {}", callId, call.conversationId,
                call.identity.identified()
                        ? "customer#" + call.identity.customerId() + " (" + call.identity.customerName() + ")" : "nepoznat");
        if (infobip.configured()) {
            // Dialog first: Infobip answers the inbound call itself once our WebSocket leg is up. Answering by API
            // without media left a PSTN call in PRE_ESTABLISHED and CALL_ESTABLISHED never came (live test 2026-10-09).
            if (!bridge(call)) {
                try {
                    infobip.answer(callId);
                    BRIDGE_CHECKS_SECONDS.forEach(s -> checks.schedule(() -> bridgeIfEstablished(call), s, TimeUnit.SECONDS));
                } catch (RuntimeException e) {
                    log.error("CALL {} javljanje nije uspjelo: {}", callId, e.getMessage());
                }
            }
        }
        return call;
    }

    /** Fallback for a missing CALL_ESTABLISHED event: ask Infobip for the call state and bridge once it is established. */
    private void bridgeIfEstablished(PhoneCall call) {
        if (call.bridged || call.ended) {
            return;
        }
        try {
            String state = infobip.state(call.callId);
            if ("ESTABLISHED".equals(state) || "PRE_ESTABLISHED".equals(state)) {
                log.warn("CALL {} stanje ESTABLISHED bez događaja CALL_ESTABLISHED (provjeri pretplatu u portalu): most preko provjere stanja",
                        call.callId);
                bridge(call);
            } else {
                log.info("CALL {} provjera stanja: {}", call.callId, state);
            }
        } catch (RuntimeException e) {
            log.warn("CALL {} provjera stanja nije uspjela: {}", call.callId, e.getMessage());
        }
    }

    /** Bridges the call to our media WebSocket (an Infobip dialog); true when the dialog exists or is not needed. */
    private boolean bridge(PhoneCall call) {
        synchronized (call) {
            if (call.bridged) {
                log.info("CALL {} već premošten", call.callId);
                return true;
            }
            call.bridged = true;
        }
        if (!infobip.configured()) {
            return true;
        }
        if (wsConfigId == null || wsConfigId.isBlank()) {
            log.error("CALL {} nema media-stream config: postavi NOTTRILIX_PUBLIC_URL ili pozovi POST /infobip/setup?wss=...",
                    call.callId);
            return false;
        }
        try {
            infobip.connectToWebSocket(call.callId, wsConfigId);
            return true;
        } catch (RuntimeException e) {
            call.bridged = false;
            log.error("CALL {} most na WebSocket nije uspio: {}", call.callId, e.getMessage());
            return false;
        }
    }

    private void end(PhoneCall call, String reason) {
        if (call.ended) {
            return;
        }
        call.ended = true;
        gateway.end(call, reason, Duration.between(call.startedAt, Instant.now(clock)).toMillis());
        registry.remove(call.callId);
        log.info("CALL {} zatvoren ({}), {} turnova", call.callId, reason, call.turns.get());
    }

    /** A value from the webhook payload: top level first, then nested maps (the payload shape varies by event). */
    static String extract(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value != null && !(value instanceof Map)) {
            return String.valueOf(value);
        }
        for (Object nested : body.values()) {
            if (nested instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                String found = extract((Map<String, Object>) map, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
