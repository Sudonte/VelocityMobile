package com.example.velocitysuites;

/**
 * Single source of truth for whether the guest may still cancel a
 * Booking/Reservation from the app. Used by the Booking List card and by the
 * tap-time re-check, so no entry point can drift from the rule.
 *
 * <p>The backend enforces the same gate (ReservationWorkflowService::cancel()
 * and Api\BookingController::cancel()), so a hand-sent request is refused too.
 *
 * <p>Cancel is hidden (GONE, not merely disabled) when:
 * <ul>
 *   <li>the booking is null, or already Cancelled / Rejected / Checked-In /
 *       Checked-Out / Completed;</li>
 *   <li>it is fully paid ({@link PaymentStateUtil#isFullyPaid});</li>
 *   <li>it is a Reservation already converted to a Booking (covers a Cash
 *       reservation the receptionist converted and a GCash Pay Later one);</li>
 *   <li>it is a Reservation (Cash) the receptionist has already verified;</li>
 *   <li>it is a direct Booking the receptionist has verified
 *       ({@code bookings.verified_at}, {@link Booking#isStaffVerified()}).</li>
 * </ul>
 */
public final class BookingActionPolicy {

    private BookingActionPolicy() {}

    public static boolean canCancel(Booking booking) {
        if (booking == null) return false;
        if (isClosedStatus(booking.getStatus())) return false;
        if (PaymentStateUtil.isFullyPaid(booking)) return false;
        if (booking.isHasBooking() && !booking.isDirectBooking()) return false;
        return !booking.isStaffVerified();
    }

    private static boolean isClosedStatus(String status) {
        return "Cancelled".equalsIgnoreCase(status)
                || "Rejected".equalsIgnoreCase(status)
                || "Checked-In".equalsIgnoreCase(status)
                || "Checked-Out".equalsIgnoreCase(status)
                || "Completed".equalsIgnoreCase(status);
    }
}
