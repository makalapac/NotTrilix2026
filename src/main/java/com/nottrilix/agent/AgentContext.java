package com.nottrilix.agent;

import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

/**
 * Kontekst jednog turna koji server daje alatima kroz Spring AI ToolContext. Model ga ne vidi i ne moze ga mijenjati:
 * tko je kupac i koji je razgovor odlucuje prijava, ne tekst razgovora.
 */
public final class AgentContext {

    public static final String CUSTOMER_ID = "customerId";
    public static final String CUSTOMER_NAME = "customerName";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String MEMORY_KEY = "memoryKey";
    public static final String TURN = "turn";
    public static final String CHANNEL = "channel";
    public static final String TRACE = "trace";

    private AgentContext() {
    }

    public static long customerId(ToolContext ctx) {
        return ((Number) required(ctx, CUSTOMER_ID)).longValue();
    }

    public static String memoryKey(ToolContext ctx) {
        return String.valueOf(required(ctx, MEMORY_KEY));
    }

    public static int turn(ToolContext ctx) {
        return ((Number) required(ctx, TURN)).intValue();
    }

    public static String string(ToolContext ctx, String key) {
        Object value = ctx == null ? null : ctx.getContext().get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static Long longOrNull(ToolContext ctx, String key) {
        Object value = ctx == null ? null : ctx.getContext().get(key);
        return value instanceof Number n ? n.longValue() : null;
    }

    public static AgentTrace trace(ToolContext ctx) {
        Object value = ctx == null ? null : ctx.getContext().get(TRACE);
        return value instanceof AgentTrace trace ? trace : null;
    }

    private static Object required(ToolContext ctx, String key) {
        Map<String, Object> context = ctx == null ? Map.of() : ctx.getContext();
        Object value = context.get(key);
        if (value == null) {
            throw new IllegalStateException("Alat je pozvan bez konteksta '" + key + "'");
        }
        return value;
    }
}
