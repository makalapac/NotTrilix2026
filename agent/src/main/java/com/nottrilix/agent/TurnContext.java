package com.nottrilix.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Context of one turn the agent application carries with every call to the system. The model cannot see or change it.
 * <ul>
 *   <li>{@code customerToken} travels as an HTTP header (Authorization) - it tells the system who the customer is;</li>
 *   <li>{@code conversationId}, {@code turn} and {@code channel} travel as MCP {@code _meta} - they tell the system
 *       in which conversation and turn an action was proposed or confirmed;</li>
 *   <li>{@code trace} stays in the agent (what the model did in this turn).</li>
 * </ul>
 */
public record TurnContext(String customerToken, String conversationId, int turn, String channel, AgentTrace trace) {

    public static final String CUSTOMER_TOKEN = "customerToken";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String TURN = "turn";
    public static final String CHANNEL = "channel";
    public static final String TRACE = "trace";

    /** As Spring AI ToolContext (tools) or advisor parameters (RAG). */
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(CUSTOMER_TOKEN, customerToken);
        map.put(CONVERSATION_ID, conversationId);
        map.put(TURN, turn);
        map.put(CHANNEL, channel);
        map.put(TRACE, trace);
        return map;
    }

    /** What part of the context may go into MCP _meta: conversation, turn and channel. Never the token or the trace. */
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
