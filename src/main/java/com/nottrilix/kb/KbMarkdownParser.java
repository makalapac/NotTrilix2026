package com.nottrilix.kb;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Knowledge base markdown document -> chunks. No external libraries: front matter is "key: value",
 * and a chunk boundary is a "## " heading (convention from docs/PHASE2.md). The preamble before the first "##" (H1 + intro) is not embedded.
 */
public final class KbMarkdownParser {

    private KbMarkdownParser() {
    }

    public static List<KbChunk> parse(String source, String markdown) {
        String text = markdown.replace("\r\n", "\n");
        Map<String, String> meta = new LinkedHashMap<>();
        String body = text;
        if (text.startsWith("---\n")) {
            int end = text.indexOf("\n---\n", 4);
            if (end > 0) {
                parseFrontMatter(text.substring(4, end), meta);
                body = text.substring(end + 5);
            }
        }
        String title = meta.getOrDefault("title", source);
        List<String> tags = parseList(meta.get("tags"));
        String fileHash = sha256(text);

        List<KbChunk> chunks = new ArrayList<>();
        String[] parts = body.split("(?m)^## ");
        int index = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int newline = part.indexOf('\n');
            String section = (newline < 0 ? part : part.substring(0, newline)).trim();
            String sectionText = (newline < 0 ? "" : part.substring(newline + 1)).trim();
            if (section.isEmpty() || sectionText.isEmpty()) {
                continue;
            }
            String content = title + " › " + section + "\n\n" + sectionText;
            chunks.add(new KbChunk(source, title, meta.getOrDefault("category", ""), tags,
                    meta.getOrDefault("audience", ""), meta.getOrDefault("updated", ""),
                    section, index++, content, fileHash));
        }
        return chunks;
    }

    private static void parseFrontMatter(String frontMatter, Map<String, String> into) {
        for (String line : frontMatter.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            into.put(key, value);
        }
    }

    /** "[a, b, c]" -> [a, b, c]; empty -> []. */
    private static List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String inner = value.trim();
        if (inner.startsWith("[") && inner.endsWith("]")) {
            inner = inner.substring(1, inner.length() - 1);
        }
        List<String> items = new ArrayList<>();
        for (String item : inner.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return List.copyOf(items);
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
