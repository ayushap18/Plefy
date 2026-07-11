package com.plefy.app.backend.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.plefy.app.model.RawRow;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Offline unit tests for {@link CsvSheetReader}. These exercise the zero-dependency parsing
 * path only, so they run on a plain JVM with no POI and no network.
 */
public class CsvSheetReaderTest {

    private final CsvSheetReader reader = new CsvSheetReader();

    private List<RawRow> parse(String csv) throws Exception {
        InputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        return reader.read(in, null);
    }

    @Test
    public void supportsCsvAndTsvOnly() {
        assertTrue(reader.supports("a.csv"));
        assertTrue(reader.supports("A.TSV"));
        assertTrue(!reader.supports("a.xlsx"));
        assertTrue(!reader.supports(null));
    }

    @Test
    public void parsesSimpleRows() throws Exception {
        List<RawRow> rows = parse("a,b,c\n1,2,3\n");
        assertEquals(2, rows.size());
        assertEquals(0, rows.get(0).getIndex());
        assertEquals("a", rows.get(0).getCells().get(0));
        assertEquals("3", rows.get(1).getCells().get(2));
    }

    @Test
    public void handlesQuotedCommasNewlinesAndEscapedQuotes() throws Exception {
        List<RawRow> rows = parse("\"hello, world\",\"line1\nline2\",\"she said \"\"hi\"\"\"\n");
        assertEquals(1, rows.size());
        List<String> cells = rows.get(0).getCells();
        assertEquals("hello, world", cells.get(0));
        assertEquals("line1\nline2", cells.get(1));
        assertEquals("she said \"hi\"", cells.get(2));
    }

    @Test
    public void autoDetectsTabDelimiter() throws Exception {
        List<RawRow> rows = parse("a\tb\tc\n1\t2\t3\n");
        assertEquals(3, rows.get(0).getCells().size());
        assertEquals("b", rows.get(0).getCells().get(1));
    }

    @Test
    public void preservesEmptyFields() throws Exception {
        List<RawRow> rows = parse("a,,c\n");
        assertEquals("", rows.get(0).getCells().get(1));
    }

    @Test
    public void keepsTrailingRowWithoutNewline() throws Exception {
        List<RawRow> rows = parse("x,y");
        assertEquals(1, rows.size());
        assertEquals("y", rows.get(0).getCells().get(1));
    }

    @Test
    public void stripsUtf8Bom() throws Exception {
        List<RawRow> rows = parse("﻿a,b\n");
        assertEquals("a", rows.get(0).getCells().get(0));
    }

    @Test
    public void handlesCrlfLineEndings() throws Exception {
        List<RawRow> rows = parse("a,b\r\nc,d\r\n");
        assertEquals(2, rows.size());
        assertEquals("d", rows.get(1).getCells().get(1));
    }

    @Test
    public void preservesBlankLinesAsSingleEmptyCellRows() throws Exception {
        // A blank physical line between records surfaces as its own row containing one
        // empty cell; column-count normalisation is left to downstream modules.
        List<RawRow> rows = parse("a,b\n\nc,d\n");
        assertEquals(3, rows.size());
        assertEquals(1, rows.get(1).getCells().size());
        assertEquals("", rows.get(1).getCells().get(0));
        assertEquals("c", rows.get(2).getCells().get(0));
    }

    @Test
    public void keepsInteriorBlankLinesInsideQuotedField() throws Exception {
        // An empty line embedded within a quoted value is part of the value, not a row break.
        List<RawRow> rows = parse("\"a\n\nb\",c\n");
        assertEquals(1, rows.size());
        assertEquals("a\n\nb", rows.get(0).getCells().get(0));
        assertEquals("c", rows.get(0).getCells().get(1));
    }

    @Test
    public void tabDelimitedQuotedFieldMayContainTabsAndCommas() throws Exception {
        // Tab is auto-detected as the delimiter; a quoted cell may then hold both tabs and
        // commas without being split.
        List<RawRow> rows = parse("x\ty\tz\n\"has\ttab, and comma\"\tb\tc\n");
        assertEquals(2, rows.size());
        assertEquals(3, rows.get(1).getCells().size());
        assertEquals("has\ttab, and comma", rows.get(1).getCells().get(0));
        assertEquals("b", rows.get(1).getCells().get(1));
    }

    @Test
    public void emptyInputProducesNoRows() throws Exception {
        List<RawRow> rows = parse("");
        assertTrue(rows.isEmpty());
    }

    @Test
    public void rowIndicesAreSequentialFromZero() throws Exception {
        List<RawRow> rows = parse("r0\nr1\nr2\n");
        assertEquals(3, rows.size());
        assertEquals(0, rows.get(0).getIndex());
        assertEquals(1, rows.get(1).getIndex());
        assertEquals(2, rows.get(2).getIndex());
    }

    @Test
    public void detectDelimiterIgnoresSeparatorsInsideQuotes() {
        // Two commas are quoted, one tab is unquoted -> tab wins.
        assertEquals('\t', CsvSheetReader.detectDelimiter("\"a,b,c\"\td\n"));
        // Bare comma line -> comma.
        assertEquals(',', CsvSheetReader.detectDelimiter("a,b,c\n"));
        // Neither present -> defaults to comma.
        assertEquals(',', CsvSheetReader.detectDelimiter("single\n"));
    }
}
