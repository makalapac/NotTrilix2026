package com.nottrilix.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Preko mreze (MCP _meta) ide samo razgovor, turn i kanal; token ide zaglavljem, a trag ostaje u agentu. */
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
