package com.nottrilix.agent;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;

/**
 * Kako kontekst prelazi mreznu granicu. Spring AI slaze MCP klijenta iz konfiguracije
 * (spring.ai.mcp.client.*); ovdje su samo cetiri dodatka:
 * <ol>
 *   <li>svaki MCP zahtjev nosi token: kupcev kad se radi u ime kupca, inace kljuc agenta;</li>
 *   <li>u MCP _meta ide samo razgovor, turn i kanal;</li>
 *   <li>model vidi alate pod njihovim imenima (bez prefiksa veze);</li>
 *   <li>alate namijenjene aplikaciji agenta model ne vidi.</li>
 * </ol>
 */
@Configuration
public class McpClientConfig {

    /** Alati koje zove aplikacija agenta (tko je kupac, sto ceka potvrdu, baza znanja), a ne model. */
    public static final Set<String> APPLICATION_TOOLS = Set.of("whoAmI", "getPendingAction", "searchKnowledgeBase");

    private static final String TOKEN = "token";

    /** Na dretvi poziva: koji token vrijedi za ovaj MCP zahtjev. */
    @Bean
    McpClientCustomizer<McpClient.SyncSpec> tokenPerCall(AgentProperties props) {
        return (connection, client) -> client.transportContextProvider(
                () -> McpTransportContext.create(Map.of(TOKEN, CustomerToken.current().orElse(props.agentKey()))));
    }

    /** Pri slanju: token u HTTP zaglavlje. */
    @Bean
    McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> tokenIntoHeader(AgentProperties props) {
        return (connection, transport) -> transport.httpRequestCustomizer((request, method, uri, body, context) -> {
            Object token = context == null ? null : context.get(TOKEN);
            request.setHeader("Authorization", "Bearer " + (token == null ? props.agentKey() : token));
        });
    }

    @Bean
    ToolContextToMcpMetaConverter conversationIntoMeta() {
        return toolContext -> TurnContext.meta(toolContext == null ? null : toolContext.getContext());
    }

    @Bean
    McpToolNamePrefixGenerator plainToolNames() {
        return McpToolNamePrefixGenerator.noPrefix();
    }

    @Bean
    McpToolFilter toolsForTheModel() {
        return (connection, tool) -> !APPLICATION_TOOLS.contains(tool.name());
    }
}
