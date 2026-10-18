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
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
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
    private AutoCompleteTextView dropdownTransaction, dropdownBookingStatus, dropdownRoomType;
    private com.google.android.material.textfield.TextInputLayout layoutCheckIn, layoutCheckOut;
    private TextInputEditText etCheckIn, etCheckOut;
    private TextView btnToggleFilters, btnClearFilters;
    private View layoutFilterPanel;
    /** The Transaction dropdown: what each entry filters on (position-matched with {@link #TRANSACTION_LABELS}). */
    private static final TransactionFilter.Type[] TRANSACTION_TYPES = {
            TransactionFilter.Type.ALL, TransactionFilter.Type.BOOKINGS, TransactionFilter.Type.RESERVATIONS,
            TransactionFilter.Type.PAYMENTS, TransactionFilter.Type.PENDING, TransactionFilter.Type.PAID,
            TransactionFilter.Type.PARTIALLY_PAID, TransactionFilter.Type.CANCELLED, TransactionFilter.Type.REJECTED,
            TransactionFilter.Type.CHECKED_IN, TransactionFilter.Type.CHECKED_OUT
    };
    private static final int[] TRANSACTION_LABELS = {
            R.string.filter_option_all_transactions, R.string.filter_bookings, R.string.filter_reservations,
            R.string.filter_payments, R.string.txn_status_pending, R.string.txn_status_paid,
            R.string.filter_option_partially_paid, R.string.upcoming_status_cancelled, R.string.upcoming_status_rejected,
            R.string.filter_option_checked_in, R.string.filter_option_checked_out
    };
    private static final DateTimeFormatter DATE_SHOWN = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private List<TransactionFilterOptions.Option> bookingStatusOptions = new ArrayList<>();
    private List<String> roomTypeNames = new ArrayList<>();
    private boolean filtersPanelVisible = true;
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
    /** True once the deep-linked transaction has been shown: from then on the guest's filters are never overridden to reveal it. */
    private boolean deepLinkSettled = false;

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
        restoreFilterState(savedInstanceState);
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
        dropdownTransaction = findViewById(R.id.dropdownTransaction);
        dropdownBookingStatus = findViewById(R.id.dropdownBookingStatus);
        dropdownRoomType = findViewById(R.id.dropdownRoomType);
        layoutCheckIn = findViewById(R.id.layoutCheckIn);
        layoutCheckOut = findViewById(R.id.layoutCheckOut);
        etCheckIn = findViewById(R.id.etCheckIn);
        etCheckOut = findViewById(R.id.etCheckOut);
        btnToggleFilters = findViewById(R.id.btnToggleFilters);
        btnClearFilters = findViewById(R.id.btnClearFilters);
        layoutFilterPanel = findViewById(R.id.layoutFilterPanel);
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

    // ---- Filters: three dropdowns and two dates ----

    private void setupFilters() {
        // Every dropdown uses a NON-filtering adapter and sets its text with setText(text, false): the list is always the
        // full list - when it first opens, after a pick, and after coming back from a transaction's details.
        String[] transactionLabels = new String[TRANSACTION_LABELS.length];
        for (int i = 0; i < transactionLabels.length; i++) transactionLabels[i] = getString(TRANSACTION_LABELS[i]);
        dropdownTransaction.setAdapter(new NonFilteringArrayAdapter(this, transactionLabels));
        dropdownTransaction.setOnItemClickListener((parent, view, position, id) -> {
            criteria.type = TRANSACTION_TYPES[position];
            onFiltersChanged();
        });

        bookingStatusOptions = TransactionFilterOptions.bookingStatuses();
        List<String> statusLabels = new ArrayList<>();
        for (TransactionFilterOptions.Option option : bookingStatusOptions) statusLabels.add(getString(option.labelRes));
        dropdownBookingStatus.setAdapter(new NonFilteringArrayAdapter(this, statusLabels));
        dropdownBookingStatus.setOnItemClickListener((parent, view, position, id) -> {
            String value = bookingStatusOptions.get(position).value;
            criteria.bookingStatus = value.isEmpty() ? null : value;
            onFiltersChanged();
        });

        refreshRoomTypeDropdown();
        dropdownRoomType.setOnItemClickListener((parent, view, position, id) -> {
            criteria.roomType = position == 0 ? null : roomTypeNames.get(position - 1);
            onFiltersChanged();
        });

        etCheckIn.setOnClickListener(v -> pickDate(true));
        etCheckOut.setOnClickListener(v -> pickDate(false));
        layoutCheckIn.setEndIconOnClickListener(v -> {
            criteria.setCheckIn(null);
            onFiltersChanged();
        });
        layoutCheckOut.setEndIconOnClickListener(v -> {
            criteria.setCheckOut(null);
            onFiltersChanged();
        });
        layoutCheckIn.setEndIconContentDescription(getString(R.string.filters_clear_date_cd, getString(R.string.filter_label_check_in)));
        layoutCheckOut.setEndIconContentDescription(getString(R.string.filters_clear_date_cd, getString(R.string.filter_label_check_out)));

        btnToggleFilters.setOnClickListener(v -> {
            filtersPanelVisible = !filtersPanelVisible;
            syncFilterWidgets();
        });
        btnClearFilters.setOnClickListener(v -> clearAllFiltersAndSearch());
        syncFilterWidgets();
    }

    /** A filter changed: show it, start the page budget over and re-filter. */
    private void onFiltersChanged() {
        // A picked field gives up focus: its border goes back to the normal colour (red only while focused / open).
        View focused = getCurrentFocus();
        if (focused != null) focused.clearFocus();
        autoPagesLoaded = 0;
        syncFilterWidgets();
        applyFilters();
    }

    /**
     * Shows the criteria in the widgets WITHOUT triggering any filtering: dropdown texts through setText(text, false), the
     * date fields with their clear icons, the Show / Hide toggle with its active count, and Clear filters only while
     * something is filtering.
     */
    private void syncFilterWidgets() {
        int typeIndex = 0;
        for (int i = 0; i < TRANSACTION_TYPES.length; i++) if (TRANSACTION_TYPES[i] == criteria.type) typeIndex = i;
        // The field carries its own label ("Transaction"), so its default value reads just "All"; the list keeps the full names.
        dropdownTransaction.setText(typeIndex == 0 ? getString(R.string.filter_value_all) : getString(TRANSACTION_LABELS[typeIndex]), false);

        int statusIndex = 0;
        for (int i = 0; i < bookingStatusOptions.size(); i++) {
            String value = bookingStatusOptions.get(i).value;
            if (criteria.bookingStatus == null ? value.isEmpty() : value.equalsIgnoreCase(criteria.bookingStatus)) statusIndex = i;
        }
        if (!bookingStatusOptions.isEmpty()) dropdownBookingStatus.setText(getString(bookingStatusOptions.get(statusIndex).labelRes), false);

        dropdownRoomType.setText(criteria.roomType != null ? criteria.roomType : getString(R.string.filter_value_all), false);

        etCheckIn.setText(criteria.checkInFrom != null ? DATE_SHOWN.format(criteria.checkInFrom) : "");
        etCheckOut.setText(criteria.checkOutTo != null ? DATE_SHOWN.format(criteria.checkOutTo) : "");
        layoutCheckIn.setEndIconVisible(criteria.checkInFrom != null);
        layoutCheckOut.setEndIconVisible(criteria.checkOutTo != null);

        int active = criteria.activeFilterCount();
        layoutFilterPanel.setVisibility(filtersPanelVisible ? View.VISIBLE : View.GONE);
        btnToggleFilters.setText(filtersPanelVisible ? getString(R.string.filters_hide)
                : active > 0 ? getResources().getQuantityString(R.plurals.filters_show_count, active, active) : getString(R.string.filters_show));
        btnClearFilters.setVisibility(active > 0 || !criteria.query.trim().isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** The Room type dropdown: All room types, then the Browse Rooms catalog (or, without it, the guest's own transactions' room types). Rebuilt only when the list changes, so a poll never closes an open dropdown. */
    private void refreshRoomTypeDropdown() {
        List<String> types = TransactionFilterOptions.roomTypes(repository.getAllRooms(), allRows);
        if (types.equals(roomTypeNames) && dropdownRoomType.getAdapter() != null) return;
        roomTypeNames = types;
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.filters_all_room_types));
        labels.addAll(types);
        dropdownRoomType.setAdapter(new NonFilteringArrayAdapter(this, labels));
        if (criteria.roomType != null) dropdownRoomType.setText(criteria.roomType, false);
    }

    /** Sets the filter an old-style type key stands for - for deep links (a guest's own picks go through the dropdowns). */
    private void setStatusFilter(TransactionFilter.Type type) {
        criteria.type = type;
        syncFilterWidgets();
    }

    /** The room-type catalog Browse Rooms uses is filled by a request; ask for it once so the Room type dropdown can list every room type. */
    private void warmRoomTypeCatalog() {
        if (!repository.getAllRooms().isEmpty()) return;
        repository.refreshRooms(new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> result) {
                if (isUiAlive()) refreshRoomTypeDropdown();
            }

            @Override
            public void onError(String message) {
                // Not fatal: the dropdown keeps the room types found in the guest's own transactions.
            }
        });
    }

    // ---- Keeping the filters across a screen rotation ----

    private static final String STATE_TYPE = "txn_filter_type";
    private static final String STATE_BOOKING = "txn_filter_booking";
    private static final String STATE_ROOM = "txn_filter_room";
    private static final String STATE_CHECK_IN = "txn_filter_check_in";
    private static final String STATE_CHECK_OUT = "txn_filter_check_out";
    private static final String STATE_PANEL = "txn_filter_panel";
    private static final String STATE_QUERY = "txn_filter_query";
    private static final long NO_DATE = Long.MIN_VALUE;

    @Override
    protected void onSaveInstanceState(android.os.Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_TYPE, criteria.type.name());
        outState.putString(STATE_BOOKING, criteria.bookingStatus);
        outState.putString(STATE_ROOM, criteria.roomType);
        outState.putLong(STATE_CHECK_IN, criteria.checkInFrom != null ? criteria.checkInFrom.toEpochDay() : NO_DATE);
        outState.putLong(STATE_CHECK_OUT, criteria.checkOutTo != null ? criteria.checkOutTo.toEpochDay() : NO_DATE);
        outState.putBoolean(STATE_PANEL, filtersPanelVisible);
        outState.putString(STATE_QUERY, criteria.query);
    }

    private void restoreFilterState(@Nullable Bundle saved) {
        if (saved == null || !saved.containsKey(STATE_TYPE)) return;
        try {
            criteria.type = TransactionFilter.Type.valueOf(saved.getString(STATE_TYPE, "ALL"));
        } catch (IllegalArgumentException unknown) {
            criteria.type = TransactionFilter.Type.ALL;
        }
        criteria.bookingStatus = saved.getString(STATE_BOOKING);
        criteria.roomType = saved.getString(STATE_ROOM);
        long in = saved.getLong(STATE_CHECK_IN, NO_DATE);
        long out = saved.getLong(STATE_CHECK_OUT, NO_DATE);
        criteria.checkInFrom = in == NO_DATE ? null : java.time.LocalDate.ofEpochDay(in);
        criteria.checkOutTo = out == NO_DATE ? null : java.time.LocalDate.ofEpochDay(out);
        filtersPanelVisible = saved.getBoolean(STATE_PANEL, true);
        criteria.query = saved.getString(STATE_QUERY, "");
    }

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
        if (dropdownRoomType != null) refreshRoomTypeDropdown();
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
        syncFilterWidgets();
        applyFilters();
    }

    // ---- Date fields ----

    /**
     * One date (check-in or check-out). The picker only offers dates that keep the window valid - a check-in not after the
     * check-out already chosen, a check-out not before the check-in - and the filter refuses the rest again, so a
     * check-out before the check-in can never be set. The picker speaks UTC-midnight millis; they become calendar dates
     * right away so no time zone can shift a day.
     */
    private void pickDate(boolean checkIn) {
        java.time.LocalDate current = checkIn ? criteria.checkInFrom : criteria.checkOutTo;
        CalendarConstraints.Builder constraints = new CalendarConstraints.Builder();
        if (checkIn && criteria.checkOutTo != null) {
            constraints.setValidator(DateValidatorPointBackward.before(criteria.checkOutTo.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()));
        } else if (!checkIn && criteria.checkInFrom != null) {
            constraints.setValidator(DateValidatorPointForward.from(criteria.checkInFrom.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()));
        }
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText(checkIn ? R.string.filter_label_check_in : R.string.filter_label_check_out)
                .setCalendarConstraints(constraints.build());
        if (current != null) builder.setSelection(current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
        MaterialDatePicker<Long> picker = builder.build();
        picker.addOnPositiveButtonClickListener(millis -> {
            if (millis == null) return;
            java.time.LocalDate date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
            if (!(checkIn ? criteria.setCheckIn(date) : criteria.setCheckOut(date))) {
                Toast.makeText(this, R.string.filters_dates_order_error, Toast.LENGTH_SHORT).show();
                return;
            }
            onFiltersChanged();
        });
        picker.show(getSupportFragmentManager(), checkIn ? TAG_PICK_CHECK_IN : TAG_PICK_CHECK_OUT);
    }

    private static final String TAG_PICK_CHECK_IN = "PICK_CHECK_IN";
    private static final String TAG_PICK_CHECK_OUT = "PICK_CHECK_OUT";

    /**
     * A date picker open during recreation comes back without its listener: it is re-attached, so choosing the date still
     * applies it.
     */
    @SuppressWarnings("unchecked")
    private void reattachRestoredPickerListeners() {
        for (String tag : new String[]{TAG_PICK_CHECK_IN, TAG_PICK_CHECK_OUT}) {
            androidx.fragment.app.Fragment restored = getSupportFragmentManager().findFragmentByTag(tag);
            if (!(restored instanceof MaterialDatePicker)) continue;
            final boolean checkIn = TAG_PICK_CHECK_IN.equals(tag);
            ((MaterialDatePicker<Long>) restored).addOnPositiveButtonClickListener(millis -> {
                if (millis == null) return;
                java.time.LocalDate date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
                if (checkIn ? criteria.setCheckIn(date) : criteria.setCheckOut(date)) onFiltersChanged();
            });
        }
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
            // Only while the link is being delivered: once the guest has seen it, their own filters stand - otherwise every filter
            // that hides the linked transaction would be wiped again at once (the Check-in / Check-out dates "not working").
            if (!deepLinkSettled && !displayedRows.contains(target) && criteria.isActive()) {
                clearAllFiltersAndSearch();
                return;
            }
            deepLinkSettled = true;
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
