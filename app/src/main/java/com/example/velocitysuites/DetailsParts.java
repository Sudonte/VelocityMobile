package com.example.velocitysuites;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The parts of the guest details screens that sit around the stay and the bill (see {@link PayingForBreakdown}): the one plain status
 * line under the transaction ID, the payment progress (a bar and the steps the money went through), and the list of the three receipts.
 * Booking Details, Reservation Details and Transaction History details all use these, so they cannot disagree.
 */
final class DetailsParts {

    private DetailsParts() {
    }

    // ---- the one plain status line ----

    /** "Checked out on Oct 24, 2026", "Waiting for front desk verification", "Converted to Booking #592"... */
    @NonNull
    static String statusLine(@NonNull Context c, @NonNull Booking b) {
        if (b.isHistoricalReservation()) {
            return c.getString(R.string.status_line_converted, b.getConvertedBookingId() != null ? b.getConvertedBookingId() : b.getDisplayId());
        }
        String status = b.getStatus() == null ? "" : b.getStatus();
        if ("Cancelled".equalsIgnoreCase(status)) return c.getString(R.string.status_line_cancelled);
        if ("Rejected".equalsIgnoreCase(status)) return c.getString(R.string.status_line_rejected);
        if (b.isNoShow()) return c.getString(R.string.status_line_no_show);
        if ("Completed".equalsIgnoreCase(status) || "Checked-Out".equalsIgnoreCase(status)) {
            String when = dateOf(b.getEventTime("completed"));
            if (when == null) when = dateOf(b.getEventTime("checked_out"));
            return when != null ? c.getString(R.string.status_line_checked_out_on, when) : c.getString(R.string.status_line_checked_out);
        }
        if ("Checked-In".equalsIgnoreCase(status)) {
            String when = dateOf(b.getEventTime("checked_in"));
            return when != null ? c.getString(R.string.status_line_checked_in_on, when) : c.getString(R.string.status_line_checked_in);
        }
        if (b.isHasBooking() && !b.isStaffVerified()) return c.getString(R.string.status_line_waiting_verification);
        if (b.isHasBooking()) return c.getString(R.string.status_line_confirmed);
        return c.getString(R.string.status_line_reservation_open);
    }

