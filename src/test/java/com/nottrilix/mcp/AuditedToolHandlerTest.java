package com.nottrilix.mcp;

import com.nottrilix.auth.AuthUser;
import com.nottrilix.auth.Realm;
import com.nottrilix.domain.AuditEventType;
import com.nottrilix.domain.AuditOutcome;
import com.nottrilix.service.AuditService;
import com.nottrilix.service.AuditService.AuditContext;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Svaki MCP poziv alata ostavlja trag u sustavu: tko (iz tokena), koji razgovor (_meta), sto i s kojim ishodom. */
@ExtendWith(MockitoExtension.class)
class AuditedToolHandlerTest {

    private static final String INPUT = "{\"transactionId\":7}";

    @Mock
    private BiFunction<McpTransportContext, CallToolRequest, CallToolResult> delegate;
    @Mock
    private AuditService audit;

    private final CallToolRequest request = CallToolRequest.builder()
            .name("refundPurchase")
            .arguments(Map.of("transactionId", 7))
            .meta(Map.of("conversationId", "abc12345", "turn", 2, "channel", "CHAT"))
            .build();
    private final AuditContext expectedContext = new AuditContext("abc12345", 1L, "Ana Anić", "CHAT");

    private AuditedToolHandler handler;

    @BeforeEach
    void setUp() {
        AuthUser ana = new AuthUser(Realm.CUSTOMER, 1L, "Ana Anić", "ana.anic@example.com");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(ana, null, List.of()));
        handler = new AuditedToolHandler("refundPurchase", delegate, audit);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static CallToolResult text(String json) {
        return CallToolResult.builder().addTextContent(json).build();
    }

    @Test
    void executedToolIsAuditedAsOk() {
        String result = "{\"ok\":true,\"status\":\"OK\",\"message\":\"Povrat je izvršen.\",\"data\":{}}";
        when(delegate.apply(McpTransportContext.EMPTY, request)).thenReturn(text(result));

        CallToolResult returned = handler.apply(McpTransportContext.EMPTY, request);

        assertThat(returned.content()).singleElement()
                .satisfies(content -> assertThat(((TextContent) content).text()).isEqualTo(result));
        verify(audit).record(eq(expectedContext), eq(AuditEventType.TOOL_CALL), eq("refundPurchase"), eq(INPUT),
                eq(AuditOutcome.OK), eq(result), anyLong());
    }

    @Test
    void ruleDenialIsAuditedWithReason() {
        when(delegate.apply(McpTransportContext.EMPTY, request)).thenReturn(text(
                "{\"ok\":false,\"status\":\"REFUND_WINDOW_EXPIRED\","
                        + "\"message\":\"Rok za povrat (14 dana) je istekao za transakciju 7\",\"data\":null}"));

        handler.apply(McpTransportContext.EMPTY, request);

        verify(audit).record(eq(expectedContext), eq(AuditEventType.TOOL_CALL), eq("refundPurchase"), eq(INPUT),
                eq(AuditOutcome.DENIED),
                eq("REFUND_WINDOW_EXPIRED: Rok za povrat (14 dana) je istekao za transakciju 7"), anyLong());
    }

    @Test
    void proposalIsAuditedAsConfirmationRequired() {
        when(delegate.apply(McpTransportContext.EMPTY, request)).thenReturn(text(
                "{\"ok\":false,\"status\":\"CONFIRMATION_REQUIRED\",\"message\":\"čeka potvrdu\",\"data\":{}}"));

        handler.apply(McpTransportContext.EMPTY, request);

        verify(audit).record(eq(expectedContext), eq(AuditEventType.TOOL_CALL), eq("refundPurchase"), eq(INPUT),
                eq(AuditOutcome.CONFIRMATION_REQUIRED), contains("CONFIRMATION_REQUIRED"), anyLong());
    }

    @Test
    void unexpectedFailureIsAuditedAndReturnedAsMcpError() {
        when(delegate.apply(McpTransportContext.EMPTY, request)).thenThrow(new IllegalStateException("baza nedostupna"));

        CallToolResult returned = handler.apply(McpTransportContext.EMPTY, request);

        assertThat(returned.isError()).isTrue();
        assertThat(returned.content()).singleElement()
                .satisfies(content -> assertThat(((TextContent) content).text()).contains("\"ok\":false"));
        verify(audit).record(eq(expectedContext), eq(AuditEventType.TOOL_CALL), eq("refundPurchase"), eq(INPUT),
                eq(AuditOutcome.ERROR), contains("baza nedostupna"), anyLong());
    }

    @Test
    void callWithoutCustomerTokenIsStillAudited() {
        SecurityContextHolder.clearContext();
        when(delegate.apply(McpTransportContext.EMPTY, request)).thenReturn(text(
                "{\"ok\":false,\"status\":\"CUSTOMER_REQUIRED\",\"message\":\"nema kupca\",\"data\":null}"));

        handler.apply(McpTransportContext.EMPTY, request);

        verify(audit).record(eq(new AuditContext("abc12345", null, null, "CHAT")), eq(AuditEventType.TOOL_CALL),
                eq("refundPurchase"), eq(INPUT), eq(AuditOutcome.DENIED), contains("CUSTOMER_REQUIRED"), anyLong());
    }

    @Test
    void applicationToolsAreNotAuditedAsToolCalls() {
        AuditedToolHandler pending = new AuditedToolHandler("getPendingAction", delegate, audit);
        when(delegate.apply(McpTransportContext.EMPTY, request)).thenReturn(text(
                "{\"ok\":true,\"status\":\"OK\",\"message\":null,\"data\":{\"pending\":false}}"));

        pending.apply(McpTransportContext.EMPTY, request);

        verify(audit, never()).record(any(), any(), any(), any(), any(), any(), any());
    }
}
