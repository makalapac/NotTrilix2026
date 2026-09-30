package com.nottrilix.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Voice channel (application.yml -> nottrilix.voice.*). */
@ConfigurationProperties(prefix = "nottrilix.voice")
public record VoiceProperties(boolean enabled, int maxReplyChars) {
}
