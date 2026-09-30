package com.nottrilix.domain;

public enum ConversationStatus {
    /** U tijeku ili zavrsen, a jos nije (ponovno) analiziran. */
    OPEN,
    /** Analiziran; nova poruka ga vraca u OPEN. */
    ANALYZED,
    /** Kupac je zatrazio brisanje: turnovi obrisani, okvir anonimiziran. */
    ERASED
}
