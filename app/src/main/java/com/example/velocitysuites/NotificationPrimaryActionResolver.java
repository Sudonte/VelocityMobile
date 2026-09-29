package com.example.velocitysuites;

/**
 * Resolves which "primary action" NotificationDetailsActivity#bindPrimaryAction()
 * should offer for a notification - whether it can jump straight to the linked
 * Booking/Reservation's own details screen, fall back to the Transaction History
 * filter, or show nothing. Extracted into a pure static resolver (no Context
 * needed, mirroring NotificationStatusResolver's pattern) so adding a new
 * notification type here is a one-line, unit-testable change instead of a
 * silent gap - a Reservation-category notification used to have neither
 * condition true even when its linked record was resolvable, hiding the
 * button entirely.
 */
public final class NotificationPrimaryActionResolver {

    private NotificationPrimaryActionResolver() {
    }

    /**
     * True when the linked Booking/Reservation is still resolvable in
     * RoomRepository's cache and this type has its own details screen worth
     * jumping straight to.
     */
    public static boolean canViewBookingDetails(String type, boolean hasRelatedBooking) {
        return hasRelatedBooking
                && (Notification.TYPE_BOOKING.equals(type)
                    || Notification.TYPE_RESERVATION.equals(type)
                    || Notification.TYPE_CHECK_IN.equals(type));
    }

    /**
     * True when there's a reference id to deep-link into the Transaction
     * History filter with - the fallback for a type whose own details
     * screen isn't reachable (relatedBooking unresolved) or that has no
     * richer destination of its own (Payment).
     */
    public static boolean canViewTransaction(String type, String referenceId) {
        return referenceId != null
                && (Notification.TYPE_BOOKING.equals(type)
                    || Notification.TYPE_RESERVATION.equals(type)
                    || Notification.TYPE_PAYMENT.equals(type)
                    || Notification.TYPE_CHECK_IN.equals(type));
    }
}
