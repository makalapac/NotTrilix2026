package com.nottrilix.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Agent settings (application.yml -> nottrilix.agent.*). agentKey = key the agent presents to the system. */
@ConfigurationProperties(prefix = "nottrilix.agent")
public record AgentProperties(String name, int maxHistoryMessages, String agentKey) {
}
