package com.example.velocitysuites;

/**
 * Single source of truth for whether the guest may still cancel a
 * Booking/Reservation from the app. Used by the Booking List card and by the
 * tap-time re-check, so no entry point can drift from the rule.
 *
 * <p>The backend enforces the same gate (ReservationWorkflowService::cancel()
 * and Api\BookingController::cancel()), so a hand-sent request is refused too.
 *
 * <p>The rule itself now lives in {@link TransactionActions} (cancel only while Pending, not verified, not converted, and for a
 * booking not fully paid); this class stays as the long-standing entry point.
 */
public final class BookingActionPolicy {

    private BookingActionPolicy() {}

    public static boolean canCancel(Booking booking) {
        return TransactionActions.canCancel(booking);
    }
}
