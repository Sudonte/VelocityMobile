package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ui.TestApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The Dashboard's Stay Calendar: which days are marked, same-day transactions, the Daily/Weekly/Monthly windows, HH:MM:SS times. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class StayCalendarModelTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US);

    private static Booking stay(String id, LocalDate in, LocalDate out, String status) {
        Booking b = new Booking(id, "1", "Deluxe", "Deluxe", in.format(FMT), out.format(FMT), 2, 3600, status, "Oct 01, 2026");
        b.setHasBooking(true);
        return b;
    }

    @Test
    public void checkInAndCheckOutDaysAreMarked() {
        StayCalendarModel m = new StayCalendarModel(List.of(
                stay("1", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22), "Confirmed")));
        assertTrue(m.hasEntries(LocalDate.of(2026, 10, 20)));
        assertTrue(m.hasEntries(LocalDate.of(2026, 10, 22)));
        assertFalse(m.hasEntries(LocalDate.of(2026, 10, 21)));
    }

    @Test
    public void severalTransactionsOnTheSameDayAreAllKept() {
        StayCalendarModel m = new StayCalendarModel(Arrays.asList(
                stay("1", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22), "Confirmed"),
                stay("2", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21), "Confirmed"),
                stay("3", LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 20), "Confirmed")));
        LocalDate day = LocalDate.of(2026, 10, 20);
        // two arrivals and one departure on the 20th
        assertEquals(3, m.entriesBetween(day, day).size());
    }

    @Test
    public void cancelledStaysNeverAppear_finishedOnesWithNothingRecordedNeither_andAnInHouseStayKeepsOnlyItsDeparture() {
        StayCalendarModel m = new StayCalendarModel(Arrays.asList(
                stay("1", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22), "Cancelled"),
                stay("2", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22), "Checked-Out"),
                stay("3", LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 16), "Checked-In")));
        assertFalse(m.hasEntries(LocalDate.of(2026, 10, 20)));
        assertFalse(m.hasEntries(LocalDate.of(2026, 10, 14)));
        assertTrue(m.hasEntries(LocalDate.of(2026, 10, 16)));
    }

    @Test
    public void recordedCheckInAndOutShowTheirActualTimeToTheSecond_andUnrecordedOnesUseThePostedTime() {
        Booking inHouse = stay("1", LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 16), "Checked-In");
        inHouse.setCheckedInAtRaw("2026-10-14T06:03:09Z"); // 14:03:09 in Manila
        Booking done = stay("2", LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 12), "Checked-Out");
        done.setCheckedInAtRaw("2026-10-10T07:15:00Z");
        done.setCheckedOutAtRaw("2026-10-12T03:45:30Z"); // 11:45:30
        StayCalendarModel m = new StayCalendarModel(Arrays.asList(inHouse, done));

        StayCalendarModel.Entry in = m.entriesBetween(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 14)).get(0);
        assertTrue(in.isActual());
        assertEquals("02:03:09 PM", in.timeText());
        StayCalendarModel.Entry out = m.entriesBetween(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12)).get(0);
        assertEquals("11:45:30 AM", out.timeText());
        // the in-house stay's departure has not happened: posted time
        StayCalendarModel.Entry due = m.entriesBetween(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16)).get(0);
        assertFalse(due.isActual());
        assertEquals("12:00:00 PM", due.timeText());
    }

    @Test
    public void windowsCoverTheDayTheSundayStartWeekAndTheMonth() {
        LocalDate wed = LocalDate.of(2026, 10, 14);
        assertEquals(wed, StayCalendarModel.windowStart(StayCalendarModel.Mode.DAILY, wed));
        assertEquals(LocalDate.of(2026, 10, 11), StayCalendarModel.windowStart(StayCalendarModel.Mode.WEEKLY, wed));
        assertEquals(LocalDate.of(2026, 10, 17), StayCalendarModel.windowEnd(StayCalendarModel.Mode.WEEKLY, wed));
        assertEquals(LocalDate.of(2026, 10, 1), StayCalendarModel.windowStart(StayCalendarModel.Mode.MONTHLY, wed));
        assertEquals(LocalDate.of(2026, 10, 31), StayCalendarModel.windowEnd(StayCalendarModel.Mode.MONTHLY, wed));
    }

    @Test
    public void entriesFollowTheChosenWindowInDateThenTimeOrder() {
        StayCalendarModel m = new StayCalendarModel(Arrays.asList(
                stay("1", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22), "Confirmed"),
                stay("2", LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 20), "Confirmed")));
        LocalDate anchor = LocalDate.of(2026, 10, 20);
        assertEquals(2, m.entriesFor(StayCalendarModel.Mode.DAILY, anchor).size());
        assertEquals(4, m.entriesFor(StayCalendarModel.Mode.MONTHLY, anchor).size());
        List<StayCalendarModel.Entry> day = m.entriesFor(StayCalendarModel.Mode.DAILY, anchor);
        // the 12:00 check-out comes before the 14:00 check-in
        assertFalse(day.get(0).checkIn);
        assertTrue(day.get(1).checkIn);
    }

    @Test
    public void timesShowHoursMinutesAndSeconds() {
        StayCalendarModel m = new StayCalendarModel(List.of(
                stay("1", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22), "Confirmed")));
        List<StayCalendarModel.Entry> in = m.entriesBetween(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20));
        List<StayCalendarModel.Entry> out = m.entriesBetween(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 22));
        assertEquals("02:00:00 PM", in.get(0).timeText());
        assertEquals("12:00:00 PM", out.get(0).timeText());
    }
}
