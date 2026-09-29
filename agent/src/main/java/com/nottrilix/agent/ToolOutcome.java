package com.nottrilix.agent;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Rezultat alata kako ga salje sustav: MCP sadrzaj (popis dijelova) u kojem je tekst = JSON
 * {ok, status, message, data}. Agent ga cita samo da bi znao ishod; pravila i podaci ostaju u sustavu.
 */
public final class ToolOutcome {

    public static final String OK = "OK";
    public static final String DENIED = "DENIED";
    public static final String CONFIRMATION_REQUIRED = "CONFIRMATION_REQUIRED";
    public static final String ERROR = "ERROR";

    /** Sto model dobije kad alat ne odgovori (mreza, sustav ne radi): jasan "nije izvrseno". */
    public static final String ERROR_RESULT =
            "{\"ok\":false,\"status\":\"ERROR\",\"message\":\"Alat trenutno nije dostupan. Radnja NIJE izvršena.\"}";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ToolOutcome() {
    }

    /** MCP sadrzaj ([{"type":"text","text":"..."}]) -> tekst prvog tekstualnog dijela; inace ulaz kakav jest. */
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

    /** Rezultat alata kao JSON stablo (null ako nije JSON). */
    public static JsonNode parse(String json) {
        try {
            return json == null || json.isBlank() ? null : JSON.readTree(json);
        } catch (JacksonException e) {
            return null;
        }
    }

    /** Ishod iz rezultata alata: ok=true -> OK, inace po statusu. */
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
