package com.plefy.app.backend.inference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.Test;

/** Unit tests for {@link NumberParsing}. Pure JVM, no external dependencies. */
public class NumberParsingTest {

    @Test
    public void parsesPlainIntegers() {
        assertEquals(Long.valueOf(42L), NumberParsing.parseLong("42"));
        assertEquals(Long.valueOf(-7L), NumberParsing.parseLong("  -7 "));
        assertEquals(Long.valueOf(3L), NumberParsing.parseLong("+3"));
    }

    @Test
    public void rejectsNonIntegers() {
        assertNull(NumberParsing.parseLong("42.0"));
        assertNull(NumberParsing.parseLong("1,000"));
        assertNull(NumberParsing.parseLong("abc"));
        assertNull(NumberParsing.parseLong(""));
        assertNull(NumberParsing.parseLong(null));
    }

    @Test
    public void parsesDecimals() {
        assertEquals(0, new BigDecimal("3.14").compareTo(NumberParsing.parseDecimal("3.14")));
        assertEquals(0, new BigDecimal("-0.5").compareTo(NumberParsing.parseDecimal("-0.5")));
        assertEquals(0, new BigDecimal("1200").compareTo(NumberParsing.parseDecimal("1.2e3")));
    }

    @Test
    public void isDecimalExcludesIntegers() {
        assertTrue(NumberParsing.isDecimal("3.14"));
        // A whole number is an integer, not a decimal, for classification purposes.
        org.junit.Assert.assertFalse(NumberParsing.isDecimal("42"));
    }

    @Test
    public void parsesCurrencyWithSymbolAndGrouping() {
        assertEquals(0, new BigDecimal("1234.50")
                .compareTo(NumberParsing.parseCurrency("$1,234.50")));
        assertEquals(0, new BigDecimal("1000")
                .compareTo(NumberParsing.parseCurrency("1,000")));
    }

    @Test
    public void parsesParenthesesAsNegative() {
        assertEquals(0, new BigDecimal("-1234.50")
                .compareTo(NumberParsing.parseCurrency("($1,234.50)")));
        assertEquals(0, new BigDecimal("-42")
                .compareTo(NumberParsing.parseCurrency("-$42")));
    }

    @Test
    public void bareNumberIsNotCurrency() {
        // Without a symbol, grouping or parentheses a plain number is not "currency".
        assertNull(NumberParsing.parseCurrency("42"));
        assertNull(NumberParsing.parseCurrency("3.14"));
    }

    @Test
    public void specCases() {
        // "1,234" -> 1234 (grouping alone signals currency in this engine).
        assertEquals(0, new BigDecimal("1234").compareTo(NumberParsing.parseCurrency("1,234")));
        // "$1,234.50" -> 1234.50
        assertEquals(0, new BigDecimal("1234.50").compareTo(NumberParsing.parseCurrency("$1,234.50")));
        // "(500)" -> -500 via accounting parentheses.
        assertEquals(0, new BigDecimal("-500").compareTo(NumberParsing.parseCurrency("(500)")));
        // "abc" -> null across every parser.
        assertNull(NumberParsing.parseCurrency("abc"));
        assertNull(NumberParsing.parseDecimal("abc"));
        assertNull(NumberParsing.parseLong("abc"));
    }

    @Test
    public void predicatesAreConsistent() {
        assertTrue(NumberParsing.isInteger("42"));
        org.junit.Assert.assertFalse(NumberParsing.isInteger("3.14"));
        assertTrue(NumberParsing.isCurrency("$1,234.50"));
        assertTrue(NumberParsing.isCurrency("(500)"));
        org.junit.Assert.assertFalse(NumberParsing.isCurrency("42"));
    }
}
