package com.nottrilix.agent.telephony;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Infobip Calls API, control plane only: answer an inbound call, bridge it to our media WebSocket (a "dialog" whose
 * child leg is a WEBSOCKET endpoint), hang up, and register the public WebSocket URL as a media-stream config.
 * Plain REST; the paths follow the Calls API as verified live in the earlier prototype.
 */
@Component
class InfobipCallsClient {

    private static final Logger log = LoggerFactory.getLogger("TELEPHONY");

    private final TelephonyProperties props;
    private final RestClient http;

    InfobipCallsClient(TelephonyProperties props) {
        this.props = props;
        this.http = props.configured()
                ? RestClient.builder().baseUrl(props.baseUrl())
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "App " + props.apiKey()).build()
                : null;
        if (!props.configured()) {
            log.warn("TELEPHONY Infobip nije konfiguriran (INFOBIP_BASE_URL / INFOBIP_API_KEY): pozivi se ne javljaju,"
                    + " radi samo simulator (scripts/phone_sim.py)");
        }
    }

    boolean configured() {
        return http != null;
    }

    void answer(String callId) {
        log.info("CALL {} javljam se", callId);
        client().post().uri("/calls/1/calls/{id}/answer", callId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of())
                .retrieve().toBodilessEntity();
    }

    /** Bridges the answered call to our media WebSocket. */
    void connectToWebSocket(String callId, String websocketEndpointConfigId) {
        log.info("CALL {} most na media WebSocket (config {})", callId, websocketEndpointConfigId);
        client().post().uri("/calls/1/dialogs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "parentCallId", callId,
                        "childCallRequest", Map.of(
                                "endpoint", Map.of(
                                        "type", "WEBSOCKET",
                                        "websocketEndpointConfigId", websocketEndpointConfigId))))
                .retrieve().toBodilessEntity();
    }

    void hangup(String callId) {
        log.info("CALL {} prekidam", callId);
        client().post().uri("/calls/1/calls/{id}/hangup", callId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of())
                .retrieve().toBodilessEntity();
    }

    /** Points an existing media-stream config at a new public wss:// URL (the tunnel changes with every start). */
    @SuppressWarnings("unchecked")
    String updateMediaConfig(String id, String name, String wssUrl, int sampleRate) {
        Map<String, Object> body = Map.of("type", "WEBSOCKET_ENDPOINT", "name", name, "url", wssUrl,
                "sampleRate", sampleRate);
        try {
            client().put().uri("/calls/1/media-stream-configs/{id}", id)
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
            log.info("TELEPHONY media-stream config {} -> {} @ {} Hz", id, wssUrl, sampleRate);
            return id;
        } catch (RestClientResponseException e) {
            log.warn("TELEPHONY media-stream config {} se ne da promijeniti (HTTP {}): stvaram novu", id,
                    e.getStatusCode().value());
            return createMediaConfig(name, wssUrl, sampleRate);
        }
    }

    @SuppressWarnings("unchecked")
    String createMediaConfig(String name, String wssUrl, int sampleRate) {
        Map<String, Object> response = client().post().uri("/calls/1/media-stream-configs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "WEBSOCKET_ENDPOINT", "name", name, "url", wssUrl, "sampleRate", sampleRate))
                .retrieve().body(Map.class);
        Object id = response == null ? null : response.get("id");
        if (id == null) {
            throw new IllegalStateException("Infobip media-stream config bez id-a: " + response);
        }
        log.info("TELEPHONY nova media-stream config {} -> {} @ {} Hz", id, wssUrl, sampleRate);
        return String.valueOf(id);
    }

    private RestClient client() {
        if (http == null) {
            throw new IllegalStateException("Infobip nije konfiguriran (INFOBIP_BASE_URL / INFOBIP_API_KEY)");
        }
        return http;
    }
}
