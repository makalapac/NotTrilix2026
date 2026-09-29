package com.nottrilix.mcp;

import com.nottrilix.service.AuditService;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Sustav kao MCP server (Spring AI, HTTP bez sesije na /mcp): alati iz {@link SupportMcpTools} postaju MCP alati,
 * svaki omotan auditom. Spring AI od ovog popisa slaze server (spring.ai.mcp.server.*).
 */
@Configuration
public class McpServerConfig {

    private static final Logger log = LoggerFactory.getLogger("MCP");

    @Bean
    List<SyncToolSpecification> nottrilixMcpTools(SupportMcpTools tools, AuditService audit) {
        List<SyncToolSpecification> specifications =
                SyncMcpAnnotationProviders.statelessToolSpecifications(List.<Object>of(tools)).stream()
                        .map(spec -> SyncToolSpecification.builder()
                                .tool(spec.tool())
                                .callHandler(new AuditedToolHandler(spec.tool().name(), spec.callHandler(), audit))
                                .build())
                        .toList();
        log.info("MCP server: {} alata na /mcp -> {}", specifications.size(),
                specifications.stream().map(spec -> spec.tool().name()).sorted().toList());
        return specifications;
    }
}
