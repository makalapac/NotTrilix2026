package com.nottrilix.agent;

import com.nottrilix.domain.AuditEventType;
import com.nottrilix.domain.AuditOutcome;
import com.nottrilix.service.AuditService;
import com.nottrilix.service.AuditService.AuditContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Svaki poziv alata ostavlja trag (baza + trag turna), a greska alata ne rusi razgovor. */
@ExtendWith(MockitoExtension.class)
class AuditingToolCallbackTest {

    private static final String INPUT = "{\"transactionId\":7}";

    @Mock
    private ToolCallback delegate;
    @Mock
    private AuditService audit;

    private final AgentTrace trace = new AgentTrace();
    private final ToolContext context = new ToolContext(Map.of(
            AgentContext.CUSTOMER_ID, 1L,
            AgentContext.CUSTOMER_NAME, "Ana Anić",
            AgentContext.CONVERSATION_ID, "abc12345",
            AgentContext.CHANNEL, "CHAT",
            AgentContext.TRACE, trace));
    private final AuditContext expectedContext = new AuditContext("abc12345", 1L, "Ana Anić", "CHAT");

    private AuditingToolCallback callback;

    @BeforeEach
    void setUp() {
        when(delegate.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name("refundPurchase").description("povrat").inputSchema("{}").build());
        callback = new AuditingToolCallback(delegate, audit);
    }

    @Test
    void executedToolIsAuditedAsOk() {
        String result = "{\"ok\":true,\"status\":\"OK\",\"message\":\"Povrat je izvršen.\",\"data\":{}}";
        when(delegate.call(INPUT, context)).thenReturn(result);

        assertThat(callback.call(INPUT, context)).isEqualTo(result);

        verify(audit).record(eq(expectedContext), eq(AuditEventType.TOOL_CALL), eq("refundPurchase"), eq(INPUT),
                eq(AuditOutcome.OK), eq(result), anyLong());
        assertThat(trace.toolCalls()).singleElement()
                .satisfies(call -> {
                    assertThat(call.tool()).isEqualTo("refundPurchase");
                    assertThat(call.outcome()).isEqualTo("OK");
                });
    }

    @Test
    void ruleDenialIsAuditedWithReason() {
        when(delegate.call(INPUT, context)).thenReturn("{\"ok\":false,\"status\":\"REFUND_WINDOW_EXPIRED\","
                + "\"message\":\"Rok za povrat (14 dana) je istekao za transakciju 7\",\"data\":null}");

        callback.call(INPUT, context);

        verify(audit).record(eq(expectedContext), eq(AuditEventType.TOOL_CALL), eq("refundPurchase"), eq(INPUT),
                eq(AuditOutcome.DENIED),
                eq("REFUND_WINDOW_EXPIRED: Rok za povrat (14 dana) je istekao za transakciju 7"), anyLong());
        assertThat(trace.toolCalls()).singleElement().satisfies(c -> assertThat(c.outcome()).isEqualTo("DENIED"));
    }

    @Test
    void proposalIsAuditedAsConfirmationRequired() {
        when(delegate.call(INPUT, context)).thenReturn(
                "{\"ok\":false,\"status\":\"CONFIRMATION_REQUIRED\",\"message\":\"čeka potvrdu\",\"data\":{}}");

        callback.call(INPUT, context);

        verify(audit).record(eq(expectedContext), eq(AuditEventType.TOOL_CALL), eq("refundPurchase"), eq(INPUT),
                eq(AuditOutcome.CONFIRMATION_REQUIRED), contains("CONFIRMATION_REQUIRED"), anyLong());
    }

    @Test
    void unexpectedFailureIsAuditedAndReturnedToModelAsNotExecuted() {
        when(delegate.call(INPUT, context)).thenThrow(new IllegalStateException("baza nedostupna"));

        String result = callback.call(INPUT, context);

        assertThat(result).isEqualTo(AuditingToolCallback.ERROR_RESULT).contains("\"ok\":false");
        verify(audit).record(eq(expectedContext), eq(AuditEventType.TOOL_CALL), eq("refundPurchase"), eq(INPUT),
                eq(AuditOutcome.ERROR), contains("baza nedostupna"), anyLong());
        assertThat(trace.toolCalls()).singleElement().satisfies(c -> assertThat(c.outcome()).isEqualTo("ERROR"));
    }
}
