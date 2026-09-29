package com.nottrilix.domain;

/** Prodajni kanal kroz koji je kupnja obavljena. */
public enum Channel {
    /** Web shop. */
    WEB,
    /** Kiosk / prodajno mjesto. */
    POS,
    /** Agent podrske u ime kupca (chat; kasnije glas i telefon). */
    AGENT
}
