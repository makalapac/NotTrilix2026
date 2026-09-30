package com.nottrilix.domain;

public enum AuditOutcome {
    /** Executed. */
    OK,
    /** Rejected by a business rule, or the data does not exist / does not belong to the customer. */
    DENIED,
    /** The action was only proposed; awaiting the customer's confirmation in the next turn. */
    CONFIRMATION_REQUIRED,
    /** Unexpected error; the action was not executed. */
    ERROR
}
