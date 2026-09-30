package com.nottrilix.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP server settings (application.yml -> nottrilix.mcp.*).
 * agentKey: the key the agent application presents when connecting (tool listing); the tools require a customer token.
 * confirmationTtlMinutes: how long a purchase/refund/block proposal waiting for customer confirmation stays valid.
 */
@ConfigurationProperties(prefix = "nottrilix.mcp")
public record McpProperties(String agentKey, int confirmationTtlMinutes) {
}
