package com.example.velocitysuites;

import android.content.Context;

/**
 * The payment-status pill shown on the dashboard, the upcoming-transactions list and the booking cards.
 * <p>
 * It no longer decides anything itself: the status is {@link TransactionStatusHelper}'s - the same rule
 * Transaction History, the detail screen, the receipt and the notifications use (Pending until a
 * receptionist verifies a payment; Paid / Partially Paid by the VERIFIED amount against the grand total;
 * Cancelled; Rejected) - so one transaction can't read "Paid" here and "Pending" there. This class keeps the
 * finer-grained {@link StatusKey} some callers branch on (a Pending transaction may be "payment under
 * verification" or "no payment yet") and hands out the helper's label, colors and icon.
 */
public final class PaymentStatusResolver {

    private PaymentStatusResolver() {
    }

    /**
     * The pure, Context-free decision - separated from resolve() purely so it's JVM-unit-testable (see
     * PaymentStatusResolverTest). PENDING_VERIFICATION / NO_PAYMENT_YET / PENDING are all the helper's single
     * Pending status, told apart only for callers that care whether a payment was submitted.
     */
    public enum StatusKey {
        CANCELLED, REJECTED, PENDING_VERIFICATION, NO_PAYMENT_YET, FULLY_PAID, PARTIALLY_PAID, PENDING
    }

    public static StatusKey resolveStatusKey(Booking booking) {
        TransactionStatusHelper.Summary summary = TransactionStatusHelper.summarize(booking);
        switch (summary.status) {
            case CANCELLED:
                return StatusKey.CANCELLED;
            case REJECTED:
                return StatusKey.REJECTED;
            case PAID:
                return StatusKey.FULLY_PAID;
            case PARTIALLY_PAID:
                return StatusKey.PARTIALLY_PAID;
            case PENDING:
            default:
                if (summary.hasPendingPayment) return StatusKey.PENDING_VERIFICATION;
                return !booking.isHasBooking() ? StatusKey.NO_PAYMENT_YET : StatusKey.PENDING;
        }
    }

    public static Result resolve(Context ctx, Booking booking) {
        TransactionStatusHelper.Style style = TransactionStatusHelper.styleFor(TransactionStatusHelper.statusOf(booking));
        return new Result(ctx.getString(style.labelRes), style.bgColorRes, style.fgColorRes, style.iconRes);
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
