package com.plefy.app.backend.inference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.plefy.app.model.RawRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/** Unit tests for {@link HeaderDetector}: detection heuristic and name generation. */
public class HeaderDetectorTest {

    private static RawRow row(int index, String... cells) {
        return new RawRow(index, Arrays.asList(cells));
    }

    @Test
    public void detectsTextHeaderOverTypedData() {
        List<RawRow> rows = Arrays.asList(
                row(0, "Name", "Age", "Active"),
                row(1, "Alice", "30", "true"),
                row(2, "Bob", "25", "false"),
                row(3, "Carol", "41", "true"));

        HeaderDetector.Result r = HeaderDetector.detect(rows);
        assertTrue(r.hasHeader());
        assertEquals(Arrays.asList("Name", "Age", "Active"), r.getColumnNames());
    }

    @Test
    public void noHeaderWhenAllRowsHomogeneousNumeric() {
        List<RawRow> rows = Arrays.asList(
                row(0, "1", "2", "3"),
                row(1, "4", "5", "6"),
                row(2, "7", "8", "9"));

        HeaderDetector.Result r = HeaderDetector.detect(rows);
        assertFalse(r.hasHeader());
        // Fallback spreadsheet-style names.
        assertEquals(Arrays.asList("Column A", "Column B", "Column C"), r.getColumnNames());
    }

    @Test
    public void headerWithBlankAndDuplicateCellsGetsFallbackAndDisambiguation() {
        List<RawRow> rows = Arrays.asList(
                row(0, "Name", "", "Name"),
                row(1, "Alice", "30", "40"),
                row(2, "Bob", "25", "35"),
                row(3, "Carol", "22", "31"));

        HeaderDetector.Result r = HeaderDetector.detect(rows);
        assertTrue(r.hasHeader());
        // Blank -> generated "Column B"; duplicate "Name" -> "Name (2)".
        assertEquals(Arrays.asList("Name", "Column B", "Name (2)"), r.getColumnNames());
    }

    @Test
    public void singleRowCannotBeHeader() {
        // With no body rows there is nothing to compare against.
        List<RawRow> rows = Collections.singletonList(row(0, "A", "B"));
        HeaderDetector.Result r = HeaderDetector.detect(rows);
        assertFalse(r.hasHeader());
        assertEquals(Arrays.asList("Column A", "Column B"), r.getColumnNames());
    }

    @Test
    public void emptyAndNullInputsProduceNoColumns() {
        assertFalse(HeaderDetector.detect(null).hasHeader());
        assertTrue(HeaderDetector.detect(null).getColumnNames().isEmpty());

        assertFalse(HeaderDetector.detect(new ArrayList<RawRow>()).hasHeader());
        assertTrue(HeaderDetector.detect(new ArrayList<RawRow>()).getColumnNames().isEmpty());
    }

    @Test
    public void generatesSpreadsheetLetterNames() {
        assertEquals("Column A", HeaderDetector.generateName(0));
        assertEquals("Column Z", HeaderDetector.generateName(25));
        assertEquals("Column AA", HeaderDetector.generateName(26));
        assertEquals("Column AB", HeaderDetector.generateName(27));
        assertEquals("Column BA", HeaderDetector.generateName(52));
    }

    @Test
    public void columnLettersBijectiveBase26() {
        assertEquals("A", HeaderDetector.columnLetters(0));
        assertEquals("Z", HeaderDetector.columnLetters(25));
        assertEquals("AA", HeaderDetector.columnLetters(26));
        assertEquals("AZ", HeaderDetector.columnLetters(51));
    }
}
