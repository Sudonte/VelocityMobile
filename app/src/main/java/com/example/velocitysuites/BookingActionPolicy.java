package com.example.velocitysuites;

/**
 * Single source of truth for whether the guest may still cancel a
 * Booking/Reservation from the app. Used by the Booking List card and by the
 * tap-time re-check, so no entry point can drift from the rule.
 *
 * <p>The receptionist's verify action sets {@code bookings.verified_at}
 * (Receptionist\PaymentController::verify() -> autoCompleteBooking(), or
 * Receptionist\BookingController::verify()); ApiMapper exposes it as
 * {@link Booking#isStaffVerified()}. The backend refuses to cancel such a
 * booking (Api\BookingController::cancel(), ReservationWorkflowService::
 * cancelConvertedBooking()), so the app hides the action to match.
 *
 * <p>Rules:
 * <ul>
 *   <li>null booking -> false.</li>
 *   <li>Fully paid -> false (unchanged, see {@link PaymentStateUtil#isFullyPaid}).</li>
 *   <li>A Booking (hasBooking) the receptionist has verified -> false.</li>
 *   <li>Anything else, including a not-yet-converted Reservation -> true
 *       (unchanged behavior for reservations).</li>
 * </ul>
 */
public final class BookingActionPolicy {

    private BookingActionPolicy() {}

    public static boolean canCancel(Booking booking) {
        if (booking == null) return false;
        if (PaymentStateUtil.isFullyPaid(booking)) return false;
        return !(booking.isHasBooking() && booking.isStaffVerified());
    }
}
