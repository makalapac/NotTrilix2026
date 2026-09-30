package com.nottrilix.service;

/** A business rule rejected the operation (e.g. OUT_OF_STOCK, DAILY_LIMIT_EXCEEDED). The API maps it to HTTP 409. */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
