package com.nottrilix.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A rule in code, not in the prompt: an action that changes money or a voucher (purchase, refund, block) is executed
 * only if it was proposed in an EARLIER turn of the same conversation - so the customer had a chance to respond
 * between the proposal and the execution. The agent cannot confirm an action to itself within the same turn.
 * <p>
 * The gate is on the system side (the MCP server), not the agent's: it applies to every MCP client. The server holds
 * the state: at most one action (the latest proposal) waits per conversation, with exact parameters. The model sends
 * no parameters when confirming, so it can neither get them wrong nor make them up.
 */
@Component
public class ConfirmationGate {

    private static final Logger log = LoggerFactory.getLogger("MCP");

    /** An action waiting for the customer's confirmation. */
    public record PendingAction(String action, String params, String summary, int turn, Instant at) {
    }

    private final Map<String, PendingAction> pending = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    public ConfirmationGate(McpProperties props, Clock clock) {
        this.ttl = Duration.ofMinutes(props.confirmationTtlMinutes());
        this.clock = clock;
    }

    /**
     * A tool call with parameters. If the same action already awaits confirmation from an earlier turn, it may be
     * executed (true); otherwise this call proposes it and replaces the previous proposal (false).
     */
    public boolean confirmed(String conversationKey, int turn, String action, String params, String summary) {
        PendingAction current = pending.get(conversationKey);
        if (current != null && current.action().equals(action) && current.params().equals(params)
                && confirmableNow(current, turn)) {
            pending.remove(conversationKey);
            log.info("CONFIRM {} {} conv={} predlozeno u turnu {}, potvrdjeno u turnu {}", action, params,
                    conversationKey, current.turn(), turn);
            return true;
        }
        pending.put(conversationKey, new PendingAction(action, params, summary, turn, Instant.now(clock)));
        log.info("CONFIRM {} {} conv={} turn={} -> ceka potvrdu kupca", action, params, conversationKey, turn);
        return false;
    }

    /** Parameterless confirmation: returns the waiting action if it may be executed in this turn, and removes it. */
    public Optional<PendingAction> takeConfirmable(String conversationKey, int turn) {
        PendingAction current = pending.get(conversationKey);
        if (current == null || !confirmableNow(current, turn)) {
            log.info("CONFIRM conv={} turn={} -> nema radnje koju se smije izvrsiti", conversationKey, turn);
            return Optional.empty();
        }
        pending.remove(conversationKey);
        log.info("CONFIRM {} {} conv={} predlozeno u turnu {}, potvrdjeno u turnu {}", current.action(),
                current.params(), conversationKey, current.turn(), turn);
        return Optional.of(current);
    }

    /** The action currently waiting for confirmation (to show the model and the customer), without changing state. */
    public Optional<PendingAction> peek(String conversationKey) {
        PendingAction current = pending.get(conversationKey);
        if (current == null || expired(current)) {
            return Optional.empty();
        }
        return Optional.of(current);
    }

    /** The customer cancelled the proposed action. */
    public Optional<PendingAction> cancel(String conversationKey) {
        PendingAction removed = pending.remove(conversationKey);
        if (removed != null) {
            log.info("CONFIRM {} {} conv={} -> otkazano", removed.action(), removed.params(), conversationKey);
        }
        return Optional.ofNullable(removed);
    }

    private boolean confirmableNow(PendingAction action, int turn) {
        return turn > action.turn() && !expired(action);
    }

    private boolean expired(PendingAction action) {
        return !Instant.now(clock).isBefore(action.at().plus(ttl));
    }
}
