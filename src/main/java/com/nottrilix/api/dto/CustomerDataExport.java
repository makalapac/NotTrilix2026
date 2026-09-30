package com.nottrilix.api.dto;

import java.time.Instant;
import java.util.List;

/** All data the system keeps about the customer, in machine-readable form (GDPR Art. 15 right of access, Art. 20 portability). */
public record CustomerDataExport(Instant exportedAt, CustomerDto customer, WalletDto wallet,
                                 List<TransactionDto> transactions, List<TicketDto> tickets,
                                 List<ConversationExport> conversations, List<AnalysisDto> analyses,
                                 List<ConsentDto> consents) {
}
