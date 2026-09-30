package com.nottrilix.api.dto;

import java.util.List;

/** What the customer needs to know about their data: retention periods, consents given, how many conversations we keep. */
public record PrivacyOverview(int conversationRetentionDays, int analysisRetentionDays, String voiceConsentVersion,
                              boolean voiceConsentGiven, List<ConsentDto> consents, long storedConversations) {
}
