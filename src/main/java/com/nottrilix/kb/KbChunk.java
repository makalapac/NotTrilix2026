package com.nottrilix.kb;

import java.util.List;

/**
 * One knowledge base chunk = one "##" section of a markdown document, with metadata from the front matter.
 * {@code content} = "document title > section title" + section text (this is what gets embedded and what the agent receives).
 */
public record KbChunk(String source, String title, String category, List<String> tags, String audience,
                      String updated, String section, int index, String content, String fileHash) {
}
