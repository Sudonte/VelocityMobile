package com.example.velocitysuites;

import android.content.Context;

/**
 * Single source of truth for the "current" payment status label/colors shown
 * across dashboard.xml, transactionhistory.xml, and the payment details
 * dialog. Priority (highest first): Cancelled booking > Rejected payment >
 * No payment yet > Fully paid > Partially paid > Pending.
 */
public final class PaymentStatusResolver {

    private PaymentStatusResolver() {
    }

    /**
     * The pure, Context-free decision this whole class exists to make -
     * separated from resolve() purely so it's JVM-unit-testable (this
     * project has no Robolectric, so anything touching Context/R.string
     * directly can't be - see PaymentStatusResolverTest). resolve() below is
     * a thin Context-string/color lookup over this result; keep the two in
     * lockstep if either changes.
     */
    public enum StatusKey {
        CANCELLED, REJECTED, PENDING_VERIFICATION, NO_PAYMENT_YET, FULLY_PAID, PARTIALLY_PAID, PENDING
    }

    public static StatusKey resolveStatusKey(Booking booking) {
        if ("Cancelled".equalsIgnoreCase(booking.getStatus())) {
            return StatusKey.CANCELLED;
        } else if (booking.isPaymentRejected()) {
            return StatusKey.REJECTED;
        } else if (booking.isPaymentPendingVerification()) {
            // Must be checked before the plain !isHasBooking() branch below -
            // a GCash payment already submitted on a not-yet-converted
            // Reservation previously fell through to "No Payment Yet" here,
            // contradicting the separate "Awaiting Verification" pill shown
            // alongside it (see DashboardActivity's tvVerification).
            return StatusKey.PENDING_VERIFICATION;
        } else if (!booking.isHasBooking() && !hasRecordedPayment(booking)) {
            return StatusKey.NO_PAYMENT_YET;
        } else if (isFullyPaid(booking)) {
            return StatusKey.FULLY_PAID;
        } else if (isPartiallyPaid(booking)) {
            return StatusKey.PARTIALLY_PAID;
        }
        return StatusKey.PENDING;
    }

    public static Result resolve(Context ctx, Booking booking) {
        String label;
        int bgColorRes;
        int fgColorRes;
        int iconRes;

        switch (resolveStatusKey(booking)) {
            case CANCELLED:
                label = ctx.getString(R.string.status_cancelled);
                bgColorRes = R.color.velocity_gray_soft;
                fgColorRes = R.color.velocity_gray_primary;
                iconRes = R.drawable.ic_close;
                break;
            case REJECTED:
                label = ctx.getString(R.string.status_rejected);
                bgColorRes = R.color.velocity_red_subtle;
                fgColorRes = R.color.velocity_red_dark;
                iconRes = R.drawable.ic_close;
                break;
            case PENDING_VERIFICATION:
                label = ctx.getString(R.string.status_payment_verification_label);
                bgColorRes = R.color.velocity_orange_soft;
                fgColorRes = R.color.velocity_orange_primary;
                iconRes = R.drawable.ic_info;
                break;
            case NO_PAYMENT_YET:
                label = ctx.getString(R.string.no_payment_yet_label);
                bgColorRes = R.color.velocity_gray_soft;
                fgColorRes = R.color.velocity_inactive_gray;
                iconRes = R.drawable.ic_clock;
                break;
            case FULLY_PAID:
                label = ctx.getString(R.string.status_fully_paid);
                bgColorRes = R.color.velocity_green_soft;
                fgColorRes = R.color.velocity_green_dark;
                iconRes = R.drawable.ic_check_circle;
                break;
            case PARTIALLY_PAID:
                label = ctx.getString(R.string.status_partial_paid);
                bgColorRes = R.color.velocity_blue_soft;
                fgColorRes = R.color.velocity_blue_primary;
                iconRes = R.drawable.ic_clock;
                break;
            case PENDING:
            default:
                label = ctx.getString(R.string.status_pending_label);
                bgColorRes = R.color.velocity_red_subtle;
                fgColorRes = R.color.velocity_red_primary;
                iconRes = R.drawable.ic_clock;
                break;
        }

        return new Result(label, bgColorRes, fgColorRes, iconRes);
    }

    /**
     * Fully Paid/Partially Paid/Pending - the backend's own
     * payment_summary.payment_status (PAID/PARTIALLY_PAID/PENDING) when the
     * backend has attached one, taken as authoritative and never locally
     * reinterpreted from raw transaction sums; only falls back to the legacy
     * getRemainingBalance()/getAmountPaid() comparison when no payment_summary
     * is present at all (an older/not-yet-migrated response) - see
     * PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md Phase 5 §13/§15.
     */
    private static boolean isFullyPaid(Booking booking) {
        if (booking.hasAuthoritativePaymentSummary()) {
            return "PAID".equals(booking.getPaymentSummary().paymentStatus);
        }
        return booking.getRemainingBalance() <= 0.009;
    }

