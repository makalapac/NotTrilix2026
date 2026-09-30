package com.nottrilix.auth;

/** The logged-in user (principal in the SecurityContext). {@code login} = customer's e-mail or admin's username. */
public record AuthUser(Realm realm, Long id, String name, String login) {
}
