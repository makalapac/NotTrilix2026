package com.nottrilix.service;

/**
 * Voucher serial numbers are stored as "STM-25-003", but a customer rarely says or types the hyphens: speech-to-text
 * gives "STM25003", a chat message "stm 25 003". Lookups therefore compare the compact form (letters and digits only,
 * upper case) and the stored value is the one that goes back to the customer.
 */
public final class VoucherSerial {

    private VoucherSerial() {
    }

    /** "stm 25-003" -> "STM25003"; null or blank -> "". */
    public static String compact(String serial) {
        if (serial == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(serial.length());
        for (int i = 0; i < serial.length(); i++) {
            char c = serial.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }
}
