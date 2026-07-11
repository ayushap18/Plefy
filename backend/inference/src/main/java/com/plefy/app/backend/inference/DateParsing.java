package com.plefy.app.backend.inference;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;

/**
 * Pure-JVM parser for the common date and date-time textual formats found in spreadsheet
 * cells, built entirely on {@link java.time}.
 *
 * <p>Candidate patterns are tried in a deliberate priority order:
 * <ol>
 *   <li>Unambiguous ISO forms first ({@code yyyy-MM-dd}, {@code yyyy-MM-dd'T'HH:mm[:ss]}).</li>
 *   <li>US-style {@code MM/dd/yyyy} before European {@code dd/MM/yyyy}, since the two are
 *       ambiguous for day/month values &le; 12 and US ordering is the more common default
 *       for the data this engine targets.</li>
 * </ol>
 *
 * <p>Results are reported as {@link Result} carrying the UTC epoch-millis instant, the
 * pattern that matched, and whether a time component was present. {@code null} is returned
 * when no pattern matches, so callers can treat {@code null} as "not a date".
 */
public final class DateParsing {

    /** Outcome of a successful parse. Immutable. */
    public static final class Result {
        private final long epochMillisUtc;
        private final String pattern;
        private final boolean hasTime;

        Result(long epochMillisUtc, String pattern, boolean hasTime) {
            this.epochMillisUtc = epochMillisUtc;
            this.pattern = pattern;
            this.hasTime = hasTime;
        }

        /** @return the parsed instant as milliseconds since the Unix epoch, interpreted in UTC. */
        public long getEpochMillisUtc() {
            return epochMillisUtc;
        }

        /** @return the pattern string that matched (e.g. {@code "yyyy-MM-dd"}). */
        public String getPattern() {
            return pattern;
        }

        /** @return {@code true} if the input carried a time-of-day component. */
        public boolean hasTime() {
            return hasTime;
        }

        @Override
        public String toString() {
            return "Result{epochMillisUtc=" + epochMillisUtc
                    + ", pattern='" + pattern + '\'' + ", hasTime=" + hasTime + '}';
        }
    }

    /** A candidate pattern plus how it should be interpreted. */
    private static final class Candidate {
        final String pattern;
        final DateTimeFormatter formatter;
        final boolean hasTime;

        Candidate(String pattern, boolean hasTime) {
            this.pattern = pattern;
            // Use a strict-ish, locale-independent formatter.
            this.formatter = DateTimeFormatter.ofPattern(pattern);
            this.hasTime = hasTime;
        }
    }

    /** Date-time candidates, tried before date-only candidates. */
    private static final List<Candidate> DATETIME_CANDIDATES;
    /** Date-only candidates. */
    private static final List<Candidate> DATE_CANDIDATES;

    static {
        DATETIME_CANDIDATES = Collections.unmodifiableList(java.util.Arrays.asList(
                new Candidate("yyyy-MM-dd'T'HH:mm:ss", true),
                new Candidate("yyyy-MM-dd'T'HH:mm", true),
                new Candidate("yyyy-MM-dd HH:mm:ss", true),
                new Candidate("yyyy-MM-dd HH:mm", true),
                new Candidate("MM/dd/yyyy HH:mm:ss", true),
                new Candidate("MM/dd/yyyy HH:mm", true),
                new Candidate("dd/MM/yyyy HH:mm:ss", true),
                new Candidate("dd/MM/yyyy HH:mm", true)));

        DATE_CANDIDATES = Collections.unmodifiableList(java.util.Arrays.asList(
                new Candidate("yyyy-MM-dd", false),
                new Candidate("yyyy/MM/dd", false),
                new Candidate("MM/dd/yyyy", false),
                new Candidate("dd/MM/yyyy", false),
                new Candidate("MM-dd-yyyy", false),
                new Candidate("dd-MM-yyyy", false),
                new Candidate("MM/dd/yy", false),
                new Candidate("dd-MMM-yyyy", false),
                new Candidate("MMM dd, yyyy", false),
                new Candidate("MMMM dd, yyyy", false)));
    }

    private DateParsing() {
        // static-only utility
    }

    /**
     * Attempts to parse {@code raw} as a date or date-time.
     *
     * @param raw the raw cell text (may be {@code null})
     * @return the parse {@link Result}, or {@code null} if no known pattern matched
     */
    public static Result parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty() || !looksLikeDate(s)) {
            return null;
        }

        // Date-time patterns take precedence, but only attempt them when a time separator is
        // actually present — a bare date otherwise pays a caught exception per date-time candidate
        // before matching. This (with looksLikeDate) is what keeps classifying/normalising a large
        // sheet from drowning in DateTimeParseExceptions.
        if (s.indexOf(':') >= 0) {
            for (Candidate c : DATETIME_CANDIDATES) {
                Long millis = tryDateTime(s, c);
                if (millis != null) {
                    return new Result(millis, c.pattern, true);
                }
            }
        }
        for (Candidate c : DATE_CANDIDATES) {
            Long millis = tryDate(s, c);
            if (millis != null) {
                return new Result(millis, c.pattern, false);
            }
        }
        return null;
    }

    /**
     * Cheap pre-filter: a parseable date has a digit, one of the separators our patterns use, and a
     * bounded length. Rejecting everything else here avoids throwing (and catching) a
     * {@link DateTimeParseException} per candidate for the common non-date cell — the dominant cost
     * when inferring/normalising large sheets. Pure char scan, no allocation, no exceptions.
     */
    private static boolean looksLikeDate(String s) {
        int n = s.length();
        if (n < 6 || n > 30) {
            return false;
        }
        boolean digit = false;
        boolean sep = false;
        for (int i = 0; i < n; i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') {
                digit = true;
            } else if (ch == '-' || ch == '/' || ch == ' ' || ch == ',' || ch == ':') {
                sep = true;
            }
        }
        return digit && sep;
    }

    private static Long tryDateTime(String s, Candidate c) {
        try {
            LocalDateTime dt = LocalDateTime.parse(s, c.formatter);
            return dt.toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static Long tryDate(String s, Candidate c) {
        try {
            LocalDate d = LocalDate.parse(s, c.formatter);
            return d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
