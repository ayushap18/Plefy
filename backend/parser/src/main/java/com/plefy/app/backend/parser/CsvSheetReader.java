package com.plefy.app.backend.parser;

import com.plefy.app.model.RawRow;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A dependency-free reader for delimiter-separated text files ({@code .csv} / {@code .tsv}).
 *
 * <p>This reader deliberately relies only on {@code java.io} so it compiles and runs on a
 * plain JVM with no third-party libraries and no network access. It implements the common
 * subset of <a href="https://www.rfc-editor.org/rfc/rfc4180">RFC&nbsp;4180</a> quoting
 * rules:
 * <ul>
 *   <li>fields may be wrapped in double quotes;</li>
 *   <li>a quoted field may contain the delimiter, {@code CR}, {@code LF} and {@code CRLF};</li>
 *   <li>a literal double quote inside a quoted field is written as two double quotes
 *       ({@code ""}).</li>
 * </ul>
 *
 * <p>The delimiter (comma vs. tab) is auto-detected from the first line: whichever of the
 * two occurs more often (outside quotes) wins, defaulting to comma when they tie or neither
 * is present. Input is decoded as UTF-8, and a leading UTF-8 byte-order-mark is stripped.
 */
public final class CsvSheetReader implements SheetReader {

    private static final char QUOTE = '"';
    private static final char COMMA = ',';
    private static final char TAB = '\t';
    private static final char CR = '\r';
    private static final char LF = '\n';

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".csv") || lower.endsWith(".tsv");
    }

    @Override
    public List<RawRow> read(InputStream in, String sheetName) throws Exception {
        // CSV has no notion of named sheets; sheetName is intentionally ignored.
        if (in == null) {
            throw new IllegalArgumentException("input stream must not be null");
        }
        String text = readAll(new InputStreamReader(in, StandardCharsets.UTF_8));
        text = stripBom(text);

        char delimiter = detectDelimiter(text);
        return parse(text, delimiter);
    }

    /** Reads the entire reader into a string. */
    private static String readAll(Reader reader) throws Exception {
        StringBuilder sb = new StringBuilder(8192);
        char[] buf = new char[8192];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private static String stripBom(String text) {
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            return text.substring(1);
        }
        return text;
    }

    /**
     * Chooses the delimiter by counting unquoted tabs and commas on the first record's line.
     * Quoted regions are skipped so that separators embedded in quoted values do not skew the
     * decision.
     */
    static char detectDelimiter(String text) {
        int commas = 0;
        int tabs = 0;
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == QUOTE) {
                if (inQuotes && i + 1 < text.length() && text.charAt(i + 1) == QUOTE) {
                    i++; // escaped quote inside a quoted field
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (!inQuotes && (c == CR || c == LF)) {
                break; // only inspect the first physical line of the first record
            } else if (!inQuotes && c == COMMA) {
                commas++;
            } else if (!inQuotes && c == TAB) {
                tabs++;
            }
        }
        return tabs > commas ? TAB : COMMA;
    }

    /**
     * Parses the full document using a single-pass state machine that honours quoting and
     * embedded newlines.
     */
    private static List<RawRow> parse(String text, char delimiter) {
        List<RawRow> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false; // whether the current field has any content/quote yet
        int rowIndex = 0;

        int i = 0;
        int len = text.length();
        while (i < len) {
            char c = text.charAt(i);

            if (inQuotes) {
                if (c == QUOTE) {
                    if (i + 1 < len && text.charAt(i + 1) == QUOTE) {
                        field.append(QUOTE); // "" -> literal "
                        i += 2;
                    } else {
                        inQuotes = false; // closing quote
                        i++;
                    }
                } else {
                    field.append(c);
                    i++;
                }
                continue;
            }

            if (c == QUOTE) {
                inQuotes = true;
                fieldStarted = true;
                i++;
            } else if (c == delimiter) {
                current.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                i++;
            } else if (c == CR || c == LF) {
                // Consume CRLF as a single line terminator.
                if (c == CR && i + 1 < len && text.charAt(i + 1) == LF) {
                    i += 2;
                } else {
                    i++;
                }
                current.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                rows.add(new RawRow(rowIndex++, normalize(current)));
                current = new ArrayList<>();
            } else {
                field.append(c);
                fieldStarted = true;
                i++;
            }
        }

        // Flush trailing field/record if the file does not end with a newline, or if a
        // final field/quote was started.
        if (fieldStarted || field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            rows.add(new RawRow(rowIndex, normalize(current)));
        }

        return rows;
    }

    /**
     * Converts a mutable working list into an immutable snapshot. Empty (unquoted) fields are
     * preserved as empty strings rather than {@code null}, since CSV cannot distinguish a
     * missing cell from an empty one.
     */
    private static List<String> normalize(List<String> cells) {
        return new ArrayList<>(cells);
    }
}
