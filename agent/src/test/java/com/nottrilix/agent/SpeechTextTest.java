package com.nottrilix.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Amounts and symbols are spoken as words; the chat text stays untouched (this is only the TTS input). */
class SpeechTextTest {

    @Test
    void numbersAsCroatianWords() {
        assertEquals("nula", SpeechText.words(0));
        assertEquals("petnaest", SpeechText.words(15));
        assertEquals("dvadeset pet", SpeechText.words(25));
        assertEquals("sto pedeset", SpeechText.words(150));
        assertEquals("dvjesto", SpeechText.words(200));
        assertEquals("tisuću dvjesto trideset četiri", SpeechText.words(1234));
        assertEquals("tri tisuće", SpeechText.words(3000));
        assertEquals("pet tisuća pet", SpeechText.words(5005));
    }

    @Test
    void euroAmounts() {
        assertEquals("jedan euro", SpeechText.amount(1, 0));
        assertEquals("dva eura", SpeechText.amount(2, 0));
        assertEquals("dvadeset jedan euro", SpeechText.amount(21, 0));
        assertEquals("jedanaest eura", SpeechText.amount(11, 0));
        assertEquals("dvanaest eura i pedeset centi", SpeechText.amount(12, 50));
        assertEquals("pet eura i jedan cent", SpeechText.amount(5, 1));
        assertEquals("pet eura i dva centa", SpeechText.amount(5, 2));
    }

    @Test
    void replyTextForSpeech() {
        assertEquals("Kupnja HT bona od deset eura naplatit će se s vašeg walleta, a stanje će se smanjiti s dvadeset pet eura"
                        + " na petnaest eura.",
                SpeechText.normalize("Kupnja HT bona od 10 € naplatit će se s vašeg walleta, a stanje će se smanjiti"
                        + " s 25 € na 15 €."));
        assertEquals("stanje dvadeset pet eura na petnaest eura", SpeechText.normalize("stanje 25 € -> 15 €"));
        assertEquals("Bon HT 10 001 je blokiran, naknada pet posto.", SpeechText.normalize("Bon HT-10-001 je blokiran, naknada 5 %."));
        assertEquals("Iznos od dvanaest eura i pedeset centi.", SpeechText.normalize("Iznos od 12,50 €."));
        assertEquals("Danas možete kupiti do sto pedeset eura.", SpeechText.normalize("Danas možete kupiti do 150 EUR."));
    }

    @Test
    void longReplyIsCutAtAWordBoundary() {
        String spoken = SpeechText.forSpeech("Prva rečenica je kratka. Druga rečenica je malo dulja od prve.", 40);
        assertEquals("Prva rečenica je kratka. Druga rečenica…", spoken);
    }
}
