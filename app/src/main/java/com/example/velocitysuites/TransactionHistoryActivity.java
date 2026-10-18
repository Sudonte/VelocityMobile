package com.example.velocitysuites;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Transaction History: one card per reservation/booking with its status, grand total, stay dates and what has
 * been paid (see {@link TransactionHistoryAdapter}).
 * <p>
 * How it stays honest and responsive:
 * <ul>
 *   <li><b>Status</b> is always {@link TransactionStatusHelper}'s, derived from receptionist-verified
 *       payments - never from what the guest submitted.</li>
 *   <li><b>Fresh</b>: painted at once from the shared cache, then refreshed on open; refreshed again on
 *       pull-to-refresh, every 30s, on return to the screen, and the moment a notification about a
 *       booking/reservation/payment arrives - so a receptionist's verification shows up without a manual
 *       reload. At most one refresh and one poll run at a time; an arrival that lands mid-flight queues one
 *       more rather than being lost.</li>
 *   <li><b>Cheap</b>: rows are immutable snapshots built once per data change; search is debounced; the
 *       list is a real recycling RecyclerView (the screen's scroll container), so paging by scrolling works.</li>
 *   <li><b>Safe</b>: no UI work after the screen is gone, handlers/executors released in onDestroy, repeated
 *       taps swallowed ({@link ClickGuard}), and Export can only end in a saved file or a clear message.</li>
 * </ul>
 */
public class TransactionHistoryActivity extends BaseNavigationActivity {

    /** Intent extra key + values used by DashboardActivity's Payment Status/Recent Bookings sections to land directly on a given filter. */
    public static final String EXTRA_OPEN_FILTER = "OPEN_FILTER";
    public static final String FILTER_PAYMENTS = "Payments";
    public static final String FILTER_BOOKINGS = "Bookings";
    /** Booking-vs-Reservation Payment Status routing (DashboardActivity#openTransactionHistoryForPaymentItem): a still-unconverted Reservation-type transaction opens under this filter. */
    public static final String FILTER_RESERVATIONS = "Reservations";
    /** Optional transaction id to scroll to, highlight and open, passed when a specific dashboard/notification item is tapped. */
    public static final String EXTRA_SELECTED_BOOKING_ID = "SELECTED_BOOKING_ID";
    /** Optional: true when the id is a direct booking, false when it is a reservation-derived transaction - only for callers that KNOW (they hold the Booking). Absent = unknown. */
    public static final String EXTRA_SELECTED_DIRECT = "SELECTED_DIRECT";
    /** Optional: the Notification.TYPE_* that linked here - disambiguates an id shared by a reservation and a direct booking. */
    public static final String EXTRA_SELECTED_TYPE_HINT = "SELECTED_TYPE_HINT";

    private static final long SEARCH_DEBOUNCE_MS = 300;

    private RecyclerView rvTransactions;
    private View scrollEmptyState, layoutEmptyState, layoutInitialLoading;
    private SwipeRefreshLayout swipeRefresh;
    private TextInputEditText etSearch;
    private ChipGroup chipGroupType, chipGroupActive;
    private MaterialButton btnFilters;
    private View rowActiveFilters;
    private boolean syncingTypeChips;
    /** Pages auto-loaded for the current filters (capped, so a server that keeps reporting "more" cannot loop forever). */
    private int autoPagesLoaded;
    private TextView tvEmptyTitle, tvEmptyDesc, tvTransactionHeader, tvResultCount;
    private MaterialButton btnEmptyAction;

    private TransactionHistoryAdapter adapter;
    private RoomRepository repository;
    private List<TransactionRow> allRows = new ArrayList<>();
    private List<TransactionRow> displayedRows = new ArrayList<>();
    private final TransactionFilter.Criteria criteria = new TransactionFilter.Criteria();

    /** A fetch has succeeded (or the cache already had data) - until then an empty list means "not loaded yet", not "no transactions". */
    private boolean loadedOnce = false;
    private boolean refreshInFlight = false;
    private boolean pollInFlight = false;
    private boolean pollQueued = false;
    private boolean notificationPollInFlight = false;
    private boolean loadingMore = false;
    /** True until this instance's first onResume(): onCreate() already loaded, so that first resume must not poll again. */
    private boolean isFirstResume = true;

    // Deep link (dashboard tap / notification) - resolved to an EXACT row by TransactionNavigator.pick().
    @Nullable private String selectedId;
    @Nullable private Boolean selectedDirect;
    @Nullable private String selectedTypeHint;
    private boolean pendingOpenSelected = false;
    private boolean attemptedLookupForSelected = false;

    private final ClickGuard clickGuard = new ClickGuard();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable applyFiltersRunnable = this::applyFilters;

    /**
     * The 30s beat (started/stopped with the screen by BaseNavigationActivity). Light "anything changed?" checks -
     * never a full reload of however far the list has been scrolled. The notification poll is what lets an arrival
     * trigger an immediate status refresh, and it repaints the header badge, so it replaces the base class's
     * badge-only default.
     */
    @Override
    protected void onVisiblePoll() {
        requestPoll();
        pollNotificationsForArrivals();
    }

    /** A notification about a booking/reservation/payment arrived: the receptionist just did something - refresh now. */
    private final RoomRepository.NotificationArrivalListener arrivalListener = arrived -> {
        if (!isUiAlive()) return;
        for (Notification n : arrived) {
            if (NotificationPrimaryActionResolver.isTransactionCategory(n.getType())) {
                requestPoll();
                return;
            }
        }
    };

    // Export. Two launchers (one per MIME type) so the saved file is typed correctly; the rows to export are
    // the snapshot of what was on screen when Export was tapped, even if a poll changes the list while the
    // system file picker is open.
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor();
    @Nullable private List<TransactionRow> pendingExportRows;
    @Nullable private AlertDialog exportProgressDialog;
    private final ActivityResultLauncher<String> createCsvLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                if (uri != null) exportTo(uri, false);
            });
    private final ActivityResultLauncher<String> createPdfLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"), uri -> {
                if (uri != null) exportTo(uri, true);
            });

    // ---- Lifecycle ----

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.transactionhistory);
        setupGuestNavigation(R.id.nav_transaction_history);
        animateScreenContent();

        repository = RoomRepository.getInstance(this);
        bindViews();
        setupRecyclerView();
        setupFilters();
        setupSearch();
        setupSwipeRefresh();
        findViewById(R.id.btnExport).setOnClickListener(v -> onExportClicked());

        readDeepLink();

        // Stale-while-revalidate: the dashboard has usually already loaded the bookings into the shared
        // cache, so paint those immediately and refresh behind them - the list is never blank for the
        // length of a network round trip.
        List<Booking> cached = repository.getAllBookings();
        if (!cached.isEmpty()) {
            allRows = TransactionRow.fromAll(cached);
            loadedOnce = true;
            applyFilters();
        }
        loadTransactions(true);
        warmRoomTypeCatalog();
        reattachRestoredPickerListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
        repository.addNotificationArrivalListener(arrivalListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        repository.removeNotificationArrivalListener(arrivalListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The very first onResume immediately follows onCreate, which already loaded; every later one
        // (returning from a details screen, the app coming back) checks for changes instead - never a full
        // reload, so the guest's scroll position and filters are untouched.
        if (isFirstResume) {
            isFirstResume = false;
        } else {
            requestPoll();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        dismissSafely(exportProgressDialog);
        exportProgressDialog = null;
        // Let a write already in progress finish (interrupting it would leave a half-written file).
        exportExecutor.shutdown();
        super.onDestroy();
    }

    private boolean isUiAlive() {
        return !isFinishing() && !isDestroyed();
    }

    // ---- Setup ----

    private void bindViews() {
        rvTransactions = findViewById(R.id.rvTransactions);
        scrollEmptyState = findViewById(R.id.scrollEmptyState);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        layoutInitialLoading = findViewById(R.id.layoutInitialLoading);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        etSearch = findViewById(R.id.etSearch);
        chipGroupType = findViewById(R.id.chipGroupType);
        chipGroupActive = findViewById(R.id.chipGroupActive);
        btnFilters = findViewById(R.id.btnFilters);
        rowActiveFilters = findViewById(R.id.rowActiveFilters);
        tvTransactionHeader = findViewById(R.id.tvTransactionHeader);
        tvResultCount = findViewById(R.id.tvResultCount);

        // The empty state is a shared include whose title/description/action are filled in here.
        tvEmptyTitle = layoutEmptyState.findViewById(R.id.emptyTitle);
        tvEmptyDesc = layoutEmptyState.findViewById(R.id.emptyDesc);
        btnEmptyAction = layoutEmptyState.findViewById(R.id.btnEmptyAction);
        ImageView emptyIcon = layoutEmptyState.findViewById(R.id.emptyIcon);
        if (emptyIcon != null) emptyIcon.setImageResource(R.drawable.ic_transaction_history);
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvTransactions.setLayoutManager(layoutManager);
        adapter = new TransactionHistoryAdapter(this::onTransactionClicked);
        rvTransactions.setAdapter(adapter);

        // Page in older transactions as the guest nears the end of what is loaded (the API pages; this list
        // used to ask for one big window and never go further). Only fires because the RecyclerView is now
        // the real scroll container.
        rvTransactions.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@androidx.annotation.NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loadingMore || !repository.hasMoreBookings()) return;
                if (layoutManager.findLastVisibleItemPosition() >= displayedRows.size() - 5) {
                    loadingMore = true;
                    repository.loadMoreBookings((merged, reservationsError, directError) -> {
                        loadingMore = false;
                        if (!isUiAlive()) return;
                        allRows = TransactionRow.fromAll(repository.getAllBookings());
                        applyFilters();
                        // A failed "load more" leaves the visible page intact - a quiet toast, no error takeover.
                        if (reservationsError != null || directError != null) {
                            Toast.makeText(TransactionHistoryActivity.this, R.string.network_error, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void onTransactionClicked(TransactionRow row) {
        if (!clickGuard.tryAcquire()) return;
        startActivity(TransactionNavigator.detailsIntent(this, row.booking));
    }

    private void setupFilters() {
        // The Type chips (All / Bookings / Reservations / Payments): the most-used filter, one tap.
        chipGroupType.setOnCheckedStateChangeListener((group, ids) -> {
            if (syncingTypeChips || ids.isEmpty()) return;
            int id = ids.get(0);
            criteria.type = id == R.id.chipTypeBookings ? TransactionFilter.Type.BOOKINGS
                    : id == R.id.chipTypeReservations ? TransactionFilter.Type.RESERVATIONS
                    : id == R.id.chipTypePayments ? TransactionFilter.Type.PAYMENTS : TransactionFilter.Type.ALL;
            applyFilters();
        });
        btnFilters.setOnClickListener(v -> showFiltersSheet());
        findViewById(R.id.btnClearAllFilters).setOnClickListener(v -> clearAllFiltersAndSearch());
        updateFilterUi();
    }

    /** Shows the Type chip that matches the criteria (any other type value, e.g. an old deep link, shows "All"). */
    private void syncTypeChips() {
        syncingTypeChips = true;
        int id = criteria.type == TransactionFilter.Type.BOOKINGS ? R.id.chipTypeBookings
                : criteria.type == TransactionFilter.Type.RESERVATIONS ? R.id.chipTypeReservations
                : criteria.type == TransactionFilter.Type.PAYMENTS ? R.id.chipTypePayments : R.id.chipTypeAll;
        chipGroupType.check(id);
        syncingTypeChips = false;
    }

    /** "Filters · 2" on the button, and one removable chip per active filter (Paid x, Deluxe x, Oct 20 - Oct 25 x) with Clear all. */
    private void updateFilterUi() {
        int count = criteria.activeFilterCount();
        btnFilters.setText(count > 0 ? getString(R.string.filters_button_count_format, count) : getString(R.string.filters_button));
        chipGroupActive.removeAllViews();
        if (criteria.paymentStatus != TransactionFilter.Type.ALL) {
            addActiveChip(paymentStatusLabel(criteria.paymentStatus), () -> criteria.paymentStatus = TransactionFilter.Type.ALL);
        }
        if (criteria.bookingStatus != null) {
            addActiveChip(TransactionStatusChip.label(criteria.bookingStatus), () -> criteria.bookingStatus = null);
        }
        if (criteria.roomType != null) addActiveChip(criteria.roomType, () -> criteria.roomType = null);
        if (criteria.hasDateRange()) {
            addActiveChip(dateRangeText(criteria.from, criteria.to), () -> {
                criteria.from = null;
                criteria.to = null;
            });
        }
        rowActiveFilters.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    private void addActiveChip(String label, Runnable clear) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCloseIconVisible(true);
        chip.setCheckable(false);
        chip.setCloseIconContentDescription(getString(R.string.filters_remove_cd, label));
        View.OnClickListener remove = v -> {
            clear.run();
            updateFilterUi();
            applyFilters();
        };
        chip.setOnCloseIconClickListener(remove);
        chip.setOnClickListener(remove);
        chipGroupActive.addView(chip);
    }

    private String paymentStatusLabel(TransactionFilter.Type type) {
        return getString(type == TransactionFilter.Type.PAID ? R.string.txn_status_paid
                : type == TransactionFilter.Type.PARTIALLY_PAID ? R.string.txn_status_partially_paid : R.string.txn_status_pending);
    }

    private String dateRangeText(@Nullable java.time.LocalDate from, @Nullable java.time.LocalDate to) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d", Locale.US);
        return getString(R.string.date_range_format, from != null ? fmt.format(from) : "…", to != null ? fmt.format(to) : "…");
    }

    /** Sets the filter an old-style type key stands for - for deep links (a guest's own picks go through the chips and the sheet). */
    private void setStatusFilter(TransactionFilter.Type type) {
        TransactionFilter.Type payment = TransactionFilter.paymentStatusOf(type);
        String booking = TransactionFilter.bookingStatusOf(type);
        criteria.type = TransactionFilter.Type.ALL;
        if (payment != TransactionFilter.Type.ALL) criteria.paymentStatus = payment;
        else if (booking != null) criteria.bookingStatus = booking;
        else criteria.type = type;
        syncTypeChips();
        updateFilterUi();
    }

    /** The room-type catalog Browse Rooms uses is filled by a request; ask for it once so the sheet can offer every room type. */
    private void warmRoomTypeCatalog() {
        if (!repository.getAllRooms().isEmpty()) return;
        repository.refreshRooms(new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> result) {
                if (isUiAlive() && fillRoomOptions != null) fillRoomOptions.run();
            }

            @Override
            public void onError(String message) {
                // Not fatal: the sheet falls back to the room types found in the guest's own transactions.
            }
        });
    }

    /** Refills the open Filters sheet's room-type chips (a late catalog answer); null while no sheet is open. */
    @Nullable private Runnable fillRoomOptions;

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                criteria.query = s.toString();
                autoPagesLoaded = 0;
                // Debounced: filtering on every keystroke re-ran the whole list per letter.
                handler.removeCallbacks(applyFiltersRunnable);
                handler.postDelayed(applyFiltersRunnable, SEARCH_DEBOUNCE_MS);
            }
        });
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            handler.removeCallbacks(applyFiltersRunnable);
            applyFilters();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.velocity_red_primary);
        // The list is wrapped in a FrameLayout (with the empty/loading states), so tell the layout whether the
        // LIST can still scroll up - otherwise pulling down mid-list would start a refresh.
        swipeRefresh.setOnChildScrollUpCallback((parent, child) ->
                rvTransactions.getVisibility() == View.VISIBLE && rvTransactions.canScrollVertically(-1));
        swipeRefresh.setOnRefreshListener(() -> loadTransactions(false));
    }

    private void readDeepLink() {
        TransactionFilter.Type openFilter = TransactionFilter.Type.fromKey(getIntent().getStringExtra(EXTRA_OPEN_FILTER));
        if (openFilter != TransactionFilter.Type.ALL) setStatusFilter(openFilter);

        selectedId = getIntent().getStringExtra(EXTRA_SELECTED_BOOKING_ID);
        selectedTypeHint = getIntent().getStringExtra(EXTRA_SELECTED_TYPE_HINT);
        selectedDirect = getIntent().hasExtra(EXTRA_SELECTED_DIRECT)
                ? Boolean.valueOf(getIntent().getBooleanExtra(EXTRA_SELECTED_DIRECT, false)) : null;
        pendingOpenSelected = selectedId != null && !selectedId.trim().isEmpty();
        if (!pendingOpenSelected) selectedId = null;
    }

    // ---- Loading ----

    /**
     * Full refresh (open, pull-to-refresh, retry). Reports each transaction family separately: if only
     * one of them fails, what did load is still shown, with a warning - instead of hiding good data
     * behind an error screen (or, worse, silently showing half the list as if it were all of it).
     *
     * @param showIndicator show the pull-to-refresh spinner (true for open/retry; a pull already shows it)
     */
    private void loadTransactions(boolean showIndicator) {
        if (refreshInFlight) return; // the in-flight one will stop the spinner when it lands
        refreshInFlight = true;
        if (showIndicator) swipeRefresh.setRefreshing(true);
        boolean firstPaint = allRows.isEmpty() && !loadedOnce;
        if (firstPaint) {
            layoutInitialLoading.setVisibility(View.VISIBLE);
            rvTransactions.setVisibility(View.GONE);
            scrollEmptyState.setVisibility(View.GONE);
        }

        repository.refreshBookingsSplit((merged, reservationsError, directError) -> {
            refreshInFlight = false;
            if (!isUiAlive()) return;
            swipeRefresh.setRefreshing(false);
            layoutInitialLoading.setVisibility(View.GONE);

            boolean bothFailed = reservationsError != null && directError != null;
            if (bothFailed && allRows.isEmpty() && merged.isEmpty()) {
                showLoadError(reservationsError);
            } else {
                if (!bothFailed) loadedOnce = true;
                else loadedOnce = loadedOnce || !merged.isEmpty();
                allRows = TransactionRow.fromAll(repository.getAllBookings());
                applyFilters();
                if (reservationsError != null || directError != null) {
                    Toast.makeText(this, bothFailed
                            ? getString(R.string.error_load_transactions_format, reservationsError)
                            : getString(R.string.txn_partial_load_warning), Toast.LENGTH_LONG).show();
                }
            }
            runQueuedPoll();
        });
    }

    /** Asks for a "what changed?" poll - runs now if nothing is in flight, else once after the current one (an arrival mid-flight is never lost). */
    private void requestPoll() {
        if (!isUiAlive()) return;
        if (pollInFlight || refreshInFlight) {
            pollQueued = true;
            return;
        }
        pollInFlight = true;
        repository.pollBookingsSplit((merged, reservationsError, directError) -> {
            pollInFlight = false;
            if (!isUiAlive()) return;
            // Silent by design: a background check the guest didn't ask for must not interrupt them with an
            // error, and a failed family keeps its last good data (the repository merges per family).
            if (reservationsError == null || directError == null) {
                loadedOnce = true;
                allRows = TransactionRow.fromAll(repository.getAllBookings());
                applyFilters();
            }
            runQueuedPoll();
        });
    }

    private void runQueuedPoll() {
        if (pollQueued && !pollInFlight && !refreshInFlight) {
            pollQueued = false;
            requestPoll();
        }
    }

    /** Checks for new notifications so a booking/reservation/payment one can trigger {@link #arrivalListener}. */
    private void pollNotificationsForArrivals() {
        if (notificationPollInFlight) return;
        notificationPollInFlight = true;
        repository.pollNotifications(new RoomRepository.RepositoryCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                notificationPollInFlight = false;
                if (isUiAlive()) updateNotificationBadge();
            }

            @Override
            public void onError(String message) {
                notificationPollInFlight = false;
            }
        });
    }

    // ---- Filtering / rendering ----

    private void applyFilters() {
        handler.removeCallbacks(applyFiltersRunnable);
        displayedRows = TransactionFilter.apply(allRows, criteria);
        render();
        loadRemainingPagesWhileFiltering();
    }

    /**
     * The server pages the guest's transactions (the list only loads the next page as the guest scrolls). A filter or a
     * search must cover ALL of them, not just what is loaded, so while one is active the remaining pages are loaded one
     * after another (the existing paging calls, no new API parameter) and the result re-filtered after each, until none
     * is left. A failed page stops it quietly - what is loaded stays filtered.
     */
    private void loadRemainingPagesWhileFiltering() {
        if (!criteria.isActive() || loadingMore || !repository.hasMoreBookings() || autoPagesLoaded >= 100) return;
        loadingMore = true;
        autoPagesLoaded++;
        repository.loadMoreBookings((merged, reservationsError, directError) -> {
            loadingMore = false;
            if (!isUiAlive()) return;
            allRows = TransactionRow.fromAll(repository.getAllBookings());
            if (reservationsError != null || directError != null) {
                displayedRows = TransactionFilter.apply(allRows, criteria);
                render();
                return;
            }
            applyFilters(); // filters what just arrived, and asks for the next page if there is one
        });
    }

    /** What Export writes: exactly the rows on screen - filtered AND searched. */
    List<TransactionRow> rowsForExport() {
        return new ArrayList<>(displayedRows);
    }

    private void render() {
        adapter.submit(displayedRows);
        tvResultCount.setText(getResources().getQuantityString(R.plurals.transactions_results, displayedRows.size(), displayedRows.size()));

        if (displayedRows.isEmpty()) {
            if (loadedOnce) showEmptyState();
            // Not loaded yet: the loader / error state that loadTransactions() manages stays as it is.
        } else {
            scrollEmptyState.setVisibility(View.GONE);
            layoutInitialLoading.setVisibility(View.GONE);
            rvTransactions.setVisibility(View.VISIBLE);
        }
        if (loadedOnce) applySelectedHighlight();
    }

    private void showEmptyState() {
        rvTransactions.setVisibility(View.GONE);
        scrollEmptyState.setVisibility(View.VISIBLE);
        if (allRows.isEmpty()) {
            tvEmptyTitle.setText(R.string.txn_empty_title);
            tvEmptyDesc.setText(R.string.txn_empty_desc);
            setEmptyAction(R.string.refresh_label, R.drawable.ic_clock, v -> {
                if (clickGuard.tryAcquire()) loadTransactions(true);
            });
        } else {
            tvEmptyTitle.setText(R.string.txn_no_match_title);
            tvEmptyDesc.setText(!criteria.query.trim().isEmpty() ? R.string.transaction_search_empty_desc
                    : criteria.type == TransactionFilter.Type.PAYMENTS ? R.string.transaction_payment_empty_desc
                    : R.string.empty_transactions_desc);
            setEmptyAction(R.string.txn_clear_filters_button, R.drawable.ic_filter, v -> clearAllFiltersAndSearch());
        }
    }

    /** First load failed and there is nothing to show: the error state, with a Retry that actually re-runs the load. */
    private void showLoadError(@Nullable String message) {
        rvTransactions.setVisibility(View.GONE);
        scrollEmptyState.setVisibility(View.VISIBLE);
        tvEmptyTitle.setText(R.string.no_transactions_load_error_title);
        tvEmptyDesc.setText(message != null && !message.trim().isEmpty() ? message : getString(R.string.no_transactions_load_error_desc));
        setEmptyAction(R.string.retry_label, R.drawable.ic_clock, v -> {
            if (clickGuard.tryAcquire()) loadTransactions(true);
        });
    }

    private void setEmptyAction(int textRes, int iconRes, View.OnClickListener listener) {
        btnEmptyAction.setVisibility(View.VISIBLE);
        btnEmptyAction.setText(textRes);
        btnEmptyAction.setIconResource(iconRes);
        btnEmptyAction.setOnClickListener(listener);
    }

    /** Resets search, type and every filter - the way out of an over-filtered list. */
    private void clearAllFiltersAndSearch() {
        criteria.clear();
        if (etSearch.getText() != null && etSearch.getText().length() > 0) {
            etSearch.setText(""); // the watcher sets criteria.query again (to "") and queues a harmless re-apply
        }
        autoPagesLoaded = 0;
        syncTypeChips();
        updateFilterUi();
        applyFilters();
    }

    // ---- The Filters sheet ----

    /** Payment status, Booking status, Room type and Dates, staged on a copy and committed by Apply (Reset clears the copy). */
    private void showFiltersSheet() {
        final BottomSheetDialog dialog = new BottomSheetDialog(this);
        @android.annotation.SuppressLint("InflateParams")
        View content = getLayoutInflater().inflate(R.layout.sheet_transaction_filters, null, false);
        final TransactionFilter.Criteria draft = criteria.copy();
        final ChipGroup payment = content.findViewById(R.id.sheetGroupPayment);
        final ChipGroup booking = content.findViewById(R.id.sheetGroupBooking);
        final ChipGroup room = content.findViewById(R.id.sheetGroupRoom);
        final MaterialButton dates = content.findViewById(R.id.btnSheetDates);

        final Runnable fillPayment = () -> {
            payment.removeAllViews();
            for (TransactionFilterOptions.Option o : TransactionFilterOptions.paymentStatuses()) {
                boolean selected = o.value.isEmpty() ? draft.paymentStatus == TransactionFilter.Type.ALL
                        : draft.paymentStatus.name().equals(o.value);
                addChoice(payment, getString(o.labelRes), o.value, selected, value ->
                        draft.paymentStatus = value.isEmpty() ? TransactionFilter.Type.ALL : TransactionFilter.Type.valueOf(value));
            }
        };
        final Runnable fillBooking = () -> {
            booking.removeAllViews();
            for (TransactionFilterOptions.Option o : TransactionFilterOptions.bookingStatuses()) {
                boolean selected = o.value.isEmpty() ? draft.bookingStatus == null : o.value.equalsIgnoreCase(draft.bookingStatus);
                addChoice(booking, getString(o.labelRes), o.value, selected, value -> draft.bookingStatus = value.isEmpty() ? null : value);
            }
        };
        fillRoomOptions = () -> {
            room.removeAllViews();
            addChoice(room, getString(R.string.filters_all_room_types), "", draft.roomType == null,
                    value -> draft.roomType = null);
            for (String type : TransactionFilterOptions.roomTypes(repository.getAllRooms(), allRows)) {
                addChoice(room, type, type, type.equalsIgnoreCase(draft.roomType), value -> draft.roomType = value);
            }
        };
        final Runnable showDates = () -> dates.setText(draft.hasDateRange()
                ? dateRangeText(draft.from, draft.to) : getString(R.string.filters_any_dates));
        fillPayment.run();
        fillBooking.run();
        fillRoomOptions.run();
        showDates.run();

        dates.setOnClickListener(v -> showDateRangePicker(draft, showDates));
        content.findViewById(R.id.btnSheetReset).setOnClickListener(v -> {
            draft.paymentStatus = TransactionFilter.Type.ALL;
            draft.bookingStatus = null;
            draft.roomType = null;
            draft.from = null;
            draft.to = null;
            fillPayment.run();
            fillBooking.run();
            fillRoomOptions.run();
            showDates.run();
        });
        content.findViewById(R.id.btnSheetApply).setOnClickListener(v -> {
            criteria.paymentStatus = draft.paymentStatus;
            criteria.bookingStatus = draft.bookingStatus;
            criteria.roomType = draft.roomType;
            criteria.from = draft.from;
            criteria.to = draft.to;
            autoPagesLoaded = 0;
            updateFilterUi();
            applyFilters();
            dialog.dismiss();
        });
        dialog.setOnDismissListener(d -> fillRoomOptions = null);
        dialog.setContentView(content);
        dialog.show();
    }

    private void addChoice(ChipGroup group, String label, String value, boolean selected, java.util.function.Consumer<String> onPicked) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, group, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(value);
        group.addView(chip);
        chip.setChecked(selected);
        chip.setOnCheckedChangeListener((button, checked) -> {
            if (checked) onPicked.accept(value);
        });
    }

    /** Date range over the stay (check-in to check-out). The picker speaks UTC-midnight millis; they become calendar dates right away so no time zone can shift a day. */
    private void showDateRangePicker(TransactionFilter.Criteria target, Runnable onPicked) {
        MaterialDatePicker.Builder<androidx.core.util.Pair<Long, Long>> builder = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.date_filter_label);
        if (target.from != null && target.to != null) {
            builder.setSelection(new androidx.core.util.Pair<>(
                    target.from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    target.to.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()));
        }
        MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker = builder.build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null || selection.first == null || selection.second == null) return;
            target.from = Instant.ofEpochMilli(selection.first).atZone(ZoneOffset.UTC).toLocalDate();
            target.to = Instant.ofEpochMilli(selection.second).atZone(ZoneOffset.UTC).toLocalDate();
            onPicked.run();
        });
        picker.show(getSupportFragmentManager(), TAG_DATE_PICKER);
    }

    private static final String TAG_DATE_PICKER = "DATE_PICKER";

    /**
     * A range picker open during recreation comes back without its listeners (and without the Filters sheet that opened
     * it): its answer is applied straight to the active filters.
     */
    @SuppressWarnings("unchecked")
    private void reattachRestoredPickerListeners() {
        androidx.fragment.app.Fragment restored = getSupportFragmentManager().findFragmentByTag(TAG_DATE_PICKER);
        if (!(restored instanceof MaterialDatePicker)) return;
        ((MaterialDatePicker<androidx.core.util.Pair<Long, Long>>) restored).addOnPositiveButtonClickListener(selection -> {
            if (selection == null || selection.first == null || selection.second == null) return;
            criteria.from = Instant.ofEpochMilli(selection.first).atZone(ZoneOffset.UTC).toLocalDate();
            criteria.to = Instant.ofEpochMilli(selection.second).atZone(ZoneOffset.UTC).toLocalDate();
            updateFilterUi();
            applyFilters();
        });
    }

    // ---- Deep link: highlight + open the EXACT transaction ----

    private void applySelectedHighlight() {
        if (selectedId == null) return;

        List<Booking> pool = new ArrayList<>(allRows.size());
        for (TransactionRow row : allRows) pool.add(row.booking);
        Booking pick = TransactionNavigator.pick(pool, selectedId, selectedDirect, selectedTypeHint, null);
        TransactionRow target = null;
        if (pick != null) {
            for (TransactionRow row : allRows) {
                if (row.booking == pick) {
                    target = row;
                    break;
                }
            }
        }

        if (target != null) {
            // Reachable whatever filter the link opened on (a Cancelled record opened on Bookings, say):
            // if the current filters hide it, show everything.
            if (!displayedRows.contains(target) && criteria.isActive()) {
                clearAllFiltersAndSearch();
                return;
            }
            adapter.setHighlightedStableId(target.stableId());
            if (pendingOpenSelected) {
                pendingOpenSelected = false;
                int position = adapter.positionOfStableId(target.stableId());
                if (position >= 0) {
                    rvTransactions.post(() -> {
                        if (isUiAlive()) rvTransactions.smoothScrollToPosition(position);
                    });
                }
                startActivity(TransactionNavigator.detailsIntent(this, target.booking));
            }
            return;
        }

        // Not among the loaded rows (older than the loaded window). Ask for it by id ONCE - a genuine
        // 404 must show the friendly message exactly once, not retry on every poll.
        if (attemptedLookupForSelected) return;
        attemptedLookupForSelected = true;
        List<RoomRepository.TransactionFamily> order = selectedDirect != null
                ? Collections.singletonList(selectedDirect ? RoomRepository.TransactionFamily.DIRECT_BOOKING : RoomRepository.TransactionFamily.RESERVATION)
                : TransactionNavigator.lookupOrder(selectedTypeHint);
        repository.lookupTransaction(selectedId, order, new RoomRepository.TransactionLookupCallback() {
            @Override
            public void onFound(Booking booking) {
                if (!isUiAlive()) return;
                allRows = TransactionRow.fromAll(repository.getAllBookings());
                applyFilters(); // re-enters applySelectedHighlight(), which now finds it
            }

            @Override
            public void onNotFound() {
                pendingOpenSelected = false;
                TransactionNavigator.showNotFoundDialog(TransactionHistoryActivity.this);
            }

            @Override
            public void onError(String message) {
                pendingOpenSelected = false;
                if (isUiAlive()) {
                    Toast.makeText(TransactionHistoryActivity.this,
                            getString(R.string.txn_lookup_error_format, message), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    // ---- Export ----

    private void onExportClicked() {
        if (!clickGuard.tryAcquire()) return;
        if (displayedRows.isEmpty()) {
            Toast.makeText(this, R.string.msg_export_nothing, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] options = {getString(R.string.export_as_pdf), getString(R.string.export_as_csv)};
        new SafeDialogs.Material(this)
                .setTitle(R.string.export_options_title)
                .setItems(options, (dialog, which) -> {
                    pendingExportRows = rowsForExport();
                    long now = System.currentTimeMillis();
                    try {
                        if (which == 0) {
                            createPdfLauncher.launch(TransactionExporter.fileName("pdf", now));
                        } else {
                            createCsvLauncher.launch(TransactionExporter.fileName("csv", now));
                        }
                    } catch (RuntimeException e) {
                        // No app on the device can create documents (a stripped-down ROM): say so.
                        Toast.makeText(this, R.string.msg_export_error, Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void exportTo(Uri uri, boolean pdf) {
        final List<TransactionRow> rows = pendingExportRows != null ? pendingExportRows : new ArrayList<>(displayedRows);
        pendingExportRows = null;
        final String format = pdf ? "PDF" : "CSV";

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_loading, null);
        TextView message = dialogView.findViewById(R.id.loadingMessage);
        if (message != null) message.setText(getString(R.string.msg_exporting_format, format));
        exportProgressDialog = new SafeDialogs.Material(this).setView(dialogView).setCancelable(false).create();
        SafeDialogs.show(exportProgressDialog);
        final AlertDialog progress = exportProgressDialog;

        // Application context: the worker must not keep this Activity alive, and only needs strings and the resolver.
        final android.content.Context appContext = getApplicationContext();
        final String generatedOn = TimeUtils.formatDateTime(Instant.now().toString());
        try {
            exportExecutor.execute(() -> {
                final String failureMessage = TransactionExporter.exportToUri(appContext, uri, pdf, rows, generatedOn);
                runOnUiThread(() -> {
                    // The file was written (or not) regardless of the guest having left; only the dialog and
                    // toast are skipped once the screen is gone.
                    dismissSafely(progress);
                    if (progress == exportProgressDialog) exportProgressDialog = null;
                    if (!isUiAlive()) return;
                    Toast.makeText(this, failureMessage == null
                            ? getString(R.string.msg_export_success, getString(R.string.downloads_folder_name))
                            : failureMessage, Toast.LENGTH_LONG).show();
                });
            });
        } catch (RuntimeException rejected) {
            // The executor was shut down (screen closing) - nothing left to do but drop the dialog.
            dismissSafely(progress);
        }
    }
}
