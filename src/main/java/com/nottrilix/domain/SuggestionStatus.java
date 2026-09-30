package com.nottrilix.domain;

public enum SuggestionStatus {
    /** Proposed by the model; awaiting the admin's decision. */
    PROPOSED,
    /** Approved by the admin; the section is in the knowledge base. */
    APPROVED,
    /** Rejected by the admin; does not enter the knowledge base. */
    REJECTED
}
