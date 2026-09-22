package com.nottrilix.kb;

import java.util.List;

/**
 * Jedan odlomak baze znanja = jedan "##" odjeljak markdown dokumenta, s metapodacima iz front mattera.
 * {@code content} = "naslov dokumenta > naslov odjeljka" + tekst odjeljka (to se embedda i to agent dobiva).
 */
public record KbChunk(String source, String title, String category, List<String> tags, String audience,
                      String updated, String section, int index, String content, String fileHash) {
}
