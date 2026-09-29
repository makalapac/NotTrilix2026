package com.nottrilix.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kontekst jednog turna koji aplikacija agenta nosi uz svaki poziv sustava. Model ga ne vidi i ne moze ga mijenjati.
 * <ul>
 *   <li>{@code customerToken} putuje kao HTTP zaglavlje (Authorization) - po njemu sustav zna tko je kupac;</li>
 *   <li>{@code conversationId}, {@code turn} i {@code channel} putuju kao MCP {@code _meta} - po njima sustav zna
 *       u kojem razgovoru i turnu je radnja predlozena odnosno potvrdjena;</li>
 *   <li>{@code trace} ostaje u agentu (sto je model radio u ovom turnu).</li>
 * </ul>
 */
public record TurnContext(String customerToken, String conversationId, int turn, String channel, AgentTrace trace) {

    public static final String CUSTOMER_TOKEN = "customerToken";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String TURN = "turn";
    public static final String CHANNEL = "channel";
    public static final String TRACE = "trace";

    /** Kao Spring AI ToolContext (alati) odnosno parametri advisora (RAG). */
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(CUSTOMER_TOKEN, customerToken);
        map.put(CONVERSATION_ID, conversationId);
        map.put(TURN, turn);
        map.put(CHANNEL, channel);
        map.put(TRACE, trace);
        return map;
    }

    /** Sto od konteksta smije u MCP _meta: razgovor, turn i kanal. Token i trag nikad. */
    public static Map<String, Object> meta(Map<String, Object> context) {
        Map<String, Object> meta = new LinkedHashMap<>();
        for (String key : new String[] {CONVERSATION_ID, TURN, CHANNEL}) {
            Object value = context == null ? null : context.get(key);
            if (value != null) {
                meta.put(key, value);
            }
        }
        return meta;
    }

    public Map<String, Object> meta() {
        return meta(asMap());
    }
}
