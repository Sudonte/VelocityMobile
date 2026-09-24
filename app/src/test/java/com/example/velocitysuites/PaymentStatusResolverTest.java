package com.example.velocitysuites;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Pure-JVM coverage for PaymentStatusResolver#resolveStatusKey() - the
 * Context-free decision resolve()/Result delegate to (see that method's own
 * doc for why it was extracted; this project has no Robolectric, so
 * resolve() itself, which touches Context.getString(), can't be unit tested
 * directly).
 */
public class PaymentStatusResolverTest {

    private static Booking booking() {
        Booking b = new Booking("B1", "R1", "Deluxe 101", "Deluxe",
                "May 01, 2025", "May 05, 2025", 2, 10000.0, "Confirmed", "Apr 30, 2025");
        b.setHasBooking(true);
        return b;
    }

    @Test
    public void authoritativePaid_winsEvenWhenLegacyAmountSaysOtherwise() {
        Booking b = booking();
        // Legacy fields deliberately say "not fully paid" - only the
        // authoritative payment_summary.payment_status should decide.
        b.setAmountPaid(2000.0);
        b.setPaymentSummary(new Booking.PaymentSummary(10000.0, 10000.0, 0.0, "PAID", 100, true));

        assertEquals(PaymentStatusResolver.StatusKey.FULLY_PAID, PaymentStatusResolver.resolveStatusKey(b));
    }

    @Test
    public void authoritativePartiallyPaid_resolvesToPartialState() {
        Booking b = booking();
        b.setAmountPaid(10000.0); // legacy says fully paid - authoritative status must still win
        b.setPaymentSummary(new Booking.PaymentSummary(10000.0, 5000.0, 5000.0, "PARTIALLY_PAID", 50, false));

        assertEquals(PaymentStatusResolver.StatusKey.PARTIALLY_PAID, PaymentStatusResolver.resolveStatusKey(b));
    }

    @Test
    public void authoritativePending_resolvesToPendingState() {
        Booking b = booking();
        b.setAmountPaid(0.0);
        b.setPaymentSummary(new Booking.PaymentSummary(10000.0, 0.0, 10000.0, "PENDING", null, false));

        assertEquals(PaymentStatusResolver.StatusKey.PENDING, PaymentStatusResolver.resolveStatusKey(b));
    }

    @Test
    public void noPaymentSummary_fallsBackToLegacyFullyPaidComparison() {
        Booking b = booking();
        b.setAmountPaid(10000.0); // totalAmount == amountPaid -> remaining balance 0

        assertEquals(PaymentStatusResolver.StatusKey.FULLY_PAID, PaymentStatusResolver.resolveStatusKey(b));
    }

    @Test
    public void noPaymentSummary_fallsBackToLegacyPartialComparison() {
        Booking b = booking();
        b.setAmountPaid(4000.0);

        assertEquals(PaymentStatusResolver.StatusKey.PARTIALLY_PAID, PaymentStatusResolver.resolveStatusKey(b));
    }

    @Test
    public void noPaymentSummary_fallsBackToLegacyPendingComparison() {
        Booking b = booking();
        b.setAmountPaid(0.0);

        assertEquals(PaymentStatusResolver.StatusKey.PENDING, PaymentStatusResolver.resolveStatusKey(b));
    }

    @Test
    public void cancelledBooking_alwaysWinsRegardlessOfPaymentSummary() {
        Booking b = booking();
        b.setStatus("Cancelled");
        b.setPaymentSummary(new Booking.PaymentSummary(10000.0, 10000.0, 0.0, "PAID", 100, true));

        assertEquals(PaymentStatusResolver.StatusKey.CANCELLED, PaymentStatusResolver.resolveStatusKey(b));
    }

    @Test
    public void rejectedPayment_winsOverAuthoritativePaymentSummary() {
        Booking b = booking();
        b.setPaymentVerificationStatus("rejected");
        b.setPaymentSummary(new Booking.PaymentSummary(10000.0, 5000.0, 5000.0, "PARTIALLY_PAID", 50, false));

        assertEquals(PaymentStatusResolver.StatusKey.REJECTED, PaymentStatusResolver.resolveStatusKey(b));
    }

    @Test
    public void effectiveTotals_preferAuthoritativePaymentSummaryOverLegacyFields() {
        Booking b = booking();
        b.setAmountPaid(2000.0);
        b.setPaymentSummary(new Booking.PaymentSummary(10000.0, 7000.0, 3000.0, "PARTIALLY_PAID", 70, false));

        assertEquals(7000.0, b.getEffectiveTotalAmountPaid(), 0.001);
        assertEquals(3000.0, b.getEffectiveRemainingBalance(), 0.001);
    }
}
