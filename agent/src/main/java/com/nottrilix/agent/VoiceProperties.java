package com.nottrilix.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Voice channel (application.yml -> nottrilix.voice.*).
 * maxReplyChars: a longer reply is cut for speech (the chat keeps the full text).
 * fillerText: the short phrase Nola says while the agent is still thinking (synthesized once, cached).
 */
@ConfigurationProperties(prefix = "nottrilix.voice")
public record VoiceProperties(boolean enabled, int maxReplyChars, String fillerText) {
}
