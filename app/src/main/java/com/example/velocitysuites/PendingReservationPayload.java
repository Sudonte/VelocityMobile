package com.example.velocitysuites;

/**
 * No longer used by any caller as of 2026-09-28 (later same day) - see
 * PaymentActivity#EXTRA_PENDING_RESERVATION's docblock. Previously carried a
 * fresh Reservation's reviewed BookingWizardState from
 * Step8ReviewPaymentFragment's Confirm button into PaymentActivity for the
 * GCash-chosen case, mirroring PendingBookingPayload's identical role on the
 * Booking side. Kept in place rather than removed in case a future product
 * decision revives GCash-at-creation for Reservations.
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
