package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The words for a booking / reservation's lifecycle status, the same everywhere a card shows one: "Pending approval",
 * "Confirmed", "Checked in", "Checked out", "Cancelled", "Rejected". They are never the same word as a payment status
 * (Unpaid / Partially paid / Paid), so a card can show both without saying "Pending" twice.
 */
final class BookingStatusWords {

    private BookingStatusWords() {
    }

    /** The label for a raw status ("Checked-In", "Pending", ...); "" when there is none. */
    @NonNull
    static String label(@NonNull Context context, @Nullable String status) {
        if (status == null || status.trim().isEmpty()) return "";
        switch (status.trim().toLowerCase(java.util.Locale.US)) {
            case "pending":
                return context.getString(R.string.booking_status_pending_approval);
            case "confirmed":
                return context.getString(R.string.upcoming_status_confirmed);
            case "checked-in":
                return context.getString(R.string.booking_status_checked_in);
            case "checked-out":
            case "completed":
                return context.getString(R.string.booking_status_checked_out);
            case "cancelled":
            case "canceled":
                return context.getString(R.string.upcoming_status_cancelled);
            case "rejected":
                return context.getString(R.string.upcoming_status_rejected);
            default:
                return TransactionStatusChip.label(status);
        }
    }
}
