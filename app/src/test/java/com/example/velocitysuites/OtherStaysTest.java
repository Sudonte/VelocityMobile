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
    public void otherStays_isIndependentOfYourNextStay_onAnySelectedDay() {
        List<Booking> all = sample();
        OtherStays reference = OtherStays.build(all);
        for (int offset = -20; offset <= 60; offset++) {
            // whatever day Your Next Stay shows, this section is the same
            assertEquals(keys(reference.upcoming), keys(OtherStays.build(all).upcoming));
            assertEquals(keys(reference.recent), keys(OtherStays.build(all).recent));
        }
        assertEquals("every non-cancelled transaction is listed", 7, reference.size());
        for (Booking b : all) {
            if (StayCalendarModel.isClosed(b)) continue;
            assertTrue(StayCalendarModel.keyOf(b), keys(reference.upcoming).contains(StayCalendarModel.keyOf(b)) || keys(reference.recent).contains(StayCalendarModel.keyOf(b)));
        }
    }

    @Test
    public void aReservationAndABookingWithTheSameId_areDifferentTransactions() {
        OtherStays others = OtherStays.build(sample());
        assertTrue("reservation #1 listed", keys(others.upcoming).contains("R:1"));
        assertTrue("booking #1 listed as recent", keys(others.recent).contains("B:1"));
    }

    @Test
    public void nothingToList_givesAnEmptyResult_notACrash() {
        assertEquals(0, OtherStays.build(new ArrayList<>()).size());
        assertEquals(0, OtherStays.build(null).size());
        assertEquals(0, OtherStays.build(Arrays.asList(stay("7", 2, 3, "Cancelled", false))).size());
    }

    @Test
    public void tags_aCheckedInStayIsCurrent_neverUpcoming_aCheckedOutOneIsRecent() {
        assertEquals(StayCardBinder.Tag.CURRENT, StayCardBinder.tagFor(stay("2", -1, 2, "Checked-In", false)));
        assertEquals(StayCardBinder.Tag.UPCOMING, StayCardBinder.tagFor(stay("4", 3, 5, "Pending", false)));
        assertEquals(StayCardBinder.Tag.UPCOMING, StayCardBinder.tagFor(stay("3", 0, 1, "Confirmed", true)));
        assertEquals(StayCardBinder.Tag.RECENT, StayCardBinder.tagFor(stay("1", -10, -8, "Checked-Out", false)));
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
