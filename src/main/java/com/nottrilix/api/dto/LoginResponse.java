package com.nottrilix.api.dto;

/** The token goes in the {@code Authorization: Bearer <token>} header on every subsequent request. */
public record LoginResponse(String token, AuthUserDto user) {
}
