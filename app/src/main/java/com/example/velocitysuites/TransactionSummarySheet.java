package com.example.velocitysuites;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * The details view of one booking or reservation as a bottom sheet: reference no., type, status, room, check-in and
 * check-out, guests, the amounts and the payment method. A fact the API did not send is left out (never a placeholder).
 * Opened from a card in Upcoming Hotel Transactions and when the Dashboard's Recent Upcoming Stay sends the guest there;
 * its button goes on to the full record in Transaction History.
 */
final class TransactionSummarySheet {

    private TransactionSummarySheet() {
    }

    /** One label/value line. */
    static final class Row {
        final String label;
        final String value;

        Row(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    /** The rows to show, in order, only for what the transaction actually carries. */
    @NonNull
    static List<Row> rows(@NonNull Context context, @NonNull Booking b) {
        List<Row> rows = new ArrayList<>();
        add(rows, context.getString(R.string.sheet_label_room), BillingSummaryActivity.roomNameOf(b));
        add(rows, context.getString(R.string.sheet_label_check_in), whenOf(context, b.getCheckInDate(), StayCalendarModel.CHECK_IN_TIME));
        add(rows, context.getString(R.string.sheet_label_check_out), whenOf(context, b.getCheckOutDate(), StayCalendarModel.CHECK_OUT_TIME));
        long nights = StayDateCalculator.nightsBetween(b.getCheckInDate(), b.getCheckOutDate());
        if (nights > 0) {
            add(rows, context.getString(R.string.nights_label),
                    context.getResources().getQuantityString(R.plurals.receipt_nights_count, (int) nights, (int) nights));
        }
        if (b.getGuests() > 0) {
            add(rows, context.getString(R.string.total_guests_label),
                    context.getResources().getQuantityString(R.plurals.guests_count, b.getGuests(), b.getGuests()));
        }
        TransactionStatusHelper.Summary money = TransactionStatusHelper.summarize(b);
        add(rows, context.getString(R.string.grand_total_label), MoneyFormat.format(b.getTotalAmount()));
        add(rows, context.getString(R.string.billing_amount_paid_label), MoneyFormat.format(b.getEffectiveTotalAmountPaid()));
        add(rows, context.getString(R.string.billing_remaining_balance_label), MoneyFormat.format(money.balance));
        String method = b.getPaymentMethod();
        if (method != null && !method.trim().isEmpty()) {
            add(rows, context.getString(R.string.review_payment_method_label),
                    "cash".equalsIgnoreCase(method.trim()) ? context.getString(R.string.payment_method_cash)
                            : context.getString(R.string.payment_method_gcash));
        }
        return rows;
    }

    private static void add(List<Row> rows, String label, @Nullable String value) {
        if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) rows.add(new Row(label, value));
    }

    @Nullable
    private static String whenOf(Context context, @Nullable String date, java.time.LocalTime time) {
        if (date == null || date.trim().isEmpty()) return null;
        return context.getString(R.string.stay_calendar_when_format, date, StayCalendarModel.formatTime(time));
    }

    /** Shows the sheet; {@code onViewFull} runs when the guest asks for the full record. Returns it (null if the screen is closing). */
    @Nullable
    static BottomSheetDialog show(@NonNull Activity activity, @NonNull Booking b, @NonNull Runnable onViewFull) {
        if (activity.isFinishing() || activity.isDestroyed()) return null;
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        // A dialog has no parent view to inflate against.
        @android.annotation.SuppressLint("InflateParams")
        View content = LayoutInflater.from(activity).inflate(R.layout.sheet_transaction_summary, null, false);
        ((TextView) content.findViewById(R.id.tvSheetType)).setText(
                b.isHasBooking() ? R.string.upcoming_event_booking_prefix : R.string.upcoming_event_reservation_prefix);
        ((TextView) content.findViewById(R.id.tvSheetRef)).setText(TransactionText.reference(activity, b.isHasBooking(), b.getDisplayId()));
        TransactionStatusChip.apply(activity, content.findViewById(R.id.tvSheetStatus), b.getStatus());

        LinearLayout container = content.findViewById(R.id.sheetRows);
        for (Row row : rows(activity, b)) container.addView(buildRow(activity, row));
        content.findViewById(R.id.btnSheetViewFull).setOnClickListener(v -> {
            dialog.dismiss();
            onViewFull.run();
        });
        dialog.setContentView(content);
        dialog.show();
        return dialog;
    }

    private static View buildRow(Context context, Row row) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

        TextView label = new TextView(context);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        label.setText(row.label);
        label.setTextSize(13f);
        label.setTextColor(context.getColor(R.color.velocity_text_secondary));

        TextView value = new TextView(context);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f);
        vp.setMarginStart((int) (12 * density));
        value.setLayoutParams(vp);
        value.setText(row.value);
        value.setTextSize(13f);
        value.setGravity(android.view.Gravity.END);
        value.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        value.setTextColor(context.getColor(R.color.velocity_text_primary));

        line.addView(label);
        line.addView(value);
        return line;
    }
}
