package com.nottrilix.api;

import com.nottrilix.api.dto.AdminLoginRequest;
import com.nottrilix.api.dto.AuthUserDto;
import com.nottrilix.api.dto.CustomerLoginRequest;
import com.nottrilix.api.dto.LoginResponse;
import com.nottrilix.auth.AuthUser;
import com.nottrilix.auth.LoginToken;
import com.nottrilix.auth.Realm;
import com.nottrilix.auth.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Prijava kupca (e-mail + lozinka) i admina (korisnicko ime + lozinka) -> bearer token")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger("AUTH");

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    @PostMapping("/customer/login")
    @Operation(summary = "Prijava kupca u shop (e-mail + lozinka)")
    public LoginResponse customerLogin(@Valid @RequestBody CustomerLoginRequest request) {
        return login(new LoginToken(Realm.CUSTOMER, request.email(), request.password()));
    }

    @PostMapping("/admin/login")
    @Operation(summary = "Prijava u admin konzolu (korisnicko ime + lozinka)")
    public LoginResponse adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        return login(new LoginToken(Realm.ADMIN, request.username(), request.password()));
    }

    @GetMapping("/me")
    @Operation(summary = "Tko je prijavljen (po tokenu)")
    public AuthUserDto me(@AuthenticationPrincipal AuthUser user) {
        return AuthUserDto.from(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Odjava - token prestaje vrijediti")
    public void logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof String token) {
            tokenService.revoke(token);
        }
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
            log.info("LOGOUT realm={} id={} login={}", user.realm(), user.id(), user.login());
        }
    }

    private LoginResponse login(LoginToken loginToken) {
        Authentication authentication = authenticationManager.authenticate(loginToken);
        AuthUser user = (AuthUser) authentication.getPrincipal();
        return new LoginResponse(tokenService.issue(user), AuthUserDto.from(user));
    }
}
