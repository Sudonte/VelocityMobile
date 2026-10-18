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

    /** What the Dashboard's Payment Status card offers for a balance. */
    public enum PayAction {
        /** Nothing is owed. */
        NONE,
        /** Pay Now. */
        PAY_NOW,
        /** A payment is already awaiting the front desk's verification: no second one. */
        UNDER_REVIEW,
        /** A booking (not a reservation): the server takes payment at the front desk only. */
        FRONT_DESK
    }

    /**
     * The one rule for every Payment Status card: a balance owed gets Pay Now - whatever the reservation's payment method
     * - unless a payment is already under review ("Payment under review") or it is a booking, which the server only
     * lets the front desk settle (a button that always fails would be worse than a note). Nothing owed, or a closed
     * transaction, gets none. (The old rule also demanded GCASH as the method, which is why two identical unpaid
     * reservations differed: one chose GCash, the other cash.)
     */
    public static PayAction payActionFor(Booking b) {
        if (b == null || !PaymentStatusList.isActive(b) || b.isHistoricalReservation()) return PayAction.NONE;
        TransactionStatusHelper.Summary summary = TransactionStatusHelper.summarize(b);
        if (!MoneyFormat.isPositive(summary.balance)) return PayAction.NONE;
        if (b.isPaymentPendingVerification() || summary.hasPendingPayment) return PayAction.UNDER_REVIEW;
        return b.isHasBooking() ? PayAction.FRONT_DESK : PayAction.PAY_NOW;
    }

    /**
     * True for a CONFIRMED booking that still has a balance: the guest pays the rest at the front desk, not in the app
     * (the server refuses in-app payments for a booking). Status and balance come from TransactionStatusHelper - the
     * server's payment summary, verified payments only - so this never calculates a number of its own. A fully paid,
     * cancelled or rejected booking has nothing to pay and gets no message.
     */
    public static boolean needsFrontDeskPayment(Booking b) {
        if (b == null || !b.isHasBooking() || b.isHistoricalReservation()) return false;
        // Nothing is owed at the desk once the stay is over, whatever a stale balance says.
        if ("Checked-Out".equalsIgnoreCase(b.getStatus()) || "Completed".equalsIgnoreCase(b.getStatus())) return false;
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
