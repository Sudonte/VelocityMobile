package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;

public class CheckInWindowTest {

    private static Clock at(String isoInstant) {
        return Clock.fixed(Instant.parse(isoInstant), ZoneId.of("UTC"));
    }

    @Test
    public void window_isTodayThroughTwoDaysAhead() {
        Clock oct8 = at("2026-10-08T02:00:00Z"); // 10:00 in Manila
        assertEquals(LocalDate.of(2026, 10, 8), CheckInWindow.earliest(oct8));
        assertEquals(LocalDate.of(2026, 10, 10), CheckInWindow.latest(oct8));
    }

    @Test
    public void edgeDates() {
        Clock oct8 = at("2026-10-08T02:00:00Z");
        assertFalse("yesterday", CheckInWindow.isAllowed(LocalDate.of(2026, 10, 7), oct8));
        assertTrue("today", CheckInWindow.isAllowed(LocalDate.of(2026, 10, 8), oct8));
        assertTrue("tomorrow", CheckInWindow.isAllowed(LocalDate.of(2026, 10, 9), oct8));
        assertTrue("day after tomorrow", CheckInWindow.isAllowed(LocalDate.of(2026, 10, 10), oct8));
        assertFalse("three days out", CheckInWindow.isAllowed(LocalDate.of(2026, 10, 11), oct8));
        assertFalse("null", CheckInWindow.isAllowed((LocalDate) null, oct8));
    }

    @Test
    public void today_followsManilaNotUtc() {
        // 20:00 UTC on Oct 8 is already 04:00 on Oct 9 in Manila.
        Clock lateUtc = at("2026-10-08T20:00:00Z");
        assertEquals(LocalDate.of(2026, 10, 9), CheckInWindow.today(lateUtc));
        assertFalse(CheckInWindow.isAllowed(LocalDate.of(2026, 10, 8), lateUtc));
        assertTrue(CheckInWindow.isAllowed(LocalDate.of(2026, 10, 11), lateUtc));
        // 15:59 UTC is still 23:59 on Oct 8 in Manila.
        Clock beforeMidnight = at("2026-10-08T15:59:59Z");
        assertEquals(LocalDate.of(2026, 10, 8), CheckInWindow.today(beforeMidnight));
    }

    @Test
    public void calendarRoundTrip_usesDateFieldsOnly() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.OCTOBER, 9, 0, 0, 0);
        assertEquals(LocalDate.of(2026, 10, 9), CheckInWindow.toLocalDate(c));
        Calendar back = CheckInWindow.toDeviceMidnight(LocalDate.of(2026, 10, 9));
        assertEquals(2026, back.get(Calendar.YEAR));
        assertEquals(Calendar.OCTOBER, back.get(Calendar.MONTH));
        assertEquals(9, back.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, back.get(Calendar.HOUR_OF_DAY));
    }
}
