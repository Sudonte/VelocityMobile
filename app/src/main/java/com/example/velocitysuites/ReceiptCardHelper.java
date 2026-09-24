package com.example.velocitysuites;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;

/**
 * Single shared home for the Payment Receipt card/row-building logic that
 * used to be triplicated (byte-for-byte in two cases) across
 * BookingDetailsActivity#buildReceiptActionCard(), TransactionDetailsActivity
 * #buildReceiptActionCard(), and PaymentReceiptActivity#buildTransactionRow()/
 * bindInflatedRow()/statusLabelFor() - see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md
 * Phase 5 §33. Every method here is a pure View-binder: it takes an already-
 * resolved Booking/record and an already-inflated/found View and fills it in -
 * it never inflates activity_*-level layouts itself and never navigates.
 */
final class ReceiptCardHelper {

    private ReceiptCardHelper() {
    }

    // ---- Legacy single-receipt action card (BookingDetailsActivity / TransactionDetailsActivity) ----

    /**
     * Same gating rule both screens already relied on independently: a
     * receipt is worth showing at all once some payment was actually
     * attempted (paid, pending verification, or rejected) - a plain
     * Reservation with no payment involved gets no card.
     */
    static boolean hasLegacyReceiptCandidate(Booking booking) {
        return booking.getAmountPaid() > 0.009 || booking.isPaymentPendingVerification() || booking.isPaymentRejected();
    }

    /**
     * The stricter "receipt is actually viewable" gate - used both by the
     * verified branch inside {@link #bindLegacyReceiptActionCard} and by
     * NotificationDetailsActivity's separate single "View Payment Receipt"
     * button, which has no room for the pending/rejected states the full
     * card shows.
     */
    static boolean isLegacyReceiptVerified(Booking booking) {
        return booking.isStaffVerified() && booking.getAmountPaid() > 0.009;
    }

    /**
     * Binds icon/title/desc/button state into an already-found
     * item_payment_receipt_action.xml card - identical to what
     * BookingDetailsActivity and TransactionDetailsActivity each used to
     * inline separately. Caller is still responsible for inflating (or
     * finding, via an <include>) the card View, adding/showing it, and
     * hiding it entirely when {@link #hasLegacyReceiptCandidate} is false.
     */
    static void bindLegacyReceiptActionCard(Activity activity, View card, Booking booking) {
        ImageView icon = card.findViewById(R.id.ivReceiptStatusIcon);
        TextView title = card.findViewById(R.id.tvReceiptStatusTitle);
        TextView desc = card.findViewById(R.id.tvReceiptStatusDesc);
        View buttonRow = card.findViewById(R.id.layoutReceiptButtons);
        com.google.android.material.button.MaterialButton btnView = card.findViewById(R.id.btnViewReceipt);
        com.google.android.material.button.MaterialButton btnDownload = card.findViewById(R.id.btnDownloadReceipt);

        boolean verified = isLegacyReceiptVerified(booking);
        if (verified) {
            icon.setImageResource(R.drawable.ic_check_circle);
            icon.setImageTintList(ContextCompat.getColorStateList(activity, R.color.velocity_green_dark));
            title.setText(R.string.receipt_verified_title);
            title.setTextColor(ContextCompat.getColor(activity, R.color.velocity_green_dark));
            desc.setText(R.string.receipt_verified_desc);
            buttonRow.setVisibility(View.VISIBLE);
            btnView.setOnClickListener(v -> activity.startActivity(PaymentReceiptActivity.newIntent(activity, booking, false)));
            btnDownload.setOnClickListener(v -> activity.startActivity(PaymentReceiptActivity.newIntent(activity, booking, true)));
        } else if (booking.isPaymentRejected()) {
            icon.setImageResource(R.drawable.ic_close);
            icon.setImageTintList(ContextCompat.getColorStateList(activity, R.color.velocity_red_dark));
            title.setText(R.string.receipt_rejected_title);
            title.setTextColor(ContextCompat.getColor(activity, R.color.velocity_red_dark));
            desc.setText(R.string.receipt_rejected_desc);
            buttonRow.setVisibility(View.GONE);
        } else {
            icon.setImageResource(R.drawable.ic_lock);
            icon.setImageTintList(ContextCompat.getColorStateList(activity, R.color.velocity_inactive_gray));
            title.setText(R.string.receipt_pending_title);
            title.setTextColor(ContextCompat.getColor(activity, R.color.velocity_text_primary));
            desc.setText(R.string.receipt_pending_desc);
            buttonRow.setVisibility(View.GONE);
        }
    }

