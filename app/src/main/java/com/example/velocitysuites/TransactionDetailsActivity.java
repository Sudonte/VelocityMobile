package com.example.velocitysuites;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Full-detail screen for a single PaymentTransaction row, replacing
 * TransactionHistoryActivity.showPaymentDetailsDialog()'s dialog. Reached by
 * tapping any card in the flattened payment-level list - both the specific
 * payment row and its parent Booking (already resident in memory) are
 * passed together as Serializable Intent extras.
 */
public class TransactionDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_TRANSACTION = "EXTRA_TRANSACTION";

    private PaymentTransaction transaction;
    private Booking booking;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_details);

        transaction = (PaymentTransaction) getIntent().getSerializableExtra(EXTRA_TRANSACTION);
        if (transaction == null || transaction.parentBooking == null) {
            finish();
            return;
        }
        booking = transaction.parentBooking;

        ImageButton btnBack = findViewById(R.id.btnTransactionDetailsBack);
        btnBack.setOnClickListener(v -> finish());

        bindHeader();

        LinearLayout sectionInfo = findViewById(R.id.sectionTxDetailInfo);
        buildInfoSection(sectionInfo);

        buildReceiptActionCard();
    }

    /**
     * Same gated Payment Receipt card as BookingDetailsActivity's Payment tab
     * (see that class's buildReceiptActionCard() for the full rationale) -
     * duplicated in structure rather than shared as one method since the two
     * screens build their info sections completely differently (this one is
     * a single flat PaymentTransaction record, not a full Booking dynamic
     * section), but the gating rule itself must stay identical: only
     * booking.isStaffVerified() with an actual payment on record unlocks
     * View/Download, routed through the same PaymentReceiptActivity so
     * there's still exactly one place that actually renders/exports the PDF.
     */
    private void buildReceiptActionCard() {
        View card = findViewById(R.id.includeReceiptAction);
        ImageView icon = card.findViewById(R.id.ivReceiptStatusIcon);
        TextView title = card.findViewById(R.id.tvReceiptStatusTitle);
        TextView desc = card.findViewById(R.id.tvReceiptStatusDesc);
        View buttonRow = card.findViewById(R.id.layoutReceiptButtons);
        com.google.android.material.button.MaterialButton btnView = card.findViewById(R.id.btnViewReceipt);
        com.google.android.material.button.MaterialButton btnDownload = card.findViewById(R.id.btnDownloadReceipt);

        boolean hasPayment = booking.getAmountPaid() > 0.009 || booking.isPaymentPendingVerification() || booking.isPaymentRejected();
        if (!hasPayment) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);

        boolean verified = booking.isStaffVerified() && booking.getAmountPaid() > 0.009;
        if (verified) {
            icon.setImageResource(R.drawable.ic_check_circle);
            icon.setImageTintList(androidx.core.content.ContextCompat.getColorStateList(this, R.color.velocity_green_dark));
            title.setText(R.string.receipt_verified_title);
            title.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.velocity_green_dark));
            desc.setText(R.string.receipt_verified_desc);
            buttonRow.setVisibility(View.VISIBLE);
            btnView.setOnClickListener(v -> startActivity(PaymentReceiptActivity.newIntent(this, booking, false)));
            btnDownload.setOnClickListener(v -> startActivity(PaymentReceiptActivity.newIntent(this, booking, true)));
        } else if (booking.isPaymentRejected()) {
            icon.setImageResource(R.drawable.ic_close);
            icon.setImageTintList(androidx.core.content.ContextCompat.getColorStateList(this, R.color.velocity_red_dark));
            title.setText(R.string.receipt_rejected_title);
            title.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.velocity_red_dark));
            desc.setText(R.string.receipt_rejected_desc);
            buttonRow.setVisibility(View.GONE);
        } else {
            icon.setImageResource(R.drawable.ic_lock);
            icon.setImageTintList(androidx.core.content.ContextCompat.getColorStateList(this, R.color.velocity_inactive_gray));
            title.setText(R.string.receipt_pending_title);
            title.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.velocity_text_primary));
            desc.setText(R.string.receipt_pending_desc);
            buttonRow.setVisibility(View.GONE);
        }
    }

    private void bindHeader() {
        String status = transaction.getStatus() != null ? transaction.getStatus() : "";
        boolean cancelled = status.equalsIgnoreCase("Cancelled") || status.equalsIgnoreCase("Rejected") || status.equalsIgnoreCase("failed");
        boolean pending = status.equalsIgnoreCase("pending") || booking.isPaymentPendingVerification();

        String title;
        int bgColorRes, fgColorRes, iconRes;
        if (cancelled) {
            title = getString(R.string.ptx_title_cancelled);
            bgColorRes = R.color.velocity_gray_soft;
            fgColorRes = R.color.velocity_inactive_gray;
            iconRes = R.drawable.ic_close;
        } else if (pending) {
            title = getString(R.string.ptx_title_pending);
            bgColorRes = R.color.velocity_blue_soft;
            fgColorRes = R.color.velocity_blue_primary;
            iconRes = R.drawable.ic_clock;
        } else {
            title = getString(R.string.ptx_title_successful);
            bgColorRes = R.color.velocity_green_soft;
            fgColorRes = R.color.velocity_green_dark;
            iconRes = R.drawable.ic_check_circle;
        }

        TextView tvTitle = findViewById(R.id.tvTxDetailTitle);
        tvTitle.setText(title);

        MaterialCardView iconContainer = findViewById(R.id.iconContainerTxDetail);
        ImageView ivIcon = findViewById(R.id.ivTxDetailIcon);
        iconContainer.setCardBackgroundColor(getColor(bgColorRes));
        ivIcon.setColorFilter(getColor(fgColorRes));
        ivIcon.setImageResource(iconRes);

        String typeLabel = booking.isHasBooking()
                ? getString(R.string.ptx_type_booking_payment)
                : getString(R.string.ptx_type_reservation_payment);
        TextView tvRoom = findViewById(R.id.tvTxDetailRoom);
        tvRoom.setText(getString(R.string.ptx_subtitle_format, typeLabel, booking.getRoomName()));

        TextView tvDate = findViewById(R.id.tvTxDetailDate);
        String date = transaction.getDate();
        tvDate.setText(date != null && !date.isEmpty() ? date : getString(R.string.label_not_available));

        TextView tvAmount = findViewById(R.id.tvTxDetailAmount);
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        tvAmount.setText(currencyFormat.format(transaction.getAmount()));
    }

    private void buildInfoSection(LinearLayout container) {
        String method = transaction.getMethod();
        boolean isGcash = "gcash".equalsIgnoreCase(method);
        String rawReference = transaction.getReferenceNumber() != null ? transaction.getReferenceNumber() : booking.getTransactionRef();
        if (isGcash) {
            // GCash Mobile # / GCash Reference # belong to this specific payment
            // (transaction.getGcashNumber()/getReferenceNumber(), see PaymentTransaction) -
            // never the booking's overall latest payment when this row is one of several.
            // Only grouped into "XXXX XXX XXXXXX" when the stored value is exactly 13
            // digits; a short/incomplete stored reference is never shown as a bare
            // digit string (see GcashReferenceFormatter#formatOrFallback) - it's
            // replaced with an explicit fallback message instead.
            addInfoRow(container, getString(R.string.receipt_gcash_mobile_label),
                    GcashReferenceFormatter.formatMobileNumber(transaction.getGcashNumber()));
            addInfoRow(container, getString(R.string.receipt_gcash_reference_number_label),
                    GcashReferenceFormatter.formatOrFallback(rawReference,
                            getString(R.string.receipt_gcash_value_missing),
                            getString(R.string.receipt_gcash_reference_legacy_incomplete)));
        } else {
            // Cash has no GCash number/reference - preserve the existing generic
            // "Transaction Ref." label and raw value (a Cash transaction may carry a
            // non-numeric placeholder here instead of digits, shown as-is).
            addInfoRow(container, getString(R.string.details_label_transaction_ref), rawReference);
        }
        addInfoRow(container, getString(R.string.details_label_room_type),
                android.text.TextUtils.join(", ", booking.getAllRoomTypeNames()));
        addInfoRow(container, getString(R.string.details_label_check_in), booking.getCheckInDate());
        addInfoRow(container, getString(R.string.details_label_check_out), booking.getCheckOutDate());

        if (!TextUtils.isEmpty(method)) {
            addInfoRow(container, getString(R.string.details_label_payment_method),
                    "cash".equalsIgnoreCase(method) ? getString(R.string.payment_method_cash) : getString(R.string.payment_method_gcash));
        }
        addInfoRow(container, getString(R.string.payment_status), BookingStatusPresenter.paymentStatusPillText(this, booking));
        addInfoRow(container, getString(R.string.details_label_payment_date), transaction.getDate());
        addInfoRow(container, getString(R.string.details_label_total_amount), formatPrice(booking.getTotalAmount()));
        if (booking.getAmountPaid() > 0.009) {
            addInfoRow(container, getString(R.string.details_label_amount_paid), formatPrice(booking.getAmountPaid()));
        }
        if (booking.getRemainingBalance() > 0.009) {
            addInfoRow(container, getString(R.string.details_label_remaining_balance), formatPrice(booking.getRemainingBalance()));
        }
        if (booking.getPaymentVerificationStatus() != null) {
            addInfoRow(container, getString(R.string.details_label_verification_status), booking.getPaymentVerificationStatus());
        }

        boolean isCancelledOrRejected = "Cancelled".equalsIgnoreCase(booking.getStatus()) || "Rejected".equalsIgnoreCase(booking.getStatus());
        if (isCancelledOrRejected) {
            addSectionDivider(container, getString(R.string.details_label_status));
            addInfoRow(container, getString(R.string.details_label_cancelled_on), booking.getCancellationDate());
            if (!TextUtils.isEmpty(booking.getTransactionRejectionReason())) {
                addInfoRow(container, getString(R.string.details_label_rejection_reason), booking.getTransactionRejectionReason());
            }
        }

        List<Booking.PaymentRecord> history = booking.getPaymentHistory();
        if (history != null && !history.isEmpty()) {
            addSectionDivider(container, getString(R.string.details_label_payment_history));
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
            for (Booking.PaymentRecord record : history) {
                container.addView(buildPaymentHistoryRow(container, record, currencyFormat));
            }
        }

        // Full accurate timestamp trail for this transaction - each row is
        // skipped entirely (addInfoRow already no-ops on empty/null) when the
        // backend hasn't sent that particular timestamp yet, so older cached
        // transactions keep displaying cleanly instead of showing "N/A" rows.
        boolean hasAnyTimestamp = !TextUtils.isEmpty(booking.getCreatedAtDisplay())
                || !TextUtils.isEmpty(booking.getPaymentVerifiedAtDisplay())
                || !TextUtils.isEmpty(booking.getCheckedInAtDisplay())
                || !TextUtils.isEmpty(booking.getCheckedOutAtDisplay());
        if (hasAnyTimestamp) {
            addSectionDivider(container, getString(R.string.details_label_status));
            addInfoRow(container, getString(R.string.timeline_created), booking.getCreatedAtDisplay());
            addInfoRow(container, getString(R.string.timeline_payment_verified), booking.getPaymentVerifiedAtDisplay());
            addInfoRow(container, getString(R.string.timeline_checked_in), booking.getCheckedInAtDisplay());
            addInfoRow(container, getString(R.string.timeline_checked_out), booking.getCheckedOutAtDisplay());
        }
    }

    private View buildPaymentHistoryRow(ViewGroup parent, Booking.PaymentRecord record, NumberFormat currencyFormat) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_payment_history_entry, parent, false);
        TextView tvMethod = row.findViewById(R.id.tvHistoryMethod);
        TextView tvDate = row.findViewById(R.id.tvHistoryDate);
        TextView tvAmount = row.findViewById(R.id.tvHistoryAmount);
        TextView tvStatus = row.findViewById(R.id.tvHistoryStatus);

        tvMethod.setText(record.method != null ? record.method : getString(R.string.label_not_available));
        tvDate.setText(record.date != null ? record.date : getString(R.string.label_not_available));
        try {
            tvAmount.setText(currencyFormat.format(Double.parseDouble(record.amount)));
        } catch (NumberFormatException | NullPointerException e) {
            tvAmount.setText(record.amount != null ? record.amount : "");
        }
        tvStatus.setText(record.status != null ? record.status : "");
        return row;
    }

    private void addInfoRow(LinearLayout container, String label, @Nullable String value) {
        if (TextUtils.isEmpty(value)) return;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(rowParams);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(getResources().getColor(R.color.velocity_text_secondary));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvLabel.setLayoutParams(labelParams);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(13);
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
        tvValue.setTextColor(getResources().getColor(R.color.velocity_text_primary));
        tvValue.setGravity(Gravity.END);

        row.addView(tvLabel);
        row.addView(tvValue);
        container.addView(row);
    }

    private void addSectionDivider(LinearLayout container, String title) {
        View divider = new View(this);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (1 * getResources().getDisplayMetrics().density));
        dividerParams.topMargin = (int) (4 * getResources().getDisplayMetrics().density);
        dividerParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(getResources().getColor(R.color.velocity_red_subtle));
        container.addView(divider);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(12);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(getResources().getColor(R.color.velocity_red_primary));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        tvTitle.setLayoutParams(titleParams);
        container.addView(tvTitle);
    }

    private String formatPrice(double amount) {
        return String.format(Locale.US, getString(R.string.price_format), amount);
    }

    public static Intent newIntent(Context context, PaymentTransaction transaction) {
        Intent intent = new Intent(context, TransactionDetailsActivity.class);
        intent.putExtra(EXTRA_TRANSACTION, transaction);
        return intent;
    }
}
