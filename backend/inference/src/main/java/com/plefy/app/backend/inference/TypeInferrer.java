package com.plefy.app.backend.inference;

import com.plefy.app.model.CellType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Infers the dominant {@link CellType} for a column from a sample of its raw string cells.
 *
 * <p>The inference tallies a per-cell classification across the sample (bounded to the
 * first {@value #SAMPLE_LIMIT} non-empty cells), ignores {@link CellType#EMPTY} entries,
 * and selects the most frequent remaining type. Confidence is reported as
 * {@code dominantCount / nonEmptyCount}. When a date/date-time type dominates, a
 * representative pattern (e.g. {@code "yyyy-MM-dd"}) is captured as the column format.
 *
 * <p>An all-empty column yields {@link CellType#EMPTY} with zero confidence.
 */
public final class TypeInferrer {

    /** Maximum number of non-empty cells sampled per column. */
    public static final int SAMPLE_LIMIT = 500;

    /** Immutable result of a column inference. */
    public static final class Result {
        private final CellType type;
        private final double confidence;
        private final String format;
        private final int nonEmptyCount;

        Result(CellType type, double confidence, String format, int nonEmptyCount) {
            this.type = type;
            this.confidence = confidence;
            this.format = format;
            this.nonEmptyCount = nonEmptyCount;
        }

        /** @return the dominant type; {@link CellType#EMPTY} for an all-empty column. */
        public CellType getType() {
            return type;
        }

        /** @return {@code dominantCount / nonEmptyCount}, in {@code [0, 1]}; {@code 0} if all empty. */
        public double getConfidence() {
            return confidence;
        }

        /** @return a representative format (matched date pattern), or {@code null} when not applicable. */
        public String getFormat() {
            return format;
        }

        /** @return the number of non-empty cells among those sampled (bounded by {@link #SAMPLE_LIMIT}). */
        public int getNonEmptyCount() {
            return nonEmptyCount;
        }

        @Override
        public String toString() {
            return "Result{type=" + type + ", confidence=" + confidence
                    + ", format='" + format + '\'' + ", nonEmpty=" + nonEmptyCount + '}';
        }
    }

    private TypeInferrer() {
        // static-only utility
    }

    /**
     * Infers the dominant type for a column from its raw cell values.
     *
     * @param rawCells the column's raw string cells, top-to-bottom (may be {@code null})
     * @return the inference {@link Result}; never {@code null}
     */
    public static Result infer(List<String> rawCells) {
        Map<CellType, Integer> tally = new EnumMap<>(CellType.class);
        // Per (date-ish type -> pattern -> count) so we can pick a representative format.
        Map<CellType, Map<String, Integer>> patternTally = new EnumMap<>(CellType.class);

        int nonEmpty = 0;

        if (rawCells != null) {
            for (String raw : rawCells) {
                if (nonEmpty >= SAMPLE_LIMIT) {
                    break;
                }
                CellType type = CellClassifier.classify(raw);
                if (type == CellType.EMPTY) {
                    // Empty cells are counted neither toward the sample budget nor the tally,
                    // so a column with sparse data still yields SAMPLE_LIMIT real observations.
                    continue;
                }
                nonEmpty++;
                tally.merge(type, 1, Integer::sum);

                if (type == CellType.DATE || type == CellType.DATETIME) {
                    DateParsing.Result dr = DateParsing.parse(raw.trim());
                    if (dr != null) {
                        patternTally
                                .computeIfAbsent(type, k -> new HashMap<>())
                                .merge(dr.getPattern(), 1, Integer::sum);
                    }
                }
            }
        }

        if (nonEmpty == 0) {
            return new Result(CellType.EMPTY, 0.0, null, 0);
        }

        CellType dominant = null;
        int dominantCount = 0;
        for (Map.Entry<CellType, Integer> e : tally.entrySet()) {
            if (e.getValue() > dominantCount) {
                dominantCount = e.getValue();
                dominant = e.getKey();
            }
        }

        double confidence = (double) dominantCount / (double) nonEmpty;
        String format = representativeFormat(dominant, patternTally);
        return new Result(dominant, confidence, format, nonEmpty);
    }

    /** Picks the most frequent pattern for a date/date-time dominant type. */
    private static String representativeFormat(CellType dominant,
                                               Map<CellType, Map<String, Integer>> patternTally) {
        if (dominant != CellType.DATE && dominant != CellType.DATETIME) {
            return null;
        }
        Map<String, Integer> patterns = patternTally.get(dominant);
        if (patterns == null || patterns.isEmpty()) {
            return null;
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : patterns.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }
}
