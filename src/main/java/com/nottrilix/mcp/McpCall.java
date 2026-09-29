package com.nottrilix.mcp;

import com.nottrilix.auth.AuthUser;
import com.nottrilix.auth.Realm;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Kontekst jednog MCP poziva alata.
 * <ul>
 *   <li><b>Tko je kupac</b> odlucuje token u HTTP zahtjevu (Spring Security), ne klijent i ne model.</li>
 *   <li><b>Koji razgovor i koji turn</b> javlja aplikacija agenta u MCP polju {@code _meta}; model ga ne vidi.</li>
 * </ul>
 */
public record McpCall(AuthUser user, Map<String, Object> meta) {

    public static final String CONVERSATION_ID = "conversationId";
    public static final String TURN = "turn";
    public static final String CHANNEL = "channel";

    private static final Pattern CONVERSATION = Pattern.compile("[A-Za-z0-9-]{1,64}");

    /** Poziv koji se upravo izvrsava (alat radi na dretvi HTTP zahtjeva, pa je prijava dostupna). */
    public static McpCall current(Map<String, Object> meta) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AuthUser user = auth != null && auth.getPrincipal() instanceof AuthUser u ? u : null;
        return new McpCall(user, meta == null ? Map.of() : meta);
    }

    /** Kupac u cije ime se alat izvrsava; bez kupceva tokena alat ne radi. */
    public AuthUser customer() {
        if (user == null || user.realm() != Realm.CUSTOMER) {
            throw new McpCallException("CUSTOMER_REQUIRED",
                    "Alat radi samo u ime prijavljenog kupca: zahtjev mora nositi token kupca");
        }
        return user;
    }

    public long customerId() {
        return customer().id();
    }

    /** Razgovor iz _meta ili null ako ga klijent nije poslao. */
    public String conversationId() {
        return meta.get(CONVERSATION_ID) instanceof String id && CONVERSATION.matcher(id).matches() ? id : null;
    }

    /** Kanal razgovora iz _meta (CHAT, kasnije glas i telefon); MCP ako ga klijent nije poslao. */
    public String channel() {
        return meta.get(CHANNEL) instanceof String channel && !channel.isBlank() && channel.length() <= 10
                ? channel : "MCP";
    }

    /** Kljuc razgovora za branu potvrde: kupac (iz tokena) + razgovor (iz _meta). */
    public String conversationKey() {
        String conversationId = conversationId();
        if (conversationId == null) {
            throw new McpCallException("CONVERSATION_REQUIRED", "Radnja traži kontekst razgovora: klijent mora "
                    + "u _meta poslati conversationId i turn");
        }
        return "c" + customerId() + ":" + conversationId;
    }

    /** Redni broj turna razgovora; potvrda je moguca tek u turnu nakon prijedloga. */
    public int turn() {
        if (meta.get(TURN) instanceof Number turn && turn.intValue() > 0) {
            return turn.intValue();
        }
        throw new McpCallException("CONVERSATION_REQUIRED", "Radnja traži kontekst razgovora: klijent mora "
                + "u _meta poslati conversationId i turn");
    }
}
