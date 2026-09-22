package com.nottrilix.kb;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Postavke baze znanja (application.yml -> nottrilix.kb.*). */
@ConfigurationProperties(prefix = "nottrilix.kb")
public record KbProperties(boolean ingestOnStartup, int topK, double similarityThreshold) {
}
