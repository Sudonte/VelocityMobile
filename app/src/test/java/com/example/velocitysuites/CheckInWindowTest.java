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

    private static final Clock OCT_8 = at("2026-10-08T02:00:00Z"); // 10:00 in Manila

    @Test
    public void earliestCheckIn_isTwoDaysFromToday() {
        assertEquals(LocalDate.of(2026, 10, 10), CheckInWindow.earliest(OCT_8));
    }

    @Test
    public void checkInEdges() {
        assertFalse("yesterday", CheckInWindow.isAllowed(LocalDate.of(2026, 10, 7), OCT_8));
        assertFalse("today", CheckInWindow.isAllowed(LocalDate.of(2026, 10, 8), OCT_8));
        assertFalse("tomorrow", CheckInWindow.isAllowed(LocalDate.of(2026, 10, 9), OCT_8));
        assertTrue("today+2", CheckInWindow.isAllowed(LocalDate.of(2026, 10, 10), OCT_8));
        assertTrue("today+3", CheckInWindow.isAllowed(LocalDate.of(2026, 10, 11), OCT_8));
        assertTrue("months ahead - no upper limit", CheckInWindow.isAllowed(LocalDate.of(2027, 2, 14), OCT_8));
        assertTrue("a year ahead", CheckInWindow.isAllowed(LocalDate.of(2027, 10, 8), OCT_8));
        assertFalse("null", CheckInWindow.isAllowed((LocalDate) null, OCT_8));
    }

    @Test
    public void checkOutMustBeAtLeastOneDayAfterCheckIn() {
        LocalDate in = LocalDate.of(2026, 10, 10);
        assertEquals(LocalDate.of(2026, 10, 11), CheckInWindow.earliestCheckOut(in));
        assertFalse("same day", CheckInWindow.isValidCheckOut(in, in));
        assertFalse("before check-in", CheckInWindow.isValidCheckOut(in, in.minusDays(1)));
        assertTrue("check-in + 1", CheckInWindow.isValidCheckOut(in, in.plusDays(1)));
        assertTrue("later", CheckInWindow.isValidCheckOut(in, in.plusDays(30)));
        // a later check-in moves the earliest check-out with it: Oct 15 -> Oct 16
        LocalDate late = LocalDate.of(2026, 10, 15);
        assertEquals(LocalDate.of(2026, 10, 16), CheckInWindow.earliestCheckOut(late));
        assertFalse(CheckInWindow.isValidCheckOut(late, late));
    }

    @Test
    public void today_followsManilaNotUtc() {
        // 20:00 UTC on Oct 8 is already 04:00 on Oct 9 in Manila -> earliest check-in Oct 11
        Clock lateUtc = at("2026-10-08T20:00:00Z");
        assertEquals(LocalDate.of(2026, 10, 9), CheckInWindow.today(lateUtc));
        assertEquals(LocalDate.of(2026, 10, 11), CheckInWindow.earliest(lateUtc));
        assertFalse(CheckInWindow.isAllowed(LocalDate.of(2026, 10, 10), lateUtc));
        // 15:59 UTC is still 23:59 on Oct 8 in Manila -> Oct 10
        assertEquals(LocalDate.of(2026, 10, 10), CheckInWindow.earliest(at("2026-10-08T15:59:59Z")));
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
