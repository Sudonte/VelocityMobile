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
        b.setPaymentSummary(new Booking.PaymentSummary(10000.0, 10000.0, 0.0, "PAID", 100, true, 0));

        assertEquals(PaymentStatusResolver.StatusKey.FULLY_PAID, PaymentStatusResolver.resolveStatusKey(b));
    }

    @Test
    public void authoritativePartiallyPaid_resolvesToPartialState() {
        Booking b = booking();
        b.setAmountPaid(10000.0); // legacy says fully paid - authoritative status must still win
        b.setPaymentSummary(new Booking.PaymentSummary(10000.0, 5000.0, 5000.0, "PARTIALLY_PAID", 50, false, 0));

        assertEquals(PaymentStatusResolver.StatusKey.PARTIALLY_PAID, PaymentStatusResolver.resolveStatusKey(b));
    }

    @Test
    public void authoritativePending_resolvesToPendingState() {
        Booking b = booking();
        b.setAmountPaid(0.0);
        b.setPaymentSummary(new Booking.PaymentSummary(10000.0, 0.0, 10000.0, "PENDING", null, false, 0));

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
        b.setPaymentSummary(new Booking.PaymentSummary(10000.0, 10000.0, 0.0, "PAID", 100, true, 0));

        assertEquals(PaymentStatusResolver.StatusKey.CANCELLED, PaymentStatusResolver.resolveStatusKey(b));
    }

    @Test
    public void rejectedLatestPayment_doesNotUndoAlreadyVerifiedMoney() {
        // The status now follows the receptionist-VERIFIED amount (TransactionStatusHelper): 5,000 of 10,000 is
        // verified, so the transaction is Partially Paid even though the latest payment attempt was rejected.
        // (It used to read "Rejected", hiding the money that had already been verified.)
        Booking b = booking();
        b.setPaymentVerificationStatus("rejected");
        b.setPaymentSummary(new Booking.PaymentSummary(10000.0, 5000.0, 5000.0, "PARTIALLY_PAID", 50, false, 0));

        assertEquals(PaymentStatusResolver.StatusKey.PARTIALLY_PAID, PaymentStatusResolver.resolveStatusKey(b));
    }

    @Test
    public void rejectedOnlyPayment_isRejected() {
        Booking b = booking();
        b.setAmountPaid(0.0);
        b.setPaymentVerificationStatus("rejected");

        assertEquals(PaymentStatusResolver.StatusKey.REJECTED, PaymentStatusResolver.resolveStatusKey(b));
    }

    @Test
    public void submittedButUnverifiedPayment_isPendingVerification_notPaid() {
        Booking b = booking();
        b.setAmountPaid(10000.0); // a direct booking counts what was SUBMITTED here
        b.setPaymentHistory(new java.util.ArrayList<>(java.util.Collections.singletonList(
                new Booking.PaymentRecord("10000.00", "GCASH", "1234567890123", "Sep 30, 2026 • 10:09 PM", "pending"))));
        b.setPaymentPendingVerification(true);

        assertEquals(PaymentStatusResolver.StatusKey.PENDING_VERIFICATION, PaymentStatusResolver.resolveStatusKey(b));
    }

    @Test
    public void reservationWithNothingSubmitted_isNoPaymentYet() {
        Booking b = booking();
        b.setHasBooking(false);
        b.setAmountPaid(0.0);

        assertEquals(PaymentStatusResolver.StatusKey.NO_PAYMENT_YET, PaymentStatusResolver.resolveStatusKey(b));
    }

    @Test
    public void effectiveTotals_preferAuthoritativePaymentSummaryOverLegacyFields() {
        Booking b = booking();
        b.setAmountPaid(2000.0);
        b.setPaymentSummary(new Booking.PaymentSummary(10000.0, 7000.0, 3000.0, "PARTIALLY_PAID", 70, false, 0));

        assertEquals(7000.0, b.getEffectiveTotalAmountPaid(), 0.001);
        assertEquals(3000.0, b.getEffectiveRemainingBalance(), 0.001);
    }
}
