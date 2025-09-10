package com.tonyyuan.paymentappbackend.util;

/**
 * Utility class for working with card numbers.
 *
 * Features:
 *  - Luhn check (validates card numbers).
 *  - Brand detection based on regex (Visa, Amex, MasterCard, Discover).
 *  - Masking PANs for safe display.
 *
 * ⚠️ Notes:
 *  - The brand detection uses simplified regexes and may not cover
 *    all BIN ranges in production.
 *  - Masking is fixed to always show last 4 digits with '*' for the rest.
 */
public class CardUtils {

    /**
     * Validates a card number using the Luhn algorithm.
     *
     * @param digits String of numeric digits (PAN).
     * @return true if valid according to Luhn checksum, false otherwise.
     */
    public static boolean luhn(String digits) {
        int sum = 0;
        boolean dbl = false;

        // Process digits right-to-left
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    /**
     * Detects the card brand from the PAN using regex rules.
     *
     * Supports:
     *  - VISA       (starting with 4, 13–19 digits)
     *  - AMEX       (starting with 34/37, 15 digits)
     *  - MASTERCARD (51–55 or 2221–2720, 16 digits)
     *  - DISCOVER   (6011 or 65xx, 16 digits)
     *  - UNKNOWN    (fallback if none match)
     *
     * @param pan Card number as digits string.
     * @return Detected brand as string.
     */
    public static String brand(String pan) {
        if (pan.matches("^4\\d{12,18}$")) return "VISA";
        if (pan.matches("^3[47]\\d{13}$")) return "AMEX";
        if (pan.matches("^(5[1-5]\\d{14}|(2221|22[3-9]\\d|2[3-6]\\d{2}|27[01]\\d|2720)\\d{12})$")) return "MASTERCARD";
        if (pan.matches("^6(?:011|5\\d{2})\\d{12}$")) return "DISCOVER";
        return "UNKNOWN";
    }

    /**
     * Masks a card number for safe display.
     *
     * Example:
     *  - Input:  4111111111111111
     *  - Output: ************1111
     *
     * @param pan Raw card number.
     * @return Masked PAN string (always shows last 4 digits).
     */
    public static String masked(String pan) {
        int n = pan.length();
        String last4 = n >= 4 ? pan.substring(n - 4) : pan;
        return "************" + last4;
    }
}
