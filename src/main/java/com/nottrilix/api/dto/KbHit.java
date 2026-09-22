package com.nottrilix.api.dto;

/** Jedan pogodak semanticke pretrage baze znanja: odakle je odlomak, koliko je slican upitu (0..1) i sam tekst. */
public record KbHit(String source, String title, String section, String category, String tags, double score,
                    String content) {
}
