package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RecentUpcomingStaysTest {

    private static Booking stay(String id, String status, String checkIn) {
        return new Booking(id, "R1", "Deluxe 101", "Deluxe", checkIn, "2026-12-31", 2, 1000, status, "2026-10-01");
    }

    @Test
    public void onlyPendingAndConfirmedQualify() {
        assertTrue(RecentUpcomingStays.qualifies(stay("1", "Pending", "Oct 20, 2026")));
        assertTrue(RecentUpcomingStays.qualifies(stay("2", "confirmed", "Oct 20, 2026")));
        for (String other : new String[]{"Checked-In", "Checked-Out", "Cancelled", "Rejected", "Completed"}) {
            assertFalse(other, RecentUpcomingStays.qualifies(stay("3", other, "Oct 20, 2026")));
        }
        assertFalse(RecentUpcomingStays.qualifies(null));
    }

    @Test
    public void selectKeepsOnlyQualifying_soonestCheckInFirst() {
        List<Booking> all = Arrays.asList(
                stay("late", "Confirmed", "Nov 01, 2026"),
                stay("closed", "Cancelled", "Oct 19, 2026"),
                stay("soon", "Pending", "Oct 20, 2026"));
        List<Booking> out = RecentUpcomingStays.select(all);
        assertEquals(2, out.size());
        assertEquals("soon", out.get(0).getId());
        assertEquals("late", out.get(1).getId());
    }

    @Test
    public void collapsedShowsFive_expandedShowsAll() {
        List<Booking> seven = new ArrayList<>();
        for (int i = 1; i <= 7; i++) seven.add(stay("s" + i, "Pending", "Oct " + (10 + i) + ", 2026"));
        assertEquals(5, RecentUpcomingStays.visible(seven, false).size());
        assertEquals(7, RecentUpcomingStays.visible(seven, true).size());
        assertEquals(3, RecentUpcomingStays.visible(seven.subList(0, 3), false).size());
    }
}
