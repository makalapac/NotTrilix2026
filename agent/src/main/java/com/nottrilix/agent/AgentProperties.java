package com.nottrilix.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Postavke agenta (application.yml -> nottrilix.agent.*). agentKey = kljuc kojim se agent predstavlja sustavu. */
@ConfigurationProperties(prefix = "nottrilix.agent")
public record AgentProperties(String name, int maxHistoryMessages, String agentKey) {
}
