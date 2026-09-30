package com.example.velocitysuites;

/**
 * Resolves whether NotificationDetailsActivity#bindPrimaryAction() should
 * offer a "View Transaction Details" button for a notification - every
 * Booking/Reservation/Payment/Check-in notification with a reference id
 * deep-links into Transaction History (scrolled to and highlighted on the
 * exact record); Promotions/Announcements/System notifications have no
 * transaction to show and get no button at all. Extracted into a pure
 * static resolver (no Context needed, mirroring NotificationStatusResolver's
 * pattern) so adding a new notification type here is a one-line,
 * unit-testable change instead of a silent gap - a Reservation-category
 * notification once had no condition true even when its linked record was
 * resolvable, hiding the button entirely.
 */
public final class NotificationPrimaryActionResolver {

    private NotificationPrimaryActionResolver() {
    }

    /**
     * True when there's a reference id to deep-link into Transaction
     * History with. Every transaction-linked category routes through the
     * same destination now - a prior version of this app sent Booking/
     * Reservation/Check-in straight to BookingDetailsActivity instead,
     * which disagreed with this button's own "View Transaction Details"
     * label for exactly those three types.
     */
    public static boolean canViewTransaction(String type, String referenceId) {
        return referenceId != null
                && (Notification.TYPE_BOOKING.equals(type)
                    || Notification.TYPE_RESERVATION.equals(type)
                    || Notification.TYPE_PAYMENT.equals(type)
                    || Notification.TYPE_CHECK_IN.equals(type));
    }

    /**
     * Best-guess starting chip for Transaction History - not load-bearing
     * for correctness, since TransactionHistoryActivity's own
     * applySelectedBookingHighlight()/applyFilters() already fall back to
     * "All" whenever the deep-linked id isn't present under this chip (a
     * Cancelled Booking opened via CheckIn's guess, for instance). CheckIn
     * only ever happens on a real Booking (a Reservation must convert
     * first), so it maps to the Bookings chip too.
     */
    public static String transactionHistoryFilterFor(String type) {
        if (Notification.TYPE_RESERVATION.equals(type)) return TransactionHistoryActivity.FILTER_RESERVATIONS;
        if (Notification.TYPE_PAYMENT.equals(type)) return TransactionHistoryActivity.FILTER_PAYMENTS;
        return TransactionHistoryActivity.FILTER_BOOKINGS;
    }
}
