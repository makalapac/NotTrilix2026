package com.nottrilix.api.dto;

/** Uniform error shape for the API: machine-readable code + human-readable message. */
public record ErrorResponse(String error, String message) {
}
