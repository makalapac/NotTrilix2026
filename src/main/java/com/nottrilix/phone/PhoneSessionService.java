package com.nottrilix.phone;

import com.nottrilix.api.dto.PhoneCallEnd;
import com.nottrilix.api.dto.PhoneCallStart;
import com.nottrilix.api.dto.PhoneIdentity;
import com.nottrilix.api.dto.PhoneTurn;
import com.nottrilix.auth.AuthUser;
import com.nottrilix.auth.Realm;
import com.nottrilix.auth.TokenService;
import com.nottrilix.domain.AuditEventType;
import com.nottrilix.domain.AuditOutcome;
import com.nottrilix.domain.Customer;
import com.nottrilix.repo.CustomerRepository;
import com.nottrilix.service.AuditService;
import com.nottrilix.service.AuditService.AuditContext;
import com.nottrilix.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The phone channel, seen from the system. A caller has no login and no browser, so the system recognises the customer
 * by caller id and issues a short-lived customer token for the agent: the same credential the chat uses, so every MCP
 * call during the phone call carries the customer's identity and the same rules, confirmation gate and audit apply.
 * Turns are recorded like chat turns (channel PHONE), the token is revoked when the call ends. An unknown caller gets
 * no token: the agent may talk, but cannot act on any account.
 */
@Service
public class PhoneSessionService {

    private static final Logger log = LoggerFactory.getLogger("PHONE");
    public static final String CHANNEL = "PHONE";

    private final CustomerRepository customers;
    private final TokenService tokens;
    private final AuditService audit;
    private final ConversationService conversations;
    private final PhoneProperties props;

    public PhoneSessionService(CustomerRepository customers, TokenService tokens, AuditService audit,
                               ConversationService conversations, PhoneProperties props) {
        this.customers = customers;
        this.tokens = tokens;
        this.audit = audit;
        this.conversations = conversations;
        this.props = props;
    }

    /** Caller id -> customer -> customer token for the agent. The number is masked in logs and in the audit trail. */
    @Transactional(readOnly = true)
    public PhoneIdentity identify(PhoneCallStart call) {
        String digits = PhoneNumbers.digits(call.from());
        String masked = PhoneNumbers.mask(call.from());
        Optional<Customer> customer = digits.isEmpty() ? Optional.empty() : customers.findByPhoneDigits(digits);
        if (customer.isEmpty() && props.demoCaller() != null && !props.demoCaller().isBlank()
                && PhoneNumbers.digits(props.demoCaller()).equals(digits)) {
            customer = customers.findById(1L); // the presenter's own phone stands in for the first seeded customer
        }
        if (customer.isEmpty()) {
            audit.record(new AuditContext(call.callId(), null, null, CHANNEL), AuditEventType.PHONE_CALL, "identify",
                    masked, AuditOutcome.DENIED, "pozivatelj nije prepoznat po broju: razgovor bez radnji na računu",
                    null);
            log.info("PHONE call={} from={} -> nepoznat pozivatelj", call.callId(), masked);
            return PhoneIdentity.unknown();
        }
        Customer found = customer.get();
        String token = tokens.issue(new AuthUser(Realm.CUSTOMER, found.getId(), found.getName(), found.getEmail()));
        audit.record(new AuditContext(call.callId(), found.getId(), found.getName(), CHANNEL),
                AuditEventType.PHONE_CALL, "identify", masked, AuditOutcome.OK,
                "pozivatelj prepoznat po broju telefona, token za agenta izdan za trajanje poziva", null);
        log.info("PHONE call={} from={} -> customer#{}", call.callId(), masked, found.getId());
        return new PhoneIdentity(true, found.getId(), found.getName(), token);
    }

    /** One turn of the call, recorded like a chat turn; the token says which customer it belongs to. */
    public void recordTurn(String callId, PhoneTurn turn) {
        AuthUser customer = tokens.resolve(turn.customerToken())
                .filter(user -> user.realm() == Realm.CUSTOMER)
                .orElseThrow(() -> new IllegalArgumentException("Token kupca za ovaj poziv nije valjan"));
        audit.record(new AuditContext(callId, customer.id(), customer.name(), CHANNEL), AuditEventType.CHAT_TURN,
                "turn", turn.message(), AuditOutcome.OK, turn.reply(), turn.durationMs(), turn.promptTokens(),
                turn.completionTokens());
        conversations.recordTurn(callId, customer.id(), CHANNEL);
        log.info("PHONE call={} turn customer#{} OK {} ms", callId, customer.id(), turn.durationMs());
    }

    /** The call is over: the customer token dies with it. */
    public void end(String callId, PhoneCallEnd end) {
        AuthUser customer = end.customerToken() == null ? null : tokens.resolve(end.customerToken()).orElse(null);
        if (end.customerToken() != null) {
            tokens.revoke(end.customerToken());
        }
        audit.record(new AuditContext(callId, customer == null ? null : customer.id(),
                        customer == null ? null : customer.name(), CHANNEL), AuditEventType.PHONE_CALL, "end",
                end.reason(), AuditOutcome.OK, "%d turnova, %d s".formatted(end.turns(), end.durationMs() / 1000),
                end.durationMs());
        log.info("PHONE call={} end reason={} turns={} {} s, token povučen={}", callId, end.reason(), end.turns(),
                end.durationMs() / 1000, end.customerToken() != null);
    }
}
