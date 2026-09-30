package com.nottrilix.privacy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The conversation goes to the model for analysis without the customer's identity; amounts, products and flow remain. */
class PseudonymizerTest {

    @Test
    void replacesNameEmailSerialCodeAndPhone() {
        String text = "Ja sam Ana Anić (ana.anic@example.com, 091 234 5678). Bon STM-25-001 s kodom 1234-5678-9012 "
                + "ne radi, a kod 123456789012 sam vec probala. Anić je prezime.";

        String result = Pseudonymizer.pseudonymize(text, "Ana Anić");

        assertThat(result).doesNotContain("Ana", "Anić", "ana.anic", "STM-25-001", "1234-5678-9012", "123456789012",
                "091 234 5678");
        assertThat(result).contains("[KUPAC]", "[E-MAIL]", "[BON]", "[KOD]", "[TELEFON]");
    }

    @Test
    void keepsAmountsProductsAndMeaning() {
        String result = Pseudonymizer.pseudonymize(
                "Kupnja HT bona od 10 € čeka potvrdu; stanje walleta 25 € -> 15 €. Rok povrata je 14 dana.", "Marko Marić");

        assertThat(result).isEqualTo(
                "Kupnja HT bona od 10 € čeka potvrdu; stanje walleta 25 € -> 15 €. Rok povrata je 14 dana.");
    }

    @Test
    void worksWithoutKnownCustomer() {
        assertThat(Pseudonymizer.pseudonymize("Pišite na info@nottrilix.hr", null)).isEqualTo("Pišite na [E-MAIL]");
        assertThat(Pseudonymizer.pseudonymize(null, "Ana Anić")).isEmpty();
    }
}
