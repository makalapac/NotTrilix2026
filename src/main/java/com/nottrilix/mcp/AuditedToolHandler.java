package com.nottrilix.mcp;

import com.nottrilix.auth.AuthUser;
import com.nottrilix.domain.AuditEventType;
import com.nottrilix.domain.AuditOutcome;
import com.nottrilix.service.AuditService;
import com.nottrilix.service.AuditService.AuditContext;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;
import java.util.function.BiFunction;

/**
 * Wrapper around every MCP tool: audit at the system boundary, not in each tool separately and not at the client.
 * Records who called (the customer from the token), in which conversation (_meta), which tool, with which arguments,
 * the outcome and the duration. The client cannot bypass it: whichever MCP client calls, the trail is created here.
 */
final class AuditedToolHandler implements BiFunction<McpTransportContext, CallToolRequest, CallToolResult> {

    private static final Logger log = LoggerFactory.getLogger("MCP");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final String ERROR_RESULT =
            "{\"ok\":false,\"status\":\"ERROR\",\"message\":\"Alat trenutno nije dostupan. Radnja NIJE izvršena.\"}";

    /** Tools called by the agent application, not the model: not an agent action, so not audited as a tool call. */
    static final Set<String> APPLICATION_TOOLS = Set.of("whoAmI", "getPendingAction", "searchKnowledgeBase");

    private final String tool;
    private final BiFunction<McpTransportContext, CallToolRequest, CallToolResult> delegate;
    private final AuditService audit;

    AuditedToolHandler(String tool, BiFunction<McpTransportContext, CallToolRequest, CallToolResult> delegate,
                       AuditService audit) {
        this.tool = tool;
        this.delegate = delegate;
        this.audit = audit;
    }

    @Override
    public CallToolResult apply(McpTransportContext transport, CallToolRequest request) {
        McpCall call = McpCall.current(request.meta());
        String input = toJson(request.arguments());
        long start = System.nanoTime();

        CallToolResult result;
        AuditOutcome outcome;
        String detail;
        try {
            result = delegate.apply(transport, request);
            String text = text(result);
            if (Boolean.TRUE.equals(result.isError())) {
                outcome = AuditOutcome.ERROR;
                detail = text;
                log.error("MCP tools/call {} neuspio: {}", tool, text);
            } else {
                outcome = outcomeOf(text);
                detail = outcome == AuditOutcome.OK ? text : reasonOf(text);
            }
        } catch (RuntimeException e) {
            log.error("MCP tools/call {} neuspio: {}", tool, e.toString(), e);
            result = CallToolResult.builder().addTextContent(ERROR_RESULT).isError(true).build();
            outcome = AuditOutcome.ERROR;
            detail = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        long ms = (System.nanoTime() - start) / 1_000_000;

        AuthUser user = call.user();
        log.info("MCP tools/call {} user={} conv={} turn={} input={} -> {} ({} ms)", tool,
                user == null ? "anonymous" : user.realm().name().toLowerCase() + "#" + user.id(),
                call.conversationId(), call.meta().get(McpCall.TURN), input, outcome, ms);
        if (!APPLICATION_TOOLS.contains(tool)) {
            audit.record(new AuditContext(call.conversationId(), user == null ? null : user.id(),
                    user == null ? null : user.name(), call.channel()),
                    AuditEventType.TOOL_CALL, tool, input, outcome, detail, ms);
        }
        return result;
    }

    /** Outcome from the tool result (ToolResult as JSON): ok=true -> OK, otherwise by status. */
    static AuditOutcome outcomeOf(String resultJson) {
        JsonNode node = parse(resultJson);
        if (node == null || !node.has("ok") || node.path("ok").asBoolean(false)) {
            return AuditOutcome.OK;
        }
        return switch (node.path("status").asString("")) {
            case ToolResult.CONFIRMATION_REQUIRED -> AuditOutcome.CONFIRMATION_REQUIRED;
            case "ERROR" -> AuditOutcome.ERROR;
            default -> AuditOutcome.DENIED;
        };
    }

    /** "CODE: message" from the result of a tool that was not executed. */
    static String reasonOf(String resultJson) {
        JsonNode node = parse(resultJson);
        if (node == null) {
            return resultJson;
        }
        return node.path("status").asString("?") + ": " + node.path("message").asString("");
    }

    private static JsonNode parse(String json) {
        try {
            return json == null || json.isBlank() ? null : JSON.readTree(json);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static String text(CallToolResult result) {
        if (result == null || result.content() == null) {
            return "";
        }
        return result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .findFirst().orElse("");
    }

    private static String toJson(Object value) {
        try {
            return value == null ? "{}" : JSON.writeValueAsString(value);
        } catch (JacksonException e) {
            return String.valueOf(value);
        }
    }
}
