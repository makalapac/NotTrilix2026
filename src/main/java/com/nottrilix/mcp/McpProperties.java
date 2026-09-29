package com.nottrilix.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Postavke MCP servera (application.yml -> nottrilix.mcp.*).
 * agentKey: kljuc kojim se aplikacija agenta predstavlja pri spajanju (popis alata); alati traze token kupca.
 * confirmationTtlMinutes: koliko dugo vrijedi prijedlog kupnje/povrata/blokade koji ceka potvrdu kupca.
 */
@ConfigurationProperties(prefix = "nottrilix.mcp")
public record McpProperties(String agentKey, int confirmationTtlMinutes) {
}
