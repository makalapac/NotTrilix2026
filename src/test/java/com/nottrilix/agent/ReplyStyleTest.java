package com.nottrilix.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Uklanja se samo opcenita zavrsna fraza; sadrzajne recenice (sljedeci korak, pitanje) ostaju. */
class ReplyStyleTest {

    private static final String CONTENT = "Bon HT-5-001 je uspješno blokiran. Blokada sama po sebi ne vraća novac.";

    @ParameterizedTest
    @ValueSource(strings = {
            "Ako imate dodatnih pitanja ili trebate daljnju pomoć, slobodno se obratite.",
            "Ako trebate nešto drugo, slobodno se javite.",
            "Ako imate problema s pristupom, slobodno mi javite!",
            "Slobodno se javite ako zatreba.",
            "Stojim Vam na raspolaganju za sva pitanja."})
    void removesGenericClosingPhrase(String closing) {
        assertThat(ReplyStyle.withoutClosingPhrase(CONTENT + " " + closing)).isEqualTo(CONTENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Povrat je moguć do 10.10.2026. Potvrđujete li povrat?",
            "Blokada ne vraća novac. Ako želite, mogu provjeriti je li povrat moguć za ovaj bon.",
            "Ne mogu izvršiti uplatu na wallet. Javite mi ako želite da otvorim prijavu.",
            "Trenutno stanje Vašeg walleta je 100 €."})
    void keepsMeaningfulLastSentence(String reply) {
        assertThat(ReplyStyle.withoutClosingPhrase(reply)).isEqualTo(reply);
    }

    @Test
    void neverReturnsEmptyReplyWhenClosingPhraseIsAllThereIs() {
        String onlyClosing = "Ako imate pitanja, slobodno se javite.";

        assertThat(ReplyStyle.withoutClosingPhrase(onlyClosing)).isEqualTo(onlyClosing);
        assertThat(ReplyStyle.withoutClosingPhrase(null)).isEmpty();
    }
}
