package com.nottrilix.api.dto;

import com.nottrilix.domain.VoucherStatus;

import java.time.Instant;

public record VoucherDto(String serial, String code, VoucherStatus status, Instant soldAt) {
}
