package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A confirmed booking's balance is settled at the front desk: the message appears exactly when TransactionStatusHelper
 * (the server's payment summary, verified payments only) says something is still owed, and never for a plain
 * reservation, a paid, cancelled or rejected booking.
 */
public class FrontDeskPaymentRuleTest {

    private static Booking confirmed(String status, double total, double summaryPaid) {
        Booking b = new Booking("588", "1", "Deluxe", "Deluxe", "Oct 01, 2026", "Oct 02, 2026", 2, total, status, "");
        b.setHasBooking(true);
        b.setPaymentSummary(new Booking.PaymentSummary(total, summaryPaid, Math.max(0, total - summaryPaid), "PARTIALLY_PAID", null, false, 0));
        return b;
    }

    @Test
    public void confirmedBookingWithABalance_showsTheServersRemainingBalance() {
        Booking b = confirmed("Confirmed", 2000, 800);
        assertTrue(PaymentEligibility.needsFrontDeskPayment(b));
        assertEquals(1200, PaymentEligibility.frontDeskBalance(b), 0);
    }

    @Test
    public void confirmedBookingWithNothingVerifiedYet_owesTheWholeTotal() {
        Booking b = confirmed("Confirmed", 2000, 0);
        assertTrue(PaymentEligibility.needsFrontDeskPayment(b));
        assertEquals(2000, PaymentEligibility.frontDeskBalance(b), 0);
    }

    @Test
    public void fullyPaidBooking_hasNoMessage() {
        assertFalse(PaymentEligibility.needsFrontDeskPayment(confirmed("Confirmed", 2000, 2000)));
        assertFalse(PaymentEligibility.needsFrontDeskPayment(confirmed("Confirmed", 2000, 2500)));
    }

    @Test
    public void cancelledOrRejectedBooking_hasNoMessage() {
        assertFalse(PaymentEligibility.needsFrontDeskPayment(confirmed("Cancelled", 2000, 800)));
        assertFalse(PaymentEligibility.needsFrontDeskPayment(confirmed("Rejected", 2000, 0)));
    }

    @Test
    public void aReservationThatIsNotABookingYet_isPaidInTheApp_soNoFrontDeskMessage() {
        Booking reservation = new Booking("588", "1", "Deluxe", "Deluxe", "Oct 01, 2026", "Oct 02, 2026", 2, 2000, "Pending", "");
        reservation.setHasBooking(false);
        assertFalse(PaymentEligibility.needsFrontDeskPayment(reservation));
        assertFalse(PaymentEligibility.needsFrontDeskPayment(null));
    }
}
