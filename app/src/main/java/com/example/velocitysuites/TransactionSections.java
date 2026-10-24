package com.example.velocitysuites;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Guests and Payments sections that sit beside {@link PayingForBreakdown} (date ranges and bill) on Booking / Reservation Details,
 * Transaction History details and the three receipts. Plain words, label left and value right, nothing shown twice.
 */
final class TransactionSections {

    private TransactionSections() {
    }

    // ---- Guests ----

    /** Who stays: the representative (or "Account owner"), adults, minors with their ages, and the ID type. */
    static void guests(@NonNull Context c, @NonNull ViewGroup into, @NonNull Booking b) {
        into.addView(row(c, RepresentativeName.rowLabel(c, b), RepresentativeName.rowValue(c, b)));
        into.addView(row(c, c.getString(R.string.details_label_adults), String.valueOf(Math.max(1, b.getAdults()))));
        minors(c, into, b.getAdditionalGuests(), b.getChildren());
        String id = b.getIdCardType();
        if (id != null && !id.trim().isEmpty() && !"none".equalsIgnoreCase(id.trim())) {
            into.addView(row(c, c.getString(R.string.id_type_label), id.trim()));
        }
    }

    /** Guests of a receipt, which only carries the names and the head counts. */
    static void guests(@NonNull Context c, @NonNull ViewGroup into, @NonNull ReceiptDetail d, @Nullable Booking local) {
        String composed = RepresentativeName.clean(d.getRepresentativeName());
        boolean sameGuest = local != null && !composed.isEmpty() && composed.equalsIgnoreCase(RepresentativeName.clean(local.getRepresentativeName()));
        String representative = sameGuest ? RepresentativeName.representativeOf(c, local) : RepresentativeName.representativeOf(c, d.getRepresentativeName());
        into.addView(row(c, c.getString(representative == null ? R.string.check_in_by_label : R.string.representative_label),
                representative == null ? c.getString(R.string.check_in_by_owner_value) : representative));
        into.addView(row(c, c.getString(R.string.details_label_adults), String.valueOf(Math.max(1, d.getAdults()))));
        if (local != null) {
            minors(c, into, local.getAdditionalGuests(), d.getChildren());
        } else if (d.getChildren() > 0) {
            into.addView(row(c, c.getString(R.string.details_label_children), String.valueOf(d.getChildren())));
        }
    }

    /** "Minor 1 · age 5": each minor with the age the guest gave; any other companion by name; just the count when there is no list. */
    private static void minors(Context c, ViewGroup into, @Nullable List<Booking.AdditionalGuest> guests, int minorCount) {
        int minor = 0;
        if (guests != null) {
            for (Booking.AdditionalGuest g : guests) {
                if (g == null) continue;
                if ("Child".equalsIgnoreCase(g.relationship) || "Minor".equalsIgnoreCase(g.relationship)) {
                    minor++;
                    into.addView(row(c, c.getString(R.string.details_minor_line_format, minor, g.age >= 0 ? String.valueOf(g.age) : c.getString(R.string.details_minor_age_unknown)), ""));
                } else if (g.name != null && !g.name.trim().isEmpty()) {
                    into.addView(row(c, g.name, g.relationship != null ? g.relationship : ""));
                }
            }
        }
        if (minor == 0 && minorCount > 0) into.addView(row(c, c.getString(R.string.details_label_children), String.valueOf(minorCount)));
    }

    // ---- Payments ----

    /** Each payment once: date, method and kind, amount, reference and its status in plain words. Newest first. */
    static void payments(@NonNull Context c, @NonNull ViewGroup into, @NonNull List<Booking.PaymentTransactionRecord> transactions,
                         @Nullable List<Booking.PaymentRecord> history) {
        List<Entry> entries = new ArrayList<>();
        if (!transactions.isEmpty()) {
            for (Booking.PaymentTransactionRecord tx : transactions) if (tx != null) entries.add(entryOf(c, tx));
        } else if (history != null) {
            int order = 0;
            for (Booking.PaymentRecord record : history) {
                if (record == null) continue;
                Entry e = entryOf(c, record);
                e.sortKey = e.sortKey != 0 ? e.sortKey : order;
                order++;
                entries.add(e);
            }
        }
        java.util.Collections.sort(entries, (a, b) -> Long.compare(b.sortKey, a.sortKey));
        if (entries.isEmpty()) {
            TextView none = new TextView(c);
            none.setText(R.string.ph_no_payments);
            none.setTextSize(13);
            none.setTextColor(ContextCompat.getColor(c, R.color.velocity_text_secondary));
            none.setPadding(0, 0, 0, dp(c, 8));
            into.addView(none);
            return;
        }
        for (Entry e : entries) {
            into.addView(row(c, e.title, MoneyFormat.format(e.amount)));
            StringBuilder detail = new StringBuilder();
            if (!e.date.isEmpty()) detail.append(e.date);
            if (e.reference != null) detail.append(detail.length() > 0 ? " · " : "").append(c.getString(R.string.ph_reference_format, e.reference));
            detail.append(detail.length() > 0 ? " · " : "").append(c.getString(e.statusRes));
            if (e.tender != null) detail.append(" · ").append(e.tender);
            TextView line = new TextView(c);
            line.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            line.setText(detail.toString());
            line.setTextSize(12);
            line.setTextColor(ContextCompat.getColor(c, R.color.velocity_text_secondary));
            line.setPadding(0, 0, 0, dp(c, 6));
            into.addView(line);
        }
    }

