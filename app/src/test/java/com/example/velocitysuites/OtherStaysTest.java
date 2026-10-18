package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Your Next Stay's selected day, Other Stays and the default day: nothing may fall between the two sections. */
public class OtherStaysTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

    private static Booking stay(String id, int inDays, int outDays, String status, boolean reservation) {
        Booking b = new Booking(id, "1", "Deluxe", "Deluxe", TODAY.plusDays(inDays).format(FMT), TODAY.plusDays(outDays).format(FMT),
                2, 3600, status, "Oct 01, 2026");
        b.setHasBooking(!reservation);
        b.setDirectBooking(!reservation);
        return b;
    }

    private static List<Booking> sample() {
        return Arrays.asList(
                stay("1", -10, -8, "Checked-Out", false),
                stay("2", -1, 2, "Checked-In", false),
                stay("3", 0, 1, "Confirmed", true),
                stay("4", 3, 5, "Pending", false),
                stay("5", 3, 4, "Confirmed", true),
                stay("6", 40, 42, "Confirmed", false),
                stay("7", 2, 3, "Cancelled", false),
                stay("8", 6, 7, "Rejected", true),
                stay("1", 9, 10, "Confirmed", true)); // same id as #1, but a reservation: a different transaction
    }

    private static Set<String> keys(List<Booking> list) {
        Set<String> out = new HashSet<>();
        for (Booking b : list) out.add(StayCalendarModel.keyOf(b));
        return out;
    }

    @Test
    public void noTransactionIsHiddenFromBothSections_onAnySelectedDay() {
        List<Booking> all = sample();
        for (int offset = -20; offset <= 60; offset++) {
            LocalDate selected = TODAY.plusDays(offset);
            List<Booking> shown = new ArrayList<>();
            for (NextStay s : NextStay.forDay(all, selected, TODAY)) shown.add(s.booking);
            OtherStays others = OtherStays.build(all, shown);
            Set<String> seen = keys(shown);
            seen.addAll(keys(others.upcoming));
            seen.addAll(keys(others.recent));
            for (Booking b : all) {
                if (StayCalendarModel.isClosed(b)) continue; // cancelled / rejected are found through the calendar's toggle
                assertTrue("day " + selected + ": " + StayCalendarModel.keyOf(b) + " is in neither section", seen.contains(StayCalendarModel.keyOf(b)));
            }
        }
    }

    @Test
    public void whatTheSelectedDayShows_isLeftOutOfOtherStays_andComesBackWhenTheDayChanges() {
        List<Booking> all = sample();
        List<Booking> day3 = new ArrayList<>();
        for (NextStay s : NextStay.forDay(all, TODAY.plusDays(3), TODAY)) day3.add(s.booking);
        assertEquals(2, day3.size());
        OtherStays withDay3 = OtherStays.build(all, day3);
        assertFalse(keys(withDay3.upcoming).containsAll(keys(day3)) || keys(withDay3.upcoming).contains(StayCalendarModel.keyOf(day3.get(0))));

        OtherStays withNothing = OtherStays.build(all, new ArrayList<>());
        assertTrue(keys(withNothing.upcoming).containsAll(keys(day3)));
    }

    @Test
    public void aReservationAndABookingWithTheSameId_areDifferentTransactions() {
        List<Booking> all = sample();
        List<Booking> shown = new ArrayList<>();
        for (NextStay s : NextStay.forDay(all, TODAY.minusDays(9), TODAY)) shown.add(s.booking); // booking #1 only
        OtherStays others = OtherStays.build(all, shown);
        assertTrue("reservation #1 still listed", keys(others.upcoming).contains("R:1"));
    }

    @Test
    public void defaultDay_isTodayWhenItHasATransaction() {
        assertEquals(TODAY, NextStay.defaultDay(sample(), TODAY));
    }

    @Test
    public void defaultDay_isTheNearestLaterDayWithATransaction_whenTodayIsEmpty() {
        List<Booking> later = Arrays.asList(stay("1", -10, -8, "Checked-Out", false), stay("2", 3, 5, "Confirmed", false),
                stay("3", 7, 8, "Confirmed", true), stay("4", 1, 2, "Cancelled", false));
        assertEquals(TODAY.plusDays(3), NextStay.defaultDay(later, TODAY));
    }

    @Test
    public void defaultDay_isTodayWhenThereIsNothingUpcoming() {
        List<Booking> past = Arrays.asList(stay("1", -10, -8, "Checked-Out", false));
        assertEquals(TODAY, NextStay.defaultDay(past, TODAY));
        assertEquals(TODAY, NextStay.defaultDay(new ArrayList<>(), TODAY));
    }

    @Test
    public void calendar_cancelledAreHiddenUnlessAsked() {
        StayCalendarModel m = new StayCalendarModel(sample());
        int hidden = m.transactionsFor(StayCalendarModel.Mode.MONTHLY, TODAY).size();
        List<Booking> withClosed = m.transactionsFor(StayCalendarModel.Mode.MONTHLY, TODAY, true);
        assertEquals(hidden + 2, withClosed.size());
    }
}