    // ---- New "Receipts" section (one card per already-issued PR/FR/OR) ----

    /**
     * Appends one item_receipt_summary_card.xml per entry in
     * booking.getReceipts() - every receipt the backend has actually issued,
     * each opening its own independent PaymentReceiptActivity instance by
     * exact receipt_number (never "latest", never re-derived) so opening one
     * never hides or overwrites another. No-ops (adds nothing) when the list
     * is empty - callers should fall back to the legacy single-receipt card
     * in that case (see hasLegacyReceiptCandidate above).
     */
    static void buildReceiptsSection(Activity activity, LinearLayout container, Booking booking) {
        List<Booking.ReceiptSummary> receipts = booking.getReceipts();
        if (receipts.isEmpty()) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (Booking.ReceiptSummary receipt : receipts) {
            View card = inflater.inflate(R.layout.item_receipt_summary_card, container, false);
            ((TextView) card.findViewById(R.id.tvReceiptCardType)).setText(ReceiptTypeMapper.labelForReceiptType(receipt.receiptType));
            ((TextView) card.findViewById(R.id.tvReceiptCardNumber)).setText(receipt.receiptNumber);
            ((TextView) card.findViewById(R.id.tvReceiptCardAmount)).setText(formatPrice(receipt.amount));

            TextView statusView = card.findViewById(R.id.tvReceiptCardStatus);
            statusView.setText(statusLabelFor(activity, receipt.status));
            boolean rejected = "rejected".equalsIgnoreCase(receipt.status) || "failed".equalsIgnoreCase(receipt.status);
            statusView.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(activity,
                    rejected ? R.color.velocity_red_subtle : R.color.velocity_green_soft)));
            statusView.setTextColor(ContextCompat.getColor(activity, rejected ? R.color.velocity_red_dark : R.color.velocity_green_dark));

