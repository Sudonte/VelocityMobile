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
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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
import com.google.android.material.chip.Chip;
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

    private static final long AUTO_REFRESH_MS = 30_000;
    private static final long SEARCH_DEBOUNCE_MS = 300;

    /** "Filter by Status" options, position-matched with STATUS_LABEL_RES (selection maps back by position, so it works in any locale). */
    private static final TransactionFilter.Type[] STATUS_TYPES = {
            TransactionFilter.Type.ALL, TransactionFilter.Type.BOOKINGS, TransactionFilter.Type.RESERVATIONS,
            TransactionFilter.Type.PAYMENTS, TransactionFilter.Type.PENDING, TransactionFilter.Type.PAID,
            TransactionFilter.Type.PARTIALLY_PAID, TransactionFilter.Type.CANCELLED, TransactionFilter.Type.REJECTED,
            TransactionFilter.Type.STAYS
    };
    private static final int[] STATUS_LABEL_RES = {
            R.string.filter_all_transactions_label, R.string.filter_bookings, R.string.filter_reservations,
            R.string.filter_payments, R.string.txn_status_pending, R.string.txn_status_paid,
            R.string.txn_status_partially_paid, R.string.txn_status_cancelled, R.string.txn_status_rejected,
            R.string.filter_stays
    };

    private RecyclerView rvTransactions;
    private View scrollEmptyState, layoutEmptyState, layoutInitialLoading;
    private SwipeRefreshLayout swipeRefresh;
    private TextInputEditText etSearch;
    private AutoCompleteTextView dropdownTransactionStatus;
    private Chip chipRoomType, chipDateFilter, chipBookingStatus;
    private TextView tvEmptyTitle, tvEmptyDesc, tvTransactionHeader;
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
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            // Light "anything changed?" checks - never a full reload of however far the list has been
            // scrolled. The notification poll is what lets an arrival trigger an immediate status refresh.
            requestPoll();
            pollNotificationsForArrivals();
            handler.postDelayed(this, AUTO_REFRESH_MS);
        }
    };

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
        List<Booking> cached = repository.getBookings();
        if (!cached.isEmpty()) {
            allRows = TransactionRow.fromAll(cached);
            loadedOnce = true;
            applyFilters();
        }
        loadTransactions(true);
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
        handler.removeCallbacks(autoRefreshRunnable);
        handler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(autoRefreshRunnable);
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
        dropdownTransactionStatus = findViewById(R.id.dropdownTransactionStatus);
        chipRoomType = findViewById(R.id.chipRoomType);
        chipDateFilter = findViewById(R.id.chipDateFilter);
        chipBookingStatus = findViewById(R.id.chipBookingStatus);
        tvTransactionHeader = findViewById(R.id.tvTransactionHeader);

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
                        allRows = TransactionRow.fromAll(merged);
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
        String[] labels = new String[STATUS_LABEL_RES.length];
        for (int i = 0; i < labels.length; i++) labels[i] = getString(STATUS_LABEL_RES[i]);
        dropdownTransactionStatus.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels));
        dropdownTransactionStatus.setOnItemClickListener((parent, view, position, id) -> {
            criteria.type = STATUS_TYPES[position];
            applyFilters();
        });

        chipRoomType.setOnClickListener(v -> showRoomTypeFilterDialog());
        chipBookingStatus.setOnClickListener(v -> showBookingStatusFilterDialog());
        chipDateFilter.setOnClickListener(v -> showDateRangePicker());

        chipRoomType.setOnCloseIconClickListener(v -> {
            criteria.roomType = null;
            updateChips();
            applyFilters();
        });
        chipBookingStatus.setOnCloseIconClickListener(v -> {
            criteria.bookingStatus = null;
            updateChips();
            applyFilters();
        });
        chipDateFilter.setOnCloseIconClickListener(v -> {
            criteria.from = null;
            criteria.to = null;
            updateChips();
            applyFilters();
        });
        for (Chip chip : new Chip[]{chipRoomType, chipBookingStatus, chipDateFilter}) {
            chip.setCloseIconContentDescription(getString(R.string.txn_chip_clear_cd));
        }
        updateChips();
    }

    /** The chips show their active value and a clear (x) button only while a filter is set. */
    private void updateChips() {
        chipRoomType.setText(criteria.roomType != null ? criteria.roomType : getString(R.string.room_type_filter_label));
        chipRoomType.setCloseIconVisible(criteria.roomType != null);
        chipBookingStatus.setText(criteria.bookingStatus != null ? criteria.bookingStatus : getString(R.string.booking_status_filter_label));
        chipBookingStatus.setCloseIconVisible(criteria.bookingStatus != null);
        if (criteria.hasDateRange()) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd", Locale.US);
            String from = criteria.from != null ? fmt.format(criteria.from) : "…";
            String to = criteria.to != null ? fmt.format(criteria.to) : "…";
            chipDateFilter.setText(getString(R.string.date_range_format, from, to));
        } else {
            chipDateFilter.setText(R.string.date_filter_label);
        }
        chipDateFilter.setCloseIconVisible(criteria.hasDateRange());
    }

    /** Sets the status filter and shows it in the dropdown - for deep links and Clear Filters (a guest's own pick goes through the item-click listener). */
    private void setStatusFilter(TransactionFilter.Type type) {
        criteria.type = type;
        for (int i = 0; i < STATUS_TYPES.length; i++) {
            if (STATUS_TYPES[i] == type) {
                dropdownTransactionStatus.setText(getString(STATUS_LABEL_RES[i]), false);
                break;
            }
        }
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                criteria.query = s.toString();
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
                allRows = TransactionRow.fromAll(merged);
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
                allRows = TransactionRow.fromAll(merged);
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
    }

    private void render() {
        adapter.submit(displayedRows);
        if (tvTransactionHeader != null) {
            tvTransactionHeader.setText(displayedRows.isEmpty() || displayedRows.size() == allRows.size()
                    ? getString(R.string.recent_transactions)
                    : getString(R.string.recent_transactions) + " (" + displayedRows.size() + ")");
        }

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
            tvEmptyTitle.setText(R.string.txn_filtered_empty_title);
            tvEmptyDesc.setText(!criteria.query.trim().isEmpty() ? R.string.transaction_search_empty_desc
                    : criteria.type == TransactionFilter.Type.PAYMENTS ? R.string.transaction_payment_empty_desc
                    : R.string.empty_transactions_desc);
            setEmptyAction(R.string.clear_filters, R.drawable.ic_filter, v -> clearAllFiltersAndSearch());
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

    /** Resets search, status, room type, booking status and date range - the way out of an over-filtered list. */
    private void clearAllFiltersAndSearch() {
        criteria.query = "";
        criteria.roomType = null;
        criteria.bookingStatus = null;
        criteria.from = null;
        criteria.to = null;
        if (etSearch.getText() != null && etSearch.getText().length() > 0) {
            etSearch.setText(""); // the watcher sets criteria.query again (to "") and queues a harmless re-apply
        }
        setStatusFilter(TransactionFilter.Type.ALL);
        updateChips();
        applyFilters();
    }

    // ---- Filter pickers ----

    private void showRoomTypeFilterDialog() {
        // Every distinct room type across every transaction, including each line of a multi-room one.
        List<String> types = new ArrayList<>();
        for (TransactionRow row : allRows) {
            for (String type : row.roomTypes) {
                if (!types.contains(type)) types.add(type);
            }
        }
        if (types.isEmpty()) {
            Toast.makeText(this, R.string.txn_filter_no_options, Toast.LENGTH_SHORT).show();
            return;
        }
        Collections.sort(types, String.CASE_INSENSITIVE_ORDER);
        String[] options = types.toArray(new String[0]);
        int checked = criteria.roomType != null ? types.indexOf(criteria.roomType) : -1;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.room_type_filter_title)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    criteria.roomType = options[which];
                    updateChips();
                    applyFilters();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.clear_filters, (dialog, which) -> {
                    criteria.roomType = null;
                    updateChips();
                    applyFilters();
                })
                .show();
    }

    /** Single-choice picker over the distinct statuses present among the guest's Bookings (Pending, Confirmed, Checked-In, ...). */
    private void showBookingStatusFilterDialog() {
        List<String> statuses = new ArrayList<>();
        for (TransactionRow row : allRows) {
            if (row.kind == TransactionRow.Kind.BOOKING && !row.lifecycleStatus.isEmpty() && !statuses.contains(row.lifecycleStatus)) {
                statuses.add(row.lifecycleStatus);
            }
        }
        if (statuses.isEmpty()) {
            Toast.makeText(this, R.string.txn_filter_no_options, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] options = statuses.toArray(new String[0]);
        int checked = criteria.bookingStatus != null ? statuses.indexOf(criteria.bookingStatus) : -1;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.booking_status_filter_title)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    criteria.bookingStatus = options[which];
                    updateChips();
                    applyFilters();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.clear_filters, (dialog, which) -> {
                    criteria.bookingStatus = null;
                    updateChips();
                    applyFilters();
                })
                .show();
    }

    /** Date range over the CHECK-IN date. The picker speaks UTC-midnight millis; they are turned into calendar dates right away so no time zone can shift a day. */
    private void showDateRangePicker() {
        MaterialDatePicker.Builder<androidx.core.util.Pair<Long, Long>> builder = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.date_filter_label);
        if (criteria.from != null && criteria.to != null) {
            builder.setSelection(new androidx.core.util.Pair<>(
                    criteria.from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    criteria.to.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()));
        }
        MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker = builder.build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null || selection.first == null || selection.second == null) return;
            criteria.from = Instant.ofEpochMilli(selection.first).atZone(ZoneOffset.UTC).toLocalDate();
            criteria.to = Instant.ofEpochMilli(selection.second).atZone(ZoneOffset.UTC).toLocalDate();
            updateChips();
            applyFilters();
        });
        picker.show(getSupportFragmentManager(), "DATE_PICKER");
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
                allRows = TransactionRow.fromAll(repository.getBookings());
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
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.export_options_title)
                .setItems(options, (dialog, which) -> {
                    pendingExportRows = new ArrayList<>(displayedRows);
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
        exportProgressDialog = new MaterialAlertDialogBuilder(this).setView(dialogView).setCancelable(false).create();
        exportProgressDialog.show();
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
