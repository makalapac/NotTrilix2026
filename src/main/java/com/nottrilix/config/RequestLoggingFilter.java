package com.nottrilix.config;

import com.nottrilix.auth.AuthUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Jedan redak po API i MCP zahtjevu: metoda, putanja, tko (po tokenu), HTTP status i trajanje.
 * Izvrsava se unutar Spring Security lanca (nakon njega u redoslijedu), pa je korisnik vec poznat;
 * zahtjeve koje Security odbije (401/403) logira SecurityConfig.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("HTTP");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") && !uri.startsWith("/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            String query = request.getQueryString();
            log.info("{} {}{} user={} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                    query == null ? "" : "?" + query, currentUser(), response.getStatus(), ms);
        }
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            // bez e-maila u logu (GDPR: logovi su kratkog vijeka, ali ne trebaju identitet)
            return user.realm().name().toLowerCase() + "#" + user.id();
        }
        return "anonymous";
    }
}
