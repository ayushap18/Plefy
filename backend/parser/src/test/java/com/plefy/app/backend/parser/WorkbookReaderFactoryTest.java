package com.plefy.app.backend.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Offline unit tests for {@link WorkbookReaderFactory}.
 *
 * <p>These verify reader <em>routing</em> only: which {@link SheetReader} implementation the
 * factory selects for a given file name or set of leading bytes. They never call
 * {@link SheetReader#read} on the POI path, so no Apache POI classes are initialised and no
 * binary spreadsheet fixtures are required. Everything runs on a plain JVM with no network.
 */
public class WorkbookReaderFactoryTest {

    // ---- ZIP local-file header ("PK\x03\x04"), used by .xlsx/.xlsm ----
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    // ---- OLE2 compound-file signature, used by legacy .xls ----
    private static final byte[] OLE2_MAGIC = {
            (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1
    };

    private static InputStream stream(byte[] bytes) {
        // ByteArrayInputStream supports mark/reset, which forContent requires.
        return new ByteArrayInputStream(bytes);
    }

    private static InputStream text(String s) {
        return stream(s.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------
    // forName(String): extension-based routing
    // ------------------------------------------------------------------

    @Test
    public void forNameRoutesSpreadsheetExtensionsToPoi() {
        assertTrue(WorkbookReaderFactory.forName("report.xlsx") instanceof PoiSpreadsheetReader);
        assertTrue(WorkbookReaderFactory.forName("macro.xlsm") instanceof PoiSpreadsheetReader);
        assertTrue(WorkbookReaderFactory.forName("legacy.xls") instanceof PoiSpreadsheetReader);
        // Case-insensitive matching.
        assertTrue(WorkbookReaderFactory.forName("REPORT.XLSX") instanceof PoiSpreadsheetReader);
    }

    @Test
    public void forNameRoutesDelimitedExtensionsToCsv() {
        assertTrue(WorkbookReaderFactory.forName("data.csv") instanceof CsvSheetReader);
        assertTrue(WorkbookReaderFactory.forName("data.tsv") instanceof CsvSheetReader);
        assertTrue(WorkbookReaderFactory.forName("Data.CSV") instanceof CsvSheetReader);
    }

    @Test
    public void forNameReturnsNullForUnknownOrNullName() {
        assertNull(WorkbookReaderFactory.forName("notes.txt"));
        assertNull(WorkbookReaderFactory.forName("archive.zip"));
        assertNull(WorkbookReaderFactory.forName((String) null));
        assertNull(WorkbookReaderFactory.forName("noextension"));
    }

    // ------------------------------------------------------------------
    // forContent(InputStream): magic-byte sniffing
    // ------------------------------------------------------------------

    @Test
    public void forContentRoutesZipMagicToPoi() throws IOException {
        InputStream in = stream(ZIP_MAGIC);
        assertTrue(WorkbookReaderFactory.forContent(in) instanceof PoiSpreadsheetReader);
    }

    @Test
    public void forContentRoutesOle2MagicToPoi() throws IOException {
        InputStream in = stream(OLE2_MAGIC);
        assertTrue(WorkbookReaderFactory.forContent(in) instanceof PoiSpreadsheetReader);
    }

    @Test
    public void forContentRoutesPlainTextToCsv() throws IOException {
        InputStream in = text("a,b,c\n1,2,3\n");
        assertTrue(WorkbookReaderFactory.forContent(in) instanceof CsvSheetReader);
    }

    @Test
    public void forContentTreatsPartialMagicAsCsv() throws IOException {
        // Bytes that merely start like a signature but are too short / do not fully match.
        InputStream in = stream(new byte[] {0x50, 0x4B}); // "PK" only
        assertTrue(WorkbookReaderFactory.forContent(in) instanceof CsvSheetReader);
    }

    @Test
    public void forContentDoesNotConsumeTheStream() throws IOException {
        // Sniffing must mark/reset, leaving the bytes intact for the returned reader.
        byte[] bytes = "hello,world\n".getBytes(StandardCharsets.UTF_8);
        InputStream in = stream(bytes);
        WorkbookReaderFactory.forContent(in);
        byte[] after = new byte[bytes.length];
        int read = in.read(after);
        assertEquals(bytes.length, read);
        for (int i = 0; i < bytes.length; i++) {
            assertEquals(bytes[i], after[i]);
        }
    }

    @Test
    public void forContentRejectsNullStream() {
        try {
            WorkbookReaderFactory.forContent(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        } catch (IOException e) {
            fail("expected IllegalArgumentException, not IOException");
        }
    }

    @Test
    public void forContentRejectsStreamWithoutMarkSupport() {
        InputStream noMark = new InputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public boolean markSupported() {
                return false;
            }
        };
        assertFalse(noMark.markSupported());
        try {
            WorkbookReaderFactory.forContent(noMark);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        } catch (IOException e) {
            fail("expected IllegalArgumentException, not IOException");
        }
    }

    // ------------------------------------------------------------------
    // forName(String, InputStream): name first, then content fallback
    // ------------------------------------------------------------------

    @Test
    public void forNameWithContentPrefersRecognisedExtensionOverBytes() throws IOException {
        // Name says CSV even though the bytes look like a ZIP/xlsx: the name wins.
        InputStream in = stream(ZIP_MAGIC);
        assertTrue(WorkbookReaderFactory.forName("data.csv", in) instanceof CsvSheetReader);
    }

    @Test
    public void forNameWithContentFallsBackToMagicWhenNameUnknown() throws IOException {
        InputStream zip = stream(ZIP_MAGIC);
        assertTrue(WorkbookReaderFactory.forName("mystery.bin", zip) instanceof PoiSpreadsheetReader);

        InputStream csv = text("x,y\n");
        assertTrue(WorkbookReaderFactory.forName("mystery.bin", csv) instanceof CsvSheetReader);
    }

    @Test
    public void forNameWithContentFallsBackWhenNameIsNull() throws IOException {
        InputStream ole2 = stream(OLE2_MAGIC);
        assertTrue(WorkbookReaderFactory.forName(null, ole2) instanceof PoiSpreadsheetReader);
    }
}
