package com.nottrilix.privacy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rokovi cuvanja i verzija teksta privole (application.yml -> nottrilix.privacy.*).
 * conversationRetentionDays: koliko dugo se cuvaju poruke razgovora (audit CHAT_TURN / KB_RETRIEVAL) i veza razgovora s kupcem.
 * analysisRetentionDays: nakon toga analize ostaju samo anonimne (statistika).
 * voiceConsentVersion: verzija teksta privole za glas; nova verzija = kupac privolu daje ponovno.
 */
@ConfigurationProperties(prefix = "nottrilix.privacy")
public record PrivacyProperties(int conversationRetentionDays, int analysisRetentionDays, String voiceConsentVersion) {
}
