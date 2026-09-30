package com.nottrilix.privacy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retention periods and consent text version (application.yml -> nottrilix.privacy.*).
 * conversationRetentionDays: how long conversation messages (audit CHAT_TURN / KB_RETRIEVAL) and the conversation-customer link are kept.
 * analysisRetentionDays: after that, analyses remain only in anonymous form (statistics).
 * voiceConsentVersion: version of the voice consent text; a new version = the customer gives consent again.
 */
@ConfigurationProperties(prefix = "nottrilix.privacy")
public record PrivacyProperties(int conversationRetentionDays, int analysisRetentionDays, String voiceConsentVersion) {
}
