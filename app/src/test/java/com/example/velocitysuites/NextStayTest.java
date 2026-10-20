package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ui.TestApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/** The Dashboard's next stay: which stay it is, the plain hint, nights, plain-language status and the balance. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class NextStayTest {

    @org.junit.Rule
    public final ThemeResetRule themeReset = new ThemeResetRule();

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US);

    private static Booking stay(String id, LocalDate in, LocalDate out, String status, boolean hasBooking, boolean verified, boolean paymentPending) {
        Booking b = new Booking(id, "1", "Deluxe", "Deluxe", in.format(FMT), out.format(FMT), 2, 3600, status, "Oct 01, 2026");
        b.setHasBooking(hasBooking);
        b.setStaffVerified(verified);
        b.setPaymentPendingVerification(paymentPending);
        return b;
    }

    private static Booking upcoming(String id, int daysAway, int nights) {
        return stay(id, TODAY.plusDays(daysAway), TODAY.plusDays(daysAway + nights), "Confirmed", true, true, false);
    }

    private final Context c = ApplicationProvider.getApplicationContext();

    @Test
    public void aGuestCheckedInBeforeTheirFirstDayIsToldSo_notGivenACheckoutCountdown() {
        Booking early = stay("9", TODAY.plusDays(2), TODAY.plusDays(3), "Checked-In", true, true, false);
        assertEquals("Checked in early - your stay starts Oct 13, 2026", NextStay.select(Collections.singletonList(early), TODAY).hint(c));
        Booking onTime = stay("8", TODAY, TODAY.plusDays(3), "Checked-In", true, true, false);
        assertEquals("Checking out in 3 days", NextStay.select(Collections.singletonList(onTime), TODAY).hint(c));
    }

    // ---- which stay

    @Test
    public void nothingToShowWhenThereAreNoStays() {
        assertNull(NextStay.select(null, TODAY));
        assertNull(NextStay.select(Collections.emptyList(), TODAY));
    }

    @Test
    public void theNearestUpcomingCheckInWins() {
        Booking far = upcoming("1", 9, 2);
        Booking near = upcoming("2", 2, 1);
        assertSame(near, NextStay.select(Arrays.asList(far, near), TODAY).booking);
    }

    @Test
    public void aGuestInHouseShowsTheirCheckOut() {
        Booking inHouse = stay("1", TODAY.minusDays(1), TODAY.plusDays(2), "Checked-In", true, true, false);
        Booking later = upcoming("2", 5, 1);
        NextStay next = NextStay.select(Arrays.asList(later, inHouse), TODAY);
        assertSame(inHouse, next.booking);
        assertEquals(NextStay.Phase.IN_HOUSE, next.phase);
        assertEquals(2, next.daysToEvent);
    }

    @Test
    public void cancelledFinishedAndPastStaysNeverQualify() {
        Booking cancelled = stay("1", TODAY.plusDays(1), TODAY.plusDays(2), "Cancelled", true, true, false);
        Booking done = stay("2", TODAY.minusDays(5), TODAY.minusDays(3), "Checked-Out", true, true, false);
        Booking missed = stay("3", TODAY.minusDays(2), TODAY.minusDays(1), "Confirmed", true, true, false);
        assertNull(NextStay.select(Arrays.asList(cancelled, done, missed), TODAY));
    }

    @Test
    public void aCheckInTodayStillCounts() {
        assertEquals(0, NextStay.select(Collections.singletonList(upcoming("1", 0, 1)), TODAY).daysToEvent);
    }

    // ---- nights, dates, balance

    @Test
    public void nightsAreTheCalendarDaysBetweenTheDates() {
        NextStay next = NextStay.select(Collections.singletonList(upcoming("1", 3, 4)), TODAY);
        assertEquals(4, next.nights);
        assertEquals("4 nights", next.nightsText(c));
        assertEquals("Oct 14, 2026 → Oct 18, 2026", next.datesText(c));
    }

    @Test
    public void oneNightReadsAsOneNight() {
        assertEquals("1 night", NextStay.select(Collections.singletonList(upcoming("1", 3, 1)), TODAY).nightsText(c));
    }

    @Test
    public void theBalanceIsWhatIsStillOwed() {
        Booking b = upcoming("1", 3, 1);
        b.setPaymentHistory(new java.util.ArrayList<>(Collections.singletonList(
                new Booking.PaymentRecord("1000.00", "CASH", null, "Oct 10, 2026 10:00 AM", "completed"))));
        b.setAmountPaid(1000);
        NextStay next = NextStay.select(Collections.singletonList(b), TODAY);
        assertTrue(next.hasBalanceDue());
        assertEquals(2600.0, next.balance, 0.01);
    }

    // ---- several transactions on the same day

    @Test
    public void everyTransactionOnTheNearestDayIsKept_bookingsAndReservationsAlike() {
        Booking booking = upcoming("1", 2, 1);
        Booking reservation = stay("2", TODAY.plusDays(2), TODAY.plusDays(4), "Pending", false, false, false);
        Booking later = upcoming("3", 5, 1);
        java.util.List<NextStay> all = NextStay.selectAll(Arrays.asList(later, reservation, booking), TODAY);
        assertEquals(2, all.size());
        assertTrue(all.get(0).booking == booking && all.get(1).booking == reservation);
        assertSame("select() is the first of them", booking, NextStay.select(Arrays.asList(later, reservation, booking), TODAY).booking);
    }

    @Test
    public void aSingleNearestStayIsAListOfOne_andNothingIsAnEmptyList() {
        assertEquals(1, NextStay.selectAll(Arrays.asList(upcoming("1", 2, 1), upcoming("2", 3, 1)), TODAY).size());
        assertTrue(NextStay.selectAll(null, TODAY).isEmpty());
        assertTrue(NextStay.selectAll(Collections.emptyList(), TODAY).isEmpty());
    }

    @Test
    public void inHouseStaysComeFirstAmongTheSameDay() {
        Booking arriving = upcoming("1", 2, 1);
        Booking leaving = stay("2", TODAY.minusDays(1), TODAY.plusDays(2), "Checked-In", true, true, false);
        java.util.List<NextStay> all = NextStay.selectAll(Arrays.asList(arriving, leaving), TODAY);
        assertEquals(2, all.size());
        assertSame(leaving, all.get(0).booking);
    }

    // ---- the plain hint

    @Test
    public void hintsForAnUpcomingStay() {
        assertEquals("Check-in today", NextStay.select(Collections.singletonList(upcoming("1", 0, 1)), TODAY).hint(c));
        assertEquals("Check-in tomorrow", NextStay.select(Collections.singletonList(upcoming("1", 1, 1)), TODAY).hint(c));
        assertEquals("Check-in is 4 days away", NextStay.select(Collections.singletonList(upcoming("1", 4, 1)), TODAY).hint(c));
        assertEquals("Check-in on Oct 25, 2026", NextStay.select(Collections.singletonList(upcoming("1", 14, 1)), TODAY).hint(c));
    }

    @Test
    public void hintsForAGuestInHouse() {
        assertEquals("Checking out today", inHouseHint(0));
        assertEquals("Checking out tomorrow", inHouseHint(1));
        assertEquals("Checking out in 3 days", inHouseHint(3));
        assertEquals("Check-out was due on Oct 10, 2026", inHouseHint(-1));
    }

    private String inHouseHint(int checkOutInDays) {
        Booking b = stay("1", TODAY.minusDays(3), TODAY.plusDays(checkOutInDays), "Checked-In", true, true, false);
        return NextStay.select(Collections.singletonList(b), TODAY).hint(c);
    }

    // ---- plain-language status

    @Test
    public void statusesUsePlainLanguage_notInternalCodes() {
        assertEquals(NextStay.PlainStatus.STAYING, NextStay.plainStatus(stay("1", TODAY, TODAY.plusDays(1), "Checked-In", true, true, false)));
        assertEquals(NextStay.PlainStatus.CONFIRMED, NextStay.plainStatus(stay("1", TODAY, TODAY.plusDays(1), "Confirmed", true, true, false)));
        assertEquals(NextStay.PlainStatus.WAITING_CONFIRMATION, NextStay.plainStatus(stay("1", TODAY, TODAY.plusDays(1), "Pending", true, false, false)));
        assertEquals(NextStay.PlainStatus.PAYMENT_REVIEW, NextStay.plainStatus(stay("1", TODAY, TODAY.plusDays(1), "Pending", true, false, true)));
        assertEquals(NextStay.PlainStatus.WAITING_PAYMENT, NextStay.plainStatus(stay("1", TODAY, TODAY.plusDays(1), "Pending", false, false, false)));
        assertEquals(NextStay.PlainStatus.PAYMENT_REVIEW, NextStay.plainStatus(stay("1", TODAY, TODAY.plusDays(1), "Pending", false, false, true)));

        assertEquals("Awaiting verification", c.getString(NextStay.PlainStatus.WAITING_CONFIRMATION.labelRes));
        assertEquals("Staying with us", c.getString(NextStay.PlainStatus.STAYING.labelRes));
    }

    @Test
    public void everyStatusHasTextAndATone_soNeverColourAlone() {
        for (NextStay.PlainStatus status : NextStay.PlainStatus.values()) {
            assertNotNull(status.tone);
            assertFalse(c.getString(status.labelRes).trim().isEmpty());
        }
    }
}
