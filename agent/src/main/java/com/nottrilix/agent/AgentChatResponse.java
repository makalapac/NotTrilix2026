package com.nottrilix.agent;

import java.util.List;

/**
 * Odgovor agenta + sto je pritom napravio (alati) i na sto se oslonio (odlomci baze znanja).
 * pendingAction: sazetak radnje koja nakon ovog turna ceka potvrdu kupca (null ako nista ne ceka).
 */
public record AgentChatResponse(String conversationId, String reply, List<AgentToolCall> toolCalls,
                                List<AgentSource> sources, long durationMs, Integer promptTokens,
                                Integer completionTokens, String pendingAction) {
}
