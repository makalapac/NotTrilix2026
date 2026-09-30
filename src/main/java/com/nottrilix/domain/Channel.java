package com.nottrilix.domain;

/** Sales channel through which the purchase was made. */
public enum Channel {
    /** Web shop. */
    WEB,
    /** Kiosk / point of sale. */
    POS,
    /** Support agent on behalf of the customer (chat; later voice and phone). */
    AGENT
}
