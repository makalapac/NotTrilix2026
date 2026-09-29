package com.nottrilix.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zadnja provjera odgovora prije nego ode kupcu. Model unatoc uputi u promptu ponekad zavrsi opcenitom frazom
 * ("Ako imate dodatnih pitanja, slobodno se javite."); u chatu je to sum, a u glasovnom kanalu cisti gubitak
 * vremena. Uklanja se samo takva zavrsna recenica i samo ako prije nje postoji sadrzaj.
 */
final class ReplyStyle {

    private static final Pattern CLOSING_PHRASE = Pattern.compile(
            "(?<=[.!?])\\s+(?:Ako\\b[^.!?]*\\bslobodno\\b[^.!?]*"
                    + "|Slobodno\\s+(?:mi\\s+)?se\\s+(?:javite|obratite)[^.!?]*"
                    + "|Stojim\\s+(?:Vam\\s+)?na\\s+raspolaganju[^.!?]*)[.!?]\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private ReplyStyle() {
    }

    static String withoutClosingPhrase(String reply) {
        if (reply == null) {
            return "";
        }
        Matcher closing = CLOSING_PHRASE.matcher(reply);
        return closing.find() ? reply.substring(0, closing.start()).stripTrailing() : reply.strip();
    }
}
