package com.plefy.app.backend.inference;

import com.plefy.app.model.CellType;
import com.plefy.app.model.TypedCell;

import java.math.BigDecimal;

/**
 * Converts a raw string cell into a canonical {@link TypedCell} for a target {@link CellType}.
 *
 * <p>The canonical value carried by the resulting {@link TypedCell} depends on the type:
 * <ul>
 *   <li>{@link CellType#EMPTY} &rarr; {@code null}</li>
 *   <li>{@link CellType#BOOLEAN} &rarr; {@link Boolean}</li>
 *   <li>{@link CellType#INTEGER} &rarr; {@link Long}</li>
 *   <li>{@link CellType#DECIMAL}, {@link CellType#CURRENCY} &rarr; {@link java.math.BigDecimal}</li>
 *   <li>{@link CellType#DATE}, {@link CellType#DATETIME} &rarr; {@link Long} epoch-millis (UTC)</li>
 *   <li>{@link CellType#TEXT} &rarr; the trimmed {@link String}</li>
 * </ul>
 *
 * <p>If the raw value cannot be coerced to the requested type (a heterogeneous column cell),
 * the normaliser degrades gracefully to a {@link CellType#TEXT} cell carrying the original
 * text, or {@link CellType#EMPTY} when the text is blank. This keeps a column's data usable
 * even when a minority of cells do not match the inferred column type.
 */
public final class CellNormalizer {

    private CellNormalizer() {
        // static-only utility
    }

    /**
     * Normalises a raw cell against a target type.
     *
     * @param raw        the raw cell text (may be {@code null})
     * @param targetType the type the column was inferred to be
     * @return a canonical {@link TypedCell}; never {@code null}
     */
    public static TypedCell normalize(String raw, CellType targetType) {
        CellType type = (targetType == null) ? CellType.TEXT : targetType;

        if (raw == null || raw.trim().isEmpty()) {
            return new TypedCell(raw, null, CellType.EMPTY);
        }
        String s = raw.trim();

        switch (type) {
            case EMPTY:
                return new TypedCell(raw, null, CellType.EMPTY);

            case BOOLEAN: {
                Boolean b = CellClassifier.toBoolean(s);
                if (b != null) {
                    return new TypedCell(raw, b, CellType.BOOLEAN);
                }
                return fallbackText(raw, s);
            }

            case INTEGER: {
                Long v = NumberParsing.parseLong(s);
                if (v != null) {
                    return new TypedCell(raw, v, CellType.INTEGER);
                }
                return fallbackText(raw, s);
            }

            case DECIMAL: {
                BigDecimal v = NumberParsing.parseDecimal(s);
                if (v != null) {
                    return new TypedCell(raw, v, CellType.DECIMAL);
                }
                return fallbackText(raw, s);
            }

            case CURRENCY: {
                BigDecimal v = NumberParsing.parseCurrency(s);
                if (v == null) {
                    // A plain number in a currency column is still a valid amount.
                    v = NumberParsing.parseDecimal(s);
                }
                if (v != null) {
                    return new TypedCell(raw, v, CellType.CURRENCY);
                }
                return fallbackText(raw, s);
            }

            case DATE: {
                DateParsing.Result d = DateParsing.parse(s);
                if (d != null) {
                    return new TypedCell(raw, d.getEpochMillisUtc(), CellType.DATE);
                }
                return fallbackText(raw, s);
            }

            case DATETIME: {
                DateParsing.Result d = DateParsing.parse(s);
                if (d != null) {
                    return new TypedCell(raw, d.getEpochMillisUtc(), CellType.DATETIME);
                }
                return fallbackText(raw, s);
            }

            case TEXT:
            default:
                return new TypedCell(raw, s, CellType.TEXT);
        }
    }

    private static TypedCell fallbackText(String raw, String trimmed) {
        return new TypedCell(raw, trimmed, CellType.TEXT);
    }
}
