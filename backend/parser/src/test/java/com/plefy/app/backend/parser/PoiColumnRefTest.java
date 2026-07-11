package com.plefy.app.backend.parser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Locks the {@link PoiSpreadsheetReader#colFromRef} column-letter math. This replaced POI's
 * per-cell {@code CellReference} (a 16x xlsx parse speedup); an off-by-one here would silently
 * misalign every column of every xlsx, so it gets its own check.
 */
public class PoiColumnRefTest {

    @Test
    public void singleLetterColumnsAreZeroBased() {
        assertEquals(0, PoiSpreadsheetReader.colFromRef("A1"));
        assertEquals(1, PoiSpreadsheetReader.colFromRef("B2"));
        assertEquals(25, PoiSpreadsheetReader.colFromRef("Z9"));
    }

    @Test
    public void doubleLetterColumnsContinuePastZ() {
        assertEquals(26, PoiSpreadsheetReader.colFromRef("AA1"));
        assertEquals(27, PoiSpreadsheetReader.colFromRef("AB1"));
        assertEquals(51, PoiSpreadsheetReader.colFromRef("AZ100"));
        assertEquals(52, PoiSpreadsheetReader.colFromRef("BA1"));
    }

    @Test
    public void multiDigitRowsDoNotAffectTheColumn() {
        assertEquals(1, PoiSpreadsheetReader.colFromRef("B12"));
        assertEquals(1, PoiSpreadsheetReader.colFromRef("B999999"));
    }
}
