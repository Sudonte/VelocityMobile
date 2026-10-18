package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Which transactions the Dashboard's Payment Status lists, in what order, and what its Pay action is. */
public class PaymentStatusListTest {

    private static Booking base(String id, String status, double total, String checkIn, boolean hasBooking) {
        Booking b = new Booking(id, "1", "Deluxe", "Deluxe", checkIn, "Dec 31, 2026", 2, total, status, "");
        b.setHasBooking(hasBooking);
        b.setDirectBooking(hasBooking);
        b.setAmountPaid(0);
        return b;
    }

    private static void pay(Booking b, String amount, String status) {
        List<Booking.PaymentRecord> rows = new ArrayList<>(b.getPaymentHistory());
        rows.add(new Booking.PaymentRecord(amount, "GCASH", "REF" + b.getId(), "Sep 30, 2026 • 10:09 PM", status));
        b.setPaymentHistory(rows);
        if ("pending".equals(status)) b.setPaymentPendingVerification(true);
    }

    private static List<String> ids(List<Booking> list) {
        List<String> out = new ArrayList<>();
        for (Booking b : list) out.add(b.getId());
        return out;
    }

    @Test
    public void checkedOutCancelledAndRejectedAreLeftOut_everyOtherStateIsListed() {
        Booking unpaid = base("1", "Pending", 1800, "Oct 21, 2026", false);
        Booking partial = base("2", "Confirmed", 2000, "Oct 22, 2026", true);
        pay(partial, "1000.00", "completed");
        Booking full = base("3", "Confirmed", 1500, "Oct 23, 2026", true);
        pay(full, "1500.00", "completed");
        Booking inHouse = base("4", "Checked-In", 1500, "Oct 18, 2026", true);
        pay(inHouse, "1500.00", "completed");
        Booking out = base("5", "Checked-Out", 1500, "Oct 10, 2026", true);
        pay(out, "1500.00", "completed");
        Booking cancelled = base("6", "Cancelled", 1500, "Oct 25, 2026", false);
        Booking rejected = base("7", "Rejected", 1500, "Oct 26, 2026", false);

        List<Booking> listed = PaymentStatusList.select(Arrays.asList(unpaid, partial, full, inHouse, out, cancelled, rejected));
        assertEquals("unpaid, then partly paid, then fully paid (nearest check-in first)", Arrays.asList("1", "2", "4", "3"), ids(listed));
    }

    @Test
    public void withinAGroup_theNearestCheckInComesFirst_andAnUnreadableDateGoesLast() {
        Booking late = base("1", "Pending", 1800, "Nov 30, 2026", false);
        Booking soon = base("2", "Pending", 1800, "Oct 20, 2026", false);
        Booking unreadable = base("3", "Pending", 1800, "not a date", false);
        assertEquals(Arrays.asList("2", "1", "3"), ids(PaymentStatusList.select(Arrays.asList(late, unreadable, soon))));
    }

    @Test
    public void payAction_isPayNowForAnyReservationWithABalance_whateverItsPaymentMethod() {
        Booking gcash = base("1", "Pending", 1800, "Oct 21, 2026", false);
        gcash.setPaymentMethod("GCASH");
        Booking cash = base("2", "Pending", 1800, "Oct 21, 2026", false);
        cash.setPaymentMethod("CASH");
        Booking none = base("3", "Pending", 1800, "Oct 21, 2026", false);
        assertEquals(PaymentEligibility.PayAction.PAY_NOW, PaymentEligibility.payActionFor(gcash));
        assertEquals("the cause of the missing button: the old rule demanded GCASH", PaymentEligibility.PayAction.PAY_NOW, PaymentEligibility.payActionFor(cash));
        assertEquals(PaymentEligibility.PayAction.PAY_NOW, PaymentEligibility.payActionFor(none));
    }

    @Test
    public void payAction_underReviewFrontDeskOrNone() {
        Booking review = base("1", "Pending", 1800, "Oct 21, 2026", false);
        pay(review, "1800.00", "pending");
        assertEquals(PaymentEligibility.PayAction.UNDER_REVIEW, PaymentEligibility.payActionFor(review));

        Booking booking = base("2", "Confirmed", 2000, "Oct 21, 2026", true);
        pay(booking, "1000.00", "completed");
        assertEquals("a booking is paid at the front desk", PaymentEligibility.PayAction.FRONT_DESK, PaymentEligibility.payActionFor(booking));

        Booking paid = base("3", "Confirmed", 1500, "Oct 21, 2026", true);
        pay(paid, "1500.00", "completed");
        assertEquals(PaymentEligibility.PayAction.NONE, PaymentEligibility.payActionFor(paid));

        Booking cancelled = base("4", "Cancelled", 1500, "Oct 21, 2026", false);
        assertEquals(PaymentEligibility.PayAction.NONE, PaymentEligibility.payActionFor(cancelled));
        assertFalse(PaymentStatusList.isActive(cancelled));
        assertTrue(PaymentStatusList.isActive(review));
    }
}
