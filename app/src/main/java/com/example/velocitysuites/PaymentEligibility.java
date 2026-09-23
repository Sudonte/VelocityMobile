package com.example.velocitysuites;

/**
 * Single source of truth for whether a Pay Now / Resubmit Payment action
 * should be offered for a given transaction - extracted because
 * BookingAndReservationActivity's canPayNow and DashboardActivity's own
 * btnQuickPay condition had drifted into two independently-buggy checks:
 * neither excluded a payment still isPaymentPendingVerification() (letting
 * a guest submit a second GCash payment while the first is still awaiting
 * staff review), and only one of the two excluded isPaymentRejected().
 */
public final class PaymentEligibility {

    private PaymentEligibility() {}

    /**
     * True when Pay Now (or, for a rejected payment, the same button
     * relabelled as Resubmit Payment) should be shown. GCash-only - Cash
     * always settles as a walk-in, never via an in-app submission. Excludes:
     * Cash, an already-converted Booking, a whole-transaction Cancelled/
     * Rejected status, and a payment still awaiting staff verification
     * (isPaymentPendingVerification()) - a second submission must not be
     * possible while the first is still under review. Deliberately does NOT
     * exclude isPaymentRejected(): a rejected payment attempt (the
     * transaction itself still Pending) is exactly when this button should
     * keep showing, doubling as Resubmit.
     *
     * No longer gates on a receptionist Accept step (a 2026-09-02 design
     * this app briefly had, since reverted server-side - GCash payment is
     * submittable directly against an AWAITING_GCASH_PAYMENT reservation
     * with no prior Accept required, confirmed live 2026-09-05).
     */
    public static boolean canPayNow(Booking b) {
        return b != null
                && !b.isHistoricalReservation()
                && "GCASH".equalsIgnoreCase(b.getPaymentMethod())
                && !b.isHasBooking()
                && !b.isPaymentPendingVerification()
                && !"Cancelled".equalsIgnoreCase(b.getStatus())
                && !"Rejected".equalsIgnoreCase(b.getStatus());
    }

    /** True when a GCash payment has been submitted and is awaiting staff review - no action button, just a status pill. */
    public static boolean isAwaitingVerification(Booking b) {
        return b != null && b.isPaymentPendingVerification();
    }
}
