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
 * How the context crosses the network boundary. Spring AI assembles the MCP client from configuration
 * (spring.ai.mcp.client.*); only four additions live here:
 * <ol>
 *   <li>every MCP request carries a token: the customer's when acting for the customer, otherwise the agent key;</li>
 *   <li>only the conversation, turn and channel go into MCP _meta;</li>
 *   <li>the model sees tools under their own names (no connection prefix);</li>
 *   <li>tools meant for the agent application are hidden from the model.</li>
 * </ol>
 */
@Configuration
public class McpClientConfig {

    /** Tools the agent application calls (who the customer is, pending confirmation, knowledge base), not the model. */
    public static final Set<String> APPLICATION_TOOLS = Set.of("whoAmI", "getPendingAction", "searchKnowledgeBase");

    private static final String TOKEN = "token";

    /** On the calling thread: which token applies to this MCP request. */
    @Bean
    McpClientCustomizer<McpClient.SyncSpec> tokenPerCall(AgentProperties props) {
        return (connection, client) -> client.transportContextProvider(
                () -> McpTransportContext.create(Map.of(TOKEN, CustomerToken.current().orElse(props.agentKey()))));
    }

    /** When sending: the token goes into the HTTP header. */
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
