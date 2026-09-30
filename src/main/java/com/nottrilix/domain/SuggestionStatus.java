package com.nottrilix.domain;

public enum SuggestionStatus {
    /** Model je predlozio; ceka odluku admina. */
    PROPOSED,
    /** Admin je odobrio; odlomak je u bazi znanja. */
    APPROVED,
    /** Admin je odbio; ne ulazi u bazu znanja. */
    REJECTED
}
