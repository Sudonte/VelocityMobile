package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.Nullable;

/**
 * The words a transaction shows, built in one place so the history card, the detail header and the export say
 * the same thing the same way: the reference ("RESERVATION #588"), the stay dates, and the Payment line that
 * reports what has been paid, what is left, and what is still awaiting the receptionist.
 */
final class TransactionText {

    private TransactionText() {
    }

    /** "Reservation #588" / "Booking #250" (cards upper-case it). */
    static String reference(Context ctx, TransactionRow row) {
        return reference(ctx, row.kind == TransactionRow.Kind.BOOKING, row.id);
    }

    static String reference(Context ctx, boolean isBooking, @Nullable String id) {
        String shown = id == null || id.trim().isEmpty() ? ctx.getString(R.string.value_missing) : id.trim();
        return ctx.getString(isBooking ? R.string.direct_booking_ref_format : R.string.reservation_ref_format, shown);
    }

    /** "Oct 01, 2026 – Oct 02, 2026"; a missing date is "—", and no dates at all is a single "—". */
    static String stay(Context ctx, @Nullable String checkIn, @Nullable String checkOut) {
        boolean noIn = checkIn == null || checkIn.trim().isEmpty();
        boolean noOut = checkOut == null || checkOut.trim().isEmpty();
        String dash = ctx.getString(R.string.value_missing);
        if (noIn && noOut) return dash;
        return ctx.getString(R.string.ptx_stay_dates_format, noIn ? dash : checkIn.trim(), noOut ? dash : checkOut.trim());
    }

    static String roomOrDash(Context ctx, String roomText) {
        return roomText == null || roomText.trim().isEmpty() ? ctx.getString(R.string.value_missing) : roomText;
    }

    /**
     * The Payment line(s) of a card:
     * <ul>
     *   <li>something verified: "₱900.00 paid • ₱900.00 balance" (a fully paid one reads "₱1,800.00 paid • ₱0.00 balance");</li>
     *   <li>a payment awaiting verification: "₱1,800.00 submitted • awaiting verification" - on its own when nothing
     *       is verified yet, or as a second line under the paid/balance line;</li>
     *   <li>nothing paid or submitted: "₱0.00 paid • ₱1,800.00 balance";</li>
     *   <li>a cancelled/rejected transaction owes nothing more, so it reports only what was paid (or "No payment made").</li>
     * </ul>
     * Only receptionist-verified money ever counts as "paid" (see TransactionStatusHelper).
     */
    static String payment(Context ctx, TransactionStatusHelper.Summary s) {
        if (s.status == TransactionStatusHelper.Status.CANCELLED || s.status == TransactionStatusHelper.Status.REJECTED) {
            return MoneyFormat.isPositive(s.verifiedPaid)
                    ? ctx.getString(R.string.txn_payment_paid_only_format, MoneyFormat.format(s.verifiedPaid))
                    : ctx.getString(R.string.txn_payment_none);
        }
        StringBuilder sb = new StringBuilder();
        boolean anythingVerified = MoneyFormat.isPositive(s.verifiedPaid);
        if (anythingVerified || !s.hasPendingPayment) {
            sb.append(ctx.getString(R.string.txn_payment_paid_balance_format,
                    MoneyFormat.format(s.verifiedPaid), MoneyFormat.format(s.balance)));
        }
        if (s.hasPendingPayment) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(MoneyFormat.isPositive(s.pendingSubmitted)
                    ? ctx.getString(R.string.txn_payment_submitted_format, MoneyFormat.format(s.pendingSubmitted))
                    : ctx.getString(R.string.txn_payment_submitted_unknown));
        }
        return sb.toString();
    }
}
