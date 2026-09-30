package com.nottrilix.api.dto;

import com.nottrilix.domain.ConsentType;

import java.time.Instant;

public record ConsentDto(ConsentType type, String textVersion, Instant givenAt, Instant withdrawnAt) {
}
