package com.nottrilix.agent;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tool result as the system sends it: MCP content (a list of parts) whose text = JSON
 * {ok, status, message, data}. The agent reads it only to learn the outcome; rules and data stay in the system.
 */
public final class ToolOutcome {

    public static final String OK = "OK";
    public static final String DENIED = "DENIED";
    public static final String CONFIRMATION_REQUIRED = "CONFIRMATION_REQUIRED";
    public static final String ERROR = "ERROR";

    /** What the model gets when a tool does not respond (network, system down): a clear "not executed". */
    public static final String ERROR_RESULT =
            "{\"ok\":false,\"status\":\"ERROR\",\"message\":\"Alat trenutno nije dostupan. Radnja NIJE izvršena.\"}";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ToolOutcome() {
    }

    /** MCP content ([{"type":"text","text":"..."}]) -> text of the first text part; otherwise the input as is. */
    public static String text(String mcpContent) {
        JsonNode content = parse(mcpContent);
        if (content != null && content.isArray()) {
            for (JsonNode part : content) {
                if (part.path("text").isString()) {
                    return part.path("text").asString();
                }
            }
        }
        return mcpContent == null ? "" : mcpContent;
    }

    /** Tool result as a JSON tree (null if not JSON). */
    public static JsonNode parse(String json) {
        try {
            return json == null || json.isBlank() ? null : JSON.readTree(json);
        } catch (JacksonException e) {
            return null;
        }
    }

    /** Outcome from the tool result: ok=true -> OK, otherwise by status. */
    public static String of(String resultJson) {
        JsonNode node = parse(resultJson);
        if (node == null || !node.has("ok") || node.path("ok").asBoolean(false)) {
            return OK;
        }
        return switch (node.path("status").asString("")) {
            case CONFIRMATION_REQUIRED -> CONFIRMATION_REQUIRED;
            case ERROR -> ERROR;
            default -> DENIED;
        };
    }
}
