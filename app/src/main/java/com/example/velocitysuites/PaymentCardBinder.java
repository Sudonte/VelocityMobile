package com.example.velocitysuites;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Fills one item_payment_history_entry card: amount (₱1,234.50), status chip, method, date, reference and - for a cash
 * payment that recorded what the guest handed over - "Cash received" and "Change". GCash payments never show the
 * cash rows. Shared by the booking / transaction detail screens so they read the same.
 */
final class PaymentCardBinder {

    private PaymentCardBinder() { }

    static void bind(@NonNull View card, @NonNull Booking.PaymentRecord record, @NonNull Context context) {
        ((TextView) card.findViewById(R.id.tvHistoryAmount)).setText(MoneyFormat.format(MoneyFormat.parse(record.amount)));
        ((TextView) card.findViewById(R.id.tvHistoryStatus)).setText(statusText(record.status));
        ((TextView) card.findViewById(R.id.tvHistoryMethod)).setText(
                record.method != null ? record.method : context.getString(R.string.label_not_available));
        ((TextView) card.findViewById(R.id.tvHistoryDate)).setText(
                record.date != null ? record.date : context.getString(R.string.label_not_available));

        TextView reference = card.findViewById(R.id.tvHistoryReference);
        boolean hasReference = record.referenceNumber != null && !record.referenceNumber.trim().isEmpty();
        reference.setVisibility(hasReference ? View.VISIBLE : View.GONE);
        if (hasReference) reference.setText(context.getString(R.string.payment_history_reference_format, record.referenceNumber));

        View cash = card.findViewById(R.id.layoutHistoryCash);
        boolean showCash = record.hasCashTender();
        cash.setVisibility(showCash ? View.VISIBLE : View.GONE);
        if (showCash) {
            ((TextView) card.findViewById(R.id.tvHistoryCashReceived)).setText(MoneyFormat.format(MoneyFormat.parse(record.cashReceived)));
            ((TextView) card.findViewById(R.id.tvHistoryChange)).setText(MoneyFormat.format(MoneyFormat.parse(record.changeGiven)));
        }
    }

    /** "Completed" / "Pending" ... for the status chip. */
    static String statusText(@Nullable String status) {
        if (status == null || status.trim().isEmpty()) return "";
        String s = status.trim();
        return s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1).toLowerCase(Locale.US);
    }
}
