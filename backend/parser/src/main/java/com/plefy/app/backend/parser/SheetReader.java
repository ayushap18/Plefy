package com.plefy.app.backend.parser;

import com.plefy.app.model.RawRow;

import java.io.InputStream;
import java.util.List;

/**
 * Reads a single spreadsheet source (CSV, XLSX, XLS, ...) into a list of raw,
 * unparsed rows.
 *
 * <p>Implementations are intentionally kept minimal: they perform <em>no</em> type
 * inference, trimming, or normalisation. Every cell is surfaced as the raw string the
 * source contained (or {@code null} for a genuinely missing/error cell). Downstream
 * modules are responsible for interpreting those strings.
 *
 * <p>Implementations must be stateless and therefore safe to reuse across threads; all
 * per-call state lives on the stack of {@link #read(InputStream, String)}.
 */
public interface SheetReader {

    /**
     * Reads the given stream into an ordered list of {@link RawRow}s.
     *
     * <p>The stream is consumed but <strong>not</strong> closed by this method; the
     * caller retains ownership and is responsible for closing it.
     *
     * @param in        the raw bytes of the workbook/file. Never {@code null}.
     * @param sheetName the name of the sheet to read, or {@code null} to read the first
     *                  (or only) sheet. Ignored by single-sheet formats such as CSV.
     * @return the rows in source order; never {@code null}, possibly empty.
     * @throws Exception if the stream cannot be read or is not in a format this reader
     *                   supports.
     */
    List<RawRow> read(InputStream in, String sheetName) throws Exception;

    /**
     * Reads <em>every</em> sheet of the source. The default reads the single/first sheet (correct
     * for CSV and other single-sheet formats); multi-sheet readers override it to return each tab.
     *
     * @param in the raw bytes; consumed but not closed by this method.
     * @return one {@link NamedSheet} per sheet, in workbook order; never {@code null}, never empty.
     */
    default java.util.List<NamedSheet> readAll(InputStream in) throws Exception {
        return java.util.Collections.singletonList(new NamedSheet(null, read(in, null)));
    }

    /**
     * Reports whether this reader can handle the given file name, judged purely by its
     * extension (case-insensitive).
     *
     * @param fileName a file name such as {@code "data.csv"} or {@code "report.xlsx"};
     *                 may be {@code null}, in which case this returns {@code false}.
     * @return {@code true} if {@link #read} is expected to succeed for this file name.
     */
    boolean supports(String fileName);
}
