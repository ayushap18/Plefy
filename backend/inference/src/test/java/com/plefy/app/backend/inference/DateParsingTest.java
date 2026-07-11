package com.plefy.app.backend.inference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.Test;

/** Unit tests for {@link DateParsing}. Pure JVM, no external dependencies. */
public class DateParsingTest {

    @Test
    public void parsesIsoDate() {
        DateParsing.Result r = DateParsing.parse("2024-03-15");
        assertNotNull(r);
        assertEquals("yyyy-MM-dd", r.getPattern());
        org.junit.Assert.assertFalse(r.hasTime());
        long expected = LocalDate.of(2024, 3, 15)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        assertEquals(expected, r.getEpochMillisUtc());
    }

    @Test
    public void parsesIsoDateTime() {
        DateParsing.Result r = DateParsing.parse("2024-03-15T08:30:00");
        assertNotNull(r);
        assertTrue(r.hasTime());
    }

    @Test
    public void prefersUsOverEuropeanForAmbiguousSlashDates() {
        // 05/06/2024 is ambiguous; the engine prefers US MM/dd/yyyy -> May 6th.
        DateParsing.Result r = DateParsing.parse("05/06/2024");
        assertNotNull(r);
        assertEquals("MM/dd/yyyy", r.getPattern());
        long may6 = LocalDate.of(2024, 5, 6)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        assertEquals(may6, r.getEpochMillisUtc());
    }

    @Test
    public void fallsBackToEuropeanWhenUsImpossible() {
        // 25/12/2024 cannot be MM/dd (month 25) so dd/MM/yyyy matches.
        DateParsing.Result r = DateParsing.parse("25/12/2024");
        assertNotNull(r);
        assertEquals("dd/MM/yyyy", r.getPattern());
    }

    @Test
    public void returnsNullForNonDates() {
        assertNull(DateParsing.parse("hello"));
        assertNull(DateParsing.parse(""));
        assertNull(DateParsing.parse(null));
        assertNull(DateParsing.parse("42"));
    }

    @Test
    public void parsesSpaceSeparatedDateTime() {
        DateParsing.Result r = DateParsing.parse("2024-03-15 08:30:00");
        assertNotNull(r);
        assertTrue(r.hasTime());
        assertEquals("yyyy-MM-dd HH:mm:ss", r.getPattern());
    }

    @Test
    public void dateTimeEpochIsUtc() {
        DateParsing.Result r = DateParsing.parse("1970-01-01T00:00:00");
        assertNotNull(r);
        assertEquals(0L, r.getEpochMillisUtc());
    }

    @Test
    public void rejectsImpossibleCalendarValues() {
        // Month 13 / day 32 match no candidate pattern.
        assertNull(DateParsing.parse("2024-13-01"));
        assertNull(DateParsing.parse("13/32/2024"));
    }
}
