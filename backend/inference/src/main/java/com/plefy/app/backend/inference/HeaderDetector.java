package com.plefy.app.backend.inference;

import com.plefy.app.model.CellType;
import com.plefy.app.model.RawRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Heuristically decides whether the first row of a sheet is a header, and supplies column
 * names either way.
 *
 * <p>The score for "row 0 is a header" rewards three signals, measured against the body
 * rows that follow:
 * <ul>
 *   <li><b>Fully populated</b> &mdash; a header rarely has blank cells.</li>
 *   <li><b>Text-heavy</b> &mdash; header labels are typically {@link CellType#TEXT}, whereas
 *       data rows are more often numeric/date/boolean.</li>
 *   <li><b>Type divergence</b> &mdash; row 0's per-column types differ from the dominant
 *       types of the rows below it.</li>
 * </ul>
 *
 * <p>When a header is detected, its trimmed cell values become the column names (blank or
 * duplicate cells get generated fallbacks). When no header is detected, spreadsheet-style
 * names {@code "Column A" .. "Column Z", "Column AA" ...} are generated.
 */
public final class HeaderDetector {

    /** Number of leading rows inspected when scoring the header. */
    public static final int SCAN_ROWS = 10;

    /** Score at or above which row 0 is treated as a header. */
    private static final double HEADER_THRESHOLD = 0.6;

    /** Immutable detection outcome. */
    public static final class Result {
        private final boolean hasHeader;
        private final List<String> columnNames;

        Result(boolean hasHeader, List<String> columnNames) {
            this.hasHeader = hasHeader;
            this.columnNames = columnNames;
        }

        /** @return {@code true} if the first row was classified as a header. */
        public boolean hasHeader() {
            return hasHeader;
        }

        /** @return one name per column; generated when no header is present or a cell is blank. */
        public List<String> getColumnNames() {
            return columnNames;
        }

        @Override
        public String toString() {
            return "Result{hasHeader=" + hasHeader + ", columnNames=" + columnNames + '}';
        }
    }

    private HeaderDetector() {
        // static-only utility
    }

    /**
     * Detects a header row and produces column names.
     *
     * @param rows the sheet rows (only the first {@link #SCAN_ROWS} are inspected); may be empty/null
     * @return the detection {@link Result}; never {@code null}
     */
    public static Result detect(List<RawRow> rows) {
        int columnCount = columnCount(rows);

        if (rows == null || rows.isEmpty() || columnCount == 0) {
            return new Result(false, generateNames(columnCount));
        }

        List<String> firstRow = cellsOf(rows.get(0), columnCount);
        int bodyRowCount = Math.min(rows.size(), SCAN_ROWS) - 1;

        // With no body rows to compare against we cannot justify treating row 0 as a header.
        if (bodyRowCount <= 0) {
            return new Result(false, generateNames(columnCount));
        }

        double score = scoreHeader(rows, firstRow, columnCount);
        if (score >= HEADER_THRESHOLD) {
            return new Result(true, deriveNames(firstRow));
        }
        return new Result(false, generateNames(columnCount));
    }

    /**
     * Computes a header-likelihood score in {@code [0, 1]} as the mean of three sub-scores:
     * fully-populated, text ratio, and type divergence versus the body.
     */
    private static double scoreHeader(List<RawRow> rows, List<String> firstRow, int columnCount) {
        // 1) Fully populated: fraction of non-empty header cells.
        int populated = 0;
        int textCells = 0;
        for (String cell : firstRow) {
            CellType t = CellClassifier.classify(cell);
            if (t != CellType.EMPTY) {
                populated++;
            }
            if (t == CellType.TEXT) {
                textCells++;
            }
        }
        double populatedScore = (double) populated / (double) columnCount;
        double textScore = (double) textCells / (double) columnCount;

        // 3) Type divergence: per column, does row 0's type differ from the body's dominant type?
        int lastScanRow = Math.min(rows.size(), SCAN_ROWS);
        int divergentColumns = 0;
        for (int col = 0; col < columnCount; col++) {
            CellType headerType = CellClassifier.classify(get(firstRow, col));

            List<String> bodySamples = new ArrayList<>();
            for (int r = 1; r < lastScanRow; r++) {
                bodySamples.add(get(cellsOf(rows.get(r), columnCount), col));
            }
            CellType bodyType = TypeInferrer.infer(bodySamples).getType();

            // Divergence counts when the body has a concrete (non-empty) type that the
            // header cell does not share - the classic "text label over numeric data".
            if (bodyType != CellType.EMPTY && headerType != bodyType) {
                divergentColumns++;
            }
        }
        double divergenceScore = (double) divergentColumns / (double) columnCount;

        return (populatedScore + textScore + divergenceScore) / 3.0;
    }

    /** Turns header cells into unique, non-blank column names. */
    private static List<String> deriveNames(List<String> firstRow) {
        List<String> names = new ArrayList<>(firstRow.size());
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < firstRow.size(); i++) {
            String raw = firstRow.get(i);
            String name = raw == null ? "" : raw.trim();
            if (name.isEmpty()) {
                name = generateName(i);
            }
            // Disambiguate duplicates deterministically.
            String candidate = name;
            int suffix = 2;
            while (!seen.add(candidate)) {
                candidate = name + " (" + suffix + ")";
                suffix++;
            }
            names.add(candidate);
        }
        return names;
    }

    /** Generates {@code count} spreadsheet-style names {@code "Column A", "Column B", ...}. */
    private static List<String> generateNames(int count) {
        List<String> names = new ArrayList<>(Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            names.add(generateName(i));
        }
        return names;
    }

    /**
     * Maps a zero-based column index to a spreadsheet letter label prefixed with
     * {@code "Column "}: {@code 0 -> "Column A"}, {@code 25 -> "Column Z"},
     * {@code 26 -> "Column AA"}, and so on.
     */
    static String generateName(int index) {
        return "Column " + columnLetters(index);
    }

    /** Bijective base-26 (A=0..Z=25, then AA, AB, ...) letter label for a column index. */
    static String columnLetters(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        while (n >= 0) {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        }
        return sb.toString();
    }

    /** @return the widest row's cell count across the scanned rows. */
    private static int columnCount(List<RawRow> rows) {
        if (rows == null) {
            return 0;
        }
        int max = 0;
        int limit = Math.min(rows.size(), SCAN_ROWS);
        for (int i = 0; i < limit; i++) {
            RawRow row = rows.get(i);
            if (row != null && row.getCells() != null) {
                max = Math.max(max, row.getCells().size());
            }
        }
        return max;
    }

    /** Returns the row's cells padded/truncated to exactly {@code width} entries. */
    private static List<String> cellsOf(RawRow row, int width) {
        List<String> out = new ArrayList<>(width);
        List<String> cells = (row == null) ? null : row.getCells();
        for (int i = 0; i < width; i++) {
            out.add(cells != null && i < cells.size() ? cells.get(i) : null);
        }
        return out;
    }

    private static String get(List<String> list, int index) {
        return (list != null && index < list.size()) ? list.get(index) : null;
    }
}
