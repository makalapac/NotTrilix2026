package com.nottrilix.domain;

public enum Brand {
    A1("A1 Hrvatska"),
    HT("Hrvatski Telekom"),
    STEAM("Steam"),
    SONY("PlayStation Store"),
    XBOX("Xbox");

    private final String displayName;

    Brand(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
