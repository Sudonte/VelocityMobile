package com.example.velocitysuites;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Pure-JVM coverage for StayDateCalculator - consolidates what used to be six
 * separate, Activity-embedded (so untestable) copies of the same nights formula.
 */
public class StayDateCalculatorTest {

    private static Calendar dateOf(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, day, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    // ---- Calendar overload ----

    @Test
    public void oneNight() {
        assertEquals(1, StayDateCalculator.nightsBetween(dateOf(2025, Calendar.MAY, 1), dateOf(2025, Calendar.MAY, 2)));
    }

    @Test
    public void twoNights() {
        assertEquals(2, StayDateCalculator.nightsBetween(dateOf(2025, Calendar.MAY, 1), dateOf(2025, Calendar.MAY, 3)));
    }

    @Test
    public void sevenNights() {
        assertEquals(7, StayDateCalculator.nightsBetween(dateOf(2025, Calendar.MAY, 1), dateOf(2025, Calendar.MAY, 8)));
    }

    @Test
    public void sameDay_clampedToOneNight() {
        // Whether a same-day stay is a valid business transaction is enforced
        // elsewhere (booking-creation validation) - this formula's own contract
        // is just "never return less than 1", not a business-rule gate.
        assertEquals(1, StayDateCalculator.nightsBetween(dateOf(2025, Calendar.MAY, 1), dateOf(2025, Calendar.MAY, 1)));
    }

    @Test
    public void checkoutBeforeCheckIn_clampedToOneNight() {
        // Same reasoning as sameDay above - never returns a negative/zero night
        // count regardless of whether the caller should have prevented this input.
        assertEquals(1, StayDateCalculator.nightsBetween(dateOf(2025, Calendar.MAY, 5), dateOf(2025, Calendar.MAY, 1)));
    }

    @Test
    public void monthBoundary() {
        assertEquals(2, StayDateCalculator.nightsBetween(dateOf(2025, Calendar.JANUARY, 30), dateOf(2025, Calendar.FEBRUARY, 1)));
    }

    @Test
    public void yearBoundary() {
        assertEquals(2, StayDateCalculator.nightsBetween(dateOf(2025, Calendar.DECEMBER, 30), dateOf(2026, Calendar.JANUARY, 1)));
    }

    @Test
    public void nullCalendar_defaultsToOneNight() {
        assertEquals(1, StayDateCalculator.nightsBetween((Calendar) null, dateOf(2025, Calendar.MAY, 5)));
        assertEquals(1, StayDateCalculator.nightsBetween(dateOf(2025, Calendar.MAY, 5), (Calendar) null));
    }

    // ---- String ("MMM dd, yyyy") overload ----

    @Test
    public void stringOverload_matchesCalendarOverload() {
        assertEquals(4, StayDateCalculator.nightsBetween("May 01, 2025", "May 05, 2025"));
    }

    @Test
    public void stringOverload_unparseable_defaultsToOneNight() {
        assertEquals(1, StayDateCalculator.nightsBetween("not a date", "May 05, 2025"));
        assertEquals(1, StayDateCalculator.nightsBetween(null, "May 05, 2025"));
    }

    @Test
    public void nightsBetweenOrNull_validDates_returnsSameAsMainOverload() {
        assertEquals(Long.valueOf(4), StayDateCalculator.nightsBetweenOrNull("May 01, 2025", "May 05, 2025"));
    }

    @Test
    public void nightsBetweenOrNull_unparseableOrMissing_returnsNullInsteadOfFabricating() {
        assertNull(StayDateCalculator.nightsBetweenOrNull(null, "May 05, 2025"));
        assertNull(StayDateCalculator.nightsBetweenOrNull("May 01, 2025", null));
        assertNull(StayDateCalculator.nightsBetweenOrNull("garbage", "May 05, 2025"));
    }
}
