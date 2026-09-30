package com.nottrilix.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where the agent application is and how long we wait for it (application.yml -> nottrilix.agent.*). */
@ConfigurationProperties(prefix = "nottrilix.agent")
public record AgentClientProperties(String url, int connectTimeoutSeconds, int readTimeoutSeconds) {
}
