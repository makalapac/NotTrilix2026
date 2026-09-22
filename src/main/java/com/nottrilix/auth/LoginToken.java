package com.nottrilix.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

/** Zahtjev za prijavu (neautenticiran): domena + login + lozinka. Provjerava ga {@link NotTrilixAuthenticationProvider}. */
public class LoginToken extends AbstractAuthenticationToken {

    private final Realm realm;
    private final String login;
    private final String password;

    public LoginToken(Realm realm, String login, String password) {
        super(List.of());
        this.realm = realm;
        this.login = login;
        this.password = password;
        setAuthenticated(false);
    }

    public Realm realm() {
        return realm;
    }

    public String login() {
        return login;
    }

    @Override
    public Object getCredentials() {
        return password;
    }

    @Override
    public Object getPrincipal() {
        return login;
    }
}
