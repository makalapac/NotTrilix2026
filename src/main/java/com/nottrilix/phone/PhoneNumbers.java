package com.nottrilix.phone;

/**
 * Phone numbers come in many spellings ("+385 91 111 2222", "385911112222", "091 111 2222"); matching uses digits only,
 * in international form without the plus. Logs and the audit trail get a masked form (personal data).
 */
public final class PhoneNumbers {

    private static final String CROATIA = "385";

    private PhoneNumbers() {
    }

    /** "+385 91 111 2222" -> "385911112222"; "091 111 2222" -> "385911112222"; "0038591..." -> "38591...". */
    public static String digits(String number) {
        if (number == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(number.length());
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        String digits = sb.toString();
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        } else if (digits.startsWith("0") && digits.length() >= 9) {
            digits = CROATIA + digits.substring(1);
        }
        return digits;
    }

    /** "385911112222" -> "385 91 *** 222": enough to recognise, not enough to dial. */
    public static String mask(String number) {
        String digits = digits(number);
        if (digits.length() < 7) {
            return digits.isEmpty() ? "(bez broja)" : "***";
        }
        return digits.substring(0, 5) + "***" + digits.substring(digits.length() - 3);
    }
}