    private static final class Entry {
        String title;
        double amount;
        String date = "";
        @Nullable String reference;
        /** Cash only: "Cash received ₱5,000.00 · Change ₱1,000.00" when the front desk recorded what was handed over. */
        @Nullable String tender;
        int statusRes;
        long sortKey;
    }

    private static Entry entryOf(Context c, Booking.PaymentTransactionRecord tx) {
        Entry e = new Entry();
        e.amount = tx.amountPaid;
        boolean gcash = "gcash".equalsIgnoreCase(tx.paymentMethod);
        String method = c.getString(gcash ? R.string.payment_method_gcash : R.string.payment_method_cash);
        String stage = tx.paymentStage == null ? "" : tx.paymentStage.toLowerCase(Locale.ROOT);
        String type;
        if (stage.contains("deposit")) {
            type = c.getString(R.string.ph_type_deposit);
        } else if ("checkout_payment".equalsIgnoreCase(tx.transactionType)) {
            type = c.getString(R.string.ph_type_balance);
        } else if (stage.contains("final")) {
            type = c.getString(Math.abs(tx.totalPaidAfterTransaction - tx.amountPaid) < 0.01 ? R.string.ph_type_full : R.string.ph_type_balance);
        } else {
            type = c.getString(R.string.ph_type_payment);
        }
        e.title = c.getString(R.string.ph_method_type_format, method, type);
        e.date = tx.paymentDate != null ? TimeUtils.formatDateTime(tx.paymentDate) : "";
        java.time.Instant at = TimeUtils.parseInstant(tx.paymentDate);
        e.sortKey = at != null ? at.toEpochMilli() : 0;
        String ref = tx.gcashReferenceNumber != null ? tx.gcashReferenceNumber : tx.referenceNumber;
        e.reference = ref == null || ref.trim().isEmpty() ? null : gcash ? GcashReferenceFormatter.format(ref) : ref;
        if (tx.cashReceived != null && tx.cashReceived > 0.009) {
            e.tender = c.getString(R.string.payment_cash_tender_format, MoneyFormat.format(tx.cashReceived),
                    MoneyFormat.format(tx.changeGiven != null ? tx.changeGiven : 0));
        }
        String verification = tx.verificationStatus != null ? tx.verificationStatus.toLowerCase(Locale.ROOT) : "";
        String paymentStatus = tx.paymentStatus != null ? tx.paymentStatus.toLowerCase(Locale.ROOT) : "";
        if ("verified".equals(verification) || (verification.isEmpty() && "completed".equals(paymentStatus))) {
            e.statusRes = R.string.ph_status_verified;
        } else if ("rejected".equals(verification) || "rejected".equals(paymentStatus) || "failed".equals(paymentStatus)) {
            e.statusRes = R.string.ph_status_rejected;
        } else {
            e.statusRes = R.string.ph_status_pending;
        }
        return e;
    }

    private static Entry entryOf(Context c, Booking.PaymentRecord record) {
        Entry e = new Entry();
        e.amount = MoneyFormat.parse(record.amount);
        boolean gcash = record.method != null && record.method.toLowerCase(Locale.ROOT).contains("gcash");
        e.title = c.getString(R.string.ph_method_type_format,
                c.getString(gcash ? R.string.payment_method_gcash : R.string.payment_method_cash), c.getString(R.string.ph_type_payment));
        e.date = record.date != null ? TimeUtils.formatDateTime(record.date) : "";
        java.time.Instant at = TimeUtils.parseInstant(record.date);
        e.sortKey = at != null ? at.toEpochMilli() : 0;
        e.reference = record.referenceNumber == null || record.referenceNumber.trim().isEmpty() ? null
                : gcash ? GcashReferenceFormatter.format(record.referenceNumber) : record.referenceNumber;
        String st = record.status == null ? "" : record.status.toLowerCase(Locale.ROOT);
        if (st.contains("reject") || st.contains("fail")) {
            e.statusRes = R.string.ph_status_rejected;
        } else if (st.contains("verif") || st.contains("complet") || st.contains("paid")) {
            e.statusRes = R.string.ph_status_verified;
        } else {
            e.statusRes = R.string.ph_status_pending;
        }
        return e;
    }

    // ---- small helpers ----

    private static android.view.View row(Context c, String label, String value) {
        return ReviewSheet.row(c, value == null || value.isEmpty() ? ReviewSheet.Row.of(label, null) : ReviewSheet.Row.of(label, value));
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
