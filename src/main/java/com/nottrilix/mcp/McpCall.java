package com.nottrilix.mcp;

import com.nottrilix.auth.AuthUser;
import com.nottrilix.auth.Realm;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Context of a single MCP tool call.
 * <ul>
 *   <li><b>Who the customer is</b> is decided by the HTTP request token (Spring Security), not the client or model.</li>
 *   <li><b>Which conversation and which turn</b> is reported by the agent application in the MCP {@code _meta} field;
 *       the model does not see it.</li>
 * </ul>
 */
public record McpCall(AuthUser user, Map<String, Object> meta) {

    public static final String CONVERSATION_ID = "conversationId";
    public static final String TURN = "turn";
    public static final String CHANNEL = "channel";

    private static final Pattern CONVERSATION = Pattern.compile("[A-Za-z0-9-]{1,64}");

    /** The call currently executing (the tool runs on the HTTP request thread, so the authenticated user is available). */
    public static McpCall current(Map<String, Object> meta) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AuthUser user = auth != null && auth.getPrincipal() instanceof AuthUser u ? u : null;
        return new McpCall(user, meta == null ? Map.of() : meta);
    }

    /** The customer on whose behalf the tool executes; without the customer's token the tool does not work. */
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

    /** The conversation from _meta, or null if the client did not send it. */
    public String conversationId() {
        return meta.get(CONVERSATION_ID) instanceof String id && CONVERSATION.matcher(id).matches() ? id : null;
    }

    /** The conversation channel from _meta (CHAT, later voice and phone); MCP if the client did not send it. */
    public String channel() {
        return meta.get(CHANNEL) instanceof String channel && !channel.isBlank() && channel.length() <= 10
                ? channel : "MCP";
    }

    /** The conversation key for the confirmation gate: customer (from the token) + conversation (from _meta). */
    public String conversationKey() {
        String conversationId = conversationId();
        if (conversationId == null) {
            throw new McpCallException("CONVERSATION_REQUIRED", "Radnja traži kontekst razgovora: klijent mora "
                    + "u _meta poslati conversationId i turn");
        }
        return "c" + customerId() + ":" + conversationId;
    }

    /** The sequence number of the conversation turn; confirmation is possible only in a turn after the proposal. */
    public int turn() {
        if (meta.get(TURN) instanceof Number turn && turn.intValue() > 0) {
            return turn.intValue();
        }
        throw new McpCallException("CONVERSATION_REQUIRED", "Radnja traži kontekst razgovora: klijent mora "
                + "u _meta poslati conversationId i turn");
    }
}
