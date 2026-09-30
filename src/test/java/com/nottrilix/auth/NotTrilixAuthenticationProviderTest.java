package com.nottrilix.auth;

import com.nottrilix.domain.AdminUser;
import com.nottrilix.domain.Customer;
import com.nottrilix.repo.AdminUserRepository;
import com.nottrilix.repo.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Customer (e-mail) and admin (username) login with bcrypt passwords. */
class NotTrilixAuthenticationProviderTest {

    private final CustomerRepository customerRepo = mock(CustomerRepository.class);
    private final AdminUserRepository adminRepo = mock(AdminUserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final NotTrilixAuthenticationProvider provider =
            new NotTrilixAuthenticationProvider(customerRepo, adminRepo, encoder);

    @Test
    void customerWithCorrectPasswordGetsCustomerRole() {
        Customer ana = new Customer("Ana Anić", "ana.anic@example.com", null, encoder.encode("ana123"));
        when(customerRepo.findByEmailIgnoreCase("ana.anic@example.com")).thenReturn(Optional.of(ana));

        Authentication auth = provider.authenticate(new LoginToken(Realm.CUSTOMER, "ana.anic@example.com", "ana123"));

        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isEqualTo(new AuthUser(Realm.CUSTOMER, null, "Ana Anić", "ana.anic@example.com"));
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void adminWithCorrectPasswordGetsAdminRole() {
        AdminUser admin = new AdminUser("admin", encoder.encode("admin123"), "NotTrilix podrška");
        when(adminRepo.findByUsername("admin")).thenReturn(Optional.of(admin));

        Authentication auth = provider.authenticate(new LoginToken(Realm.ADMIN, "admin", "admin123"));

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        assertThat(((AuthUser) auth.getPrincipal()).login()).isEqualTo("admin");
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameError() {
        Customer ana = new Customer("Ana Anić", "ana.anic@example.com", null, encoder.encode("ana123"));
        when(customerRepo.findByEmailIgnoreCase("ana.anic@example.com")).thenReturn(Optional.of(ana));
        when(customerRepo.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.authenticate(new LoginToken(Realm.CUSTOMER, "ana.anic@example.com", "kriva")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(NotTrilixAuthenticationProvider.BAD_CREDENTIALS);
        assertThatThrownBy(() -> provider.authenticate(new LoginToken(Realm.CUSTOMER, "nobody@example.com", "x")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(NotTrilixAuthenticationProvider.BAD_CREDENTIALS);
    }
}
