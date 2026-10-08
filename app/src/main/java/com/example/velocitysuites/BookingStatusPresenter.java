package com.example.velocitysuites;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.Locale;

/**
 * Status label/badge logic shared between the compact booking card
 * (BookingAndReservationActivity) and the full BookingDetailsActivity, so
 * both always agree on what a given Booking's status reads/looks like.
 * Extracted from BookingAndReservationActivity's own former private
 * computeStatusLabel()/styleStatusBadge() methods - behavior unchanged.
 */
public final class BookingStatusPresenter {

    private BookingStatusPresenter() {}

    public static String computeStatusLabel(Context context, Booking b) {
        boolean noShow = b.isNoShow();
        boolean pendingVerification = b.isPaymentPendingVerification() && !b.isStaffVerified();
        boolean cancelledWithPaymentAttempt = b.getStatus().equalsIgnoreCase("Cancelled")
                && (b.getGcashNumber() != null || b.getTransactionRef() != null || b.getAmountPaid() > 0.009);
        return noShow
                ? context.getString(R.string.status_no_show)
                : pendingVerification
                    ? context.getString(R.string.pending_verification_label).toUpperCase(Locale.US)
                    : cancelledWithPaymentAttempt
                        ? context.getString(R.string.transaction_failed_label)
                        : b.getStatus().toUpperCase(Locale.US);
    }

    public static void styleStatusBadge(Context context, TextView badge, Booking b) {
        boolean pendingVerification = b.isPaymentPendingVerification() && !b.isStaffVerified();
        int iconRes = R.drawable.ic_clock;
        if (b.getStatus().equalsIgnoreCase("Cancelled")) {
            badge.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.status_cancelled_bg)));
            badge.setTextColor(context.getColor(R.color.status_cancelled_fg));
            iconRes = R.drawable.ic_close;
        } else if (b.getStatus().equalsIgnoreCase("Rejected")) {
            // Distinct from Cancelled (guest-initiated, neutral gray) -
            // Rejected is a staff decision, so it gets the app's warning/
            // error red rather than being visually indistinguishable from
            // a plain cancellation (see class doc: "different semantic
            // appearances for each status").
            badge.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.status_rejected_bg)));
            badge.setTextColor(context.getColor(R.color.status_rejected_fg));
            iconRes = R.drawable.ic_close;
        } else if (pendingVerification) {
            badge.setBackgroundTintList(ColorStateList.valueOf(context.getResources().getColor(R.color.velocity_blue_soft)));
            badge.setTextColor(context.getResources().getColor(R.color.velocity_blue_primary));
            iconRes = R.drawable.ic_info;
        } else if (b.getStatus().equalsIgnoreCase("Confirmed")) {
            badge.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.status_paid_bg)));
            badge.setTextColor(context.getColor(R.color.status_paid_fg));
            iconRes = R.drawable.ic_check_circle;
        } else if (b.getStatus().equalsIgnoreCase("Pending")) {
            badge.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.status_pending_bg)));
            badge.setTextColor(context.getColor(R.color.status_pending_fg));
            iconRes = R.drawable.ic_clock;
        }
        Drawable icon = ContextCompat.getDrawable(context, iconRes);
        if (icon != null) {
            int size = (int) (12 * context.getResources().getDisplayMetrics().density);
            icon.setBounds(0, 0, size, size);
            icon.setTint(badge.getCurrentTextColor());
        }
        badge.setCompoundDrawables(icon, null, null, null);
        badge.setCompoundDrawablePadding((int) (4 * context.getResources().getDisplayMetrics().density));
    }

    /**
     * Plain payment-status word for the dedicated Payment Status pill (no "Payment: " prefix) - the shared
     * status every transaction screen uses (TransactionStatusHelper): Pending / Paid / Partially Paid /
     * Cancelled / Rejected, decided by the receptionist-verified amount against the grand total.
     */
    public static String paymentStatusPillText(Context context, Booking b) {
        return context.getString(TransactionStatusHelper.styleFor(TransactionStatusHelper.statusOf(b)).labelRes);
    }

    public static void stylePaymentStatusPill(Context context, TextView pill, Booking b) {
        TransactionStatusHelper.Style style = TransactionStatusHelper.styleFor(TransactionStatusHelper.statusOf(b));
        pill.setBackgroundTintList(ColorStateList.valueOf(context.getResources().getColor(style.bgColorRes)));
        pill.setTextColor(context.getResources().getColor(style.fgColorRes));
        Drawable icon = ContextCompat.getDrawable(context, style.iconRes);
        if (icon != null) {
            int size = (int) (12 * context.getResources().getDisplayMetrics().density);
            icon.setBounds(0, 0, size, size);
            icon.setTint(context.getResources().getColor(style.fgColorRes));
        }
        pill.setCompoundDrawables(icon, null, null, null);
        pill.setCompoundDrawablePadding((int) (4 * context.getResources().getDisplayMetrics().density));
    }
}