    private static boolean isPartiallyPaid(Booking booking) {
        if (booking.hasAuthoritativePaymentSummary()) {
            return "PARTIALLY_PAID".equals(booking.getPaymentSummary().paymentStatus);
        }
        return booking.getAmountPaid() > 0;
    }

    /**
     * True once real money has actually been recorded against this
     * Booking/Reservation - lets a not-yet-converted Reservation that
     * already received a payment (e.g. a PR-anchored partial payment made
     * before conversion) resolve to its real FULLY_PAID/PARTIALLY_PAID/
     * PENDING state above instead of the !isHasBooking() branch forcing
     * NO_PAYMENT_YET regardless of what was actually paid.
     */
    private static boolean hasRecordedPayment(Booking booking) {
        if (booking.hasAuthoritativePaymentSummary()) {
            return booking.getPaymentSummary().totalAmountPaid > 0.009;
        }
        return booking.getAmountPaid() > 0.009;
    }

    /**
     * Second, additive resolver for the transaction-*type* lifecycle state
     * (Reservation Pending / Booking Pending / Validated), distinct from
     * resolve() above which stays focused on payment-amount state (Fully
     * Paid/Partially Paid/etc.) - the two are shown as separate pills side
     * by side wherever both are relevant (see DashboardActivity). Priority
     * (highest first): Cancelled/Rejected > a Reservation verified without
     * ever converting to a Booking (see TransactionCategorizer, same
     * underlying isStaffVerified() signal) > still a pure Reservation > a
     * Booking whose latest payment hasn't been staff-verified yet > a
     * Booking not yet staff-verified > Validated.
     */
    public static Result resolveLifecycleBadge(Context ctx, Booking booking) {
        String label;
        int bgColorRes;
        int fgColorRes;
        int iconRes;

        if ("Cancelled".equalsIgnoreCase(booking.getStatus())) {
            label = ctx.getString(R.string.status_cancelled);
            bgColorRes = R.color.velocity_gray_soft;
            fgColorRes = R.color.velocity_gray_primary;
            iconRes = R.drawable.ic_close;
        } else if (booking.isPaymentRejected()) {
            label = ctx.getString(R.string.status_rejected);
            bgColorRes = R.color.velocity_red_subtle;
            fgColorRes = R.color.velocity_red_dark;
            iconRes = R.drawable.ic_close;
        } else if (!booking.isHasBooking() && booking.isStaffVerified()) {
            label = ctx.getString(R.string.status_completed_reservation);
            bgColorRes = R.color.velocity_green_soft;
            fgColorRes = R.color.velocity_green_dark;
            iconRes = R.drawable.ic_check_circle;
        } else if (!booking.isHasBooking()) {
            label = ctx.getString(R.string.reservation_pending_title);
            bgColorRes = R.color.velocity_orange_soft;
            fgColorRes = R.color.velocity_orange_primary;
            iconRes = R.drawable.ic_clock;
        } else if ("pending_verification".equalsIgnoreCase(booking.getPaymentVerificationStatus())) {
            label = ctx.getString(R.string.status_payment_verification_label);
            bgColorRes = R.color.velocity_orange_soft;
            fgColorRes = R.color.velocity_orange_primary;
            iconRes = R.drawable.ic_info;
        } else if (!booking.isStaffVerified()) {
            label = ctx.getString(R.string.status_booking_pending);
            bgColorRes = R.color.velocity_orange_soft;
            fgColorRes = R.color.velocity_orange_primary;
            iconRes = R.drawable.ic_clock;
        } else {
            label = ctx.getString(R.string.status_validated);
            bgColorRes = R.color.velocity_green_soft;
            fgColorRes = R.color.velocity_green_dark;
            iconRes = R.drawable.ic_check_circle;
        }

        return new Result(label, bgColorRes, fgColorRes, iconRes);
    }

    public static final class Result {
        public final String label;
        public final int bgColorRes;
        public final int fgColorRes;
        /** Small leading icon differentiating this status by shape/icon rather than color (the app is strictly red & white). */
        public final int iconRes;

        Result(String label, int bgColorRes, int fgColorRes, int iconRes) {
            this.label = label;
            this.bgColorRes = bgColorRes;
            this.fgColorRes = fgColorRes;
            this.iconRes = iconRes;
        }
    }
}
