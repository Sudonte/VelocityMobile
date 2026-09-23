package com.example.velocitysuites;

/**
 * No longer set by any caller - Step8ReviewPaymentFragment's fresh-
 * Reservation path now calls RoomRepository#createReservation() directly
 * instead of staging state here for PaymentActivity to consume (see
 * PaymentActivity#EXTRA_PENDING_RESERVATION's docblock). Kept in place
 * (rather than deleted) only because PaymentActivity's now-dead
 * isPendingReservationMode code still references consume() - see that
 * class's docblock for why that dead code wasn't also removed.
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
