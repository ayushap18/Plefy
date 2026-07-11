package com.plefy.app.backend.parser;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Selects the appropriate {@link SheetReader} for a given source.
 *
 * <p>Selection is primarily driven by the file name's extension. When the name is unknown or
 * ambiguous, the factory falls back to sniffing the leading "magic" bytes of the content:
 * <ul>
 *   <li>{@code 50 4B 03 04} ("PK", a ZIP local-file header) &rarr; XLSX;</li>
 *   <li>{@code D0 CF 11 E0 A1 B1 1A E1} (the OLE2 compound-file signature) &rarr; XLS;</li>
 *   <li>anything else &rarr; treated as delimited text (CSV/TSV).</li>
 * </ul>
 *
 * <p>The reader instances returned are stateless and cheap; new ones are created per call for
 * thread-safety, but callers may also cache them freely.
 *
 * <p>Note: this class deliberately contains <em>no</em> reference to Apache POI types. It only
 * names the {@link PoiSpreadsheetReader} class, so the POI dependency stays isolated inside
 * that single implementation.
 */
public final class WorkbookReaderFactory {

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2_MAGIC = {
            (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1
    };

    private WorkbookReaderFactory() {
        // static factory; not instantiable
    }

    /**
     * Chooses a reader from the file name's extension.
     *
     * @param fileName the source file name (e.g. {@code "report.xlsx"}); may be {@code null}.
     * @return a matching {@link SheetReader}, or {@code null} if no reader recognises the
     *         extension. Callers that also have the bytes should prefer
     *         {@link #forName(String, InputStream)}.
     */
    public static SheetReader forName(String fileName) {
        SheetReader poi = new PoiSpreadsheetReader();
        if (poi.supports(fileName)) {
            return poi;
        }
        SheetReader csv = new CsvSheetReader();
        if (csv.supports(fileName)) {
            return csv;
        }
        return null;
    }

    /**
     * Chooses a reader for the given source, preferring the file-name extension and falling
     * back to content sniffing when the extension is unrecognised.
     *
     * <p>The supplied stream must support {@code mark}/{@code reset}; if it does not, it is
     * wrapped in a {@link BufferedInputStream}. In either case the returned reader must be fed
     * the <em>returned</em> stream, since sniffing consumes and then resets bytes. Use the
     * two-value pattern below:
     *
     * <pre>{@code
     * InputStream buffered = new BufferedInputStream(rawStream);
     * SheetReader reader = WorkbookReaderFactory.forContent(buffered);
     * List<RawRow> rows = reader.read(buffered, null);
     * }</pre>
     *
     * @param content a stream positioned at the start of the file; will be marked/reset.
     * @return the best-matching reader; never {@code null} (defaults to CSV).
     * @throws IOException if the leading bytes cannot be read.
     */
    public static SheetReader forContent(InputStream content) throws IOException {
        if (content == null) {
            throw new IllegalArgumentException("content stream must not be null");
        }
        if (!content.markSupported()) {
            throw new IllegalArgumentException(
                    "content stream must support mark/reset; wrap it in a BufferedInputStream");
        }
        byte[] head = peek(content, OLE2_MAGIC.length);
        if (startsWith(head, ZIP_MAGIC) || startsWith(head, OLE2_MAGIC)) {
            return new PoiSpreadsheetReader();
        }
        return new CsvSheetReader();
    }

    /**
     * Chooses a reader by name, falling back to content sniffing when the name is unknown.
     *
     * @param fileName the source file name; may be {@code null}.
     * @param content  a mark-supporting stream positioned at the start of the file. Feed the
     *                 <em>same</em> stream to the returned reader.
     * @return the best-matching reader; never {@code null}.
     * @throws IOException if content sniffing is required and the bytes cannot be read.
     */
    public static SheetReader forName(String fileName, InputStream content) throws IOException {
        SheetReader byName = forName(fileName);
        if (byName != null) {
            return byName;
        }
        return forContent(content);
    }

    /** Reads up to {@code n} leading bytes without permanently consuming them. */
    private static byte[] peek(InputStream in, int n) throws IOException {
        in.mark(n + 1);
        byte[] buf = new byte[n];
        int read = 0;
        while (read < n) {
            int r = in.read(buf, read, n - read);
            if (r == -1) {
                break;
            }
            read += r;
        }
        in.reset();
        if (read == n) {
            return buf;
        }
        byte[] trimmed = new byte[read];
        System.arraycopy(buf, 0, trimmed, 0, read);
        return trimmed;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
