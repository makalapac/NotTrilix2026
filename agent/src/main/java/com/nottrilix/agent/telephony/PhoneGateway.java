package com.nottrilix.agent.telephony;

import com.nottrilix.agent.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

/**
 * The system's phone endpoints (/api/phone/**), called with the agent's own key: who the caller is (and the customer
 * token to act for them), each turn for the audit trail, the end of the call. Bookkeeping failures are logged and must
 * never drop a live call.
 */
@Component
class PhoneGateway {

    private static final Logger log = LoggerFactory.getLogger("TELEPHONY");

    private final RestClient http;

    PhoneGateway(TelephonyProperties props, AgentProperties agent) {
        this.http = RestClient.builder()
                .baseUrl(props.systemUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + agent.agentKey())
                .build();
    }

    /** Caller id -> customer identity; the system masks the number in its own records. */
    PhoneCall.Identity identify(String callId, String from, String to) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("callId", callId);
            body.put("from", from);
            body.put("to", to);
            PhoneCall.Identity identity = http.post().uri("/api/phone/calls")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(PhoneCall.Identity.class);
            return identity == null ? PhoneCall.Identity.unknown() : identity;
        } catch (RestClientException e) {
            log.error("CALL {} identitet pozivatelja nije dohvaćen iz sustava: {}", callId, e.getMessage());
            return PhoneCall.Identity.unknown();
        }
    }

    void recordTurn(PhoneCall call, String message, String reply, long durationMs, Integer promptTokens,
                    Integer completionTokens) {
        if (!call.identity.identified()) {
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("customerToken", call.identity.customerToken());
            body.put("message", message);
            body.put("reply", reply);
            body.put("durationMs", durationMs);
            body.put("promptTokens", promptTokens);
            body.put("completionTokens", completionTokens);
            http.post().uri("/api/phone/calls/{id}/turns", call.callId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("CALL {} turn nije zabilježen u sustavu: {}", call.callId, e.getMessage());
        }
    }

    void end(PhoneCall call, String reason, long durationMs) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("customerToken", call.identity.customerToken());
            body.put("reason", reason);
            body.put("durationMs", durationMs);
            body.put("turns", call.turns.get());
            http.post().uri("/api/phone/calls/{id}/end", call.callId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("CALL {} kraj poziva nije zabilježen u sustavu: {}", call.callId, e.getMessage());
        }
    }
}
