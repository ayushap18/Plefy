package com.plefy.app.backend.inference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.plefy.app.model.CellType;

import org.junit.Test;

/** Unit tests for {@link CellClassifier}, exercising every {@link CellType} branch. */
public class CellClassifierTest {

    @Test
    public void classifiesEmpty() {
        assertEquals(CellType.EMPTY, CellClassifier.classify(null));
        assertEquals(CellType.EMPTY, CellClassifier.classify(""));
        assertEquals(CellType.EMPTY, CellClassifier.classify("   "));
    }

    @Test
    public void classifiesBoolean() {
        assertEquals(CellType.BOOLEAN, CellClassifier.classify("true"));
        assertEquals(CellType.BOOLEAN, CellClassifier.classify("FALSE"));
        assertEquals(CellType.BOOLEAN, CellClassifier.classify("Yes"));
        assertEquals(CellType.BOOLEAN, CellClassifier.classify(" no "));
    }

    @Test
    public void classifiesInteger() {
        assertEquals(CellType.INTEGER, CellClassifier.classify("42"));
        assertEquals(CellType.INTEGER, CellClassifier.classify("-7"));
        assertEquals(CellType.INTEGER, CellClassifier.classify("+3"));
    }

    @Test
    public void classifiesCurrency() {
        assertEquals(CellType.CURRENCY, CellClassifier.classify("$1,234.50"));
        assertEquals(CellType.CURRENCY, CellClassifier.classify("(500)"));
        // Grouping alone is enough of a currency signal.
        assertEquals(CellType.CURRENCY, CellClassifier.classify("1,000"));
    }

    @Test
    public void classifiesDecimal() {
        assertEquals(CellType.DECIMAL, CellClassifier.classify("3.14"));
        assertEquals(CellType.DECIMAL, CellClassifier.classify("-0.5"));
        assertEquals(CellType.DECIMAL, CellClassifier.classify("1.2e3"));
    }

    @Test
    public void classifiesDate() {
        assertEquals(CellType.DATE, CellClassifier.classify("2024-03-15"));
        assertEquals(CellType.DATE, CellClassifier.classify("05/06/2024"));
    }

    @Test
    public void classifiesDateTime() {
        assertEquals(CellType.DATETIME, CellClassifier.classify("2024-03-15T08:30:00"));
        assertEquals(CellType.DATETIME, CellClassifier.classify("2024-03-15 08:30"));
    }

    @Test
    public void classifiesText() {
        assertEquals(CellType.TEXT, CellClassifier.classify("hello"));
        assertEquals(CellType.TEXT, CellClassifier.classify("N/A"));
    }

    @Test
    public void integerBeatsCurrencyAndDecimal() {
        // A bare whole number is INTEGER, not CURRENCY or DECIMAL.
        assertEquals(CellType.INTEGER, CellClassifier.classify("100"));
    }

    @Test
    public void booleanHelpers() {
        assertTrue(CellClassifier.isBoolean("yes"));
        assertFalse(CellClassifier.isBoolean("maybe"));
        assertEquals(Boolean.TRUE, CellClassifier.toBoolean(" TRUE "));
        assertEquals(Boolean.FALSE, CellClassifier.toBoolean("no"));
        assertNull(CellClassifier.toBoolean("1"));
        assertNull(CellClassifier.toBoolean(null));
    }
}
