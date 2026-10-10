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

    /**
     * True for a CONFIRMED booking that still has a balance: the guest pays the rest at the front desk, not in the app
     * (the server refuses in-app payments for a booking). Status and balance come from TransactionStatusHelper - the
     * server's payment summary, verified payments only - so this never calculates a number of its own. A fully paid,
     * cancelled or rejected booking has nothing to pay and gets no message.
     */
    public static boolean needsFrontDeskPayment(Booking b) {
        if (b == null || !b.isHasBooking() || b.isHistoricalReservation()) return false;
        TransactionStatusHelper.Summary summary = TransactionStatusHelper.summarize(b);
        boolean open = summary.status == TransactionStatusHelper.Status.PENDING
                || summary.status == TransactionStatusHelper.Status.PARTIALLY_PAID;
        return open && MoneyFormat.isPositive(summary.balance);
    }

    /** The remaining balance to quote in the front-desk message (the server's figure, verified payments only). */
    public static double frontDeskBalance(Booking b) {
        return TransactionStatusHelper.summarize(b).balance;
    }

    /** True when a GCash payment has been submitted and is awaiting staff review - no action button, just a status pill. */
    public static boolean isAwaitingVerification(Booking b) {
        return b != null && b.isPaymentPendingVerification();
    }
}
