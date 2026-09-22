package com.nottrilix.auth;

/** Prijavljeni korisnik (principal u SecurityContextu). {@code login} = e-mail kupca ili korisnicko ime admina. */
public record AuthUser(Realm realm, Long id, String name, String login) {
}
