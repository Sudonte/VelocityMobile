package com.example.velocitysuites;

import android.content.Context;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * The review sheet the wizard shows before it leaves a step: titled sections made of "label ... amount" rows (label left, amount right,
 * both wrapping so neither can push the other off screen), the totals underneath, and two buttons - Edit (stay on the step) and Continue.
 * One at a time: nothing opens while another dialog or sheet of the wizard is showing.
 */
final class ReviewSheet {

    /** One line: a label on the left and, when given, an amount on the right. */
    static final class Row {
        final String label;
        @Nullable final String amount;
        final boolean bold;
        final boolean indent;
        /** A colour for the amount (0 = the usual one): "₱0.00 · Fully paid" in green. */
        @androidx.annotation.ColorRes int amountColor;

        Row(@NonNull String label, @Nullable String amount, boolean bold, boolean indent) {
            this.label = label;
            this.amount = amount;
            this.bold = bold;
            this.indent = indent;
        }

        static Row of(String label, String amount) {
            return new Row(label, amount, false, false);
        }

        static Row indented(String label, String amount) {
            return new Row(label, amount, false, true);
        }

        static Row bold(String label, String amount) {
            return new Row(label, amount, true, false);
        }

        /** A bold row whose amount is drawn in {@code colorRes}. */
        static Row boldColored(String label, String amount, @androidx.annotation.ColorRes int colorRes) {
            Row r = new Row(label, amount, true, false);
            r.amountColor = colorRes;
            return r;
        }
    }

    /** A bold heading with its rows ("Stay 1 - Oct 25 - Oct 26 - 1 night"). */
    static final class Section {
        final String heading;
        final List<Row> rows = new ArrayList<>();

        Section(@NonNull String heading) {
            this.heading = heading;
        }

        Section add(Row row) {
            rows.add(row);
            return this;
        }
    }

    private ReviewSheet() {
    }

    /** Shows the sheet; returns it (or null when another dialog is already up). */
    @Nullable
    @android.annotation.SuppressLint("InflateParams") // a dialog's content view has no parent to inflate into
    static BottomSheetDialog show(@NonNull BookingWizardActivity host, @NonNull String title, @Nullable String subtitle,
                                  @NonNull List<Section> sections, @NonNull List<Row> totals,
                                  @NonNull String editLabel, @NonNull String continueLabel,
                                  @Nullable Runnable onEdit, @NonNull Runnable onContinue) {
        if (!host.canOpenModal() || host.isFinishing() || host.isDestroyed()) return null;
        View root = LayoutInflater.from(host).inflate(R.layout.sheet_wizard_review, null);
        ((TextView) root.findViewById(R.id.tvReviewTitle)).setText(title);
        TextView tvSub = root.findViewById(R.id.tvReviewSubtitle);
        if (subtitle != null && !subtitle.isEmpty()) {
            tvSub.setText(subtitle);
            tvSub.setVisibility(View.VISIBLE);
        }

        LinearLayout body = root.findViewById(R.id.reviewBody);
        boolean first = true;
        for (Section section : sections) {
            body.addView(heading(host, section.heading, first));
            first = false;
            for (Row row : section.rows) body.addView(row(host, row));
        }
        LinearLayout totalsBox = root.findViewById(R.id.reviewTotals);
        for (Row row : totals) totalsBox.addView(row(host, row));

        // A long review scrolls inside the sheet instead of growing past the screen.
        DisplayMetrics dm = host.getResources().getDisplayMetrics();
        int bodyWidth = dm.widthPixels - Math.round(44 * dm.density);
        body.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, bodyWidth), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int maxBody = Math.round(dm.heightPixels * 0.5f);
        if (body.getMeasuredHeight() > maxBody) {
            View scroll = root.findViewById(R.id.reviewScroll);
            ViewGroup.LayoutParams lp = scroll.getLayoutParams();
            lp.height = maxBody;
            scroll.setLayoutParams(lp);
        }

        BottomSheetDialog dialog = new BottomSheetDialog(host);
        dialog.setContentView(root);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);

        MaterialButton edit = root.findViewById(R.id.btnReviewEdit);
        edit.setText(editLabel);
        edit.setOnClickListener(v -> {
            dialog.dismiss();
            if (onEdit != null) onEdit.run();
        });
        MaterialButton go = root.findViewById(R.id.btnReviewContinue);
        go.setText(continueLabel);
        ClickGuard guard = new ClickGuard();
        go.setOnClickListener(v -> {
            if (!guard.tryAcquire()) return;
            dialog.dismiss();
            onContinue.run();
        });

        host.trackModal(dialog);
        SafeDialogs.show(dialog);
        return dialog;
    }

    static View heading(Context c, String text, boolean first) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setTextColor(ContextCompat.getColor(c, R.color.velocity_text_primary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, first ? 0 : 14);
        lp.bottomMargin = dp(c, 4);
        tv.setLayoutParams(lp);
        return tv;
    }

    /** Label left (wraps), amount right (kept whole). */
    static View row(Context c, Row r) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setPadding(r.indent ? dp(c, 12) : 0, dp(c, 3), 0, dp(c, 3));

        TextView label = new TextView(c);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        label.setText(r.label);
        label.setTextSize(r.bold ? 14 : 12);
        label.setTextColor(ContextCompat.getColor(c, r.bold ? R.color.velocity_text_primary : R.color.velocity_text_secondary));
        if (r.bold) label.setTypeface(label.getTypeface(), Typeface.BOLD);
        row.addView(label);

        if (r.amount != null) {
            TextView amount = new TextView(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(c, 12));
            amount.setLayoutParams(lp);
            amount.setText(r.amount);
            amount.setGravity(android.view.Gravity.END);
            amount.setTextSize(r.bold ? 14 : 12);
            amount.setTextColor(ContextCompat.getColor(c, r.amountColor != 0 ? r.amountColor : R.color.velocity_text_primary));
            if (r.bold) amount.setTypeface(amount.getTypeface(), Typeface.BOLD);
            row.addView(amount);
        }
        return row;
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
