package com.nottrilix.agent;

/**
 * Jedinstven oblik rezultata svakog alata (model ga dobiva kao JSON).
 * ok=true: radnja je izvrsena / podaci su u data. ok=false: NIJE izvrseno; status je kod razloga
 * (npr. INSUFFICIENT_FUNDS, NOT_FOUND) ili CONFIRMATION_REQUIRED.
 */
public record ToolResult(boolean ok, String status, String message, Object data) {

    public static final String CONFIRMATION_REQUIRED = "CONFIRMATION_REQUIRED";

    public static ToolResult ok(Object data) {
        return new ToolResult(true, "OK", null, data);
    }

    public static ToolResult ok(String message, Object data) {
        return new ToolResult(true, "OK", message, data);
    }

    public static ToolResult denied(String code, String message) {
        return new ToolResult(false, code, message, null);
    }

    public static ToolResult confirmationRequired(String summary, Object data) {
        return new ToolResult(false, CONFIRMATION_REQUIRED,
                "Radnja NIJE izvršena, čeka potvrdu kupca: " + summary
                        + ". Reci kupcu što će se dogoditi i pitaj potvrđuje li. "
                        + "Kad potvrdi, pozovi confirmPendingAction; ako odbije, cancelPendingAction.", data);
    }
}
