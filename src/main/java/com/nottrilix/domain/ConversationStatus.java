package com.nottrilix.domain;

public enum ConversationStatus {
    /** In progress or finished, but not yet (re-)analyzed. */
    OPEN,
    /** Analyzed; a new message returns it to OPEN. */
    ANALYZED,
    /** The customer requested erasure: turns deleted, conversation record anonymized. */
    ERASED
}
