package com.nottrilix.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Omotac oko svakog MCP alata na strani agenta. Alat je sada s druge strane mreze, pa omotac radi tri stvari:
 * poziv izvrsava u ime kupca (njegov token), modelu vraca cist rezultat alata i pamti sto je model radio u turnu.
 * Ako sustav ne odgovori, razgovor se ne rusi: model dobiva jasan "nije izvrseno".
 * Audit nije ovdje nego u sustavu - agent ga ne moze ni preskociti ni krivotvoriti.
 */
public class TracingToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger("AGENT");

    private final ToolCallback delegate;

    public TracingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
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
        Object token = toolContext == null ? null : toolContext.getContext().get(TurnContext.CUSTOMER_TOKEN);
        long start = System.nanoTime();
        String result;
        String outcome;
        try {
            String content = CustomerToken.callWith(token == null ? null : String.valueOf(token),
                    () -> delegate.call(toolInput, toolContext));
            result = ToolOutcome.text(content);
            outcome = ToolOutcome.of(result);
        } catch (RuntimeException e) {
            log.error("TOOL {} nije odgovorio: {}", tool, rootCause(e).toString());
            result = ToolOutcome.ERROR_RESULT;
            outcome = ToolOutcome.ERROR;
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        log.info("TOOL {} input={} -> {} ({} ms preko MCP-a)", tool, toolInput, outcome, ms);

        Object trace = toolContext == null ? null : toolContext.getContext().get(TurnContext.TRACE);
        if (trace instanceof AgentTrace agentTrace) {
            agentTrace.add(new AgentToolCall(tool, toolInput, outcome, ms));
        }
        return result;
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
