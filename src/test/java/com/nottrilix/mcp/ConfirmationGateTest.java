package com.nottrilix.mcp;

import com.nottrilix.mcp.ConfirmationGate.PendingAction;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** An action executes only if proposed in an earlier turn of the same conversation and the proposal is still valid. */
class ConfirmationGateTest {

    private static final String CONV = "c1:abc";
    private static final String SUMMARY = "kupnja Steam Wallet 25 €";

    /** A clock the test can advance. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-12T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Europe/Zagreb");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();
    private final ConfirmationGate gate = new ConfirmationGate(new McpProperties("test-key", 10), clock);

    @Test
    void firstCallOnlyProposesAndSameTurnCannotConfirm() {
        assertThat(gate.confirmed(CONV, 1, "PURCHASE", "8", SUMMARY)).isFalse();
        assertThat(gate.confirmed(CONV, 1, "PURCHASE", "8", SUMMARY)).isFalse();
        assertThat(gate.takeConfirmable(CONV, 1)).isEmpty();
        assertThat(gate.peek(CONV)).map(PendingAction::summary).contains(SUMMARY);
    }

    @Test
    void confirmationWithoutParametersExecutesWhatWasProposed() {
        gate.confirmed(CONV, 1, "REFUND", "6", "povrat 50 €");

        assertThat(gate.takeConfirmable(CONV, 2)).hasValueSatisfying(action -> {
            assertThat(action.action()).isEqualTo("REFUND");
            assertThat(action.params()).isEqualTo("6");
        });
        // the confirmation has been consumed
        assertThat(gate.takeConfirmable(CONV, 3)).isEmpty();
        assertThat(gate.peek(CONV)).isEmpty();
    }

    @Test
    void sameToolWithSameParametersInLaterTurnAlsoConfirmsExactlyOnce() {
        gate.confirmed(CONV, 1, "PURCHASE", "8", SUMMARY);

        assertThat(gate.confirmed(CONV, 2, "PURCHASE", "8", SUMMARY)).isTrue();
        assertThat(gate.confirmed(CONV, 3, "PURCHASE", "8", SUMMARY)).isFalse();
    }

    @Test
    void differentParametersReplaceTheProposalInsteadOfExecuting() {
        gate.confirmed(CONV, 1, "PURCHASE", "5", "kupnja HT bon 10 €");

        // the model sent the wrong product in the next turn: nothing is executed, this is a new proposal
        assertThat(gate.confirmed(CONV, 2, "PURCHASE", "3", "kupnja A1 bon 20 €")).isFalse();
        assertThat(gate.peek(CONV)).map(PendingAction::params).contains("3");
        assertThat(gate.takeConfirmable(CONV, 2)).isEmpty();
    }

    @Test
    void conversationsAreIsolated() {
        gate.confirmed(CONV, 1, "PURCHASE", "8", SUMMARY);

        assertThat(gate.takeConfirmable("c2:abc", 2)).isEmpty();
        assertThat(gate.takeConfirmable(CONV, 2)).isPresent();
    }

    @Test
    void proposalExpires() {
        gate.confirmed(CONV, 1, "REFUND", "7", "povrat");
        clock.advance(Duration.ofMinutes(11));

        assertThat(gate.peek(CONV)).isEmpty();
        assertThat(gate.takeConfirmable(CONV, 2)).isEmpty();
    }

    @Test
    void cancelRemovesTheProposal() {
        gate.confirmed(CONV, 1, "BLOCK", "STM-25-002", "blokada");

        assertThat(gate.cancel(CONV)).isPresent();
        assertThat(gate.takeConfirmable(CONV, 2)).isEmpty();
        assertThat(gate.cancel(CONV)).isEmpty();
    }
}
