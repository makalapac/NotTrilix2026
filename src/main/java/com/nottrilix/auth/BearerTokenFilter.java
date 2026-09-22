package com.nottrilix.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * {@code Authorization: Bearer <token>} -> prijavljeni korisnik u SecurityContextu (samo za taj zahtjev, bez sesije).
 * Nepoznat/istekao token = anoniman zahtjev; autorizacija po putanjama je u {@code SecurityConfig}.
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final TokenService tokenService;

    public BearerTokenFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            tokenService.resolve(token).ifPresent(user -> {
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        user, null, NotTrilixAuthenticationProvider.authorities(user));
                authentication.setDetails(token); // logout zna koji token ponistiti
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
