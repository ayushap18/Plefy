package com.plefy.app.backend.inference;

import com.plefy.app.model.InferredColumn;
import com.plefy.app.model.RawRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates end-to-end schema inference over a sheet of raw string rows.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>{@link HeaderDetector} decides whether row 0 is a header and supplies column names.</li>
 *   <li>The remaining body rows are transposed into per-column samples.</li>
 *   <li>{@link TypeInferrer} infers the dominant type, confidence and representative format
 *       for each column, producing an {@link InferredColumn}.</li>
 * </ol>
 *
 * <p>This class depends only on {@code java.*} and {@code :core:model} types, so it runs on a
 * plain JVM with no Android or I/O dependencies.
 */
public final class SchemaInferenceEngine {

    /** Aggregated result of running the engine over a sheet. */
    public static final class InferenceOutcome {
        private final boolean hasHeader;
        private final List<InferredColumn> columns;

        InferenceOutcome(boolean hasHeader, List<InferredColumn> columns) {
            this.hasHeader = hasHeader;
            this.columns = columns;
        }

        /** @return whether the first row was treated as a header (and thus excluded from data). */
        public boolean hasHeader() {
            return hasHeader;
        }

        /** @return the inferred columns, left-to-right. */
        public List<InferredColumn> getColumns() {
            return columns;
        }
    }

    /**
     * Runs header detection and per-column type inference.
     *
     * @param rows the sheet's raw rows (may be {@code null}/empty)
     * @return the inference outcome; never {@code null}
     */
    public InferenceOutcome infer(List<RawRow> rows) {
        List<RawRow> safeRows = (rows == null) ? new ArrayList<>() : rows;

        HeaderDetector.Result header = HeaderDetector.detect(safeRows);
        List<String> columnNames = header.getColumnNames();
        int columnCount = columnNames.size();

        int firstBodyRow = header.hasHeader() ? 1 : 0;

        List<InferredColumn> columns = new ArrayList<>(columnCount);
        for (int col = 0; col < columnCount; col++) {
            List<String> samples = columnSamples(safeRows, firstBodyRow, col);
            TypeInferrer.Result inferred = TypeInferrer.infer(samples);
            columns.add(new InferredColumn(
                    col,
                    columnNames.get(col),
                    inferred.getType(),
                    inferred.getFormat(),
                    inferred.getConfidence()));
        }

        return new InferenceOutcome(header.hasHeader(), columns);
    }

    /** Collects the raw values of a single column across the body rows. */
    private static List<String> columnSamples(List<RawRow> rows, int firstBodyRow, int col) {
        List<String> samples = new ArrayList<>();
        for (int r = firstBodyRow; r < rows.size(); r++) {
            RawRow row = rows.get(r);
            List<String> cells = (row == null) ? null : row.getCells();
            if (cells != null && col < cells.size()) {
                samples.add(cells.get(col));
            } else {
                samples.add(null);
            }
        }
        return samples;
    }
}
