package com.plefy.app.backend.inference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.plefy.app.model.CellType;
import com.plefy.app.model.TypedCell;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.Test;

/** Unit tests for {@link CellNormalizer}: canonical typed outputs and graceful fallback. */
public class CellNormalizerTest {

    @Test
    public void emptyRawAlwaysNormalisesToEmpty() {
        TypedCell c = CellNormalizer.normalize(null, CellType.INTEGER);
        assertEquals(CellType.EMPTY, c.getType());
        assertNull(c.getTyped());

        TypedCell blank = CellNormalizer.normalize("   ", CellType.TEXT);
        assertEquals(CellType.EMPTY, blank.getType());
        assertNull(blank.getTyped());
    }

    @Test
    public void booleanCanonicalValue() {
        TypedCell c = CellNormalizer.normalize(" Yes ", CellType.BOOLEAN);
        assertEquals(CellType.BOOLEAN, c.getType());
        assertEquals(Boolean.TRUE, c.getTyped());
        assertEquals(" Yes ", c.getRaw());
    }

    @Test
    public void integerCanonicalIsLong() {
        TypedCell c = CellNormalizer.normalize("1234", CellType.INTEGER);
        assertEquals(CellType.INTEGER, c.getType());
        assertEquals(Long.valueOf(1234L), c.getTyped());
    }

    @Test
    public void decimalCanonicalIsBigDecimal() {
        TypedCell c = CellNormalizer.normalize("3.14", CellType.DECIMAL);
        assertEquals(CellType.DECIMAL, c.getType());
        assertTrue(c.getTyped() instanceof BigDecimal);
        assertEquals(0, new BigDecimal("3.14").compareTo((BigDecimal) c.getTyped()));
    }

    @Test
    public void currencyCanonicalIsBigDecimal() {
        TypedCell c = CellNormalizer.normalize("$1,234.50", CellType.CURRENCY);
        assertEquals(CellType.CURRENCY, c.getType());
        assertEquals(0, new BigDecimal("1234.50").compareTo((BigDecimal) c.getTyped()));
    }

    @Test
    public void parenthesesCurrencyIsNegative() {
        TypedCell c = CellNormalizer.normalize("(500)", CellType.CURRENCY);
        assertEquals(CellType.CURRENCY, c.getType());
        assertEquals(0, new BigDecimal("-500").compareTo((BigDecimal) c.getTyped()));
    }

    @Test
    public void plainNumberInCurrencyColumnStillParses() {
        // A bare number in a currency-typed column is treated as a valid amount.
        TypedCell c = CellNormalizer.normalize("42", CellType.CURRENCY);
        assertEquals(CellType.CURRENCY, c.getType());
        assertEquals(0, new BigDecimal("42").compareTo((BigDecimal) c.getTyped()));
    }

    @Test
    public void dateCanonicalIsEpochMillisUtc() {
        TypedCell c = CellNormalizer.normalize("2024-03-15", CellType.DATE);
        assertEquals(CellType.DATE, c.getType());
        long expected = LocalDate.of(2024, 3, 15)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        assertEquals(Long.valueOf(expected), c.getTyped());
    }

    @Test
    public void dateTimeCanonicalIsEpochMillisUtc() {
        TypedCell c = CellNormalizer.normalize("1970-01-01T00:00:00", CellType.DATETIME);
        assertEquals(CellType.DATETIME, c.getType());
        assertEquals(Long.valueOf(0L), c.getTyped());
    }

    @Test
    public void textCanonicalIsTrimmedString() {
        TypedCell c = CellNormalizer.normalize("  hello  ", CellType.TEXT);
        assertEquals(CellType.TEXT, c.getType());
        assertEquals("hello", c.getTyped());
        // Raw is preserved verbatim.
        assertEquals("  hello  ", c.getRaw());
    }

    @Test
    public void uncoercibleValueFallsBackToText() {
        // "abc" cannot be an integer -> degrade to TEXT carrying the trimmed value.
        TypedCell c = CellNormalizer.normalize(" abc ", CellType.INTEGER);
        assertEquals(CellType.TEXT, c.getType());
        assertEquals("abc", c.getTyped());
    }

    @Test
    public void nullTargetTypeDefaultsToText() {
        TypedCell c = CellNormalizer.normalize("hello", null);
        assertEquals(CellType.TEXT, c.getType());
        assertEquals("hello", c.getTyped());
    }
}
