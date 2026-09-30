package com.nottrilix.api.dto;

import java.util.List;

/**
 * The agent's reply + what it did along the way (tools) and what it relied on (knowledge base chunks).
 * pendingAction: summary of the action awaiting the customer's confirmation after this turn (null if nothing is pending).
 */
public record AgentChatResponse(String conversationId, String reply, List<AgentToolCall> toolCalls,
                                List<AgentSource> sources, long durationMs, Integer promptTokens,
                                Integer completionTokens, String pendingAction) {
}
