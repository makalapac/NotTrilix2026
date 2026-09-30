package com.nottrilix.config;

import com.nottrilix.auth.AuthUser;
import com.nottrilix.auth.BearerTokenFilter;
import com.nottrilix.auth.NotTrilixAuthenticationProvider;
import com.nottrilix.auth.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Pristup po putanjama: shop samo za kupce, admin samo za admine, MCP za kupce i agenta; sve preko bearer tokena
 * (bez sesija i CSRF-a). Statika (React stranice), Swagger i login endpointi su javni.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger("AUTH");

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(NotTrilixAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, TokenService tokenService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new BearerTokenFilter(tokenService), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/customer/login", "/api/auth/admin/login").permitAll()
                        .requestMatchers("/api/shop/**", "/api/agent/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/admin/**", "/api/customers/**").hasRole("ADMIN")
                        // MCP server: agent se spaja svojim kljucem (popis alata), alati traze token kupca
                        .requestMatchers("/mcp", "/mcp/**").hasAnyRole("CUSTOMER", "AGENT")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> unauthorized(request, response))
                        .accessDeniedHandler((request, response, ex) -> forbidden(request, response)));
        return http.build();
    }

    private static void unauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.warn("AUTH 401 {} {} - prijava je potrebna", request.getMethod(), request.getRequestURI());
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Prijava je potrebna");
    }

    private static void forbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String who = auth != null && auth.getPrincipal() instanceof AuthUser u ? u.realm() + "#" + u.id() : "anonymous";
        log.warn("AUTH 403 {} {} user={} - nema pravo pristupa", request.getMethod(), request.getRequestURI(), who);
        writeJson(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Nemate pravo pristupa");
    }

    private static void writeJson(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
