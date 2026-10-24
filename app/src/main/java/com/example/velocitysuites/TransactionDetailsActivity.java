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
public class TransactionDetailsActivity extends SafeActivity {

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
    private final VisiblePoller visiblePoller = new VisiblePoller(this::pollNow, DetailsLive.POLL_INTERVAL_MS);
    private boolean pollInFlight = false;
    private DetailsLive live;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    /** The "Bill" heading: where a tap on the money summary scrolls to. */
    private TextView billTitle;
    private LinearLayout timelineBox;
    private final java.util.Set<String> seenTimeline = new java.util.HashSet<>();
    private final Runnable amenityRequestsListener = this::renderTimeline;

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

        swipeRefresh = findViewById(R.id.swipeRefreshTxDetails);
        swipeRefresh.setColorSchemeResources(R.color.velocity_red_primary);
        FlatUi.removeSpinnerShadow(swipeRefresh);
        swipeRefresh.setOnRefreshListener(() -> pollNow(true));
        live = DetailsLive.attach(this, DetailsLive.contentOf(swipeRefresh), () -> pollNow(true));
        AmenityRoomSources.listen(booking, amenityRequestsListener);

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
        if (booking == null) return;
        if (DetailsLive.isActive(booking)) visiblePoller.start();
        pollNow(false); // back on the screen: catch up quietly
    }

    @Override
    protected void onPause() {
        visiblePoller.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        visiblePoller.stop();
        if (booking != null) AmenityRoomSources.unlisten(booking, amenityRequestsListener);
        super.onDestroy();
    }

    private void pollNow() {
        if (!DetailsLive.isActive(booking)) { // checked out, cancelled, rejected or failed: nothing left to wait for
            visiblePoller.stop();
            return;
        }
        pollNow(false);
    }

    private void pollNow(boolean userInitiated) {
        if (booking == null || isFinishing() || isDestroyed()) return;
        if (pollInFlight) {
            if (userInitiated) swipeRefresh.setRefreshing(false);
            return;
        }
        pollInFlight = true;
        if (userInitiated) swipeRefresh.setRefreshing(true);
        AmenityRoomSources.fetch(this, booking);
        // The merge inside the poll notifies bookingsChangedListener, which re-renders if this transaction changed.
        RoomRepository.getInstance(this).pollBookingsSplit((merged, reservationsError, directError) -> {
            pollInFlight = false;
            if (isFinishing() || isDestroyed()) return;
            swipeRefresh.setRefreshing(false);
            if (reservationsError != null || directError != null) {
                live.markFailed();
            } else {
                live.markUpdated(System.currentTimeMillis());
            }
            renderTimeline();
        });
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
        androidx.core.widget.NestedScrollView scroller = DetailsLive.scrollerOf(swipeRefresh);
        final int keepScroll = scroller.getScrollY();
        bindViewBookingDetailsButton();
        bindHeader();

        LinearLayout sectionInfo = findViewById(R.id.sectionTxDetailInfo);
        sectionInfo.removeAllViews();
        buildInfoSection(sectionInfo);

        buildReceiptActionCard();
        bindMoneySummary();
        if (keepScroll > 0) scroller.post(() -> scroller.scrollTo(0, keepScroll)); // a quiet refresh never jumps the page
    }

    /** The full timeline (see {@link TransactionTimeline}); redrawn on its own whenever a source changes. */
    private void renderTimeline() {
        if (timelineBox == null || booking == null || isFinishing() || isDestroyed()) return;
        TransactionTimelineView.render(this, timelineBox, TransactionTimeline.of(this, booking), seenTimeline);
    }

    /**
     * "Receipts" (one card per booking.getReceipts() entry) when the backend has already issued at least one,
     * hiding the legacy single-receipt include entirely in that case; otherwise falls back to the same gated
     * Payment Receipt card BookingDetailsActivity's Payment tab shows, shared via ReceiptCardHelper.
     */
    private void buildReceiptActionCard() {
        // the three receipts are listed inside the details, between Payments and the Timeline; the old single block is gone
        findViewById(R.id.includeReceiptAction).setVisibility(View.GONE);
        ((LinearLayout) findViewById(R.id.sectionTxDetailReceipts)).removeAllViews();
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
                TransactionText.reference(this, booking.isHasBooking(), booking.getDisplayId()));
        ((TextView) findViewById(R.id.tvTxDetailTitle)).setText(DetailsParts.statusLine(this, booking));
        findViewById(R.id.tvTxDetailRoom).setVisibility(View.GONE); // the rooms are listed once, under "Your Stay"

        // Created date (the payments below carry their own dates)
        String created = booking.getCreatedAtDisplay();
        TextView tvDate = findViewById(R.id.tvTxDetailDate);
        tvDate.setText(TextUtils.isEmpty(created) ? "" : getString(R.string.details_created_format, created));
        tvDate.setVisibility(TextUtils.isEmpty(created) ? View.GONE : View.VISIBLE);

        // the payment progress, near the top; the bill itself is one section below, not a second copy up here
        DetailsParts.paymentProgress(this, findViewById(R.id.sectionTxDetailMoney), booking);
    }

    /** Grand total, Remaining balance and Amount to pay, pinned under the page; a tap scrolls to the Bill. */
    private void bindMoneySummary() {
        android.widget.FrameLayout host = findViewById(R.id.moneySummaryHost);
        if (host == null || booking == null) return;
        MoneySummaryPanel.bindPinned(this, host, MoneySummary.of(this, booking),
                () -> MoneySummaryPanel.scrollTo(DetailsLive.scrollerOf(swipeRefresh), billTitle != null ? billTitle : host));
    }

    private void buildInfoSection(LinearLayout container) {
        PayingForBreakdown breakdown = PayingForBreakdown.forDetails(this, booking);
        addSectionDivider(container, getString(R.string.details_title_stay));
        breakdown.renderRanges(this, container);

        addSectionDivider(container, getString(R.string.details_title_guests));
        TransactionSections.guests(this, container, booking);

        billTitle = addSectionDivider(container, getString(R.string.details_title_bill));
        breakdown.renderBill(this, container);

        addSectionDivider(container, getString(R.string.details_title_payments));
        TransactionSections.payments(this, container, booking.getPaymentTransactions(), booking.getPaymentHistory());

        LinearLayout receipts = new LinearLayout(this);
        receipts.setOrientation(LinearLayout.VERTICAL);
        DetailsParts.receipts(this, receipts, booking);
        if (receipts.getChildCount() > 0) {
            addSectionDivider(container, getString(R.string.details_title_receipts));
            container.addView(receipts);
        }

        boolean isCancelledOrRejected = "Cancelled".equalsIgnoreCase(booking.getStatus()) || "Rejected".equalsIgnoreCase(booking.getStatus());
        if (isCancelledOrRejected) {
            addSectionDivider(container, getString(R.string.details_label_status));
            addInfoRow(container, getString(R.string.details_label_cancelled_on), booking.getCancellationDate());
            if (!TextUtils.isEmpty(booking.getTransactionRejectionReason())) {
                addInfoRow(container, getString(R.string.details_label_rejection_reason), booking.getTransactionRejectionReason());
            }
        }

        // Timeline: every dated event from creation to check-out, newest first.
        addSectionDivider(container, getString(R.string.section_title_transaction_timeline));
        timelineBox = new LinearLayout(this);
        timelineBox.setOrientation(LinearLayout.VERTICAL);
        container.addView(timelineBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        renderTimeline();
    }

    /** The text, or the view gone when there is none - a details screen never shows a placeholder. */
    private static void showOrHide(TextView view, @Nullable String value) {
        boolean show = TransactionDetailSections.isShowable(value);
        view.setText(show ? value : "");
        view.setVisibility(show ? View.VISIBLE : View.GONE);
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
        if (!TransactionDetailSections.isShowable(value)) return;
        addRow(container, label, value);
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

    private TextView addSectionDivider(LinearLayout container, String title) {
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
        return tvTitle;
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
