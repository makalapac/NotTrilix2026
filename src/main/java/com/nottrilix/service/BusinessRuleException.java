package com.nottrilix.service;

/** Poslovno pravilo je odbilo operaciju (npr. OUT_OF_STOCK, DAILY_LIMIT_EXCEEDED). API je mapira na HTTP 409. */
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
