package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * THE rules for what a guest may do with a Booking or Reservation: cancel, delete, edit, pay, book again, and which line of
 * explanation to show. One class, used by the Booking & Reservation cards, the details screens and Upcoming Hotel
 * Transactions, so no screen can drift from the rule. (The server enforces the same gates, so a hand-sent request is refused too.)
 *
 * <p><b>Cancel</b> - a RESERVATION while it is Pending, not verified or confirmed by the front desk and not converted to a booking;
 * a BOOKING any time until the front desk verifies it, whatever has been paid (the partial payment is non-refundable). Never a
 * verified or confirmed booking, or one that is checked in or out.
 * <p><b>Delete</b> - only a finished item (Completed / Checked out / converted reservation) or a Cancelled / Rejected one (which
 * includes "Transaction failed" and no-shows). Pending, confirmed, verified and checked-in items can never be deleted.
 */
public final class TransactionActions {

    private TransactionActions() {
    }

    /** The one line of explanation above the actions, when it applies. */
    public enum InfoLine { NONE, BALANCE_AT_FRONT_DESK, PAY_AT_FRONT_DESK, CONVERTED }

    /** Everything a card needs to know about which actions to offer (pure data, table-testable). */
    public static final class Plan {
        public final boolean payNow;
        public final boolean viewDetailsOutlined;
        public final boolean viewDetailsText;
        public final boolean bookAgain;
        public final boolean viewConvertedBooking;
        public final boolean edit;
        public final boolean cancel;
        public final boolean delete;
        @NonNull public final InfoLine info;

        Plan(boolean payNow, boolean viewDetailsOutlined, boolean viewDetailsText, boolean bookAgain, boolean viewConvertedBooking,
             boolean edit, boolean cancel, boolean delete, @NonNull InfoLine info) {
            this.payNow = payNow;
            this.viewDetailsOutlined = viewDetailsOutlined;
            this.viewDetailsText = viewDetailsText;
            this.bookAgain = bookAgain;
            this.viewConvertedBooking = viewConvertedBooking;
            this.edit = edit;
            this.cancel = cancel;
            this.delete = delete;
            this.info = info;
        }
    }

    private static String status(@Nullable Booking b) {
        return b == null || b.getStatus() == null ? "" : b.getStatus().trim();
    }

    /** True once nothing is owed on the guest's actual total (the same total every screen shows). */
    public static boolean isFullyPaid(@NonNull Booking b) {
        TransactionStatusHelper.Summary s = TransactionStatusHelper.summarize(b);
        if ("paid".equalsIgnoreCase(b.getBillingStatus())) return true;
        return s.grandTotal > MoneyFormat.EPSILON && s.balance <= MoneyFormat.EPSILON && MoneyFormat.isPositive(s.verifiedPaid);
    }

    /** A reservation that became a booking: the frozen "Completed" copy, or the booking made from it. */
    public static boolean isConverted(@NonNull Booking b) {
        return b.isHistoricalReservation() || (b.isHasBooking() && !b.isDirectBooking());
    }

    /**
     * "Pending" as the guest means it: not yet verified by the front desk. A direct booking is stored as ACTIVE_BOOKING from the moment it
     * is created (the app calls that "Confirmed"), so for a booking an unverified "Confirmed" is still pending; a reservation is pending
     * only while its own status says so.
     */
    private static boolean isPending(@NonNull Booking b) {
        String s = status(b);
        return s.isEmpty() || "Pending".equalsIgnoreCase(s) || (b.isHasBooking() && "Confirmed".equalsIgnoreCase(s));
    }

    /**
     * A booking the front desk has not verified yet. Its stored status already says "Confirmed" (ACTIVE_BOOKING), but the guest must
     * read "Awaiting verification" until it is verified; this is the same flag {@link #canCancel} uses, so the chip and the Cancel
     * button always agree.
     */
    public static boolean isAwaitingVerification(@Nullable Booking b) {
        return b != null && b.isHasBooking() && !isConverted(b) && !b.isStaffVerified()
                && "Confirmed".equalsIgnoreCase(status(b));
    }

    public static boolean canCancel(@Nullable Booking b) {
        if (b == null || isConverted(b)) return false;
        if (!isPending(b)) return false;      // confirmed, checked in / out, cancelled, rejected, completed
        return !b.isStaffVerified();
    }

    /**
     * Why Cancel is not offered, for the details screen: a string resource, or 0 when there is nothing to explain (already
     * cancelled / rejected / finished, or cancel is available).
     */
    @StringRes
    public static int cancelBlockedReason(@Nullable Booking b) {
        if (b == null || canCancel(b)) return 0;
        String s = status(b);
        if ("Cancelled".equalsIgnoreCase(s) || "Rejected".equalsIgnoreCase(s) || "Completed".equalsIgnoreCase(s) || b.isHistoricalReservation()) return 0;
        if ("Checked-In".equalsIgnoreCase(s) || "Checked-Out".equalsIgnoreCase(s)) return R.string.cancel_blocked_stay_started;
        if (b.isStaffVerified() || isConverted(b) || (!b.isHasBooking() && !"Pending".equalsIgnoreCase(s))) return R.string.cancel_blocked_verified;
        return R.string.cancel_blocked_verified;
    }

    public static boolean canDelete(@Nullable Booking b) {
        if (b == null) return false;
        String s = status(b);
        return b.isHistoricalReservation()
                || "Cancelled".equalsIgnoreCase(s) || "Rejected".equalsIgnoreCase(s)
                || "Checked-Out".equalsIgnoreCase(s) || "Completed".equalsIgnoreCase(s);
    }

    /** The whole set of actions for one card. {@code bookingTab} is true on the Bookings tab. */
    @NonNull
    public static Plan plan(@NonNull Booking b, boolean bookingTab, boolean locallyModified) {
        if (b.isHistoricalReservation()) {
            return new Plan(false, false, false, false, true, false, false, true, InfoLine.CONVERTED);
        }
        if (canDelete(b)) {
            return new Plan(false, false, true, true, false, false, false, true, InfoLine.NONE);
        }
        PaymentEligibility.PayAction action = PaymentEligibility.payActionFor(b);
        boolean payNow = !bookingTab && action == PaymentEligibility.PayAction.PAY_NOW;
        boolean edit = !bookingTab && ReservationEditPolicy.canEdit(b, locallyModified);
        boolean cancel = canCancel(b);
        InfoLine info = PaymentEligibility.needsFrontDeskPayment(b) ? InfoLine.BALANCE_AT_FRONT_DESK
                : (!bookingTab && action == PaymentEligibility.PayAction.FRONT_DESK && "CASH".equalsIgnoreCase(b.getPaymentMethod()))
                ? InfoLine.PAY_AT_FRONT_DESK : InfoLine.NONE;
        return new Plan(payNow, true, false, false, false, edit, cancel, false, info);
    }
}
