package com.nottrilix.agent;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ono sto aplikacija agenta sama pita sustav (ne model): tko je kupac, ceka li neka radnja potvrdu i sto o pitanju
 * kaze baza znanja. Ide istim putem kao alati modela - MCP poziv s tokenom kupca - pa agent i dalje nema
 * nikakav drugi pristup sustavu.
 */
@Component
public class SystemGateway {

    private static final Logger log = LoggerFactory.getLogger("AGENT");

    /** Kupac kako ga vidi sustav (prema tokenu). */
    public record Customer(long id, String name, String email) {
    }

    /** Sustav ne prihvaca token (istekao, odjavljen ili nije kupcev). */
    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }

    /** Sustav ne odgovara ili je vratio gresku. */
    public static class SystemUnavailableException extends RuntimeException {
        public SystemUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final McpSyncClient mcp;

    public SystemGateway(List<McpSyncClient> clients) {
        if (clients.size() != 1) {
            throw new IllegalStateException("Ocekujem tocno jednu MCP vezu (sustav), a konfigurirano ih je "
                    + clients.size());
        }
        this.mcp = clients.getFirst();
    }

    public Customer whoAmI(String customerToken) {
        JsonNode data = call(customerToken, "whoAmI", Map.of(), Map.of());
        return new Customer(data.path("customerId").asLong(), data.path("name").asString(""),
                data.path("email").asString(""));
    }

    /** Sazetak radnje koja u razgovoru ceka potvrdu kupca; null ako nista ne ceka. */
    public String pendingAction(TurnContext turn) {
        JsonNode data = call(turn.customerToken(), "getPendingAction", Map.of(), turn.meta());
        return data.path("pending").asBoolean(false) ? data.path("summary").asString(null) : null;
    }

    /** Odlomci baze znanja najslicniji upitu, kao Spring AI dokumenti (tekst + izvor, odjeljak, slicnost). */
    public List<Document> searchKnowledgeBase(String query, String customerToken, Map<String, Object> meta) {
        JsonNode data = call(customerToken, "searchKnowledgeBase", Map.of("query", query), meta);
        List<Document> documents = new ArrayList<>();
        for (JsonNode hit : data) {
            documents.add(Document.builder()
                    .text(hit.path("content").asString(""))
                    .metadata(Map.of(
                            "source", hit.path("source").asString(""),
                            "title", hit.path("title").asString(""),
                            "section", hit.path("section").asString("")))
                    .score(hit.path("score").asDouble(0.0))
                    .build());
        }
        return documents;
    }

    private JsonNode call(String customerToken, String tool, Map<String, Object> arguments,
                          Map<String, Object> meta) {
        long start = System.nanoTime();
        CallToolResult result;
        try {
            result = CustomerToken.callWith(customerToken, () -> mcp.callTool(
                    CallToolRequest.builder().name(tool).arguments(arguments).meta(meta).build()));
        } catch (RuntimeException e) {
            if (isUnauthorized(e)) {
                throw new InvalidTokenException("Sustav ne prihvaća token kupca");
            }
            log.error("MCP {} nije uspio: {}", tool, e.toString());
            throw new SystemUnavailableException("Sustav trenutno nije dostupan", e);
        }
        String text = result.content() == null ? "" : result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .findFirst().orElse("");
        JsonNode node = ToolOutcome.parse(text);
        long ms = (System.nanoTime() - start) / 1_000_000;
        if (Boolean.TRUE.equals(result.isError()) || node == null || !node.path("ok").asBoolean(false)) {
            String status = node == null ? "?" : node.path("status").asString("?");
            log.warn("MCP {} -> {} ({} ms): {}", tool, status, ms, text);
            if ("CUSTOMER_REQUIRED".equals(status)) {
                throw new InvalidTokenException("Token ne pripada kupcu");
            }
            throw new SystemUnavailableException("Sustav je odbio " + tool + ": " + status, null);
        }
        log.debug("MCP {} {} -> OK ({} ms)", tool, arguments, ms);
        return node.path("data");
    }

    /** HTTP 401/403 od sustava: MCP klijent ga javlja iznimkom, a status je u poruci ili uzroku. */
    private static boolean isUnauthorized(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            String text = cause.getClass().getSimpleName() + " " + cause.getMessage();
            if (text.contains("Authorization") || text.contains("401") || text.contains("403")) {
                return true;
            }
        }
        return false;
    }
}
