package com.nottrilix.api.dto;

import java.util.List;

/** Sto kupac treba znati o svojim podacima: rokovi cuvanja, dane privole, koliko razgovora cuvamo. */
public record PrivacyOverview(int conversationRetentionDays, int analysisRetentionDays, String voiceConsentVersion,
                              boolean voiceConsentGiven, List<ConsentDto> consents, long storedConversations) {
}