            card.setOnClickListener(v -> activity.startActivity(
                    PaymentReceiptActivity.newIntentForReceipt(activity, booking, receipt.receiptNumber)));
            container.addView(card);
        }
    }

    // ---- Payment Transaction History timeline row (PaymentReceiptActivity + authoritative Booking/Transaction Details) ----

    /**
     * Builds one item_receipt_transaction_entry.xml row for a single
     * Booking.PaymentTransactionRecord - moved here unchanged from
     * PaymentReceiptActivity#buildTransactionRow() so BookingDetailsActivity
     * and TransactionDetailsActivity can render the exact same authoritative
     * timeline (via booking.getPaymentTransactions()) instead of the legacy
     * flat item_payment_history_entry.xml row, once the backend has attached
     * payment_transactions to this Booking.
     */
    static View buildTransactionRow(Activity activity, ViewGroup parent, Booking.PaymentTransactionRecord tx, boolean isLast) {
        View row = LayoutInflater.from(activity).inflate(R.layout.item_receipt_transaction_entry, parent, false);

        String methodLabel = "gcash".equalsIgnoreCase(tx.paymentMethod)
                ? activity.getString(R.string.payment_method_gcash) : activity.getString(R.string.payment_method_cash);
        String typeLabel = ReceiptTypeMapper.labelForTransactionType(tx.transactionType);
        ((TextView) row.findViewById(R.id.tvTxTitle)).setText(methodLabel + " " + typeLabel);
        ((TextView) row.findViewById(R.id.tvTxDate)).setText(
                tx.paymentDate != null ? TimeUtils.formatDateTime(tx.paymentDate) : "");

        TextView statusView = row.findViewById(R.id.tvTxStatus);
        String statusLabel;
        int statusBg;
        int statusFg;
        if (tx.verificationStatus != null) {
            switch (tx.verificationStatus) {
                case "verified":
                    statusLabel = activity.getString(R.string.status_verified);
                    statusBg = R.color.velocity_green_soft;
                    statusFg = R.color.velocity_green_dark;
                    break;
                case "rejected":
                    statusLabel = activity.getString(R.string.status_rejected);
                    statusBg = R.color.velocity_red_subtle;
                    statusFg = R.color.velocity_red_dark;
                    break;
                default:
                    statusLabel = activity.getString(R.string.status_payment_verification_label);
                    statusBg = R.color.velocity_orange_soft;
                    statusFg = R.color.velocity_orange_primary;
            }
        } else if ("rejected".equalsIgnoreCase(tx.paymentStatus) || "failed".equalsIgnoreCase(tx.paymentStatus)) {
            statusLabel = activity.getString(R.string.status_rejected);
            statusBg = R.color.velocity_red_subtle;
            statusFg = R.color.velocity_red_dark;
        } else if ("completed".equalsIgnoreCase(tx.paymentStatus)) {
            statusLabel = activity.getString(R.string.receipt_status_official_paid);
            statusBg = R.color.velocity_green_soft;
            statusFg = R.color.velocity_green_dark;
        } else {
            statusLabel = activity.getString(R.string.status_pending_label);
            statusBg = R.color.velocity_orange_soft;
            statusFg = R.color.velocity_orange_primary;
        }
        statusView.setText(statusLabel);
        statusView.setBackgroundTintList(ColorStateList.valueOf(activity.getColor(statusBg)));
        statusView.setTextColor(activity.getColor(statusFg));

        bindInflatedRow(activity, row, R.id.rowTxAmount, activity.getString(R.string.details_label_amount_paid), formatPrice(tx.amountPaid));
        bindInflatedRow(activity, row, R.id.rowTxPercentage, activity.getString(R.string.receipt_payment_percentage_label),
                tx.paymentPercentage != null ? PaymentPercentageUtil.formatApiPercentageForDisplay(tx.paymentPercentage) : null);
        boolean isGcash = "gcash".equalsIgnoreCase(tx.paymentMethod);
        bindInflatedRow(activity, row, R.id.rowTxGcashMobile, activity.getString(R.string.receipt_gcash_mobile_label),
                isGcash ? GcashReferenceFormatter.formatMobileNumber(tx.gcashNumber) : null);
        String reference = tx.gcashReferenceNumber != null ? tx.gcashReferenceNumber : tx.referenceNumber;
        bindInflatedRow(activity, row, R.id.rowTxGcashReference, activity.getString(R.string.receipt_gcash_reference_number_label),
                isGcash ? GcashReferenceFormatter.formatOrFallback(reference, activity.getString(R.string.receipt_gcash_value_missing),
                        activity.getString(R.string.receipt_gcash_reference_legacy_incomplete)) : null);
        bindInflatedRow(activity, row, R.id.rowTxVerifiedAt, activity.getString(R.string.receipt_verified_at_label),
                tx.verifiedAt != null ? TimeUtils.formatDateTime(tx.verifiedAt) : null);

        View receiptBadge = row.findViewById(R.id.layoutTxReceiptBadge);
        if (tx.receiptType != null && tx.receiptNumber != null) {
            receiptBadge.setVisibility(View.VISIBLE);
            ((TextView) row.findViewById(R.id.tvTxReceiptType)).setText(ReceiptTypeMapper.labelForReceiptType(tx.receiptType));
            ((TextView) row.findViewById(R.id.tvTxReceiptNumber)).setText(tx.receiptNumber);
        } else {
            receiptBadge.setVisibility(View.GONE);
        }

        if (isLast) {
            row.findViewById(R.id.viewTimelineConnector).setVisibility(View.INVISIBLE);
        }

        return row;
    }

    static void bindInflatedRow(Activity activity, View parent, int includeId, String label, @Nullable String value) {
        View row = parent.findViewById(includeId);
        if (row == null) return;
        if (value == null || value.trim().isEmpty()) {
            row.setVisibility(View.GONE);
            return;
        }
        ((TextView) row.findViewById(R.id.tvRowLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvRowValue)).setText(value);
    }

    /** PAID -> "Paid", PARTIALLY_PAID -> "Partially Paid", PENDING -> "Pending"; anything else (incl. a lowercase legacy status/receipt status like "verified"/"rejected") passed through as-is/title-cased by the caller. */
    static String statusLabelFor(Activity activity, @Nullable String paymentStatus) {
        if (paymentStatus == null) return activity.getString(R.string.status_verified);
        switch (paymentStatus) {
            case "PAID":
                return activity.getString(R.string.receipt_status_official_paid);
            case "PARTIALLY_PAID":
                return activity.getString(R.string.status_partial_paid);
            case "PENDING":
                return activity.getString(R.string.status_pending_label);
            case "verified":
                return activity.getString(R.string.status_verified);
            case "rejected":
                return activity.getString(R.string.status_rejected);
            default:
                return paymentStatus;
        }
    }

    static String formatPrice(double value) {
        return String.format(Locale.US, "₱%,.2f", value);
    }
}
