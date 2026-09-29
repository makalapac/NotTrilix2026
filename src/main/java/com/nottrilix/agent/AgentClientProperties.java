package com.nottrilix.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Gdje je aplikacija agenta i koliko je cekamo (application.yml -> nottrilix.agent.*). */
@ConfigurationProperties(prefix = "nottrilix.agent")
public record AgentClientProperties(String url, int connectTimeoutSeconds, int readTimeoutSeconds) {
}
