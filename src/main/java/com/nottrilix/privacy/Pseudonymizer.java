package com.nottrilix.privacy;

import java.util.regex.Pattern;

/**
 * Sto se salje modelu na analizu ne mora sadrzavati tko je kupac: ime, e-mail, telefon, serijski brojevi bonova
 * i aktivacijski kodovi zamjenjuju se oznakama. Iznosi, proizvodi i tijek razgovora ostaju - to analiza treba.
 * Minimizacija podataka (GDPR cl. 5(1)(c)) u praksi: obradjivac (OpenAI) dobiva onoliko koliko mu treba.
 */
public final class Pseudonymizer {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![\\d€])(?:\\+\\d{1,3}[ /-]?)?0?9\\d[ /-]?\\d{3}[ /-]?\\d{3,4}(?!\\d)");
    private static final Pattern VOUCHER_SERIAL = Pattern.compile("\\b[A-Z]{2,4}-\\d{1,3}-\\d{3}\\b");
    private static final Pattern ACTIVATION_CODE = Pattern.compile("(?<!\\d)(?:\\d{4}-\\d{4}-\\d{4}|\\d{12})(?!\\d)");
    /** Granica rijeci koja razumije hrvatske znakove (\b ne zna da je "ć" slovo). */
    private static final String NOT_LETTER_BEFORE = "(?<![\\p{L}\\p{N}])";
    private static final String NOT_LETTER_AFTER = "(?![\\p{L}\\p{N}])";

    private Pseudonymizer() {
    }

    /**
     * @param text         tekst (poruka kupca, odgovor agenta)
     * @param customerName ime i prezime kupca kako ga sustav zna (null ako je kupac nepoznat)
     */
    public static String pseudonymize(String text, String customerName) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // prvo strukturirani podaci (e-mail sadrzi ime, pa ide prije zamjene imena)
        String result = EMAIL.matcher(text).replaceAll("[E-MAIL]");
        result = ACTIVATION_CODE.matcher(result).replaceAll("[KOD]");
        result = VOUCHER_SERIAL.matcher(result).replaceAll("[BON]");
        result = PHONE.matcher(result).replaceAll("[TELEFON]");
        if (customerName != null && !customerName.isBlank()) {
            result = namePattern(customerName.trim()).matcher(result).replaceAll("[KUPAC]");
            // i svaki dio imena zasebno (npr. samo "Ana" ili "Anić"); rijeci krace od 3 znaka preskacemo
            for (String part : customerName.trim().split("\\s+")) {
                if (part.length() >= 3) {
                    result = namePattern(part).matcher(result).replaceAll("[KUPAC]");
                }
            }
        }
        return result;
    }

    private static Pattern namePattern(String name) {
        return Pattern.compile(NOT_LETTER_BEFORE + Pattern.quote(name) + NOT_LETTER_AFTER,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
