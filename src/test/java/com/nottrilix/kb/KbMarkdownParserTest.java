package com.nottrilix.kb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Front matter -> metapodaci, "##" odjeljci -> odlomci, preambula se ne embedda, hash stabilan. */
class KbMarkdownParserTest {

    private static final String DOC = """
            ---
            title: Povrat novca, reklamacije i prijave
            category: podrska
            tags: [povrat, refund, 14 dana]
            audience: agent
            updated: 2026-09-22
            ---

            # Povrat novca, reklamacije i prijave

            Uvodna rečenica koja se ne embedda.

            ## Pravila povrata novca

            Povrat je moguć unutar 14 dana ako bon nije iskorišten.

            ## Goodwill uplata na wallet

            Agent odobrava do 10 € po slučaju.
            Iznad 10 € odobrava voditelj podrške.
            """;

    @Test
    void splitsSectionsAndKeepsFrontMatterAsMetadata() {
        List<KbChunk> chunks = KbMarkdownParser.parse("05-povrat.md", DOC);

        assertThat(chunks).hasSize(2);
        KbChunk first = chunks.getFirst();
        assertThat(first.source()).isEqualTo("05-povrat.md");
        assertThat(first.title()).isEqualTo("Povrat novca, reklamacije i prijave");
        assertThat(first.category()).isEqualTo("podrska");
        assertThat(first.tags()).containsExactly("povrat", "refund", "14 dana");
        assertThat(first.audience()).isEqualTo("agent");
        assertThat(first.section()).isEqualTo("Pravila povrata novca");
        assertThat(first.index()).isZero();
        assertThat(first.content()).startsWith("Povrat novca, reklamacije i prijave › Pravila povrata novca\n\n")
                .contains("unutar 14 dana")
                .doesNotContain("Uvodna rečenica");

        KbChunk second = chunks.get(1);
        assertThat(second.section()).isEqualTo("Goodwill uplata na wallet");
        assertThat(second.index()).isEqualTo(1);
        assertThat(second.content()).contains("Agent odobrava do 10 € po slučaju.\nIznad 10 € odobrava voditelj podrške.");
    }

    @Test
    void fileHashIsStableAndChangesWithContent() {
        List<KbChunk> a = KbMarkdownParser.parse("x.md", DOC);
        List<KbChunk> b = KbMarkdownParser.parse("x.md", DOC);
        List<KbChunk> changed = KbMarkdownParser.parse("x.md", DOC.replace("14 dana", "30 dana"));

        assertThat(a.getFirst().fileHash()).hasSize(16).isEqualTo(b.getFirst().fileHash());
        assertThat(changed.getFirst().fileHash()).isNotEqualTo(a.getFirst().fileHash());
        assertThat(a).allSatisfy(chunk -> assertThat(chunk.fileHash()).isEqualTo(a.getFirst().fileHash()));
    }

    @Test
    void documentWithoutFrontMatterOrSectionsIsHandled() {
        assertThat(KbMarkdownParser.parse("plain.md", "# Samo naslov\n\nBez odjeljaka.")).isEmpty();

        List<KbChunk> chunks = KbMarkdownParser.parse("plain.md", "## Jedini odjeljak\ntekst\r\n");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().title()).isEqualTo("plain.md");
        assertThat(chunks.getFirst().tags()).isEmpty();
        assertThat(chunks.getFirst().content()).isEqualTo("plain.md › Jedini odjeljak\n\ntekst");
    }
}
