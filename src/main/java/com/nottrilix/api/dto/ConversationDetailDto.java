package com.nottrilix.api.dto;

import java.util.List;

/** Razgovor s transkriptom (iz audit traga) i analizom. */
public record ConversationDetailDto(ConversationDto conversation, List<TranscriptLine> transcript) {
}
