package com.nottrilix.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Last check of the reply before it goes to the customer. Despite the prompt, the model sometimes ends with a generic
 * phrase ("Ako imate dodatnih pitanja, slobodno se javite.", i.e. "feel free to reach out"); in chat that is noise, in
 * the voice channel a pure waste of time. Only such a closing sentence is removed, and only if content precedes it.
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
