package com.nottrilix.auth;

import com.nottrilix.mcp.McpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bearer tokens for the API: opaque random string -> logged-in user, in memory, with an expiry.
 * An application restart invalidates all tokens (enough for the demo; production = JWT or persistent storage).
 * The agent key (nottrilix.mcp.agent-key) is the permanent token of the agent application, with the AGENT role.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger("AUTH");
    private static final Duration TTL = Duration.ofHours(8);
    private static final AuthUser AGENT = new AuthUser(Realm.AGENT, 0L, "NotTrilix agent", "agent");

    private record Entry(AuthUser user, Instant expiresAt) {
    }

    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final byte[] agentKey;

    public TokenService(Clock clock, McpProperties mcp) {
        this.clock = clock;
        this.agentKey = mcp.agentKey() == null ? new byte[0] : mcp.agentKey().getBytes(StandardCharsets.UTF_8);
    }

    public String issue(AuthUser user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(token, new Entry(user, Instant.now(clock).plus(TTL)));
        log.info("TOKEN issued realm={} id={} login={} ttl={}h active={}",
                user.realm(), user.id(), user.login(), TTL.toHours(), tokens.size());
        return token;
    }

    public Optional<AuthUser> resolve(String token) {
        if (isAgentKey(token)) {
            return Optional.of(AGENT);
        }
        Entry entry = tokens.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now(clock))) {
            tokens.remove(token);
            log.info("TOKEN expired realm={} login={}", entry.user().realm(), entry.user().login());
            return Optional.empty();
        }
        return Optional.of(entry.user());
    }

    public void revoke(String token) {
        Entry entry = tokens.remove(token);
        if (entry != null) {
            log.info("TOKEN revoked realm={} login={} active={}", entry.user().realm(), entry.user().login(), tokens.size());
        }
    }

    private boolean isAgentKey(String token) {
        return agentKey.length > 0 && token != null
                && MessageDigest.isEqual(agentKey, token.getBytes(StandardCharsets.UTF_8));
    }
}
