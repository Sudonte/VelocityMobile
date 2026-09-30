package com.example.velocitysuites;

import androidx.annotation.Nullable;

/**
 * The one place a Transaction History row's payment status is decided, shared by the list card
 * (PaymentTransactionAdapter) and the detail screen's header (TransactionDetailsActivity) so the
 * two can never disagree about the same payment - they used to each carry their own copy of the
 * rule, and the copy had a real flaw: it treated a row as "pending" whenever the parent booking
 * had ANY payment awaiting verification, so an already-verified payment showed "Payment Under
 * Verification" just because a different payment on the same booking was still pending.
 * <p>
 * Backend payment_status values are exactly {@code pending}, {@code completed} (verified/paid -
 * PaymentMath::COUNTS_AS_PAID_STATUS), {@code rejected} and {@code failed}. A row that carries one
 * of those (an itemized payment record) is judged by it; a row that does not (a synthetic summary
 * row for a transaction with no itemized payments, or a legacy record with no status) falls back to
 * the booking-level signals, exactly as before.
 * <p>
 * Pure decision logic, no Context (mirrors NotificationStatusResolver's split) so it is
 * JVM-unit-testable - see PaymentTransactionStatusTest. {@link #styleFor(Key)} only hands out
 * resource ids; callers resolve strings/colors/drawables themselves.
 */
final class PaymentTransactionStatus {

    private PaymentTransactionStatus() {
    }

    enum Key {
        /** A verified, completed payment. */
        PAID,
        /** A payment submitted but not yet verified by staff (or a transaction still awaiting its payment). */
        PENDING,
        /** A rejected/failed payment, or the whole transaction was cancelled/rejected. */
        CANCELLED,
        /** No payment has been made against this transaction yet - a synthetic row worth nothing. */
        UNPAID
    }

    /** Backend payment_status - the only value that means "this payment counts as paid". */
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_PENDING = "pending";

    static Key resolve(PaymentTransaction tx) {
        Booking booking = tx.parentBooking;
        String recordStatus = tx.record != null ? tx.record.status : null;
        return resolve(recordStatus, booking.getStatus(), booking.isPaymentPendingVerification(), tx.getAmount());
    }

    /**
     * @param paymentStatus       this payment's own status (Booking.PaymentRecord#status), or null/blank
     *                            when the row has no itemized payment behind it
     * @param transactionStatus   the parent Booking/Reservation's status ("Pending", "Confirmed",
     *                            "Cancelled", "Rejected", ...)
     * @param anyPaymentPending   the parent's booking-level "some payment awaits verification" flag -
     *                            only consulted when {@code paymentStatus} says nothing itself
     * @param amount              what this row shows as its amount
     */
    static Key resolve(@Nullable String paymentStatus, @Nullable String transactionStatus,
                       boolean anyPaymentPending, double amount) {
        // 1. Cancelled - the payment itself was rejected/failed, or the transaction it belongs to was
        //    cancelled/rejected. The latter also keeps a row consistent with the "Cancelled" filter,
        //    which groups by the transaction's status: it would otherwise list "Paid" rows.
        if (isCancelledLike(paymentStatus) || isCancelledLike(transactionStatus)) {
            return Key.CANCELLED;
        }

        // 2. An explicit per-payment status wins over any booking-level signal.
        if (paymentStatus != null && !paymentStatus.trim().isEmpty()) {
            if (STATUS_PENDING.equalsIgnoreCase(paymentStatus.trim())) return Key.PENDING;
            if (STATUS_COMPLETED.equalsIgnoreCase(paymentStatus.trim())) return Key.PAID;
            // Any other value is unknown to this client - fall through to the booking-level rules
            // rather than guessing.
        }

        // 3. No usable per-payment status: the booking-level signals decide.
        if (STATUS_PENDING.equalsIgnoreCase(transactionStatus == null ? "" : transactionStatus.trim()) || anyPaymentPending) {
            return Key.PENDING;
        }

        // 4. Nothing recorded and nothing owed-and-awaiting: an honest "Unpaid", never a "Paid" badge
        //    sitting next to a ₱0.00 amount.
        if (amount <= 0.009) {
            return Key.UNPAID;
        }
        return Key.PAID;
    }

    private static boolean isCancelledLike(@Nullable String status) {
        if (status == null) return false;
        String s = status.trim();
        return s.equalsIgnoreCase("cancelled") || s.equalsIgnoreCase("canceled")
                || s.equalsIgnoreCase("rejected") || s.equalsIgnoreCase("failed");
    }

    /** The presentation half of a Key: resource ids only (see the class doc). */
    static final class Style {
        /** Short badge text - "Paid", "Pending", "Cancelled", "Unpaid". */
        final int badgeLabelRes;
        /** Longer, descriptive headline for the detail screen - "Payment Successful", ... */
        final int headlineRes;
        final int iconRes;
        /** Background of the small icon circle. */
        final int iconBgColorRes;
        final int iconFgColorRes;
        /** Badge pill colors: the pair for a filled badge (PAID) vs a soft one - see the layout's own comment on why PAID is solid. */
        final int badgeBgColorRes;
        final int badgeFgColorRes;

        Style(int badgeLabelRes, int headlineRes, int iconRes, int iconBgColorRes, int iconFgColorRes,
              int badgeBgColorRes, int badgeFgColorRes) {
            this.badgeLabelRes = badgeLabelRes;
            this.headlineRes = headlineRes;
            this.iconRes = iconRes;
            this.iconBgColorRes = iconBgColorRes;
            this.iconFgColorRes = iconFgColorRes;
            this.badgeBgColorRes = badgeBgColorRes;
            this.badgeFgColorRes = badgeFgColorRes;
        }
    }

    /**
     * The app is deliberately red-and-white only (see DashboardActivity#applyPillIcon()), so status
     * is told apart by fill, icon and label rather than by hue: PAID is the one SOLID brand-red pill
     * with white text and a check; PENDING a soft-red pill with a clock; CANCELLED/UNPAID neutral
     * gray. Every color here is a name values-night does NOT override (or a constant like white),
     * so each pill keeps a readable background/text pair in dark mode too.
     */
    static Style styleFor(Key key) {
        switch (key) {
            case CANCELLED:
                return new Style(R.string.ptx_status_cancelled, R.string.ptx_title_cancelled, R.drawable.ic_close,
                        R.color.velocity_gray_soft, R.color.velocity_gray_primary,
                        R.color.velocity_gray_soft, R.color.velocity_gray_primary);
            case PENDING:
                return new Style(R.string.ptx_status_pending, R.string.ptx_title_pending, R.drawable.ic_clock,
                        R.color.velocity_blue_soft, R.color.velocity_blue_primary,
                        R.color.velocity_blue_soft, R.color.velocity_blue_primary);
            case UNPAID:
                return new Style(R.string.ptx_status_unpaid, R.string.ptx_title_unpaid, R.drawable.ic_info,
                        R.color.velocity_gray_soft, R.color.velocity_gray_primary,
                        R.color.velocity_gray_soft, R.color.velocity_gray_primary);
            case PAID:
            default:
                return new Style(R.string.ptx_status_paid, R.string.ptx_title_successful, R.drawable.ic_check_circle,
                        R.color.velocity_green_soft, R.color.velocity_green_dark,
                        R.color.velocity_red_primary, R.color.white);
        }
    }
}
