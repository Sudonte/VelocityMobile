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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Full detail for one transaction (a reservation or a booking), reached from a Transaction History card or a
 * notification's "View Transaction".
 * <p>
 * The header's status badge, headline and money figures come from {@link TransactionStatusHelper} - the same
 * source the history card uses - so the two cannot disagree. The screen also follows the shared bookings
 * cache: when a poll/refresh elsewhere brings newer data for this transaction (say the receptionist just
 * verified the payment), it re-renders instead of showing the snapshot it was opened with.
 */
public class TransactionDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_TRANSACTION = "EXTRA_TRANSACTION";

    /** Kept for its latest-payment accessors (method, reference, GCash number, date): a transaction-level view passes a record-less PaymentTransaction, whose getters fall back to the booking's latest payment. */
    private PaymentTransaction transaction;
    private Booking booking;
    private final ClickGuard clickGuard = new ClickGuard();
    private final RoomRepository.BookingsChangedListener bookingsChangedListener = this::refreshFromCache;

    /**
     * While this screen is open a guest may be waiting for the receptionist to verify their payment - so it checks
     * for changes itself every 30s (the same light, merge-only poll Transaction History uses), one at a time, and
     * re-renders only if something the screen shows actually changed.
     */
    private final VisiblePoller visiblePoller = new VisiblePoller(this::pollNow);
    private boolean pollInFlight = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_details);

        Object extra = getIntent().getSerializableExtra(EXTRA_TRANSACTION);
        transaction = extra instanceof PaymentTransaction ? (PaymentTransaction) extra : null;
        if (transaction == null || transaction.parentBooking == null) {
            finish();
            return;
        }
        booking = transaction.parentBooking;

        ImageButton btnBack = findViewById(R.id.btnTransactionDetailsBack);
        btnBack.setOnClickListener(v -> finish());

        bindAll();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (booking == null) return;
        RoomRepository repository = RoomRepository.getInstance(this);
        repository.addBookingsChangedListener(bookingsChangedListener);
        refreshFromCache(); // it may have changed while this screen was stopped
    }

    @Override
    protected void onStop() {
        super.onStop();
        RoomRepository.getInstance(this).removeBookingsChangedListener(bookingsChangedListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visiblePoller.start();
    }

    @Override
    protected void onPause() {
        visiblePoller.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        visiblePoller.stop();
        super.onDestroy();
    }

    private void pollNow() {
        if (pollInFlight || booking == null || isFinishing() || isDestroyed()) return;
        pollInFlight = true;
        // The merge inside the poll notifies bookingsChangedListener, which re-renders if this transaction changed.
        RoomRepository.getInstance(this).pollBookingsSplit((merged, reservationsError, directError) -> pollInFlight = false);
    }

    /**
     * Follows the cache's current copy of THIS transaction (same id AND same family - a reservation and a direct
     * booking can share an id). The screen is rebuilt only when something it shows changed: the cache replaces
     * Booking objects on every poll, and re-rendering an unchanged screen every 30s would be pure churn.
     */
    private void refreshFromCache() {
        if (booking == null || isFinishing() || isDestroyed()) return;
        for (Booking candidate : RoomRepository.getInstance(this).getAllBookings()) {
            if (candidate != booking && candidate.isDirectBooking() == booking.isDirectBooking()
                    && java.util.Objects.equals(candidate.getId(), booking.getId())) {
                boolean changed = !TransactionRow.from(booking).sameContentAs(TransactionRow.from(candidate))
                        || booking.getPaymentHistory().size() != candidate.getPaymentHistory().size();
                booking = candidate;
                transaction = new PaymentTransaction(candidate, null);
                if (changed) bindAll();
                return;
            }
        }
    }

    private void bindAll() {
        bindViewBookingDetailsButton();
        bindHeader();

        LinearLayout sectionInfo = findViewById(R.id.sectionTxDetailInfo);
        sectionInfo.removeAllViews();
        buildInfoSection(sectionInfo);

        buildReceiptActionCard();
    }

    /**
     * "Receipts" (one card per booking.getReceipts() entry) when the backend has already issued at least one,
     * hiding the legacy single-receipt include entirely in that case; otherwise falls back to the same gated
     * Payment Receipt card BookingDetailsActivity's Payment tab shows, shared via ReceiptCardHelper.
     */
    private void buildReceiptActionCard() {
        View legacyCard = findViewById(R.id.includeReceiptAction);
        LinearLayout receiptsSection = findViewById(R.id.sectionTxDetailReceipts);
        receiptsSection.removeAllViews();

        if (!booking.getReceipts().isEmpty()) {
            legacyCard.setVisibility(View.GONE);
            ReceiptCardHelper.buildReceiptsSection(this, receiptsSection, booking);
            return;
        }

        if (!ReceiptCardHelper.hasLegacyReceiptCandidate(booking)) {
            legacyCard.setVisibility(View.GONE);
            return;
        }
        legacyCard.setVisibility(View.VISIBLE);
        ReceiptCardHelper.bindLegacyReceiptActionCard(this, legacyCard, booking);
    }

    /**
     * Opens the fuller Booking/Reservation Details screen for this transaction - a notification's "View
     * Transaction" always lands here first, so this keeps the richer view one tap away.
     */
    private void bindViewBookingDetailsButton() {
        MaterialButton btn = findViewById(R.id.btnViewBookingDetails);
        if (btn == null) return;
        btn.setText(booking.isHasBooking() ? R.string.view_booking_details_button : R.string.view_reservation_details_button);
        btn.setOnClickListener(v -> {
            if (!clickGuard.tryAcquire()) return;
            startActivity(BookingDetailsActivity.newIntent(this, booking));
        });
    }

    private void bindHeader() {
        TransactionStatusHelper.Summary summary = TransactionStatusHelper.summarize(booking);

        StatusBadges.bind(findViewById(R.id.tvTxDetailStatusBadge), summary.status);
        ((TextView) findViewById(R.id.tvTxDetailRef)).setText(
                TransactionText.reference(this, booking.isHasBooking(), booking.getId()));
        ((TextView) findViewById(R.id.tvTxDetailTitle)).setText(TransactionStatusHelper.headlineRes(summary));
        ((TextView) findViewById(R.id.tvTxDetailRoom)).setText(
                TransactionText.roomOrDash(this, TransactionRow.roomTextOf(booking)));

        // The latest payment's date, when there is one - never a placeholder standing in for a real date.
        String lastPayment = transaction.getDate();
        ((TextView) findViewById(R.id.tvTxDetailDate)).setText(TextUtils.isEmpty(lastPayment)
                ? getString(R.string.txn_no_payment_yet)
                : getString(R.string.txn_last_payment_format, lastPayment));

        LinearLayout money = findViewById(R.id.sectionTxDetailMoney);
        money.removeAllViews();
        addGrandTotal(money, summary.grandTotal);
        addMoneyRow(money, getString(R.string.txn_amount_paid_verified_label), MoneyFormat.format(summary.verifiedPaid));
        if (summary.status != TransactionStatusHelper.Status.CANCELLED && summary.status != TransactionStatusHelper.Status.REJECTED) {
            addMoneyRow(money, getString(R.string.details_label_remaining_balance), MoneyFormat.format(summary.balance));
        }
        if (PaymentEligibility.needsFrontDeskPayment(booking)) {
            TextView note = (TextView) LayoutInflater.from(this).inflate(R.layout.view_front_desk_note, money, false);
            note.setText(FrontDeskNote.messageFor(this, booking));
            money.addView(note);
        }
        if (summary.hasPendingPayment) {
            addMoneyRow(money, getString(R.string.txn_submitted_label), MoneyFormat.isPositive(summary.pendingSubmitted)
                    ? MoneyFormat.format(summary.pendingSubmitted) : getString(R.string.value_missing));
        }
    }

    /** The emphasized Grand Total panel (the receipt's own component), then the supporting rows beneath it. */
    private void addGrandTotal(LinearLayout container, double grandTotal) {
        View panel = LayoutInflater.from(this).inflate(R.layout.item_receipt_grand_total, container, false);
        ((TextView) panel.findViewById(R.id.tvGrandTotalLabel)).setText(R.string.receipt_grand_total_label);
        ((TextView) panel.findViewById(R.id.tvGrandTotalAmount)).setText(MoneyFormat.format(grandTotal));
        ((ViewGroup.MarginLayoutParams) panel.getLayoutParams()).bottomMargin = dp(12);
        container.addView(panel);
    }

    private void addMoneyRow(LinearLayout container, String label, String value) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_receipt_row, container, false);
        ((TextView) row.findViewById(R.id.tvRowLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvRowValue)).setText(value);
        container.addView(row);
    }

    private void buildInfoSection(LinearLayout container) {
        // Grouped into labeled sub-sections (Stay / Guest / Payment) via addSectionDivider(), one flat card.
        addSectionDivider(container, getString(R.string.room_and_stay_title));
        addInfoRowOrDash(container, getString(R.string.details_label_room_type),
                TextUtils.join(", ", booking.getAllRoomTypeNames()));
        addInfoRowOrDash(container, getString(R.string.details_label_check_in), booking.getCheckInDate());
        addInfoRowOrDash(container, getString(R.string.details_label_check_out), booking.getCheckOutDate());

        // The representative guest, when one was entered - an older/guest-less record skips straight to Payment.
        if (!TextUtils.isEmpty(booking.getRepresentativeName())) {
            addSectionDivider(container, getString(R.string.guest_information_title));
            addInfoRow(container, getString(R.string.details_label_representative_name), booking.getRepresentativeName());
        }

        addSectionDivider(container, getString(R.string.details_section_payment));
        String method = transaction.getMethod();
        boolean isGcash = "gcash".equalsIgnoreCase(method);
        String rawReference = transaction.getReferenceNumber() != null ? transaction.getReferenceNumber() : booking.getTransactionRef();
        if (isGcash) {
            // GCash Mobile # / GCash Reference # belong to the latest payment; only grouped into
            // "XXXX XXX XXXXXX" when exactly 13 digits - a short stored reference is never shown as a bare
            // digit string (see GcashReferenceFormatter#formatOrFallback).
            addInfoRow(container, getString(R.string.receipt_gcash_mobile_label),
                    GcashReferenceFormatter.formatMobileNumber(transaction.getGcashNumber()));
            addInfoRow(container, getString(R.string.receipt_gcash_reference_number_label),
                    GcashReferenceFormatter.formatOrFallback(rawReference,
                            getString(R.string.receipt_gcash_value_missing),
                            getString(R.string.receipt_gcash_reference_legacy_incomplete)));
        } else {
            addInfoRowOrDash(container, getString(R.string.details_label_transaction_ref), rawReference);
        }

        addInfoRowOrDash(container, getString(R.string.details_label_payment_method), TextUtils.isEmpty(method) ? null
                : "cash".equalsIgnoreCase(method) ? getString(R.string.payment_method_cash) : getString(R.string.payment_method_gcash));
        addInfoRow(container, getString(R.string.payment_status),
                getString(TransactionStatusHelper.styleFor(TransactionStatusHelper.statusOf(booking)).labelRes));
        addInfoRowOrDash(container, getString(R.string.details_label_payment_date), transaction.getDate());
        if (booking.getPaymentVerificationStatus() != null) {
            addInfoRow(container, getString(R.string.details_label_verification_status),
                    friendlyVerificationStatus(booking.getPaymentVerificationStatus()));
        }

        boolean isCancelledOrRejected = "Cancelled".equalsIgnoreCase(booking.getStatus()) || "Rejected".equalsIgnoreCase(booking.getStatus());
        if (isCancelledOrRejected) {
            addSectionDivider(container, getString(R.string.details_label_status));
            addInfoRow(container, getString(R.string.details_label_cancelled_on), booking.getCancellationDate());
            if (!TextUtils.isEmpty(booking.getTransactionRejectionReason())) {
                addInfoRow(container, getString(R.string.details_label_rejection_reason), booking.getTransactionRejectionReason());
            }
        }

        buildPaymentHistorySection(container);

        // Full accurate timestamp trail - each row is skipped when the backend hasn't sent that timestamp yet.
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

    /** Authoritative-first-with-fallback, same rule as BookingDetailsActivity#buildPaymentHistorySection(). */
    private void buildPaymentHistorySection(LinearLayout container) {
        List<Booking.PaymentTransactionRecord> transactions = booking.getPaymentTransactions();
        if (!transactions.isEmpty()) {
            addSectionDivider(container, getString(R.string.details_label_payment_history));
            for (int i = 0; i < transactions.size(); i++) {
                container.addView(ReceiptCardHelper.buildTransactionRow(this, container, transactions.get(i), i == transactions.size() - 1));
            }
            return;
        }

        List<Booking.PaymentRecord> history = booking.getPaymentHistory();
        if (history != null && !history.isEmpty()) {
            addSectionDivider(container, getString(R.string.details_label_payment_history));
            for (Booking.PaymentRecord record : history) {
                if (record != null) container.addView(buildPaymentHistoryRow(container, record));
            }
        }
    }

    private View buildPaymentHistoryRow(ViewGroup parent, Booking.PaymentRecord record) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_payment_history_entry, parent, false);
        ((TextView) row.findViewById(R.id.tvHistoryMethod)).setText(
                TextUtils.isEmpty(record.method) ? getString(R.string.value_missing) : record.method);
        ((TextView) row.findViewById(R.id.tvHistoryDate)).setText(
                TextUtils.isEmpty(record.date) ? getString(R.string.value_missing) : record.date);
        ((TextView) row.findViewById(R.id.tvHistoryAmount)).setText(MoneyFormat.format(MoneyFormat.parse(record.amount)));
        ((TextView) row.findViewById(R.id.tvHistoryStatus)).setText(friendlyPaymentStatus(record.status));
        return row;
    }

    /** The backend's payment_status ("completed", "pending", ...) is an internal vocabulary - guests read "Verified" / "Awaiting verification" / "Rejected". */
    private String friendlyPaymentStatus(@Nullable String status) {
        if (status == null || status.trim().isEmpty()) return getString(R.string.value_missing);
        switch (status.trim().toLowerCase(java.util.Locale.US)) {
            case "completed":
                return getString(R.string.txn_history_status_verified);
            case "pending":
                return getString(R.string.txn_history_status_awaiting);
            case "rejected":
            case "failed":
                return getString(R.string.txn_history_status_rejected);
            default:
                return status;
        }
    }

    private String friendlyVerificationStatus(String status) {
        switch (status.trim().toLowerCase(java.util.Locale.US)) {
            case "verified":
                return getString(R.string.txn_history_status_verified);
            case "pending_verification":
                return getString(R.string.txn_history_status_awaiting);
            case "rejected":
                return getString(R.string.txn_history_status_rejected);
            default:
                return status;
        }
    }

    /** A row that is hidden when there is nothing to show (an optional field). */
    private void addInfoRow(LinearLayout container, String label, @Nullable String value) {
        if (TextUtils.isEmpty(value)) return;
        addRow(container, label, value);
    }

    /** A row for a field every transaction is expected to have: a missing value reads "—", never a blank or "null". */
    private void addInfoRowOrDash(LinearLayout container, String label, @Nullable String value) {
        addRow(container, label, TextUtils.isEmpty(value) ? getString(R.string.value_missing) : value);
    }

    private void addRow(LinearLayout container, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        row.setLayoutParams(rowParams);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(getColor(R.color.velocity_text_secondary));
        // Label and value share the row; both wrap, and the value is right-aligned - neither can push the other off screen.
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(13);
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
        tvValue.setTextColor(getColor(R.color.velocity_text_primary));
        tvValue.setGravity(Gravity.END);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        valueParams.setMarginStart(dp(12));
        tvValue.setLayoutParams(valueParams);

        row.addView(tvLabel);
        row.addView(tvValue);
        container.addView(row);
    }

    private void addSectionDivider(LinearLayout container, String title) {
        View divider = new View(this);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(4);
        dividerParams.bottomMargin = dp(8);
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(getColor(R.color.velocity_red_subtle));
        container.addView(divider);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(12);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(getColor(R.color.velocity_action_text));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(8);
        tvTitle.setLayoutParams(titleParams);
        container.addView(tvTitle);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public static Intent newIntent(Context context, PaymentTransaction transaction) {
        Intent intent = new Intent(context, TransactionDetailsActivity.class);
        intent.putExtra(EXTRA_TRANSACTION, transaction);
        return intent;
    }
}
