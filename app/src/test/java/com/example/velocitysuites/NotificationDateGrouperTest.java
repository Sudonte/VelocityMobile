package com.example.velocitysuites;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;

/**
 * Pure-JVM coverage for NotificationDateGrouper#daysBetween() - the
 * Context-free calendar-day math groupLabel() builds its Today/Yesterday/
 * Earlier decision on (groupLabel() itself needs a Context for
 * getString(), so isn't directly unit-testable here - same constraint as
 * NotificationStatusResolver/PaymentStatusResolver).
 */
public class NotificationDateGrouperTest {

    private static long millisAt(int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, day, hour, minute);
        return c.getTimeInMillis();
    }

    @Test
    public void sameCalendarDay_zeroDaysApart() {
        long morning = millisAt(2026, Calendar.SEPTEMBER, 29, 1, 0);
        long night = millisAt(2026, Calendar.SEPTEMBER, 29, 23, 0);
        assertEquals(0, NotificationDateGrouper.daysBetween(morning, night));
    }

    @Test
    public void adjacentCalendarDays_lessThan24hApart_isOneDayApart() {
        // 11pm one day to 1am the next - under 24 real hours but 1 calendar day apart.
        long lateNight = millisAt(2026, Calendar.SEPTEMBER, 28, 23, 0);
        long earlyMorning = millisAt(2026, Calendar.SEPTEMBER, 29, 1, 0);
        assertEquals(1, NotificationDateGrouper.daysBetween(lateNight, earlyMorning));
    }

    @Test
    public void argumentOrderDoesNotMatter() {
        long earlier = millisAt(2026, Calendar.SEPTEMBER, 27, 12, 0);
        long later = millisAt(2026, Calendar.SEPTEMBER, 29, 12, 0);
        assertEquals(2, NotificationDateGrouper.daysBetween(earlier, later));
        assertEquals(2, NotificationDateGrouper.daysBetween(later, earlier));
    }
}
