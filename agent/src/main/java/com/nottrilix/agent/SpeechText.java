package com.nottrilix.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a reply into text that reads well aloud. ElevenLabs offers no text normalization for Croatian, so the few
 * things the agent writes as symbols are rewritten here, deterministically: euro amounts become words
 * ("10 €" -> "deset eura", "12,50 €" -> "dvanaest eura i pedeset centi"), "->" becomes "na", "%" becomes "posto" and
 * hyphens inside identifiers such as serial numbers become pauses. A long reply is cut at a word boundary
 * (the chat keeps the full text).
 */
public final class SpeechText {

    private static final Pattern AMOUNT = Pattern.compile("(?<![\\d,.])(\\d{1,4})(?:[,.](\\d{1,2}))?\\s?(?:€|EUR\\b|eur\\b)");
    private static final Pattern ARROW = Pattern.compile("\\s*->\\s*");
    private static final Pattern PERCENT = Pattern.compile("(?<!\\d)(\\d{1,3})\\s?%");
    private static final Pattern CODE_HYPHEN = Pattern.compile("(?<=[A-Za-z0-9])-(?=[A-Za-z0-9])");
    private static final Pattern TRAILING_WORD = Pattern.compile("\\s+\\S*$");

    private static final String[] ONES = {"nula", "jedan", "dva", "tri", "četiri", "pet", "šest", "sedam", "osam",
            "devet", "deset", "jedanaest", "dvanaest", "trinaest", "četrnaest", "petnaest", "šesnaest", "sedamnaest",
            "osamnaest", "devetnaest"};
    private static final String[] TENS = {"", "", "dvadeset", "trideset", "četrdeset", "pedeset", "šezdeset",
            "sedamdeset", "osamdeset", "devedeset"};
    private static final String[] HUNDREDS = {"", "sto", "dvjesto", "tristo", "četiristo", "petsto", "šesto",
            "sedamsto", "osamsto", "devetsto"};

    private SpeechText() {
    }

    /** Reply text ready for synthesis: amounts and symbols as words, cut to {@code maxChars} at a word boundary. */
    public static String forSpeech(String text, int maxChars) {
        String spoken = normalize(text == null ? "" : text);
        if (spoken.length() <= maxChars) {
            return spoken;
        }
        return TRAILING_WORD.matcher(spoken.substring(0, maxChars)).replaceAll("") + "…";
    }

    /** Symbols the model writes for the eye, rewritten for the ear. */
    static String normalize(String text) {
        Matcher m = AMOUNT.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int euros = Integer.parseInt(m.group(1));
            String fraction = m.group(2);
            int cents = fraction == null ? 0 : Integer.parseInt(fraction.length() == 1 ? fraction + "0" : fraction);
            m.appendReplacement(sb, Matcher.quoteReplacement(amount(euros, cents)));
        }
        m.appendTail(sb);
        String result = ARROW.matcher(sb).replaceAll(" na ");
        Matcher percent = PERCENT.matcher(result);
        StringBuilder withPercent = new StringBuilder();
        while (percent.find()) {
            percent.appendReplacement(withPercent, Matcher.quoteReplacement(words(Integer.parseInt(percent.group(1))) + " posto"));
        }
        percent.appendTail(withPercent);
        return CODE_HYPHEN.matcher(withPercent).replaceAll(" ");
    }

    /** "10 €" -> "deset eura", "1 €" -> "jedan euro", "12,50 €" -> "dvanaest eura i pedeset centi". */
    static String amount(int euros, int cents) {
        String result = words(euros) + " " + (euros % 10 == 1 && euros % 100 != 11 ? "euro" : "eura");
        if (cents > 0) {
            result += " i " + words(cents) + " " + centWord(cents);
        }
        return result;
    }

    /** Croatian cardinal number as words, 0-9999. */
    static String words(int n) {
        if (n < 20) {
            return ONES[n];
        }
        if (n < 100) {
            return TENS[n / 10] + (n % 10 == 0 ? "" : " " + ONES[n % 10]);
        }
        if (n < 1000) {
            return HUNDREDS[n / 100] + (n % 100 == 0 ? "" : " " + words(n % 100));
        }
        int thousands = n / 1000;
        String head = thousands == 1 ? "tisuću" : words(thousands) + (thousands < 5 ? " tisuće" : " tisuća");
        return head + (n % 1000 == 0 ? "" : " " + words(n % 1000));
    }

    private static String centWord(int n) {
        int last = n % 10;
        int lastTwo = n % 100;
        if (last == 1 && lastTwo != 11) {
            return "cent";
        }
        if (last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14)) {
            return "centa";
        }
        return "centi";
    }
}
