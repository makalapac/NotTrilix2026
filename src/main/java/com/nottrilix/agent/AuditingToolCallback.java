package com.nottrilix.agent;

import com.nottrilix.api.dto.AgentToolCall;
import com.nottrilix.domain.AuditEventType;
import com.nottrilix.domain.AuditOutcome;
import com.nottrilix.service.AuditService;
import com.nottrilix.service.AuditService.AuditContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Omotac oko svakog alata: audit na granici, ne u svakom alatu posebno. Biljezi naziv alata, argumente koje je
 * poslao model, ishod i trajanje - u bazu (AuditService) i u trag turna (AgentTrace). Neocekivana greska alata
 * ne rusi razgovor: modelu se vraca jasan "nije izvrseno".
 */
public class AuditingToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger("AGENT");
    private static final Pattern OK = Pattern.compile("\"ok\"\\s*:\\s*(true|false)");
    private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"([A-Z_]+)\"");
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    static final String ERROR_RESULT =
            "{\"ok\":false,\"status\":\"ERROR\",\"message\":\"Alat trenutno nije dostupan. Radnja NIJE izvršena.\"}";

    private final ToolCallback delegate;
    private final AuditService audit;

    public AuditingToolCallback(ToolCallback delegate, AuditService audit) {
        this.delegate = delegate;
        this.audit = audit;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String tool = delegate.getToolDefinition().name();
        long start = System.nanoTime();
        String result;
        AuditOutcome outcome;
        String detail;
        try {
            result = toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
            outcome = outcomeOf(result);
            detail = outcome == AuditOutcome.OK ? result : reasonOf(result);
        } catch (RuntimeException e) {
            Throwable cause = rootCause(e);
            log.error("TOOL {} neuspio: {}", tool, cause.toString(), e);
            result = ERROR_RESULT;
            outcome = AuditOutcome.ERROR;
            detail = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
        long ms = (System.nanoTime() - start) / 1_000_000;

        AuditContext ctx = new AuditContext(
                AgentContext.string(toolContext, AgentContext.CONVERSATION_ID),
                AgentContext.longOrNull(toolContext, AgentContext.CUSTOMER_ID),
                AgentContext.string(toolContext, AgentContext.CUSTOMER_NAME),
                AgentContext.string(toolContext, AgentContext.CHANNEL));
        audit.record(ctx, AuditEventType.TOOL_CALL, tool, toolInput, outcome, detail, ms);

        AgentTrace trace = AgentContext.trace(toolContext);
        if (trace != null) {
            trace.add(new AgentToolCall(tool, toolInput, outcome.name(), ms));
        }
        return result;
    }

    static AuditOutcome outcomeOf(String result) {
        Matcher ok = OK.matcher(result == null ? "" : result);
        if (!ok.find() || "true".equals(ok.group(1))) {
            return AuditOutcome.OK;
        }
        Matcher status = STATUS.matcher(result);
        String code = status.find() ? status.group(1) : "";
        return switch (code) {
            case ToolResult.CONFIRMATION_REQUIRED -> AuditOutcome.CONFIRMATION_REQUIRED;
            case "ERROR" -> AuditOutcome.ERROR;
            default -> AuditOutcome.DENIED;
        };
    }

    /** "KOD: poruka" iz rezultata alata koji nije izvrsen. */
    static String reasonOf(String result) {
        Matcher status = STATUS.matcher(result);
        Matcher message = MESSAGE.matcher(result);
        String code = status.find() ? status.group(1) : "?";
        String text = message.find() ? message.group(1).replace("\\\"", "\"") : "";
        return code + ": " + text;
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
