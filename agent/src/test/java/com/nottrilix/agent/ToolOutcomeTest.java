package com.nottrilix.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolOutcomeTest {

    @Test
    void readsTheTextPartOfMcpContent() {
        assertThat(ToolOutcome.text("[{\"type\":\"text\",\"text\":\"{\\\"ok\\\":true}\"}]")).isEqualTo("{\"ok\":true}");
        assertThat(ToolOutcome.text("[{\"type\":\"image\",\"data\":\"...\"},{\"type\":\"text\",\"text\":\"drugi\"}]"))
                .isEqualTo("drugi");
    }

    @Test
    void leavesAnythingElseAsItIs() {
        assertThat(ToolOutcome.text("nije json")).isEqualTo("nije json");
        assertThat(ToolOutcome.text("{\"ok\":true}")).isEqualTo("{\"ok\":true}");
        assertThat(ToolOutcome.text(null)).isEmpty();
    }

    @Test
    void outcomeFollowsOkAndStatus() {
        assertThat(ToolOutcome.of("{\"ok\":true,\"status\":\"OK\"}")).isEqualTo("OK");
        assertThat(ToolOutcome.of("{\"ok\":false,\"status\":\"INSUFFICIENT_FUNDS\"}")).isEqualTo("DENIED");
        assertThat(ToolOutcome.of("{\"ok\":false,\"status\":\"CONFIRMATION_REQUIRED\"}"))
                .isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(ToolOutcome.of("{\"ok\":false,\"status\":\"ERROR\"}")).isEqualTo("ERROR");
    }
}
