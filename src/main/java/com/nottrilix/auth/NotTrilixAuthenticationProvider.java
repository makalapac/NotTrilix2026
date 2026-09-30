package com.nottrilix.auth;

import com.nottrilix.repo.AdminUserRepository;
import com.nottrilix.repo.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Password check (bcrypt) for both realms: customer by e-mail, admin by username. */
@Component
public class NotTrilixAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger("AUTH");
    static final String BAD_CREDENTIALS = "Neispravna e-mail adresa / korisničko ime ili lozinka";

    private final CustomerRepository customerRepo;
    private final AdminUserRepository adminRepo;
    private final PasswordEncoder encoder;

    public NotTrilixAuthenticationProvider(CustomerRepository customerRepo, AdminUserRepository adminRepo,
                                           PasswordEncoder encoder) {
        this.customerRepo = customerRepo;
        this.adminRepo = adminRepo;
        this.encoder = encoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        LoginToken login = (LoginToken) authentication;
        String password = String.valueOf(login.getCredentials());
        Optional<AuthUser> user = switch (login.realm()) {
            case CUSTOMER -> customerRepo.findByEmailIgnoreCase(login.login())
                    .filter(c -> encoder.matches(password, c.getPasswordHash()))
                    .map(c -> new AuthUser(Realm.CUSTOMER, c.getId(), c.getName(), c.getEmail()));
            case ADMIN -> adminRepo.findByUsername(login.login())
                    .filter(a -> encoder.matches(password, a.getPasswordHash()))
                    .map(a -> new AuthUser(Realm.ADMIN, a.getId(), a.getName(), a.getUsername()));
            case AGENT -> Optional.empty(); // the agent does not log in with a password but with a key (TokenService)
        };
        AuthUser authUser = user.orElseThrow(() -> {
            log.warn("LOGIN FAILED realm={} login={}", login.realm(), login.login());
            return new BadCredentialsException(BAD_CREDENTIALS);
        });
        log.info("LOGIN OK realm={} id={} login={}", authUser.realm(), authUser.id(), authUser.login());
        return UsernamePasswordAuthenticationToken.authenticated(authUser, null, authorities(authUser));
    }

    /** ROLE_CUSTOMER, ROLE_ADMIN or ROLE_AGENT - also used by the bearer filter. */
    public static List<GrantedAuthority> authorities(AuthUser user) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.realm().name()));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return LoginToken.class.isAssignableFrom(authentication);
    }
}
