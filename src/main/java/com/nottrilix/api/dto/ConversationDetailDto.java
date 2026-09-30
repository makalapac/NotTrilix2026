package com.nottrilix.api.dto;

import java.util.List;

/** A conversation with its transcript (from the audit trail) and analysis. */
public record ConversationDetailDto(ConversationDto conversation, List<TranscriptLine> transcript) {
}
