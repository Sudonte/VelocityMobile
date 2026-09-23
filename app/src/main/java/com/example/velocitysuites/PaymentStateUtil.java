package com.example.velocitysuites;

/**
 * Shared payment-state checks, extracted out of BookingAndReservationActivity
 * so DashboardActivity's own booking cards can apply the same fully-paid
 * guard instead of a drifted copy.
 */
public final class PaymentStateUtil {

    private PaymentStateUtil() {}

    /**
     * True once the booking's billed amount has been paid in full.
     * Deliberately never trusts billingStatus text alone - always falls
     * back to the actual paid-vs-total amounts, so a stale/inconsistent
     * status string (e.g. left over as "partial" after the balance was
     * actually settled) can never let a fully-paid transaction still look
     * cancellable client-side. getAmountPaid() already only counts
     * completed/verified payments (see ApiMapper#toBooking -
     * billing.amountPaidCompleted()), never a still-pending, unverified
     * submission, so this amount check is safe to apply unconditionally
     * rather than only when billingStatus is null.
     */
    public static boolean isFullyPaid(Booking b) {
        if ("paid".equalsIgnoreCase(b.getBillingStatus())) return true;
        // Not gated on isHasBooking(): a 100% GCash payment made at
        // reservation time already covers the full quoted total, whether
        // that's pre-checkout on a converted Booking (no Billing row yet)
        // or on a not-yet-converted Reservation awaiting the receptionist's
        // verification (see ReservationWorkflowService::cancel()'s mirror
        // of this same rule server-side) - either way there's nothing left
        // to pay, so it must read as fully paid.
        return b.getTotalAmount() > 0 && b.getAmountPaid() >= b.getTotalAmount() - 0.01;
    }

    /** True when some money has been put down but the balance isn't fully settled yet. */
    public static boolean isPartiallyPaid(Booking b) {
        return !isFullyPaid(b)
                && ("partial".equalsIgnoreCase(b.getBillingStatus()) || b.getAmountPaid() > 0);
    }
}
