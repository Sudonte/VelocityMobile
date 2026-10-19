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

    /** The status word for a transaction: "Awaiting verification" until the front desk verifies a booking (same flag the Cancel rule uses). */
    public static String statusWord(Context context, Booking b) {
        return TransactionStatusChip.label(context, b);
    }

    public static String computeStatusLabel(Context context, Booking b) {
        boolean noShow = b.isNoShow();
        boolean pendingVerification = b.isPaymentPendingVerification() && !b.isStaffVerified() && !TransactionActions.isAwaitingVerification(b);
        boolean cancelledWithPaymentAttempt = b.getStatus().equalsIgnoreCase("Cancelled")
                && (b.getGcashNumber() != null || b.getTransactionRef() != null || b.getAmountPaid() > 0.009);
        return noShow
                ? context.getString(R.string.status_no_show)
                : pendingVerification
                    ? context.getString(R.string.pending_verification_label)
                    : cancelledWithPaymentAttempt
                        ? context.getString(R.string.transaction_failed_label)
                        : statusWord(context, b);
    }

    public static void styleStatusBadge(Context context, TextView badge, Booking b) {
        boolean pendingVerification = b.isPaymentPendingVerification() && !b.isStaffVerified() && !TransactionActions.isAwaitingVerification(b);
        int iconRes = R.drawable.ic_clock;
        String colour = TransactionStatusChip.colourStatus(b);
        String status = colour == null ? "" : colour;
        // One colour scheme across the app: Pending amber, Confirmed and Checked in green, Checked out gray, Cancelled / Rejected red
        // (a payment still waiting for the front desk is blue).
        int bg;
        int fg;
        if (status.equalsIgnoreCase("Cancelled") || status.equalsIgnoreCase("Rejected")) {
            bg = R.color.velocity_red_status_soft;
            fg = R.color.velocity_red_status_dark;
            iconRes = R.drawable.ic_close;
        } else if (pendingVerification) {
            bg = R.color.velocity_blue_soft;
            fg = R.color.velocity_blue_primary;
            iconRes = R.drawable.ic_info;
        } else if (status.equalsIgnoreCase("Confirmed") || status.equalsIgnoreCase("Checked-In")) {
            bg = R.color.status_paid_bg;
            fg = R.color.status_paid_fg;
            iconRes = R.drawable.ic_check_circle;
        } else if (status.equalsIgnoreCase("Checked-Out") || status.equalsIgnoreCase("Completed")) {
            bg = R.color.status_cancelled_bg;
            fg = R.color.status_cancelled_fg;
            iconRes = R.drawable.ic_check_circle;
        } else {
            bg = R.color.status_pending_bg;
            fg = R.color.status_pending_fg;
        }
        badge.setBackgroundTintList(ColorStateList.valueOf(context.getColor(bg)));
        badge.setTextColor(context.getColor(fg));
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
