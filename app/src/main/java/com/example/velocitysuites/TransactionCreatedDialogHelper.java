package com.example.velocitysuites;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Shown once per successful Booking/Reservation creation, right before
 * navigating to the newly created transaction - large, bold, centered
 * transaction ID (dialog_transaction_created.xml) so the guest can't miss
 * or lose it. Acknowledging it ("View My Booking"/"View My Reservation")
 * is what actually triggers onAcknowledged - never redirects silently
 * without showing the ID first.
 *
 * Shared by every creation path so none of them can accidentally skip this
 * step: PaymentActivity#navigateToBookingSection()/navigateToReservationSection()
 * (a direct Booking's GCash success, an existing Reservation's Pay Now/GCash
 * success) and Step8ReviewPaymentFragment#onReservationsCreated() (a fresh
 * Reservation's own Cash/GCash-pay-later creation, which never passes
 * through PaymentActivity at all).
 */
final class TransactionCreatedDialogHelper {

    private TransactionCreatedDialogHelper() {}

    interface OnAcknowledged {
        void run();
    }

    /**
     * A null/empty transactionId (shouldn't normally happen at this point in
     * the flow, but defensive) skips straight to onAcknowledged rather than
     * showing a dialog with a blank ID.
     */
    static void show(Context context, boolean isBooking, @Nullable String transactionId, OnAcknowledged onAcknowledged) {
        if (transactionId == null || transactionId.isEmpty()) {
            onAcknowledged.run();
            return;
        }

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_transaction_created, null);
        TextView tvTitle = dialogView.findViewById(R.id.tvTransactionCreatedTitle);
        TextView tvLabel = dialogView.findViewById(R.id.tvTransactionCreatedLabel);
        TextView tvId = dialogView.findViewById(R.id.tvTransactionCreatedId);
        TextView tvFooter = dialogView.findViewById(R.id.tvTransactionCreatedFooter);
        MaterialButton btnAction = dialogView.findViewById(R.id.btnTransactionCreatedAction);

        tvTitle.setText(isBooking ? R.string.booking_created_dialog_title : R.string.reservation_created_dialog_title);
        tvLabel.setText(isBooking ? R.string.your_booking_id_is_label : R.string.your_reservation_id_is_label);
        tvId.setText(context.getString(isBooking ? R.string.direct_booking_ref_format : R.string.reservation_ref_format, transactionId));
        tvFooter.setText(isBooking ? R.string.keep_booking_id_reference_msg : R.string.keep_reservation_id_reference_msg);
        btnAction.setText(isBooking ? R.string.view_my_booking_button : R.string.view_my_reservation_button);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setCancelable(false)
                .create();
        btnAction.setOnClickListener(v -> {
            dialog.dismiss();
            onAcknowledged.run();
        });
        dialog.show();
    }
}
