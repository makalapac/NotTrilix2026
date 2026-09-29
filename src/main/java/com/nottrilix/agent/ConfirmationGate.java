package com.nottrilix.agent;

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
 * Pravilo u kodu, ne u promptu: radnja koja mijenja novac ili bon (kupnja, povrat, blokada) izvrsava se tek
 * ako je predlozena u RANIJEM turnu istog razgovora - dakle kupac je izmedju prijedloga i izvrsenja imao
 * priliku odgovoriti. Agent ne moze sam sebi potvrditi radnju unutar istog turna.
 * <p>
 * Stanje drzi server, ne model: po razgovoru ceka najvise jedna radnja (zadnji prijedlog), s tocnim
 * parametrima. Model pri potvrdi ne salje parametre, pa ih ne moze ni pogrijesiti ni izmisliti.
 */
@Component
public class ConfirmationGate {

    private static final Logger log = LoggerFactory.getLogger("AGENT");

    /** Radnja koja ceka potvrdu kupca. */
    public record PendingAction(String action, String params, String summary, int turn, Instant at) {
    }

    private final Map<String, PendingAction> pending = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    public ConfirmationGate(AgentProperties props, Clock clock) {
        this.ttl = Duration.ofMinutes(props.confirmationTtlMinutes());
        this.clock = clock;
    }

    /**
     * Poziv alata s parametrima. Ako ista radnja vec ceka potvrdu iz ranijeg turna, smije se izvrsiti (true);
     * inace se ovim pozivom predlaze i zamjenjuje prethodni prijedlog (false).
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

    /** Potvrda bez parametara: vraca radnju koja ceka, ako se u ovom turnu smije izvrsiti, i uklanja je. */
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

    /** Radnja koja trenutno ceka potvrdu (za prikaz modelu), bez mijenjanja stanja. */
    public Optional<PendingAction> peek(String conversationKey) {
        PendingAction current = pending.get(conversationKey);
        if (current == null || expired(current)) {
            return Optional.empty();
        }
        return Optional.of(current);
    }

    /** Kupac je odustao. */
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
