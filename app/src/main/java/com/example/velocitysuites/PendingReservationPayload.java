package com.example.velocitysuites;

/**
 * Carries a fresh Reservation's reviewed BookingWizardState from
 * Step8ReviewPaymentFragment's Confirm button into PaymentActivity, for the
 * GCash-chosen case only (see PaymentActivity#EXTRA_PENDING_RESERVATION's
 * docblock) - a fresh Cash Reservation is created directly by
 * Step8ReviewPaymentFragment itself and never touches this class. Mirrors
 * PendingBookingPayload's identical role on the Booking side exactly.
 */
final class PendingReservationPayload {

    private static BookingWizardState pendingState;

    private PendingReservationPayload() {}

    static void set(BookingWizardState state) {
        pendingState = state;
    }

    /** Returns the pending state and clears it so a stale review can never leak into a later, unrelated payment. */
    static BookingWizardState consume() {
        BookingWizardState result = pendingState;
        pendingState = null;
        return result;
    }
}
