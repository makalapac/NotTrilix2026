package com.nottrilix.api.dto;

/** Jedinstven oblik greske za API: strojni kod + poruka za covjeka. */
public record ErrorResponse(String error, String message) {
}
