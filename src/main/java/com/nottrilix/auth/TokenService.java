package com.nottrilix.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bearer tokeni za API: neprozirni slucajni string -> prijavljeni korisnik, u memoriji, s rokom trajanja.
 * Restart aplikacije ponistava sve tokene (za demo dovoljno; produkcija = JWT ili trajna pohrana).
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger("AUTH");
    private static final Duration TTL = Duration.ofHours(8);

    private record Entry(AuthUser user, Instant expiresAt) {
    }

    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public TokenService(Clock clock) {
        this.clock = clock;
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
}
