package com.example.velocitysuites;

import org.junit.Test;

import static com.example.velocitysuites.PaymentTransactionStatus.Key.CANCELLED;
import static com.example.velocitysuites.PaymentTransactionStatus.Key.PAID;
import static com.example.velocitysuites.PaymentTransactionStatus.Key.PENDING;
import static com.example.velocitysuites.PaymentTransactionStatus.Key.UNPAID;
import static org.junit.Assert.assertEquals;

/**
 * Pins the Paid / Pending / Cancelled / Unpaid decision every Transaction History row and the
 * detail screen's header share. The headline case is the one the old per-screen copies got wrong:
 * a payment that has already been verified must read "Paid" even while a DIFFERENT payment on the
 * same booking is still awaiting verification.
 */
public class PaymentTransactionStatusTest {

    // ---- an itemized payment judged by its own status ----

    @Test
    public void completedPayment_isPaid() {
        assertEquals(PAID, PaymentTransactionStatus.resolve("completed", "Confirmed", false, 2900.0));
    }

    @Test
    public void pendingPayment_isPending() {
        assertEquals(PENDING, PaymentTransactionStatus.resolve("pending", "Pending", true, 2900.0));
    }

    @Test
    public void rejectedOrFailedPayment_isCancelled() {
        assertEquals(CANCELLED, PaymentTransactionStatus.resolve("rejected", "Pending", false, 2900.0));
        assertEquals(CANCELLED, PaymentTransactionStatus.resolve("failed", "Pending", false, 2900.0));
    }

    @Test
    public void aVerifiedPayment_staysPaid_evenWhileAnotherPaymentOnTheSameBookingIsPending() {
        // The regression this class exists for: booking-level "a payment is pending" must NOT relabel a completed one.
        assertEquals(PAID, PaymentTransactionStatus.resolve("completed", "Confirmed", true, 1000.0));
        assertEquals(PENDING, PaymentTransactionStatus.resolve("pending", "Confirmed", true, 1900.0));
    }

    @Test
    public void statusMatchingIsCaseAndWhitespaceInsensitive() {
        assertEquals(PAID, PaymentTransactionStatus.resolve("  Completed ", "Confirmed", false, 100.0));
        assertEquals(PENDING, PaymentTransactionStatus.resolve("PENDING", "Confirmed", false, 100.0));
        assertEquals(CANCELLED, PaymentTransactionStatus.resolve("Rejected", "Confirmed", false, 100.0));
    }

    // ---- the whole transaction cancelled ----

    @Test
    public void aCancelledOrRejectedTransaction_makesEveryRowCancelled_evenACompletedPayment() {
        assertEquals(CANCELLED, PaymentTransactionStatus.resolve("completed", "Cancelled", false, 2900.0));
        assertEquals(CANCELLED, PaymentTransactionStatus.resolve("completed", "Rejected", false, 2900.0));
        assertEquals(CANCELLED, PaymentTransactionStatus.resolve(null, "Cancelled", false, 0.0));
        assertEquals(CANCELLED, PaymentTransactionStatus.resolve("completed", "canceled", false, 2900.0));
    }

    // ---- rows without an itemized payment status: booking-level fallback ----

    @Test
    public void noPaymentStatus_pendingTransaction_isPending() {
        assertEquals(PENDING, PaymentTransactionStatus.resolve(null, "Pending", false, 0.0));
        assertEquals(PENDING, PaymentTransactionStatus.resolve("", "Pending", false, 2900.0));
    }

    @Test
    public void noPaymentStatus_butAPaymentAwaitsVerification_isPending() {
        assertEquals(PENDING, PaymentTransactionStatus.resolve(null, "Confirmed", true, 0.0));
    }

    @Test
    public void noPaymentStatus_noAmount_isUnpaid_neverPaidNextToZeroPesos() {
        // A confirmed cash reservation nobody has paid against yet.
        assertEquals(UNPAID, PaymentTransactionStatus.resolve(null, "Confirmed", false, 0.0));
        assertEquals(UNPAID, PaymentTransactionStatus.resolve("", "Checked-In", false, 0.004));
    }

    @Test
    public void noPaymentStatus_withMoneyPaid_isPaid() {
        // A legacy transaction with no itemized payments but a real amount paid.
        assertEquals(PAID, PaymentTransactionStatus.resolve(null, "Confirmed", false, 5000.0));
        assertEquals(PAID, PaymentTransactionStatus.resolve(null, "Checked-Out", false, 5000.0));
    }

    @Test
    public void anUnknownPaymentStatus_fallsBackToTheBookingLevelRules_insteadOfGuessing() {
        assertEquals(PENDING, PaymentTransactionStatus.resolve("processing", "Pending", false, 100.0));
        assertEquals(PAID, PaymentTransactionStatus.resolve("processing", "Confirmed", false, 100.0));
        assertEquals(UNPAID, PaymentTransactionStatus.resolve("processing", "Confirmed", false, 0.0));
    }

    // ---- through the real model, as the adapter/details screen call it ----

    private static Booking booking(String status, boolean pendingVerification, double amountPaid) {
        Booking b = new Booking("250", "204", "Deluxe Room", "Deluxe",
                "Sep 25, 2026", "Sep 27, 2026", 2, 10000.0, status, "Sep 20, 2026");
        b.setPaymentPendingVerification(pendingVerification);
        b.setAmountPaid(amountPaid);
        return b;
    }

    @Test
    public void resolve_readsAnItemizedRecordsOwnStatus() {
        Booking b = booking("Confirmed", true, 1000.0);
        Booking.PaymentRecord completed = new Booking.PaymentRecord("1000.00", "GCASH", "REF1", "Sep 02, 2026 • 10:24 AM", "completed", "09171234567");
        Booking.PaymentRecord pending = new Booking.PaymentRecord("1900.00", "GCASH", "REF2", "Sep 03, 2026 • 9:00 AM", "pending", "09171234567");

        assertEquals(PAID, PaymentTransactionStatus.resolve(new PaymentTransaction(b, completed)));
        assertEquals(PENDING, PaymentTransactionStatus.resolve(new PaymentTransaction(b, pending)));
    }

    @Test
    public void resolve_aSyntheticRow_usesTheBookingsOwnFields() {
        assertEquals(UNPAID, PaymentTransactionStatus.resolve(new PaymentTransaction(booking("Confirmed", false, 0.0), null)));
        assertEquals(PAID, PaymentTransactionStatus.resolve(new PaymentTransaction(booking("Checked-Out", false, 5000.0), null)));
        assertEquals(PENDING, PaymentTransactionStatus.resolve(new PaymentTransaction(booking("Pending", false, 0.0), null)));
        assertEquals(CANCELLED, PaymentTransactionStatus.resolve(new PaymentTransaction(booking("Cancelled", false, 0.0), null)));
    }
}
