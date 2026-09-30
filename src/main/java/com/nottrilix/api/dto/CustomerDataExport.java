package com.nottrilix.api.dto;

import java.time.Instant;
import java.util.List;

/** Svi podaci koje sustav cuva o kupcu, u strojno citljivom obliku (GDPR cl. 15 pravo pristupa, cl. 20 prenosivost). */
public record CustomerDataExport(Instant exportedAt, CustomerDto customer, WalletDto wallet,
                                 List<TransactionDto> transactions, List<TicketDto> tickets,
                                 List<ConversationExport> conversations, List<AnalysisDto> analyses,
                                 List<ConsentDto> consents) {
}
