package com.nottrilix.privacy;

import java.util.regex.Pattern;

/**
 * What is sent to the model for analysis need not reveal who the customer is: name, e-mail, phone, voucher serial numbers
 * and activation codes are replaced with placeholders. Amounts, products and the flow of the conversation stay - the analysis needs them.
 * Data minimization (GDPR Art. 5(1)(c)) in practice: the processor (OpenAI) receives only as much as it needs.
 */
public final class Pseudonymizer {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![\\d€])(?:\\+\\d{1,3}[ /-]?)?0?9\\d[ /-]?\\d{3}[ /-]?\\d{3,4}(?!\\d)");
    private static final Pattern VOUCHER_SERIAL = Pattern.compile("\\b[A-Z]{2,4}-\\d{1,3}-\\d{3}\\b");
    private static final Pattern ACTIVATION_CODE = Pattern.compile("(?<!\\d)(?:\\d{4}-\\d{4}-\\d{4}|\\d{12})(?!\\d)");
    /** Word boundary that understands Croatian characters (\b does not know that "ć" is a letter). */
    private static final String NOT_LETTER_BEFORE = "(?<![\\p{L}\\p{N}])";
    private static final String NOT_LETTER_AFTER = "(?![\\p{L}\\p{N}])";

    private Pseudonymizer() {
    }

    /**
     * @param text         the text (customer message, agent reply)
     * @param customerName the customer's full name as the system knows it (null if the customer is unknown)
     */
    public static String pseudonymize(String text, String customerName) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // structured data first (an e-mail contains the name, so it goes before the name replacement)
        String result = EMAIL.matcher(text).replaceAll("[E-MAIL]");
        result = ACTIVATION_CODE.matcher(result).replaceAll("[KOD]");
        result = VOUCHER_SERIAL.matcher(result).replaceAll("[BON]");
        result = PHONE.matcher(result).replaceAll("[TELEFON]");
        if (customerName != null && !customerName.isBlank()) {
            result = namePattern(customerName.trim()).matcher(result).replaceAll("[KUPAC]");
            // and each part of the name separately (e.g. just "Ana" or "Anić"); words shorter than 3 characters are skipped
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
