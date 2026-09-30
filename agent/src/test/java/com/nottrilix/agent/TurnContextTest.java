package com.nottrilix.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Only conversation, turn and channel go over the wire (MCP _meta); token as a header, trace stays in the agent. */
class TurnContextTest {

    @Test
    void metaCarriesConversationButNeverTheTokenOrTheTrace() {
        TurnContext turn = new TurnContext("token-ana", "abc12345", 3, "CHAT", new AgentTrace());

        assertThat(turn.meta()).isEqualTo(Map.of("conversationId", "abc12345", "turn", 3, "channel", "CHAT"));
        assertThat(turn.asMap()).containsKeys("customerToken", "trace");
    }

    @Test
    void missingContextGivesEmptyMeta() {
        assertThat(TurnContext.meta(null)).isEmpty();
        assertThat(TurnContext.meta(Map.of("customerToken", "token-ana"))).isEmpty();
    }
}
