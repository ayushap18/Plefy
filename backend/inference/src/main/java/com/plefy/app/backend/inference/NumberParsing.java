package com.plefy.app.backend.inference;

import java.math.BigDecimal;

/**
 * Pure-JVM helpers for detecting and parsing numeric raw string cells.
 *
 * <p>Three numeric flavours are recognised:
 * <ul>
 *   <li><b>Integer</b> &mdash; an optionally signed whole number (parsed into {@link Long}).</li>
 *   <li><b>Decimal</b> &mdash; an optionally signed number with a fractional part
 *       (parsed into {@link BigDecimal} to preserve precision).</li>
 *   <li><b>Currency</b> &mdash; a number decorated with a currency symbol, thousands
 *       separators and/or accounting-style parentheses to denote a negative value.</li>
 * </ul>
 *
 * <p>All parse methods return {@code null} when the input is {@code null}, blank or not
 * parseable as the requested type, so callers can treat {@code null} as "not this type".
 * This class holds no state and cannot be instantiated.
 */
public final class NumberParsing {

    private NumberParsing() {
        // static-only utility
    }

    /**
     * Parses a raw string as a signed integer.
     *
     * <p>Leading/trailing whitespace and a single leading {@code +} sign are tolerated.
     * Thousands separators are <em>not</em> accepted here (use {@link #parseCurrency} for
     * grouped numbers) so that plain-text identifiers such as {@code "1,000"} are not
     * silently coerced.
     *
     * @param raw the raw cell text (may be {@code null})
     * @return the parsed value, or {@code null} if not a plain integer
     */
    public static Long parseLong(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        // Allow a leading '+' which Long.parseLong rejects on some inputs but accepts here.
        if (!isPlainInteger(s)) {
            return null;
        }
        try {
            return Long.valueOf(s.charAt(0) == '+' ? s.substring(1) : s);
        } catch (NumberFormatException ex) {
            // Overflows a long but is still numerically an integer: fall back to BigDecimal
            // is intentionally not done here since the contract is Long-or-null.
            return null;
        }
    }

    /**
     * Parses a raw string as a decimal number.
     *
     * <p>Both integer-looking and fractional inputs are accepted; the result is always a
     * {@link BigDecimal} so no precision is lost. Scientific notation (e.g. {@code 1.2e3})
     * is accepted because {@link BigDecimal} understands it.
     *
     * @param raw the raw cell text (may be {@code null})
     * @return the parsed value, or {@code null} if not a decimal number
     */
    public static BigDecimal parseDecimal(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (!isPlainNumber(s)) {
            return null;
        }
        try {
            return new BigDecimal(s.charAt(0) == '+' ? s.substring(1) : s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Parses a raw string as a currency amount.
     *
     * <p>Recognises: a leading or trailing currency symbol (dollar, pound, euro, yen or
     * rupee), grouped
     * thousands separators ({@code 1,234,567.89}), a percent-free numeric body, and
     * accounting-style parentheses ({@code (1,234.50)}) which denote a negative value.
     * A leading {@code -} sign is also honoured.
     *
     * @param raw the raw cell text (may be {@code null})
     * @return the parsed amount, or {@code null} if it is not a currency-formatted number
     */
    public static BigDecimal parseCurrency(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }

        boolean negative = false;
        boolean hadParens = false;

        // Accounting parentheses -> negative. This is a genuine currency signal.
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true;
            hadParens = true;
            s = s.substring(1, s.length() - 1).trim();
        }

        // A leading minus sign also means negative.
        if (s.startsWith("-")) {
            negative = !negative;
            s = s.substring(1).trim();
        } else if (s.startsWith("+")) {
            s = s.substring(1).trim();
        }

        // Strip a single leading or trailing currency symbol.
        boolean hadSymbol = false;
        if (!s.isEmpty() && isCurrencySymbol(s.charAt(0))) {
            hadSymbol = true;
            s = s.substring(1).trim();
        } else if (!s.isEmpty() && isCurrencySymbol(s.charAt(s.length() - 1))) {
            hadSymbol = true;
            s = s.substring(0, s.length() - 1).trim();
        }

        if (s.isEmpty()) {
            return null;
        }

        // Remove thousands separators. Track whether any were present so that a bare
        // integer without a symbol is not misclassified as currency by this method.
        boolean hadGrouping = s.indexOf(',') >= 0;
        String body = s.replace(",", "");

        if (!isPlainNumber(body)) {
            return null;
        }

        // Require some currency signal: a symbol, grouping or accounting parentheses.
        // A plain leading '-' sign is NOT a currency signal (e.g. "-0.5" is a DECIMAL).
        // Otherwise this is just a plain number that the decimal/integer parsers cover.
        if (!hadSymbol && !hadGrouping && !hadParens) {
            // Still parseable, but the caller asked specifically for currency; a bare
            // "42" is not currency. Return null so classification stays precise.
            return null;
        }

        try {
            BigDecimal value = new BigDecimal(body);
            return negative ? value.negate() : value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** @return {@code true} if {@code raw} parses as a plain integer. */
    public static boolean isInteger(String raw) {
        return parseLong(raw) != null;
    }

    /** @return {@code true} if {@code raw} parses as a decimal (non-integer) number. */
    public static boolean isDecimal(String raw) {
        BigDecimal d = parseDecimal(raw);
        // A value is "decimal" for classification purposes when it parses as a number but
        // not as a plain integer (integers get their own, higher-priority classification).
        return d != null && !isInteger(raw);
    }

    /** @return {@code true} if {@code raw} parses as a currency amount. */
    public static boolean isCurrency(String raw) {
        return parseCurrency(raw) != null;
    }

    private static boolean isCurrencySymbol(char c) {
        // Unicode escapes keep this file ASCII-safe regardless of javac's default encoding.
        return c == '$'
                || c == '\u00A3'   // pound sign
                || c == '\u20AC'   // euro sign
                || c == '\u00A5'   // yen sign
                || c == '\u20B9';  // indian rupee sign
    }

    /** Matches an optionally signed run of digits with nothing else. */
    private static boolean isPlainInteger(String s) {
        int i = 0;
        int n = s.length();
        if (n == 0) {
            return false;
        }
        if (s.charAt(0) == '+' || s.charAt(0) == '-') {
            i = 1;
        }
        if (i == n) {
            return false;
        }
        for (; i < n; i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Matches an optionally signed decimal number, with optional fractional part and
     * optional scientific-notation exponent. Does not accept grouping separators.
     */
    private static boolean isPlainNumber(String s) {
        int n = s.length();
        if (n == 0) {
            return false;
        }
        int i = 0;
        if (s.charAt(0) == '+' || s.charAt(0) == '-') {
            i = 1;
        }
        boolean sawDigit = false;
        boolean sawDot = false;
        boolean sawExp = false;
        for (; i < n; i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                sawDigit = true;
            } else if (c == '.' && !sawDot && !sawExp) {
                sawDot = true;
            } else if ((c == 'e' || c == 'E') && sawDigit && !sawExp) {
                sawExp = true;
                // Optional sign directly after the exponent marker.
                if (i + 1 < n && (s.charAt(i + 1) == '+' || s.charAt(i + 1) == '-')) {
                    i++;
                }
                sawDigit = false; // require at least one digit in the exponent
            } else {
                return false;
            }
        }
        return sawDigit;
    }
}
