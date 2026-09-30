package com.nottrilix.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Tool over the network: runs as the customer, plain result to the model, a failure does not end the conversation. */
@ExtendWith(MockitoExtension.class)
class TracingToolCallbackTest {

    private static final String INPUT = "{\"transactionId\":7}";

    @Mock
    private ToolCallback mcpTool;

    private final AgentTrace trace = new AgentTrace();
    private final ToolContext context = new ToolContext(
            new TurnContext("token-ana", "abc12345", 2, "CHAT", trace).asMap());

    private TracingToolCallback callback;

    @BeforeEach
    void setUp() {
        when(mcpTool.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name("refundPurchase").description("povrat").inputSchema("{}").build());
        callback = new TracingToolCallback(mcpTool);
    }

    /** The way the Spring AI MCP client returns a result: a list of content parts whose text is the tool's JSON. */
    private static String mcpContent(String toolResultJson) {
        return "[{\"type\":\"text\",\"text\":\"" + toolResultJson.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\"}]";
    }

    @Test
    void modelGetsThePlainToolResultAndTheCallIsTraced() {
        String result = "{\"ok\":true,\"status\":\"OK\",\"message\":\"Povrat je izvršen.\",\"data\":{}}";
        when(mcpTool.call(INPUT, context)).thenReturn(mcpContent(result));

        assertThat(callback.call(INPUT, context)).isEqualTo(result);

        assertThat(trace.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.tool()).isEqualTo("refundPurchase");
            assertThat(call.input()).isEqualTo(INPUT);
            assertThat(call.outcome()).isEqualTo("OK");
        });
    }

    @Test
    void callRunsOnBehalfOfTheCustomer() {
        AtomicReference<String> tokenDuringCall = new AtomicReference<>();
        when(mcpTool.call(INPUT, context)).thenAnswer(invocation -> {
            tokenDuringCall.set(CustomerToken.current().orElse(null));
            return mcpContent("{\"ok\":true,\"status\":\"OK\"}");
        });

        callback.call(INPUT, context);

        assertThat(tokenDuringCall.get()).isEqualTo("token-ana");
        assertThat(CustomerToken.current()).isEmpty(); // the token is valid only for the duration of the call
    }

    @Test
    void denialAndProposalAreTracedWithTheirOutcome() {
        when(mcpTool.call(INPUT, context))
                .thenReturn(mcpContent("{\"ok\":false,\"status\":\"REFUND_WINDOW_EXPIRED\",\"message\":\"istekao\"}"))
                .thenReturn(mcpContent("{\"ok\":false,\"status\":\"CONFIRMATION_REQUIRED\",\"message\":\"čeka\"}"));

        callback.call(INPUT, context);
        callback.call(INPUT, context);

        assertThat(trace.toolCalls()).extracting(AgentToolCall::outcome)
                .containsExactly("DENIED", "CONFIRMATION_REQUIRED");
    }

    @Test
    void whenTheSystemDoesNotAnswerTheModelIsToldNothingWasDone() {
        when(mcpTool.call(INPUT, context)).thenThrow(new IllegalStateException("Connection refused"));

        String result = callback.call(INPUT, context);

        assertThat(result).isEqualTo(ToolOutcome.ERROR_RESULT).contains("\"ok\":false");
        assertThat(trace.toolCalls()).singleElement().satisfies(c -> assertThat(c.outcome()).isEqualTo("ERROR"));
    }
}
