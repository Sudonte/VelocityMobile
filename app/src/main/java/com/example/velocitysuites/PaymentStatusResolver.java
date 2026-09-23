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

    public static Result resolve(Context ctx, Booking booking) {
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
        } else if (booking.isPaymentPendingVerification()) {
            // Must be checked before the plain !isHasBooking() branch below -
            // a GCash payment already submitted on a not-yet-converted
            // Reservation previously fell through to "No Payment Yet" here,
            // contradicting the separate "Awaiting Verification" pill shown
            // alongside it (see DashboardActivity's tvVerification).
            label = ctx.getString(R.string.status_payment_verification_label);
            bgColorRes = R.color.velocity_orange_soft;
            fgColorRes = R.color.velocity_orange_primary;
            iconRes = R.drawable.ic_info;
        } else if (!booking.isHasBooking()) {
            label = ctx.getString(R.string.no_payment_yet_label);
            bgColorRes = R.color.velocity_gray_soft;
            fgColorRes = R.color.velocity_inactive_gray;
            iconRes = R.drawable.ic_clock;
        } else if (booking.getRemainingBalance() <= 0.009) {
            label = ctx.getString(R.string.status_fully_paid);
            bgColorRes = R.color.velocity_green_soft;
            fgColorRes = R.color.velocity_green_dark;
            iconRes = R.drawable.ic_check_circle;
        } else if (booking.getAmountPaid() > 0) {
            label = ctx.getString(R.string.status_partial_paid);
            bgColorRes = R.color.velocity_blue_soft;
            fgColorRes = R.color.velocity_blue_primary;
            iconRes = R.drawable.ic_clock;
        } else {
            label = ctx.getString(R.string.status_pending_label);
            bgColorRes = R.color.velocity_red_subtle;
            fgColorRes = R.color.velocity_red_primary;
            iconRes = R.drawable.ic_clock;
        }

        return new Result(label, bgColorRes, fgColorRes, iconRes);
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
