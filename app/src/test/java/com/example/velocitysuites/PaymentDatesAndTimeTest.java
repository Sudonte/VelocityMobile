package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Display-date parsing for sorting/filtering, and the compact one-line notification timestamp. */
public class PaymentDatesAndTimeTest {

    // ---- PaymentDates ----

    @Test
    public void parsesEveryDisplayShape() {
        long expected = LocalDate.of(2026, 9, 2).atTime(10, 24).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertEquals(expected, PaymentDates.parseMillis("Sep 02, 2026 • 10:24 AM"));
        assertEquals(expected, PaymentDates.parseMillis("Sep 2, 2026 • 10:24 AM"));
        assertEquals(expected, PaymentDates.parseMillis("Sep 02, 2026 10:24 AM"));
        assertEquals(LocalDate.of(2026, 9, 2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                PaymentDates.parseMillis("Sep 02, 2026"));
    }

    @Test
    public void unknownDatesAreZero_neverAnException() {
        assertEquals(0, PaymentDates.parseMillis(null));
        assertEquals(0, PaymentDates.parseMillis(""));
        assertEquals(0, PaymentDates.parseMillis("   "));
        assertEquals(0, PaymentDates.parseMillis("N/A"));
        assertEquals(0, PaymentDates.parseMillis("next tuesday"));
    }

    @Test
    public void calendarDatesParse_withOrWithoutPaddingOrATime() {
        assertEquals(LocalDate.of(2026, 10, 1), PaymentDates.parseDate("Oct 01, 2026"));
        assertEquals(LocalDate.of(2026, 10, 1), PaymentDates.parseDate("Oct 1, 2026"));
        assertEquals(LocalDate.of(2026, 10, 1), PaymentDates.parseDate("Oct 01, 2026 • 9:00 PM"));
        assertNull(PaymentDates.parseDate(null));
        assertNull(PaymentDates.parseDate("soon"));
    }

    // ---- TimeUtils.formatCompact ----

    private static long at(String isoManila) {
        return ZonedDateTime.parse(isoManila + "[Asia/Manila]").toInstant().toEpochMilli();
    }

    @Test
    public void recentTimesAreRelative_andShort() {
        long now = at("2026-10-01T22:15:00+08:00");
        assertEquals("Just now", TimeUtils.formatCompact(now - 20_000, now));
        assertEquals("1m ago", TimeUtils.formatCompact(now - 61_000, now));
        assertEquals("59m ago", TimeUtils.formatCompact(now - 59 * 60_000L, now));
        assertEquals("1h ago", TimeUtils.formatCompact(now - 60 * 60_000L, now));
        assertEquals("23h ago", TimeUtils.formatCompact(now - (23 * 60 + 59) * 60_000L, now));
    }

    @Test
    public void olderTimesAreADateAndClock_onOneLine() {
        long now = at("2026-10-01T22:15:00+08:00");
        long then = at("2026-09-30T22:09:00+08:00") - 60_000L * 60 * 2; // > 24h ago
        String text = TimeUtils.formatCompact(then, now);
        assertTrue(text, text.matches("[A-Z][a-z]{2} \\d{1,2} • \\d{1,2}:\\d{2} [AP]M"));
    }

    @Test
    public void theSpecExampleFormat() {
        long now = at("2026-10-05T12:00:00+08:00");
        assertEquals("Sep 30 • 10:09 PM", TimeUtils.formatCompact(at("2026-09-30T22:09:00+08:00"), now));
    }

    @Test
    public void anEarlierYearShowsTheYear_notAClock() {
        long now = at("2026-10-05T12:00:00+08:00");
        assertEquals("Sep 30, 2025", TimeUtils.formatCompact(at("2025-09-30T22:09:00+08:00"), now));
    }

    @Test
    public void aTimeInTheFuture_isJustNow_andAnUnknownOneIsEmpty() {
        long now = at("2026-10-05T12:00:00+08:00");
        assertEquals("Just now", TimeUtils.formatCompact(now + 3_600_000L, now));
        assertEquals("", TimeUtils.formatCompact(0, now));
        assertEquals("", TimeUtils.formatCompact(-5, now));
    }

    @Test
    public void theDayBoundaryIsManilasNotTheDevices() {
        // 23:30 Manila on Dec 31 vs 00:10 Manila on Jan 1: the year changes in Manila regardless of the device zone.
        long now = at("2027-01-01T00:10:00+08:00");
        long then = at("2026-12-30T23:30:00+08:00");
        assertEquals("Dec 30, 2026", TimeUtils.formatCompact(then, now));
    }
}
