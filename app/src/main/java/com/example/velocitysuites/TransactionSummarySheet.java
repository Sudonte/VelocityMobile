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
 * Opened from a card in Upcoming Hotel Transactions and when the Dashboard's Pending & Confirmed Stays sends the guest there;
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

    /** A titled group of rows: Stay Details, Room Details, Payment Details. */
    static final class Section {
        final String title;
        final List<Row> rows;

        Section(String title, List<Row> rows) {
            this.title = title;
            this.rows = rows;
        }
    }

    private static List<Row> rowsOf(List<TransactionDetailSections.Row> source) {
        List<Row> rows = new ArrayList<>();
        for (TransactionDetailSections.Row r : source) rows.add(new Row(r.label, r.value));
        return rows;
    }

    /** The sections to show, in order; a section with nothing to say is left out entirely. */
    @NonNull
    static List<Section> sections(@NonNull Context context, @NonNull Booking b) {
        TransactionDetailSections model = TransactionDetailSections.of(context, b, null);
        List<Section> sections = new ArrayList<>();
        if (!model.stay.isEmpty()) sections.add(new Section(context.getString(R.string.upcoming_section_stay), rowsOf(model.stay)));
        if (!model.room.isEmpty()) sections.add(new Section(context.getString(R.string.upcoming_section_room), rowsOf(model.room)));
        if (!model.payment.isEmpty()) sections.add(new Section(context.getString(R.string.upcoming_section_payment), rowsOf(model.payment)));
        return sections;
    }

    /** Every row in section order (for callers that only need the facts). */
    @NonNull
    static List<Row> rows(@NonNull Context context, @NonNull Booking b) {
        List<Row> all = new ArrayList<>();
        for (Section section : sections(context, b)) all.addAll(section.rows);
        return all;
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
        for (Section section : sections(activity, b)) {
            container.addView(buildHeader(activity, section.title, container.getChildCount() == 0));
            for (Row row : section.rows) container.addView(buildRow(activity, row));
        }
        content.findViewById(R.id.btnSheetViewFull).setOnClickListener(v -> {
            dialog.dismiss();
            onViewFull.run();
        });
        dialog.setContentView(content);
        dialog.show();
        return dialog;
    }

    private static View buildHeader(Context context, String title, boolean first) {
        float density = context.getResources().getDisplayMetrics().density;
        TextView header = new TextView(context);
        header.setText(title);
        header.setTextSize(12f);
        header.setAllCaps(false);
        header.setLetterSpacing(0f);
        header.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.setTextColor(context.getColor(R.color.velocity_red_primary));
        header.setPadding(0, (int) ((first ? 0 : 14) * density), 0, (int) (2 * density));
        header.setAccessibilityHeading(true);
        return header;
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
