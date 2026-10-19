package com.example.velocitysuites;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * The booking / reservation status pill (Pending, Confirmed, Checked-In, ...) used by the compact dashboard rows and
 * Upcoming Hotel Transactions, so every list colours a status the same way. Night-aware colours; the words are always
 * shown, so the status never depends on colour alone.
 */
final class TransactionStatusChip {

    private TransactionStatusChip() {
    }

    /** Readable status text: "Checked-In" -> "Checked In". */
    @NonNull
    static String label(@Nullable String status) {
        return DisplayLabels.status(status);
    }

    /** The thin accent bar of a stay card: green for Confirmed / Checked-In, gray for Checked-Out, red for Cancelled / Rejected, amber otherwise. */
    static int accentColor(@Nullable String status) {
        if ("Confirmed".equalsIgnoreCase(status) || "Checked-In".equalsIgnoreCase(status) || "Verified".equalsIgnoreCase(status)) {
            return R.color.status_paid_fg;
        }
        if ("Checked-Out".equalsIgnoreCase(status) || "Completed".equalsIgnoreCase(status)) return R.color.status_cancelled_fg;
        if ("Cancelled".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)) return R.color.velocity_red_status_dark;
        return R.color.status_pending_fg;
    }

    /** The status a guest reads for this transaction: "Awaiting verification" until the front desk verifies a booking, else {@link #label(String)}. */
    @NonNull
    static String label(@NonNull Context context, @NonNull Booking b) {
        return TransactionActions.isAwaitingVerification(b) ? context.getString(R.string.status_awaiting_verification) : label(b.getStatus());
    }

    /** The status as the colour scheme sees it: an unverified booking is amber like Pending, not green. */
    @Nullable
    static String colourStatus(@NonNull Booking b) {
        return TransactionActions.isAwaitingVerification(b) ? "Pending" : b.getStatus();
    }

    static void apply(@NonNull Context context, @NonNull TextView chip, @NonNull Booking b) {
        apply(context, chip, colourStatus(b));
        chip.setText(label(context, b));
    }

    static void apply(@NonNull Context context, @NonNull TextView chip, @Nullable String status) {
        int bg;
        int fg;
        if ("Confirmed".equalsIgnoreCase(status) || "Checked-In".equalsIgnoreCase(status) || "Verified".equalsIgnoreCase(status)) {
            bg = R.color.status_paid_bg;
            fg = R.color.status_paid_fg;
        } else if ("Checked-Out".equalsIgnoreCase(status) || "Completed".equalsIgnoreCase(status)) {
            bg = R.color.status_cancelled_bg;
            fg = R.color.status_cancelled_fg;
        } else if ("Cancelled".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)) {
            bg = R.color.velocity_red_status_soft;
            fg = R.color.velocity_red_status_dark;
        } else {
            bg = R.color.status_pending_bg;
            fg = R.color.status_pending_fg;
        }
        chip.setText(label(status));
        chip.setBackgroundResource(R.drawable.shape_intro_pill);
        chip.setBackgroundTintList(ContextCompat.getColorStateList(context, bg));
        chip.setTextColor(ContextCompat.getColor(context, fg));
    }
}
