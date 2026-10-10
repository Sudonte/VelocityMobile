package com.example.velocitysuites;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.ChipGroup;

import java.time.ZoneId;
import java.util.List;

/**
 * Payment History: every payment the guest has made across all their bookings and reservations - one card per payment
 * (amount, status chip, method, date, reference, and cash received / change for cash), newest first, with totals at the
 * top and method / date filters. Reads the same cached transactions as Transaction History (removed bookings included -
 * they are the guest's proof), refreshes from the server on open and on pull-to-refresh, and has loading, empty and
 * error (with Retry) states.
 */
public class PaymentHistoryActivity extends BaseNavigationActivity {

    private RoomRepository repository;
    private LinearLayout listContainer;
    private View emptyState;
    private View errorState;
    private View loadingOverlay;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvTotalPaid;
    private TextView tvTotalPending;
    private TextView tvPaymentCount;
    private TextView tvEmptyTitle;
    private TextView tvEmptyDesc;

    private PaymentHistoryModel.Method method = PaymentHistoryModel.Method.ALL;
    private PaymentHistoryModel.Range range = PaymentHistoryModel.Range.ALL_TIME;
    private List<PaymentHistoryModel.Entry> all = java.util.Collections.emptyList();
    private boolean loadedOnce = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.payment_history);
        setupGuestNavigation(R.id.nav_payment_history);

        repository = RoomRepository.getInstance(this);
        listContainer = findViewById(R.id.paymentListContainer);
        emptyState = findViewById(R.id.layoutPaymentEmpty);
        errorState = findViewById(R.id.layoutPaymentError);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        swipeRefresh = findViewById(R.id.swipeRefreshPayments);
        tvTotalPaid = findViewById(R.id.tvTotalPaid);
        tvTotalPending = findViewById(R.id.tvTotalPending);
        tvPaymentCount = findViewById(R.id.tvPaymentCount);
        tvEmptyTitle = findViewById(R.id.tvPaymentEmptyTitle);
        tvEmptyDesc = findViewById(R.id.tvPaymentEmptyDesc);

        swipeRefresh.setColorSchemeResources(R.color.velocity_red_primary);
        swipeRefresh.setOnRefreshListener(() -> load(false));
        findViewById(R.id.btnPaymentRetry).setOnClickListener(v -> load(true));

        ChipGroup methodGroup = findViewById(R.id.chipGroupMethod);
        methodGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.contains(R.id.chipMethodGcash)) method = PaymentHistoryModel.Method.GCASH;
            else if (ids.contains(R.id.chipMethodCash)) method = PaymentHistoryModel.Method.CASH;
            else method = PaymentHistoryModel.Method.ALL;
            render();
        });
        ChipGroup rangeGroup = findViewById(R.id.chipGroupRange);
        rangeGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.contains(R.id.chipRange30)) range = PaymentHistoryModel.Range.LAST_30_DAYS;
            else if (ids.contains(R.id.chipRangeMonth)) range = PaymentHistoryModel.Range.THIS_MONTH;
            else range = PaymentHistoryModel.Range.ALL_TIME;
            render();
        });

        // What is already cached paints at once; the refresh behind it brings it up to date.
        all = PaymentHistoryModel.collect(repository.getAllBookings());
        if (!all.isEmpty()) {
            loadedOnce = true;
            render();
        }
        load(true);
    }

    private void load(boolean showOverlay) {
        errorState.setVisibility(View.GONE);
        if (showOverlay && !loadedOnce) loadingOverlay.setVisibility(View.VISIBLE);
        repository.refreshBookingsSplit((merged, reservationsError, directError) -> {
            if (isFinishing() || isDestroyed()) return;
            loadingOverlay.setVisibility(View.GONE);
            swipeRefresh.setRefreshing(false);
            boolean bothFailed = reservationsError != null && directError != null;
            if (bothFailed && !loadedOnce) {
                listContainer.removeAllViews();
                emptyState.setVisibility(View.GONE);
                errorState.setVisibility(View.VISIBLE);
                return;
            }
            // A failed refresh keeps the last good data on screen - only a first load with nothing shows the error state.
            if (!bothFailed) loadedOnce = true;
            all = PaymentHistoryModel.collect(repository.getAllBookings());
            render();
        });
    }

    private void render() {
        errorState.setVisibility(View.GONE);
        List<PaymentHistoryModel.Entry> shown = PaymentHistoryModel.filter(
                all, method, range, System.currentTimeMillis(), ZoneId.systemDefault());

        PaymentHistoryModel.Totals totals = PaymentHistoryModel.totals(shown);
        tvTotalPaid.setText(MoneyFormat.format(totals.paid));
        tvTotalPending.setText(MoneyFormat.format(totals.pending));
        tvPaymentCount.setText(String.valueOf(totals.count));

        listContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (PaymentHistoryModel.Entry entry : shown) {
            View card = inflater.inflate(R.layout.item_payment_history_entry, listContainer, false);
            PaymentCardBinder.bind(card, entry.record, this);
            card.setOnClickListener(v -> openTransaction(entry));
            listContainer.addView(card);
        }

        boolean filtered = method != PaymentHistoryModel.Method.ALL || range != PaymentHistoryModel.Range.ALL_TIME;
        emptyState.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        tvEmptyTitle.setText(filtered ? R.string.payment_history_empty_filtered_title : R.string.payment_history_empty_title);
        tvEmptyDesc.setText(filtered ? R.string.payment_history_empty_filtered_desc : R.string.payment_history_empty_desc);
    }

    /** Tapping a payment opens the transaction it belongs to (the same details screen Transaction History opens). */
    private void openTransaction(PaymentHistoryModel.Entry entry) {
        for (Booking b : repository.getAllBookings()) {
            if (b != null && String.valueOf(b.getId()).equals(entry.transactionId) && b.isDirectBooking() == entry.direct) {
                startActivity(new android.content.Intent(this, BookingDetailsActivity.class)
                        .putExtra(BookingDetailsActivity.EXTRA_BOOKING, b));
                return;
            }
        }
    }

    @Override
    protected void onVisiblePoll() {
        super.onVisiblePoll();
        repository.pollBookingsSplit((merged, reservationsError, directError) -> {
            if (isFinishing() || isDestroyed() || (reservationsError != null && directError != null)) return;
            List<PaymentHistoryModel.Entry> fresh = PaymentHistoryModel.collect(repository.getAllBookings());
            if (fresh.size() != all.size()) {
                all = fresh;
                render();
            }
        });
    }
}
