package com.plefy.app.backend.inference;

import com.plefy.app.model.CellType;

/**
 * Classifies a single raw string cell into a {@link CellType}.
 *
 * <p>Classification is deterministic and applied in priority order so that the most
 * specific interpretation wins:
 * <ol>
 *   <li>{@code null}/blank &rarr; {@link CellType#EMPTY}</li>
 *   <li>boolean-like tokens ({@code true/false/yes/no}) &rarr; {@link CellType#BOOLEAN}</li>
 *   <li>plain whole numbers &rarr; {@link CellType#INTEGER}</li>
 *   <li>currency-decorated numbers &rarr; {@link CellType#CURRENCY}</li>
 *   <li>fractional numbers &rarr; {@link CellType#DECIMAL}</li>
 *   <li>date-time values &rarr; {@link CellType#DATETIME}</li>
 *   <li>date values &rarr; {@link CellType#DATE}</li>
 *   <li>anything else &rarr; {@link CellType#TEXT}</li>
 * </ol>
 *
 * <p>The class is stateless and cannot be instantiated.
 */
public final class CellClassifier {

    private CellClassifier() {
        // static-only utility
    }

    /**
     * Classifies a single raw cell value.
     *
     * @param raw the raw cell text (may be {@code null})
     * @return the inferred {@link CellType}; never {@code null}
     */
    public static CellType classify(String raw) {
        if (raw == null) {
            return CellType.EMPTY;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return CellType.EMPTY;
        }

        if (isBoolean(s)) {
            return CellType.BOOLEAN;
        }

        // Whole numbers before currency/decimal: a bare "42" is an INTEGER.
        if (NumberParsing.isInteger(s)) {
            return CellType.INTEGER;
        }

        // Currency before decimal: "$1,234.50" carries explicit money formatting that a
        // plain decimal check would reject anyway.
        if (NumberParsing.isCurrency(s)) {
            return CellType.CURRENCY;
        }

        if (NumberParsing.isDecimal(s)) {
            return CellType.DECIMAL;
        }

        DateParsing.Result date = DateParsing.parse(s);
        if (date != null) {
            return date.hasTime() ? CellType.DATETIME : CellType.DATE;
        }

        return CellType.TEXT;
    }

    /**
     * @return {@code true} if the (already trimmed) token is a recognised boolean literal.
     */
    public static boolean isBoolean(String s) {
        return s.equalsIgnoreCase("true")
                || s.equalsIgnoreCase("false")
                || s.equalsIgnoreCase("yes")
                || s.equalsIgnoreCase("no");
    }

    /**
     * Resolves a boolean literal to its {@link Boolean} value.
     *
     * @param s a trimmed token
     * @return {@link Boolean#TRUE}/{@link Boolean#FALSE}, or {@code null} if not boolean-like
     */
    public static Boolean toBoolean(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.equalsIgnoreCase("true") || t.equalsIgnoreCase("yes")) {
            return Boolean.TRUE;
        }
        if (t.equalsIgnoreCase("false") || t.equalsIgnoreCase("no")) {
            return Boolean.FALSE;
        }
        return null;
    }
}
