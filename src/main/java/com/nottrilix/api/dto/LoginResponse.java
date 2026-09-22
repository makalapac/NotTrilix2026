package com.nottrilix.api.dto;

/** Token ide u header {@code Authorization: Bearer <token>} na svakom sljedecem zahtjevu. */
public record LoginResponse(String token, AuthUserDto user) {
}
