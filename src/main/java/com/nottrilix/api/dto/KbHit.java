package com.nottrilix.api.dto;

/** One hit of the knowledge base semantic search: where the chunk comes from, how similar it is to the query (0..1) and the text itself. */
public record KbHit(String source, String title, String section, String category, String tags, double score,
                    String content) {
}
