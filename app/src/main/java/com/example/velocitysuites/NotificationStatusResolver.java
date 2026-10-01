package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * The status pill (and status icon) of a notification card. A notification is a point-in-time EVENT - the
 * notifications table has no status column - so what it announces is read from the backend-authored
 * {@code title} ("Payment Pending Validation", "Reservation Cancelled", "Payment Verified", ...) and, for a
 * payment-verified/checkout notification, from the backend-attached {@code receipt_type}: a Partial Receipt
 * means the verified payment left a balance (Partially Paid), a Full-Payment/Official Receipt means it
 * settled the transaction (Paid). The receipt type is backend data, so this never guesses paid-ness from
 * wording alone when the backend said which it was.
 * <p>
 * For the states the payment rules define (Pending / Paid / Partially Paid / Cancelled / Rejected) the label,
 * color and icon come from {@link TransactionStatusHelper#styleFor}, the same source Transaction History,
 * the detail screen and the receipt use - so a "Pending" notification and a "Pending" history card are the
 * same amber clock. The lifecycle outcomes that are not payment states (Confirmed, Checked in/out, Verified)
 * share the green check.
 * <p>
 * An old notification is deliberately NOT re-styled from the transaction's current state: "Payment Pending
 * Validation" stays Pending forever - the later "Payment Verified" notification carries the new state.
 * <p>
 * Returns no status (and therefore no pill, and the category icon) for general/system notifications.
 */
public final class NotificationStatusResolver {

    private NotificationStatusResolver() {
    }

    /**
     * The pure, Context-free decision this whole class exists to make - separated from resolve() purely so
     * it is JVM-unit-testable (see NotificationStatusResolverTest, and PaymentStatusResolver's identical
     * pattern). resolve() below is a thin Context-string/color lookup over this result.
     */
    public enum StatusKey {
        CANCELLED, REJECTED, PENDING, CHECKED_IN, CHECKED_OUT, CONFIRMED, VERIFIED, FULLY_PAID, PARTIALLY_PAID
    }

    public static StatusKey resolveKey(@Nullable String title) {
        return resolveKey(title, null);
    }

    /** @param receiptType PARTIAL_RECEIPT / FULL_PAYMENT_RECEIPT / OFFICIAL_RECEIPT, or null - see Notification#getReceiptType() */
    public static StatusKey resolveKey(@Nullable String title, @Nullable String receiptType) {
        String lower = title == null ? "" : title.toLowerCase(Locale.US);

        // Negative outcomes first: nothing the notification says later can make them positive.
        if (lower.contains("cancelled") || lower.contains("canceled")) return StatusKey.CANCELLED;
        if (lower.contains("rejected")) return StatusKey.REJECTED;

        // The backend says exactly what the verified payment amounted to.
        if ("PARTIAL_RECEIPT".equals(receiptType)) return StatusKey.PARTIALLY_PAID;
        if ("FULL_PAYMENT_RECEIPT".equals(receiptType) || "OFFICIAL_RECEIPT".equals(receiptType)) return StatusKey.FULLY_PAID;

        if (title == null) return null;
        if (lower.contains("pending")) return StatusKey.PENDING;
        if (lower.contains("verified")) return StatusKey.VERIFIED;
        if (lower.contains("checked in")) return StatusKey.CHECKED_IN;
        if (lower.contains("checked out")) return StatusKey.CHECKED_OUT;
        if (lower.contains("confirmed")) return StatusKey.CONFIRMED;
        if (lower.contains("complete") || lower.contains("received") || lower.contains("recorded")) return StatusKey.FULLY_PAID;
        return null;
    }

    /** The status pill/icon for this notification, or null when it implies no status. */
    @Nullable
    public static Result resolve(Context ctx, Notification notification) {
        StatusKey key = resolveKey(notification.getTitle(), notification.getReceiptType());
        if (key == null) return null;

        switch (key) {
            case PENDING:
                return fromShared(ctx, TransactionStatusHelper.Status.PENDING);
            case CANCELLED:
                return fromShared(ctx, TransactionStatusHelper.Status.CANCELLED);
            case REJECTED:
                return fromShared(ctx, TransactionStatusHelper.Status.REJECTED);
            case PARTIALLY_PAID:
                return fromShared(ctx, TransactionStatusHelper.Status.PARTIALLY_PAID);
            case FULLY_PAID:
                return fromShared(ctx, TransactionStatusHelper.Status.PAID);
            case CHECKED_IN:
                return positive(ctx, R.string.status_checked_in);
            case CHECKED_OUT:
                return positive(ctx, R.string.status_checked_out);
            case VERIFIED:
                return positive(ctx, R.string.status_verified);
            case CONFIRMED:
            default:
                return positive(ctx, R.string.status_confirmed);
        }
    }

    private static Result fromShared(Context ctx, TransactionStatusHelper.Status status) {
        TransactionStatusHelper.Style style = TransactionStatusHelper.styleFor(status);
        return new Result(ctx.getString(style.labelRes), style.iconRes, style.bgColorRes, style.fgColorRes);
    }

    /** A good outcome that is not itself a payment state - same green check the Paid state uses. */
    private static Result positive(Context ctx, int labelRes) {
        TransactionStatusHelper.Style paid = TransactionStatusHelper.styleFor(TransactionStatusHelper.Status.PAID);
        return new Result(ctx.getString(labelRes), paid.iconRes, paid.bgColorRes, paid.fgColorRes);
    }

    /** What the card shows for a status: the pill's label/colors, and the icon that replaces the category icon in the circle. */
    public static final class Result {
        public final String label;
        /** Clock (pending), check (paid / verified / confirmed / checked in-out) or X (cancelled / rejected). */
        @DrawableRes
        public final int iconRes;
        public final int bgColorRes;
        public final int fgColorRes;

        Result(String label, @DrawableRes int iconRes, int bgColorRes, int fgColorRes) {
            this.label = label;
            this.iconRes = iconRes;
            this.bgColorRes = bgColorRes;
            this.fgColorRes = fgColorRes;
        }
    }
}