    @Nullable
    private static String dateOf(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        long ms = ApiMapper_epoch(raw);
        if (ms <= 0) return null;
        return java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
                .format(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.of("Asia/Manila")));
    }

    private static long ApiMapper_epoch(String raw) {
        return com.example.velocitysuites.network.ApiMapper.epochMillis(raw);
    }

    /** A date and time for a payment or receipt row, or null when the server sent none (never "N/A"). */
    @Nullable
    static String when(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String shown = TimeUtils.formatDateTime(raw);
        return shown == null || shown.trim().isEmpty() || "N/A".equalsIgnoreCase(shown.trim()) ? null : shown;
    }

    // ---- payment progress ----

    /** One step of the money's way: its words, when it happened (null = not yet) and whether it is the one the guest is on. */
    static final class Step {
        final String label;
        @Nullable final String when;
        final boolean done;
        boolean current;

        Step(String label, @Nullable String when, boolean done) {
            this.label = label;
            this.when = when;
            this.done = done;
        }
    }

    /** The steps that apply to this transaction, in order. Empty for a cancelled or rejected one. */
    @NonNull
    static List<Step> progressSteps(@NonNull Context c, @NonNull Booking b) {
        List<Step> steps = new ArrayList<>();
        TransactionStatusHelper.Summary summary = TransactionStatusHelper.summarize(b);
        if (summary.status == TransactionStatusHelper.Status.CANCELLED || summary.status == TransactionStatusHelper.Status.REJECTED) return steps;
        Booking.PaymentTransactionRecord online = null;
        Booking.PaymentTransactionRecord atCheckin = null;
        Booking.PaymentTransactionRecord atCheckout = null;
        Booking.PaymentTransactionRecord rejected = null;
        for (Booking.PaymentTransactionRecord p : b.getPaymentTransactions()) {
            if (p == null) continue;
            if ("gcash".equalsIgnoreCase(p.paymentMethod)) {
                boolean wasRejected = "rejected".equalsIgnoreCase(p.verificationStatus) || "rejected".equalsIgnoreCase(p.paymentStatus) || "failed".equalsIgnoreCase(p.paymentStatus);
                if (wasRejected) rejected = p;
                else if (online == null) online = p;
                continue;
            }
            if (!"completed".equalsIgnoreCase(p.paymentStatus)) continue;
            if (p.referenceNumber != null && p.referenceNumber.startsWith("CHECKIN-")) {
                if (atCheckin == null) atCheckin = p;
            } else if (atCheckout == null) {
                atCheckout = p;
            }
        }
        boolean checkedIn = "Checked-In".equalsIgnoreCase(b.getStatus()) || "Completed".equalsIgnoreCase(b.getStatus()) || "Checked-Out".equalsIgnoreCase(b.getStatus());
        boolean finished = "Completed".equalsIgnoreCase(b.getStatus()) || "Checked-Out".equalsIgnoreCase(b.getStatus());
        boolean fully = summary.balance <= MoneyFormat.EPSILON && summary.grandTotal > MoneyFormat.EPSILON;

        if (online != null) {
            boolean verified = online.verifiedAt != null || "verified".equalsIgnoreCase(online.verificationStatus);
            // a submitted payment is "submitted" until the front desk verifies it; only then it is "paid online"
            steps.add(new Step(c.getString(verified ? R.string.progress_paid_online : R.string.progress_submitted), when(online.paymentDate), true));
            steps.add(new Step(c.getString(R.string.progress_verified), verified ? when(online.verifiedAt) : null, verified));
        } else if (rejected != null) {
            String reason = rejected.rejectionReason != null && !rejected.rejectionReason.trim().isEmpty()
                    ? c.getString(R.string.payment_rejected_format, rejected.rejectionReason.trim()) : c.getString(R.string.payment_rejected_plain);
            steps.add(new Step(reason, when(rejected.paymentDate), false));
        }
        if (atCheckin != null || (!checkedIn && !fully)) {
            steps.add(new Step(c.getString(R.string.progress_paid_checkin), atCheckin != null ? when(atCheckin.paymentDate) : null, atCheckin != null));
        }
        if (atCheckout != null || (checkedIn && !finished && !fully) || (finished && atCheckout != null)) {
            steps.add(new Step(c.getString(R.string.progress_paid_checkout), atCheckout != null ? when(atCheckout.paymentDate) : null, atCheckout != null));
        }
        steps.add(new Step(c.getString(R.string.progress_fully_paid), null, fully));
        // the current one: the first step still to come, or the last one when everything is done
        Step current = null;
        for (Step s : steps) if (!s.done) { current = s; break; }
        if (current == null && !steps.isEmpty()) current = steps.get(steps.size() - 1);
        if (current != null) current.current = true;
        return steps;
    }

    /** "50% paid", the bar, and the steps with their check marks. */
    static void paymentProgress(@NonNull Context c, @NonNull ViewGroup into, @NonNull Booking b) {
        into.removeAllViews();
        List<Step> steps = progressSteps(c, b);
        if (steps.isEmpty()) {
            into.setVisibility(View.GONE);
            return;
        }
        into.setVisibility(View.VISIBLE);
        TransactionStatusHelper.Summary summary = TransactionStatusHelper.summarize(b);
        double total = summary.grandTotal;
        double paid = Math.min(summary.verifiedPaid, total > 0 ? total : summary.verifiedPaid);
        // a percentage: the Grand total and the Remaining balance are the summary at the bottom, each shown once
        int percent = total > MoneyFormat.EPSILON ? (int) Math.floor(Math.min(1.0, paid / total) * 100 + 1e-9) : 0;

        TextView title = text(c, c.getString(R.string.progress_title), 12, true, R.color.velocity_action_text);
        title.setPadding(0, 0, 0, dp(c, 8));
        into.addView(title);
        TextView head = text(c, c.getString(R.string.progress_percent_paid, percent), 14, true, R.color.velocity_text_primary);
        head.setPadding(0, 0, 0, dp(c, 8));
        into.addView(head);
        if (summary.pendingSubmitted > MoneyFormat.EPSILON) {
            TextView waiting = text(c, c.getString(R.string.progress_waiting_line, MoneyFormat.format(summary.pendingSubmitted)), 12, false, R.color.velocity_text_secondary);
            waiting.setPadding(0, 0, 0, dp(c, 8));
            into.addView(waiting);
        }

        ProgressBar bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(total > 0 ? (int) Math.round(Math.min(1.0, paid / total) * 1000) : 0);
        bar.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(c, R.color.velocity_action_text)));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, R.color.velocity_divider_hairline)));
        bar.setContentDescription(head.getText());
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 10)));
        into.addView(bar);

        for (Step s : steps) {
            String mark = s.done ? "✓ " : "○ ";
            String line = mark + s.label + (s.when != null ? " · " + s.when : "");
            TextView tv = text(c, line, 13, s.current, s.done || s.current ? R.color.velocity_text_primary : R.color.velocity_text_secondary);
            tv.setPadding(0, dp(c, 8), 0, 0);
            into.addView(tv);
        }
    }

    // ---- the three receipts ----

    /** Receipt 1, 2 and 3 in order: issued ones open their receipt, the others are greyed with when they will be issued. */
    static void receipts(@NonNull Activity activity, @NonNull ViewGroup into, @NonNull Booking b) {
        into.removeAllViews();
        TransactionStatusHelper.Summary summary = TransactionStatusHelper.summarize(b);
        if (summary.status == TransactionStatusHelper.Status.CANCELLED || summary.status == TransactionStatusHelper.Status.REJECTED) {
            return;
        }
        Booking.ReceiptSummary[] issued = new Booking.ReceiptSummary[4];
        for (Booking.ReceiptSummary r : b.getReceipts()) {
            int stage = ReceiptTypeMapper.stageOf(r.receiptType);
            if (stage >= 1 && stage <= 3 && issued[stage] == null) issued[stage] = r;
        }
        int[] title = {0, R.string.receipt_row_1, R.string.receipt_row_2, R.string.receipt_row_3};
        int[] later = {0, R.string.receipt_later_1, R.string.receipt_later_2, R.string.receipt_later_3};
        for (int stage = 1; stage <= 3; stage++) {
            Booking.ReceiptSummary r = issued[stage];
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
            row.setBackgroundResource(R.drawable.bg_flat_box);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(activity, 8);
            row.setLayoutParams(lp);

            LinearLayout top = new LinearLayout(activity);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(activity, activity.getString(title[stage]), 14, true, r != null ? R.color.velocity_text_primary : R.color.velocity_text_secondary);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            top.addView(name);
            if (r != null) {
                TextView chip = text(activity, activity.getString(R.string.receipt_chip_issued), 12, true, R.color.velocity_green_dark);
                chip.setBackgroundResource(R.drawable.shape_intro_pill);
                chip.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.velocity_green_soft)));
                chip.setPadding(dp(activity, 10), dp(activity, 3), dp(activity, 10), dp(activity, 3));
                top.addView(chip);
            }
            row.addView(top);

            if (r != null) {
                StringBuilder detail = new StringBuilder();
                if (r.receiptNumber != null && !r.receiptNumber.trim().isEmpty()) detail.append(r.receiptNumber);
                String date = when(r.issuedAt);
                if (date != null) detail.append(detail.length() > 0 ? " · " : "").append(date);
                if (r.amount > MoneyFormat.EPSILON) detail.append(detail.length() > 0 ? " · " : "").append(MoneyFormat.format(r.amount));
                row.addView(text(activity, detail.toString(), 12, false, R.color.velocity_text_secondary));
                row.setClickable(true);
                row.setFocusable(true);
                row.setOnClickListener(v -> activity.startActivity(PaymentReceiptActivity.newIntentForReceipt(activity, b, r.receiptNumber)));
            } else {
                row.addView(text(activity, activity.getString(later[stage]), 12, false, R.color.velocity_text_secondary));
                row.setAlpha(0.75f);
            }
            into.addView(row);
        }
    }

    // ---- helpers ----

    static TextView text(Context c, String s, int sp, boolean bold, int colorRes) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(ContextCompat.getColor(c, colorRes));
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
