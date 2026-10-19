package com.example.velocitysuites;

import androidx.annotation.NonNull;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RoomBrowsingActivity extends BaseNavigationActivity implements RoomAdapter.OnRoomClickListener {

    private RecyclerView rvRooms;
    private SwipeRefreshLayout swipeRefresh;
    private RoomAdapter adapter;
    private List<Room> allRooms;
    private List<Room> filteredRooms;

    private TextInputEditText etCheckIn, etCheckOut;
    private TextInputLayout checkInLayout, checkOutLayout;
    private MaterialButton btnSearchAvailability, btnFilters, btnResetSearch;
    private MaterialButton btnSort;
    private View noResultsView, loadingOverlay, layoutRoomError;
    private TextView tvResultsCount;
    private TextView emptyStateTitle, emptyStateDesc;
    private ImageView emptyStateIcon;
    private MaterialButton btnEmptyStateAction, btnEmptyStateAction2;

    private Calendar checkInDate, checkOutDate;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    /** What the Check-in / Check-out fields show: "Mon, Oct 12, 2026". */
    private final SimpleDateFormat fieldDateFormat = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault());
    private final SimpleDateFormat shortDateFormat = new SimpleDateFormat("MMM d", Locale.getDefault());
    private TextView tvNightsSummary, tvPinnedDates;
    private ChipGroup chipsActiveFilters;
    private View layoutActiveFilters;
    /** The Filters bottom sheet while it is open (one at a time; dismissed with the screen). */
    private com.google.android.material.bottomsheet.BottomSheetDialog filterSheet;

    // Filters and Sorting
    private int selectedCapacity = 0; // 0 for any, 1 for 1-2, 2 for 3-4, 3 for 5+
    /** Price range filter: {@link RoomFilter#UNSET} on a side = no limit there. */
    private float selectedMinPrice = RoomFilter.UNSET;
    private float selectedMaxPrice = RoomFilter.UNSET;
    private String currentSortMode = "Default"; // Default, PriceLow, PriceHigh, NameAZ
    private String selectedRoomTypeFilter = "All";
    private String selectedStatusFilter = "All"; // All, Available, Limited, Unavailable

    private com.google.android.material.appbar.AppBarLayout appBarLayout;
    private String selectedRoomIdFromLanding;
    private String selectedRoomActionFromLanding;
    private boolean shouldOpenSelectedRoom;
    private boolean shouldOpenSelectedRoomsSummary;
    private boolean hasLoadedRoomsOnce;
    /** True once the guest has searched/filtered/sorted: the list then shows applyFilters()' result, not simply every room (see showLoadedRooms()). */
    private boolean filtersApplied;
    /** What the room list was last drawn from, so the silent 30s refresh redraws only on a real change (see onVisiblePoll()). */
    private final ChangeGate roomsGate = new ChangeGate();
    private List<String> roomTypeOptions = new ArrayList<>();

    /** Multi-room cart: room id -> selected quantity, pre-seeded from an incoming ROOM_IDS extra (landing.xml carryover), extendable here via each card's quantity stepper. */
    private final Map<String, Integer> selectedQuantities = new LinkedHashMap<>();
    private View cardSelectionSummaryBar;
    private TextView tvSelectionSummaryCount, tvSelectionSummaryTotal;
    /** Debounces rapid double-taps on Book Now/Reserve Now so a fast double-tap can't launch two Activities. Reset in onResume(). */
    private boolean isNavigatingToBookingFlow = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.roombrowsing);
        selectedRoomIdFromLanding = getIntent().getStringExtra("ROOM_ID");
        selectedRoomActionFromLanding = getIntent().getStringExtra("ROOM_ACTION");
        shouldOpenSelectedRoom = getIntent().getBooleanExtra("OPEN_ROOM_DETAILS", selectedRoomIdFromLanding != null);
        String roomIdsCsv = getIntent().getStringExtra("ROOM_IDS");
        if (savedInstanceState != null) {
            // Recreated (dark-mode toggle, system kill): the guest's own cart, dates and filters win over the launch extras,
            // and the one-time "open this room / the cart summary" hand-off from the landing page must not fire again.
            restoreBrowsingState(savedInstanceState);
            shouldOpenSelectedRoom = false;
        } else if (roomIdsCsv != null && !roomIdsCsv.isEmpty()) {
            // Each unit of quantity is one repeated id in the CSV (the same
            // "duplicates ARE the quantity" convention PendingRoomSelection/
            // PendingWizardRooms/BookingWizardState already use) - count
            // occurrences per id rather than Collections.addAll into a Set,
            // which would silently collapse repeated ids and lose quantity.
            String[] ids = roomIdsCsv.split(",");
            for (String id : ids) {
                selectedQuantities.merge(id, 1, Integer::sum);
            }
            // A multi-room cart carried over from landing.xml must immediately
            // show its full details here, not just the collapsed summary bar -
            // mirrors the single-room auto-open path below.
            shouldOpenSelectedRoomsSummary = ids.length >= 2;
        } else if (selectedRoomIdFromLanding != null) {
            // Whatever quantity the guest already staged on this room's card
            // stepper on landing.xml (ROOM_QTY, default 1) must carry through
            // here regardless of whether this launch auto-opens the details
            // dialog - previously this branch only ran when
            // shouldOpenSelectedRoom was false, so the far more common
            // "Book Now opens the details dialog immediately" path silently
            // dropped any quantity back to 1 (see navigateToBooking() /
            // resolveCartRoomsIncluding(), which read this same map).
            int qtyFromLanding = Math.max(1, getIntent().getIntExtra("ROOM_QTY", 1));
            selectedQuantities.put(selectedRoomIdFromLanding, qtyFromLanding);
        }
        setupGuestNavigation(R.id.nav_room_browsing);

        initializeViews();
        setupRecyclerView();
        loadRooms();
        setupDatePickers();
        setupSearchAndFilters();

        animateScreenContent();
        updatePersonalizedGreeting();
        applyRestoredDates();
        reattachRestoredPickerListeners();
    }

    // ---- surviving recreation (dark-mode toggle, process death) ----

    private static final String STATE_CART_IDS = "cart_ids";
    private static final String STATE_CART_QTY = "cart_qty";
    private static final String STATE_CHECK_IN = "check_in_utc";
    private static final String STATE_CHECK_OUT = "check_out_utc";
    private static final String STATE_CAPACITY = "capacity";
    private static final String STATE_MAX_PRICE = "max_price_v2";
    private static final String STATE_MIN_PRICE = "min_price_v2";
    private static final String STATE_SORT = "sort_mode";
    private static final String STATE_TYPE = "type_filter";
    private static final String STATE_STATUS = "status_filter";
    private static final String STATE_FILTERS_APPLIED = "filters_applied";
    /** Dates read back from a saved state, applied to the fields and searched once the screen is built (applyRestoredDates). */
    private long restoredCheckInUtc = -1L, restoredCheckOutUtc = -1L;

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        String[] ids = selectedQuantities.keySet().toArray(new String[0]);
        int[] qty = new int[ids.length];
        for (int i = 0; i < ids.length; i++) {
            Integer q = selectedQuantities.get(ids[i]);
            qty[i] = q == null ? 0 : q;
        }
        outState.putStringArray(STATE_CART_IDS, ids);
        outState.putIntArray(STATE_CART_QTY, qty);
        outState.putLong(STATE_CHECK_IN, checkInDate != null ? toUtcMidnight(checkInDate) : -1L);
        outState.putLong(STATE_CHECK_OUT, checkOutDate != null ? toUtcMidnight(checkOutDate) : -1L);
        outState.putInt(STATE_CAPACITY, selectedCapacity);
        outState.putFloat(STATE_MAX_PRICE, selectedMaxPrice);
        outState.putFloat(STATE_MIN_PRICE, selectedMinPrice);
        outState.putString(STATE_SORT, currentSortMode);
        outState.putString(STATE_TYPE, selectedRoomTypeFilter);
        outState.putString(STATE_STATUS, selectedStatusFilter);
        outState.putBoolean(STATE_FILTERS_APPLIED, filtersApplied);
    }

    private void restoreBrowsingState(Bundle state) {
        String[] ids = state.getStringArray(STATE_CART_IDS);
        int[] qty = state.getIntArray(STATE_CART_QTY);
        if (ids != null && qty != null) {
            for (int i = 0; i < ids.length && i < qty.length; i++) {
                if (qty[i] > 0) selectedQuantities.put(ids[i], qty[i]);
            }
        }
        restoredCheckInUtc = state.getLong(STATE_CHECK_IN, -1L);
        restoredCheckOutUtc = state.getLong(STATE_CHECK_OUT, -1L);
        selectedCapacity = state.getInt(STATE_CAPACITY, 0);
        selectedMaxPrice = state.getFloat(STATE_MAX_PRICE, RoomFilter.UNSET);
        selectedMinPrice = state.getFloat(STATE_MIN_PRICE, RoomFilter.UNSET);
        currentSortMode = state.getString(STATE_SORT, "Default");
        selectedRoomTypeFilter = state.getString(STATE_TYPE, "All");
        selectedStatusFilter = state.getString(STATE_STATUS, "All");
        filtersApplied = state.getBoolean(STATE_FILTERS_APPLIED, false);
    }

    /** Puts the restored dates back in the fields and re-runs the date-aware availability search (the plain load that onCreate started is superseded). */
    private void applyRestoredDates() {
        if (restoredCheckInUtc < 0) return;
        checkInDate = fromUtcMidnight(restoredCheckInUtc);
        etCheckIn.setText(fieldDateFormat.format(checkInDate.getTime()));
        checkOutLayout.setEnabled(true);
        if (restoredCheckOutUtc >= 0) {
            checkOutDate = fromUtcMidnight(restoredCheckOutUtc);
            etCheckOut.setText(fieldDateFormat.format(checkOutDate.getTime()));
        }
        updateDateSummary();
        if (checkOutDate != null && checkOutDate.after(checkInDate)) performSearchWithLoading();
        restoredCheckInUtc = restoredCheckOutUtc = -1L;
    }

    /** The room-details dialog has its own heart: when it closes, redraw the card hearts from the store. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && adapter != null) adapter.refreshFavorites();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isNavigatingToBookingFlow = false;
        // onCreate() already triggered the initial loadRooms() - skip that first resume
        // and only re-fetch on subsequent returns to this screen (e.g. after completing
        // a booking elsewhere), so statuses/filters reflect the latest server state.
        if (hasLoadedRoomsOnce) {
            if (checkInDate != null && checkOutDate != null) {
                performSearchWithLoading();
            } else {
                loadRooms();
            }
        } else {
            hasLoadedRoomsOnce = true;
        }
    }

    private void initializeViews() {
        rvRooms = findViewById(R.id.rvRooms);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.velocity_red_primary);
            // Same real network refetch onResume() already performs when
            // dates are already picked - never just a redraw of allRooms.
            swipeRefresh.setOnRefreshListener(() -> {
                if (checkInDate != null && checkOutDate != null) {
                    performSearchWithLoading();
                } else {
                    loadRooms();
                }
            });
        }
        etCheckIn = findViewById(R.id.etCheckIn);
        etCheckOut = findViewById(R.id.etCheckOut);
        checkInLayout = findViewById(R.id.checkInLayout);
        tvNightsSummary = findViewById(R.id.tvNightsSummary);
        tvPinnedDates = findViewById(R.id.tvPinnedDates);
        checkOutLayout = findViewById(R.id.checkOutLayout);
        chipsActiveFilters = findViewById(R.id.chipsActiveFilters);
        layoutActiveFilters = findViewById(R.id.layoutActiveFilters);
        // An error replaces the helper text under the field (never squeezed beside the value), and the red error icon is not
        // drawn over the field's own clear (x) icon.
        checkInLayout.setErrorIconDrawable(null);
        checkOutLayout.setErrorIconDrawable(null);
        btnSearchAvailability = findViewById(R.id.btnSearchAvailability);
        btnFilters = findViewById(R.id.btnFilters);
        btnResetSearch = findViewById(R.id.btnResetSearch);
        btnSort = findViewById(R.id.btnSort);
        noResultsView = findViewById(R.id.noResultsView);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        tvResultsCount = findViewById(R.id.tvResultsCount);
        appBarLayout = findViewById(R.id.appBarLayout);
        if (appBarLayout != null) {
            // Pull-to-refresh only while the header is fully expanded. With the header collapsed a stray pull
            // used to leave SwipeRefresh's white circle hanging over the filter dropdowns.
            appBarLayout.addOnOffsetChangedListener((bar, offset) -> {
                if (swipeRefresh != null && !swipeRefresh.isRefreshing()) swipeRefresh.setEnabled(offset == 0);
            });
        }

        // The empty state is a shared include (view_empty_state.xml) whose title/description/
        // action are populated per-screen in code - without this it would render as a blank
        // card (only an icon) whenever a search/filter combination matches zero rooms.
        // Child lookups are scoped to noResultsView's own subtree (not Activity-wide
        // findViewById) since layoutRoomError below reuses the same shared layout and
        // therefore the same child ids (emptyIcon/emptyTitle/emptyDesc/btnEmptyAction).
        emptyStateTitle = noResultsView.findViewById(R.id.emptyTitle);
        emptyStateDesc = noResultsView.findViewById(R.id.emptyDesc);
        emptyStateIcon = noResultsView.findViewById(R.id.emptyIcon);
        btnEmptyStateAction = noResultsView.findViewById(R.id.btnEmptyAction);
        if (emptyStateTitle != null) emptyStateTitle.setText(R.string.no_matching_rooms);
        if (emptyStateDesc != null) emptyStateDesc.setText(R.string.no_matching_rooms_desc);
        if (emptyStateIcon != null) emptyStateIcon.setImageResource(R.drawable.ic_bed);
        btnEmptyStateAction2 = noResultsView.findViewById(R.id.btnEmptyAction2);

        // Persistent network/API failure state - see roombrowsing.xml's layoutRoomError.
        layoutRoomError = findViewById(R.id.layoutRoomError);
        TextView roomErrorTitle = layoutRoomError.findViewById(R.id.emptyTitle);
        TextView roomErrorDesc = layoutRoomError.findViewById(R.id.emptyDesc);
        ImageView roomErrorIcon = layoutRoomError.findViewById(R.id.emptyIcon);
        MaterialButton btnRetryRooms = layoutRoomError.findViewById(R.id.btnEmptyAction);
        roomErrorTitle.setText(R.string.room_error_title);
        roomErrorDesc.setText(R.string.room_error_message);
        roomErrorIcon.setImageResource(R.drawable.ic_wifi_off);
        btnRetryRooms.setText(R.string.try_again_label);
        btnRetryRooms.setVisibility(View.VISIBLE);
        btnRetryRooms.setOnClickListener(v -> loadRooms());

        cardSelectionSummaryBar = findViewById(R.id.cardSelectionSummaryBar);
        tvSelectionSummaryCount = findViewById(R.id.tvSelectionSummaryCount);
        tvSelectionSummaryTotal = findViewById(R.id.tvSelectionSummaryTotal);
    }

    private void setupRecyclerView() {
        rvRooms.setLayoutManager(new LinearLayoutManager(this));
        allRooms = new ArrayList<>();
        filteredRooms = new ArrayList<>();
        adapter = new RoomAdapter(filteredRooms, this, false, selectedQuantities);
        rvRooms.setAdapter(adapter);

        findViewById(R.id.btnCartClear).setOnClickListener(v -> {
            selectedQuantities.clear();
            adapter.notifyQuantitiesChanged(null);
            updateSelectionSummaryBar();
        });
        findViewById(R.id.btnCartReserve).setOnClickListener(v -> navigateToBookingWithCart(true));
        findViewById(R.id.btnCartBook).setOnClickListener(v -> navigateToBookingWithCart(false));
        findViewById(R.id.layoutSelectionSummaryDetails).setOnClickListener(v -> showSelectedRoomsDialog());
    }

    private void showSelectedRoomsDialog() {
        List<Room> selected = new ArrayList<>();
        for (Room r : allRooms) {
            Integer qty = selectedQuantities.get(r.getId());
            if (qty == null) continue;
            for (int i = 0; i < qty; i++) selected.add(r);
        }
        if (selected.isEmpty()) return;
        SelectedRoomsDialog.show(this, selected, room -> {
            selectedQuantities.remove(room.getId());
            adapter.notifyQuantitiesChanged(room.getId());
            updateSelectionSummaryBar();
        });
    }

    private void loadRooms() {
        final int seq = ++roomsRequestSeq;
        setRoomsLoading(true);
        layoutRoomError.setVisibility(View.GONE);
        // The no-cache request path (opening, coming back, pull-to-refresh): "refresh" must never be answered from a
        // stale copy sitting in front of the server.
        RoomRepository.getInstance(this).refreshRoomsFresh(new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> result) {
                if (isFinishing() || isDestroyed() || seq != roomsRequestSeq) return;
                setRoomsLoading(false);
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                layoutRoomError.setVisibility(View.GONE);
                allRooms = result != null ? result : new ArrayList<>();
                roomsGate.accept(allRooms); // what is about to be drawn - see onVisiblePoll()
                refreshRoomTypeDropdownOptions();
                showLoadedRooms();
                updateSelectionSummaryBar();
                openSelectedRoomFromLanding();
                openSelectedRoomsSummaryFromLanding();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed() || seq != roomsRequestSeq) return;
                setRoomsLoading(false);
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                noResultsView.setVisibility(View.GONE);
                layoutRoomError.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * Shows {@code allRooms} the way the guest last asked to see them: re-applying their search, filters and sort
     * if they have ever used any (applyFilters() sets {@link #filtersApplied}), otherwise simply every room.
     * A reload used to always show every room, so after returning to this screen the list ignored filters the
     * guest had set while the filter controls still showed them.
     */
    private void showLoadedRooms() {
        if (filtersApplied) {
            applyFilters();
            return;
        }
        applyFilters();
    }

    /**
     * The 30s beat (started/stopped with the screen by BaseNavigationActivity): quietly re-fetch the rooms - for the
     * dates the guest searched, or the default window - so a room that just sold out or came back appears without
     * a pull. No loading overlay, no error message if it fails, and nothing is redrawn unless the rooms really
     * changed; when they did, the guest's own search/filters/sort are re-applied and their Reserve/Book cart is
     * trimmed to what is still free. The no-cache request path is used, as on the landing page.
     */
    @Override
    protected void onVisiblePoll() {
        super.onVisiblePoll();
        // A search / reload that starts after this beat (the guest changed the dates) must not be overwritten by this older answer.
        final int seq = roomsRequestSeq;
        RoomRepository.getInstance(this).refreshRoomsFresh(checkInDate, checkOutDate, new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> result) {
                if (isFinishing() || isDestroyed() || result == null || seq != roomsRequestSeq) return;
                if (!roomsGate.accept(result)) return;
                allRooms = result;
                refreshRoomTypeDropdownOptions();
                showLoadedRooms();
                List<Room> bookable = new ArrayList<>();
                for (Room room : allRooms) {
                    if (room != null && room.isAvailable()) bookable.add(room);
                }
                CartSelectionReconciler.reconcile(selectedQuantities, bookable);
                updateSelectionSummaryBar();
            }

            @Override
            public void onError(String message) {
                // silent by design - the next beat (or any real action) tries again
            }
        });
    }

    private void openSelectedRoomFromLanding() {
        // A multi-room cart carried over from landing.xml shows via the
        // selection summary bar instead of auto-opening a single dialog -
        // OPEN_ROOM_DETAILS is only ever true for a genuine single-room hand-off.
        if (!shouldOpenSelectedRoom || selectedRoomIdFromLanding == null || selectedRoomIdFromLanding.isEmpty()) {
            return;
        }

        for (int index = 0; index < filteredRooms.size(); index++) {
            Room room = filteredRooms.get(index);
            if (selectedRoomIdFromLanding.equals(room.getId())) {
                shouldOpenSelectedRoom = false;
                if (appBarLayout != null) {
                    appBarLayout.setExpanded(false, true);
                }
                int selectedIndex = index;
                String action = selectedRoomActionFromLanding != null
                        ? selectedRoomActionFromLanding : PendingRoomSelection.ACTION_VIEW;
                rvRooms.postDelayed(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    rvRooms.smoothScrollToPosition(selectedIndex);
                    showRoomDetailsDialog(room, action);
                }, 450L);
                return;
            }
        }

        shouldOpenSelectedRoom = false;
        Toast.makeText(this, "Selected room is no longer available. Showing current rooms instead.", Toast.LENGTH_LONG).show();
    }

    /**
     * "The selected rooms must not need to be selected again" - a multi-room
     * cart handed over from landing.xml (ROOM_IDS with 2+ ids) opens the same
     * View Details popup the sticky cart bar itself uses, so the guest lands
     * here already seeing every selected room's full details and the
     * combined total instead of having to tap into it manually.
     */
    private void openSelectedRoomsSummaryFromLanding() {
        if (!shouldOpenSelectedRoomsSummary) return;
        shouldOpenSelectedRoomsSummary = false;
        if (appBarLayout != null) {
            appBarLayout.setExpanded(false, true);
        }
        rvRooms.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) showSelectedRoomsDialog();
        }, 450L);
    }

    private void setupDatePickers() {
        // Check-out stays inaccessible until a valid check-in is chosen.
        etCheckIn.setOnClickListener(v -> showCheckInPicker());
        etCheckOut.setOnClickListener(v -> showCheckOutPicker());
        // The x at the end of a field clears that date (clearing check-in clears check-out too: it needs a check-in).
        checkInLayout.setEndIconOnClickListener(v -> {
            checkInDate = null;
            checkOutDate = null;
            etCheckIn.setText("");
            etCheckOut.setText("");
            updateDateSummary();
        });
        checkOutLayout.setEndIconOnClickListener(v -> {
            checkOutDate = null;
            etCheckOut.setText("");
            updateDateSummary();
        });

        btnSearchAvailability.setOnClickListener(v -> {
            if (!(checkInDate != null && checkOutDate != null && checkOutDate.after(checkInDate))) {
                // Normally unreachable: the button is disabled until both dates are valid. Kept for accessibility tooling.
                if (appBarLayout != null) appBarLayout.setExpanded(true, true);
                if (checkInDate == null) setFieldError(checkInLayout, getString(R.string.error_pick_checkin));
                if (checkOutDate == null) setFieldError(checkOutLayout, getString(R.string.error_pick_checkout));
                return;
            }
            checkInLayout.setError(null);
            checkOutLayout.setError(null);
            refreshHelperTexts();
            performSearchWithLoading();
        });
        refreshHelperTexts();
        updateDateSummary();
    }

    /** The error goes in the field's own helper/error line (one line, ellipsized), never beside the value. */
    private void setFieldError(TextInputLayout layout, String message) {
        layout.setError(message);
        oneLine((TextView) layout.findViewById(com.google.android.material.R.id.textinput_error));
    }

    private static void oneLine(@androidx.annotation.Nullable TextView view) {
        if (view == null) return;
        view.setMaxLines(1);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }

    /**
     * One line of helper text under each field: the earliest check-in (from the 48-hour rule), and for check-out either the one-night rule
     * or - while there is no check-in - why it cannot be picked yet. This is also where a disabled View availability says why.
     */
    private void refreshHelperTexts() {
        String earliest = CheckInWindow.earliest().format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US));
        checkInLayout.setHelperText(getString(R.string.helper_earliest_format, earliest));
        oneLine((TextView) checkInLayout.findViewById(com.google.android.material.R.id.textinput_helper_text));
        checkOutLayout.setHelperText(getString(checkInDate == null ? R.string.helper_pick_checkin_first : R.string.helper_checkout_after_checkin));
        oneLine((TextView) checkOutLayout.findViewById(com.google.android.material.R.id.textinput_helper_text));
    }

    /** Sort / type / capacity / price changes re-filter the rooms already loaded: no network call, no spinner. */
    private void applyControlChange() {
        applyFilters();
        if (rvRooms != null) rvRooms.scrollToPosition(0);
    }

    /** Bumped by every room fetch so a slow, older response can never overwrite a newer one. */
    private int roomsRequestSeq;

    private void performSearchWithLoading() {
        setRoomsLoading(true);
        final int seq = ++roomsRequestSeq;

        // Re-fetch from the backend with the guest's actual dates first -
        // availability is date-range-aware server-side, so filtering the
        // stale default-window snapshot client-side would show the wrong
        // Fully Booked state for any dates other than the initial load's.
        RoomRepository.getInstance(this).refreshRoomsFresh(checkInDate, checkOutDate, new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> result) {
                if (isFinishing() || isDestroyed() || seq != roomsRequestSeq) return;
                allRooms = result != null ? result : new ArrayList<>();
                roomsGate.accept(allRooms); // what is about to be drawn - see onVisiblePoll()
                refreshRoomTypeDropdownOptions();
                finishSearch();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed() || seq != roomsRequestSeq) return;
                Toast.makeText(RoomBrowsingActivity.this, R.string.room_load_error_toast, Toast.LENGTH_LONG).show();
                finishSearch();
            }
        });
    }

    private void finishSearch() {
        applyFilters();
        setRoomsLoading(false);
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);

        // Auto-direct to results by collapsing the search section
        if (appBarLayout != null) {
            appBarLayout.setExpanded(false, true);
        }

        // Scroll to the first room in the list as requested
        if (rvRooms != null && adapter.getItemCount() > 0) {
            rvRooms.postDelayed(() -> {
                rvRooms.smoothScrollToPosition(0);
            }, 400); // Slightly longer delay to ensure layout is ready after expansion change
        }
    }

    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;

    private boolean roomsLoading;

    /** Search Availability stays disabled until both dates are valid, and while a search is running. */
    private void updateSearchButton() {
        if (btnSearchAvailability == null) return;
        boolean valid = checkInDate != null && checkOutDate != null && checkOutDate.after(checkInDate);
        btnSearchAvailability.setEnabled(valid && !roomsLoading);
        btnSearchAvailability.setText(roomsLoading ? R.string.searching_rooms_button : R.string.search_availability);
        updateResetVisibility();
    }

    /** "Reset" shows only when something is set: a date, a filter or a sort. */
    private void updateResetVisibility() {
        if (btnResetSearch == null) return;
        boolean something = checkInDate != null || checkOutDate != null || activeFilterCount() > 0 || !"Default".equals(currentSortMode);
        btnResetSearch.setVisibility(something ? View.VISIBLE : View.GONE);
    }

    /** Skeleton cards while the room list loads; also puts the Search button in its loading state. */
    private void setRoomsLoading(boolean loading) {
        roomsLoading = loading;
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
            View skeleton = loadingOverlay.findViewById(R.id.skeletonList);
            if (skeleton != null) {
                skeleton.clearAnimation();
                if (loading) skeleton.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.skeleton_pulse));
            }
        }
        updateSearchButton();
    }

    /** "2 nights" under the fields and "Oct 12 - Oct 14 - 2 nights" in the pinned bar - only when both dates are set. */
    private void updateDateSummary() {
        boolean both = checkInDate != null && checkOutDate != null && checkOutDate.after(checkInDate);
        checkInLayout.setEndIconVisible(checkInDate != null);
        checkOutLayout.setEndIconVisible(checkOutDate != null);
        refreshHelperTexts();
        updateSearchButton();
        if (tvNightsSummary == null || tvPinnedDates == null) return;
        if (!both) {
            tvNightsSummary.setVisibility(View.GONE);
            tvPinnedDates.setVisibility(View.GONE);
            return;
        }
        int nights = (int) StayDateCalculator.nightsBetween(checkInDate, checkOutDate);
        String nightsText = getResources().getQuantityString(R.plurals.nights_lower, nights, nights);
        tvNightsSummary.setText(nightsText);
        tvNightsSummary.setVisibility(View.VISIBLE);
        String pinned = getString(R.string.pinned_dates_format,
                shortDateFormat.format(checkInDate.getTime()), shortDateFormat.format(checkOutDate.getTime()), nightsText);
        tvPinnedDates.setText(pinned);
        tvPinnedDates.setContentDescription(getString(R.string.pinned_dates_cd, pinned));
        tvPinnedDates.setVisibility(View.VISIBLE);
    }

    /** Thicker red outline on the date field whose picker is open; restored when the picker closes. */
    private void highlightWhileOpen(com.google.android.material.textfield.TextInputLayout layout, MaterialDatePicker<Long> picker) {
        final int width = layout.getBoxStrokeWidth();
        final int color = layout.getBoxStrokeColor();
        layout.setBoxStrokeWidth(width * 2);
        layout.setBoxStrokeColor(getColor(R.color.velocity_red_primary));
        picker.addOnDismissListener(d -> {
            layout.setBoxStrokeWidth(width);
            layout.setBoxStrokeColor(color);
        });
    }

    private boolean isPickerShowing() {
        return getSupportFragmentManager().findFragmentByTag(TAG_CHECK_IN_PICKER) != null
                || getSupportFragmentManager().findFragmentByTag(TAG_CHECK_OUT_PICKER) != null;
    }

    private void showCheckInPicker() {
        if (isPickerShowing()) return; // a rapid second tap must not stack a second picker
        // Earliest check-in: hotel-local today + 2 days, no upper limit (see CheckInWindow)
        long earliestCheckInUtc = CheckInWindow.earliestUtcMillis();
        com.google.android.material.datepicker.CalendarConstraints constraints =
                new com.google.android.material.datepicker.CalendarConstraints.Builder()
                        .setStart(earliestCheckInUtc)
                        .setValidator(com.google.android.material.datepicker.DateValidatorPointForward.from(earliestCheckInUtc))
                        .build();

        long initialSelection = checkInDate != null
                ? Math.max(toUtcMidnight(checkInDate), earliestCheckInUtc)
                : earliestCheckInUtc;

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.title_select_checkin))
                .setCalendarConstraints(constraints)
                .setSelection(initialSelection)
                .build();

        attachCheckInListeners(picker);
        picker.show(getSupportFragmentManager(), TAG_CHECK_IN_PICKER);
    }

    private static final String TAG_CHECK_IN_PICKER = "CHECK_IN_PICKER";
    private static final String TAG_CHECK_OUT_PICKER = "CHECK_OUT_PICKER";

    /** What choosing a check-in date does. Listeners are not saved with a picker fragment, so this also runs for one restored after recreation. */
    private void attachCheckInListeners(MaterialDatePicker<Long> picker) {
        picker.addOnPositiveButtonClickListener(selection -> {
            checkInDate = fromUtcMidnight(selection);
            etCheckIn.setText(fieldDateFormat.format(checkInDate.getTime()));
            checkInLayout.setError(null);
            clearFieldFocus();

            // A previously chosen check-out that is no longer at least one
            // night after the new check-in must be re-picked
            if (checkOutDate != null && !checkOutDate.after(checkInDate)) {
                checkOutDate = null;
                etCheckOut.setText("");
                checkOutLayout.setError(null);
            }

            // Check-out becomes selectable now that check-in is valid, but the
            // guest picks it themselves rather than being forced into it here.
            checkOutLayout.setEnabled(true);
            updateDateSummary();
        });
        highlightWhileOpen(checkInLayout, picker);
    }

    /** What choosing a check-out date does (see attachCheckInListeners). */
    private void attachCheckOutListeners(MaterialDatePicker<Long> picker) {
        picker.addOnPositiveButtonClickListener(selection -> {
            checkOutDate = fromUtcMidnight(selection);
            etCheckOut.setText(fieldDateFormat.format(checkOutDate.getTime()));
            checkOutLayout.setError(null);
            clearFieldFocus();
            updateDateSummary();
        });
        highlightWhileOpen(checkOutLayout, picker);
    }

    /**
     * A date picker that was open when the screen was recreated (dark-mode toggle, system kill) comes back from the
     * FragmentManager with its dialog but without its click listeners: re-attach them, so choosing a date still works.
     */
    @SuppressWarnings("unchecked")
    private void reattachRestoredPickerListeners() {
        androidx.fragment.app.Fragment checkIn = getSupportFragmentManager().findFragmentByTag(TAG_CHECK_IN_PICKER);
        if (checkIn instanceof MaterialDatePicker) attachCheckInListeners((MaterialDatePicker<Long>) checkIn);
        androidx.fragment.app.Fragment checkOut = getSupportFragmentManager().findFragmentByTag(TAG_CHECK_OUT_PICKER);
        if (checkOut instanceof MaterialDatePicker) attachCheckOutListeners((MaterialDatePicker<Long>) checkOut);
    }

    private void clearFieldFocus() {
        View focus = getCurrentFocus();
        if (focus != null) focus.clearFocus();
    }

    private void showCheckOutPicker() {
        if (checkInDate == null) {
            // Check-out needs a check-in first: say so on the check-in field.
            setFieldError(checkInLayout, getString(R.string.error_select_checkin_first));
            return;
        }
        if (isPickerShowing()) return; // a rapid second tap must not stack a second picker

        // Check-out can only be picked from the day after check-in onward
        long earliestCheckOutUtc = toUtcMidnight(checkInDate) + ONE_DAY_MS;
        com.google.android.material.datepicker.CalendarConstraints constraints =
                new com.google.android.material.datepicker.CalendarConstraints.Builder()
                        .setValidator(com.google.android.material.datepicker.DateValidatorPointForward.from(earliestCheckOutUtc))
                        .build();

        long initialSelection = checkOutDate != null
                ? Math.max(toUtcMidnight(checkOutDate), earliestCheckOutUtc)
                : earliestCheckOutUtc;

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.title_select_checkout))
                .setCalendarConstraints(constraints)
                .setSelection(initialSelection)
                .build();

        attachCheckOutListeners(picker);
        picker.show(getSupportFragmentManager(), TAG_CHECK_OUT_PICKER);
    }

    // MaterialDatePicker works in UTC-midnight millis while the rest of the
    // screen uses local Calendars, so convert by calendar fields to avoid
    // day-shift errors across timezones.
    private long toUtcMidnight(Calendar local) {
        Calendar utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    private Calendar fromUtcMidnight(long utcMillis) {
        Calendar utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
        return local;
    }

    private void setupSearchAndFilters() {
        // We'll let the Search Availability button be the primary trigger for searching with loading.
        // However, we can keep the TextWatcher for clearing results if needed, or just let it be.
        // The user specifically wants the loading indicator on button click.

        setupSortButton();
        updateFilterBadge();

        btnFilters.setOnClickListener(v -> showFilterDialog());
        btnResetSearch.setOnClickListener(v -> {
            clearAllFilters();   // dates, sort, type, status, guests, price
            loadRooms();         // and back to the default room list
        });
        findViewById(R.id.btnClearActiveFilters).setOnClickListener(v -> clearFiltersKeepDates());
        if (tvPinnedDates != null) {
            tvPinnedDates.setOnClickListener(v -> {
                if (appBarLayout != null) appBarLayout.setExpanded(true, true);
                if (rvRooms != null) rvRooms.smoothScrollToPosition(0);
            });
        }
    }

    private void setupSortButton() {
        btnSort.setOnClickListener(v -> {
            androidx.appcompat.widget.PopupMenu menu = new androidx.appcompat.widget.PopupMenu(this, btnSort);
            String[] sortOptions = getResources().getStringArray(R.array.sort_options);
            for (int i = 0; i < sortOptions.length; i++) menu.getMenu().add(0, i, i, sortOptions[i]);
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1: currentSortMode = "PriceLow"; break;
                    case 2: currentSortMode = "PriceHigh"; break;
                    case 3: currentSortMode = "NameAZ"; break;
                    default: currentSortMode = "Default"; break;
                }
                applyControlChange();
                return true;
            });
            menu.show();
        });
        updateSortButtonLabel();
    }

    private void updateSortButtonLabel() {
        if (btnSort == null) return;
        String[] sortOptions = getResources().getStringArray(R.array.sort_options);
        int index = "PriceLow".equals(currentSortMode) ? 1 : "PriceHigh".equals(currentSortMode) ? 2 : "NameAZ".equals(currentSortMode) ? 3 : 0;
        btnSort.setText(getString(R.string.sort_prefix_format, sortOptions[index]));
        btnSort.setContentDescription(getString(R.string.sorting_by_label, sortOptions[index]));
    }

    /** Type / availability / guests / price that differ from "show everything" - drives the "Filters . n" count and the chips. */
    private int activeFilterCount() {
        int count = 0;
        if (!"All".equals(selectedRoomTypeFilter)) count++;
        if (!"All".equals(selectedStatusFilter)) count++;
        if (selectedCapacity != 0) count++;
        if (selectedMinPrice != RoomFilter.UNSET || selectedMaxPrice != RoomFilter.UNSET) count++;
        return count;
    }

    private void updateFilterBadge() {
        if (btnFilters == null) return;
        int count = activeFilterCount();
        btnFilters.setText(count == 0 ? getString(R.string.filter_label) : getString(R.string.filters_count_format, count));
        updateActiveFilterChips();
        updateResetVisibility();
    }

    private static String money(float value) {
        return String.format(Locale.getDefault(), "%,.0f", value);
    }

    private String guestsText(int bucket) {
        return getString(R.string.chip_guests_format, getString(bucket == 1 ? R.string.guests_1_2_short : bucket == 2 ? R.string.guests_3_4_short : R.string.guests_5_plus_short));
    }

    private String priceChipText() {
        if (selectedMinPrice != RoomFilter.UNSET && selectedMaxPrice != RoomFilter.UNSET) {
            return getString(R.string.price_range_format, money(selectedMinPrice), money(selectedMaxPrice));
        }
        if (selectedMaxPrice != RoomFilter.UNSET) return getString(R.string.chip_up_to_price_format, money(selectedMaxPrice));
        return getString(R.string.chip_from_price_format, money(selectedMinPrice));
    }

    /** One removable chip per active filter ("Deluxe x", "3-4 guests x", "Up to P15,000 x") under the results row, plus Clear all. */
    private void updateActiveFilterChips() {
        if (chipsActiveFilters == null || layoutActiveFilters == null) return;
        chipsActiveFilters.removeAllViews();
        if (!"All".equals(selectedRoomTypeFilter)) {
            addFilterChip(selectedRoomTypeFilter, () -> { selectedRoomTypeFilter = "All"; applyControlChange(); });
        }
        if (selectedCapacity != 0) addFilterChip(guestsText(selectedCapacity), () -> { selectedCapacity = 0; applyControlChange(); });
        if (selectedMinPrice != RoomFilter.UNSET || selectedMaxPrice != RoomFilter.UNSET) {
            addFilterChip(priceChipText(), () -> { selectedMinPrice = selectedMaxPrice = RoomFilter.UNSET; applyControlChange(); });
        }
        if (!"All".equals(selectedStatusFilter)) {
            String[] labels = getResources().getStringArray(R.array.status_filter_options);
            int i = "Available".equals(selectedStatusFilter) ? 1 : "Limited".equals(selectedStatusFilter) ? 2 : 3;
            addFilterChip(labels[i], () -> { selectedStatusFilter = "All"; applyControlChange(); });
        }
        layoutActiveFilters.setVisibility(chipsActiveFilters.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void addFilterChip(String text, Runnable onRemove) {
        com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
        chip.setText(text);
        chip.setCloseIconVisible(true);
        chip.setCloseIconContentDescription(getString(R.string.cd_remove_filter_format, text));
        chip.setChipBackgroundColorResource(R.color.velocity_red_soft);
        chip.setChipStrokeColorResource(R.color.velocity_red_primary);
        chip.setChipStrokeWidth(getResources().getDisplayMetrics().density);
        chip.setTextColor(getColor(R.color.velocity_action_text));
        chip.setCloseIconTintResource(R.color.velocity_action_text);
        chip.setEnsureMinTouchTargetSize(true);
        chip.setOnCloseIconClickListener(v -> onRemove.run());
        chipsActiveFilters.addView(chip);
    }

    /** Distinct room types in the loaded list (the admin can add / rename types at any time), "All" first. */
    private void refreshRoomTypeDropdownOptions() {
        java.util.TreeSet<String> distinctTypes = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Room r : allRooms) {
            if (r != null && r.getType() != null && !r.getType().trim().isEmpty()) {
                distinctTypes.add(r.getType().trim());
            }
        }
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.all_categories));
        options.addAll(distinctTypes);
        roomTypeOptions = options;
        if (!"All".equals(selectedRoomTypeFilter) && !options.contains(selectedRoomTypeFilter)) {
            selectedRoomTypeFilter = "All"; // the selected type no longer exists
            updateFilterBadge();
        }
    }

    /**
     * Filter rooms: an opaque bottom sheet. Choices are held as pending values and only written on "Show N rooms" (the button carries
     * the live count of rooms they would leave). Room type and Availability are two different filters, each a labelled dropdown.
     */
    @android.annotation.SuppressLint("InflateParams") // a dialog has no parent to inflate against
    private void showFilterDialog() {
        if (filterSheet != null && filterSheet.isShowing()) return; // a rapid second tap must not open a second sheet
        if (isFinishing() || isDestroyed()) return;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_room_filters, null);
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        dialog.setContentView(dialogView);
        filterSheet = dialog;
        dialog.setOnDismissListener(d -> { if (filterSheet == dialog) filterSheet = null; });
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);

        com.google.android.material.button.MaterialButtonToggleGroup capacityGroup = dialogView.findViewById(R.id.capacityChipGroup);
        com.google.android.material.slider.RangeSlider priceSlider = dialogView.findViewById(R.id.priceSlider);
        TextView tvPriceLabel = dialogView.findViewById(R.id.tvPriceLabel);
        MaterialButton btnApply = dialogView.findViewById(R.id.btnApplyFilters);
        View btnClose = dialogView.findViewById(R.id.btnCloseFilters);
        MaterialButton btnClear = dialogView.findViewById(R.id.btnClearAll);
        AutoCompleteTextView typeDropdown = dialogView.findViewById(R.id.dropdownRoomType);
        AutoCompleteTextView statusDropdown = dialogView.findViewById(R.id.dropdownStatus);

        final String[] pendingType = {selectedRoomTypeFilter};
        final String[] pendingStatus = {selectedStatusFilter};
        final int[] pendingCapacity = {selectedCapacity};
        final String allTypes = getString(R.string.all_categories);
        final List<String> typeOptions = roomTypeOptions.isEmpty() ? java.util.Collections.singletonList(allTypes) : roomTypeOptions;
        typeDropdown.setAdapter(new NonFilteringArrayAdapter(this, typeOptions));
        typeDropdown.setText("All".equals(pendingType[0]) ? allTypes : pendingType[0], false);
        final String[] statusOptions = getResources().getStringArray(R.array.status_filter_options);
        final String[] statusKeys = {"All", "Available", "Limited", "Unavailable"};
        statusDropdown.setAdapter(new NonFilteringArrayAdapter(this, statusOptions));
        statusDropdown.setText(statusOptions[0], false);
        for (int i = 0; i < statusKeys.length; i++) {
            if (statusKeys[i].equals(pendingStatus[0])) statusDropdown.setText(statusOptions[i], false);
        }

        // The slider's bounds are the real lowest and highest room prices.
        final float[] bounds = RoomFilter.priceBounds(allRooms);
        priceSlider.setValueFrom(bounds[0]);
        priceSlider.setValueTo(bounds[1]);
        float lo = selectedMinPrice == RoomFilter.UNSET ? bounds[0] : Math.max(bounds[0], Math.min(selectedMinPrice, bounds[1]));
        float hi = selectedMaxPrice == RoomFilter.UNSET ? bounds[1] : Math.max(bounds[0], Math.min(selectedMaxPrice, bounds[1]));
        if (hi < lo) hi = lo;
        priceSlider.setValues(lo, hi);

        capacityGroup.check(selectedCapacity == 1 ? R.id.chip12 : selectedCapacity == 2 ? R.id.chip34 : selectedCapacity == 3 ? R.id.chip5plus : R.id.chipAnyCap);

        final Runnable refresh = () -> {
            List<Float> v = priceSlider.getValues();
            float min = v.get(0);
            float max = v.get(v.size() - 1);
            tvPriceLabel.setText(getString(R.string.price_range_format, money(min), money(max)));
            int n = RoomFilter.count(allRooms, pendingType[0], pendingCapacity[0],
                    min <= bounds[0] ? RoomFilter.UNSET : min, max >= bounds[1] ? RoomFilter.UNSET : max, pendingStatus[0]);
            btnApply.setText(n == 0 ? getString(R.string.show_no_rooms) : getResources().getQuantityString(R.plurals.show_rooms, n, n));
            btnApply.setEnabled(n > 0);
        };
        typeDropdown.setOnItemClickListener((parent, view, position, id) -> {
            pendingType[0] = position == 0 ? "All" : typeOptions.get(position);
            refresh.run();
        });
        statusDropdown.setOnItemClickListener((parent, view, position, id) -> {
            pendingStatus[0] = position < statusKeys.length ? statusKeys[position] : "All";
            refresh.run();
        });
        capacityGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            pendingCapacity[0] = checkedId == R.id.chip12 ? 1 : checkedId == R.id.chip34 ? 2 : checkedId == R.id.chip5plus ? 3 : 0;
            refresh.run();
        });
        priceSlider.addOnChangeListener((slider, value, fromUser) -> refresh.run());
        refresh.run();

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Clear = back to "show everything" right away (dates and sort are left alone; Reset on the search card clears those too).
        btnClear.setOnClickListener(v -> {
            selectedRoomTypeFilter = "All";
            selectedStatusFilter = "All";
            selectedCapacity = 0;
            selectedMinPrice = selectedMaxPrice = RoomFilter.UNSET;
            dialog.dismiss();
            applyControlChange();
        });

        btnApply.setOnClickListener(v -> {
            List<Float> values = priceSlider.getValues();
            float min = values.get(0);
            float max = values.get(values.size() - 1);
            selectedRoomTypeFilter = pendingType[0];
            selectedStatusFilter = pendingStatus[0];
            selectedCapacity = pendingCapacity[0];
            selectedMinPrice = min <= bounds[0] ? RoomFilter.UNSET : min;
            selectedMaxPrice = max >= bounds[1] ? RoomFilter.UNSET : max;
            dialog.dismiss();
            applyControlChange();
        });

        SafeDialogs.show(dialog);
    }

    @Override
    protected void onDestroy() {
        // A sheet or picker open while the screen is torn down (rotation, back) must not leak its window.
        if (filterSheet != null) {
            try {
                filterSheet.dismiss();
            } catch (RuntimeException ignored) {
                // already gone
            }
            filterSheet = null;
        }
        super.onDestroy();
    }

    /** Back to "show every room" for type / availability / guests / price, keeping the dates and the sort (the chips' Clear all). */
    private void clearFiltersKeepDates() {
        selectedRoomTypeFilter = "All";
        selectedStatusFilter = "All";
        selectedCapacity = 0;
        selectedMinPrice = selectedMaxPrice = RoomFilter.UNSET;
        applyControlChange();
    }

    private void clearAllFilters() {
        etCheckIn.setText("");
        etCheckOut.setText("");
        checkInDate = null;
        checkOutDate = null;
        checkInLayout.setError(null);
        checkOutLayout.setError(null);
        updateDateSummary();
        selectedRoomTypeFilter = "All";
        selectedStatusFilter = "All";
        selectedCapacity = 0;
        selectedMinPrice = selectedMaxPrice = RoomFilter.UNSET;
        currentSortMode = "Default";
        applyFilters();
    }

    private void applyFilters() {
        filtersApplied = true;
        List<Room> rooms = allRooms == null ? new ArrayList<>() : allRooms;
        List<Room> newFilteredList = new ArrayList<>();
        for (Room room : rooms) {
            if (RoomFilter.matches(room, selectedRoomTypeFilter, selectedCapacity, selectedMinPrice, selectedMaxPrice, "All")) {
                newFilteredList.add(room);
            }
        }

        boolean isDateSearch = checkInDate != null && checkOutDate != null;
        int shown;

        if (!isDateSearch) {
            // Pre-search browsing (no dates chosen yet): a flat list; the availability filter applies directly.
            newFilteredList.removeIf(room -> !RoomFilter.statusMatches(selectedStatusFilter, room));
            sortRoomsList(newFilteredList);
            adapter.updateList(newFilteredList);
            filteredRooms = newFilteredList;
            shown = filteredRooms.size();
        } else {
            sortRoomsList(newFilteredList);
            filteredRooms = newFilteredList;

            List<Room> trueAvailable = new ArrayList<>();
            List<Room> trueUnavailable = new ArrayList<>();
            for (Room r : newFilteredList) {
                (r.isAvailable() ? trueAvailable : trueUnavailable).add(r);
            }

            // The availability filter narrows which section(s) are shown, but must not change whether the "no rooms available"
            // verdict is triggered - that verdict is about real availability, not the guest's own filter choice.
            List<Room> displayAvailable;
            if ("Unavailable".equals(selectedStatusFilter)) {
                displayAvailable = new ArrayList<>();
            } else if ("Limited".equals(selectedStatusFilter)) {
                displayAvailable = new ArrayList<>();
                for (Room r : trueAvailable) {
                    if (RoomAvailabilityStatus.of(r) == RoomAvailabilityStatus.LIMITED) displayAvailable.add(r);
                }
            } else {
                displayAvailable = trueAvailable;
            }
            List<Room> displayUnavailable = ("Available".equals(selectedStatusFilter) || "Limited".equals(selectedStatusFilter))
                    ? new ArrayList<>() : trueUnavailable;

            String occupiedRangeText = getString(R.string.occupied_from_until_format,
                    dateFormat.format(checkInDate.getTime()), dateFormat.format(checkOutDate.getTime()));
            String noAvailableMessage = trueAvailable.isEmpty() && !displayUnavailable.isEmpty() ? getString(R.string.no_rooms_available_for_dates) : null;

            adapter.updateSectionedResults(
                    displayAvailable,
                    displayUnavailable,
                    getString(R.string.available_rooms_header_format, displayAvailable.size()),
                    getString(R.string.unavailable_rooms_header_format, displayUnavailable.size()),
                    occupiedRangeText,
                    noAvailableMessage
            );
            shown = displayAvailable.size() + displayUnavailable.size();
        }

        if (tvResultsCount != null) {
            tvResultsCount.setText(getResources().getQuantityString(R.plurals.rooms_found, shown, shown));
        }
        showEmptyStateIfNeeded(shown == 0);
        updateSortButtonLabel();
        updateFilterBadge();
        updateSelectionSummaryBar();
    }

    /**
     * Nothing to list: say why. Dates only -> "No rooms for these dates" + Change dates; filters only -> "No rooms match your filters" +
     * Clear filters; both -> both buttons; neither (no rooms at all) -> a plain "No rooms available" + Refresh. The view sits in the normal
     * layout flow below the results bar.
     */
    private void showEmptyStateIfNeeded(boolean empty) {
        noResultsView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) return;
        boolean dates = checkInDate != null && checkOutDate != null;
        boolean filters = activeFilterCount() > 0;
        int title = dates && filters ? R.string.rooms_empty_both_title : dates ? R.string.rooms_empty_dates_title
                : filters ? R.string.rooms_empty_filters_title : R.string.no_matching_rooms;
        int desc = dates && filters ? R.string.rooms_empty_both_desc : dates ? R.string.rooms_empty_dates_desc
                : filters ? R.string.rooms_empty_filters_desc : R.string.no_matching_rooms_desc;
        if (emptyStateTitle != null) emptyStateTitle.setText(title);
        if (emptyStateDesc != null) emptyStateDesc.setText(desc);
        if (btnEmptyStateAction == null) return;
        btnEmptyStateAction.setVisibility(View.VISIBLE);
        btnEmptyStateAction.setIcon(null);
        btnEmptyStateAction2.setVisibility(View.GONE);
        if (filters) {
            btnEmptyStateAction.setText(R.string.notif_clear_filters);
            btnEmptyStateAction.setOnClickListener(v -> clearFiltersKeepDates());
            if (dates) {
                btnEmptyStateAction2.setText(R.string.change_dates_label);
                btnEmptyStateAction2.setVisibility(View.VISIBLE);
                btnEmptyStateAction2.setOnClickListener(v -> changeDates());
            }
        } else if (dates) {
            btnEmptyStateAction.setText(R.string.change_dates_label);
            btnEmptyStateAction.setOnClickListener(v -> changeDates());
        } else {
            btnEmptyStateAction.setText(R.string.refresh_label);
            btnEmptyStateAction.setOnClickListener(v -> loadRooms());
        }
    }

    /** Back up to the search card to pick other dates. */
    private void changeDates() {
        if (appBarLayout != null) appBarLayout.setExpanded(true, true);
        if (rvRooms != null) rvRooms.scrollToPosition(0);
    }

    private void sortRoomsList(List<Room> roomsToSort) {
        switch (currentSortMode) {
            case "PriceLow":
                Collections.sort(roomsToSort, (r1, r2) -> Double.compare(r1.getPricePerNight(), r2.getPricePerNight()));
                break;
            case "PriceHigh":
                Collections.sort(roomsToSort, (r1, r2) -> Double.compare(r2.getPricePerNight(), r1.getPricePerNight()));
                break;
            case "NameAZ":
                Collections.sort(roomsToSort, (r1, r2) -> (r1.getName() == null ? "" : r1.getName()).compareToIgnoreCase(r2.getName() == null ? "" : r2.getName()));
                break;
            case "Default":
            default:
                // Keep loaded order (mock sequence)
                break;
        }
    }

    @Override
    public void onRoomClick(Room room) {
        showRoomDetailsDialog(room, PendingRoomSelection.ACTION_VIEW);
    }

    @Override
    public void onViewDetailsClick(Room room) {
        showRoomDetailsDialog(room, PendingRoomSelection.ACTION_VIEW);
    }

    @Override
    public void onBookNowClick(Room room) {
        // Tapping Book Now from the room list opens the details dialog first
        // (with only Book Now visible) - the guest confirms there before the
        // screen actually navigates to the booking form.
        showRoomDetailsDialog(room, PendingRoomSelection.ACTION_BOOK);
    }

    @Override
    public void onReserveNowClick(Room room) {
        showRoomDetailsDialog(room, PendingRoomSelection.ACTION_RESERVE);
    }

    @Override
    public void onRoomSelectionChanged() {
        updateSelectionSummaryBar();
    }

    private void updateSelectionSummaryBar() {
        if (cardSelectionSummaryBar == null) return;
        // Shown as soon as at least one room is selected - this is the
        // guest's "Selected Rooms / Proceed" summary, not just a multi-room
        // convenience, so it must not require a second selection to appear.
        if (selectedQuantities.isEmpty()) {
            cardSelectionSummaryBar.setVisibility(View.GONE);
            return;
        }
        int combinedCapacity = 0;
        double totalPerNight = 0;
        int matched = 0;
        for (Room r : allRooms) {
            Integer qty = selectedQuantities.get(r.getId());
            if (qty != null) {
                combinedCapacity += r.getCapacity() * qty;
                totalPerNight += r.getPricePerNight() * qty;
                matched += qty;
            }
        }
        if (matched == 0) {
            cardSelectionSummaryBar.setVisibility(View.GONE);
            return;
        }
        cardSelectionSummaryBar.setVisibility(View.VISIBLE);
        tvSelectionSummaryCount.setText(getResources().getQuantityString(
                R.plurals.cart_summary_capacity_format, matched, matched, combinedCapacity));
        tvSelectionSummaryTotal.setText(getString(R.string.cart_summary_total_format, totalPerNight));
    }

    /**
     * Hands the selected room (plus any others already staged in the
     * multi-room cart, if this room is part of it) straight into
     * BookingWizardActivity's Step 2 (Room Selection) via PendingWizardRooms - no more
     * round-tripping through BookingAndReservationActivity's now-vestigial
     * inline form, which never actually fed the wizard.
     */
    private void navigateToBooking(Room room, boolean reservation) {
        if (isNavigatingToBookingFlow) return;
        isNavigatingToBookingFlow = true;
        List<Room> roomsToCarry = resolveCartRoomsIncluding(room);
        PendingWizardRooms.set(roomsToCarry);
        PendingWizardRooms.setDates(checkInDate, checkOutDate);
        startActivity(StartingTransactionActivity.newIntent(this,
                reservation ? BookingWizardState.Mode.RESERVATION : BookingWizardState.Mode.BOOKING));
    }

    /** If the tapped room is part of the active multi-room cart, carry the whole cart (each room type expanded by its quantity); otherwise just this one room. */
    private List<Room> resolveCartRoomsIncluding(Room room) {
        if (selectedQuantities.isEmpty() || !selectedQuantities.containsKey(room.getId())) {
            return java.util.Collections.singletonList(room);
        }
        List<Room> cartRooms = new ArrayList<>();
        for (Room r : allRooms) {
            Integer qty = selectedQuantities.get(r.getId());
            if (qty == null) continue;
            for (int i = 0; i < qty; i++) cartRooms.add(r);
        }
        return cartRooms.isEmpty() ? java.util.Collections.singletonList(room) : cartRooms;
    }

    /**
     * Multi-room cart summary bar: navigates every currently-selected room
     * straight into the wizard (Reserve Now/Book Now acting on the whole
     * selection, not just one card) - same PendingWizardRooms handoff as
     * the single-room per-card path above.
     */
    private void navigateToBookingWithCart(boolean reservation) {
        if (isNavigatingToBookingFlow || selectedQuantities.isEmpty()) return;
        List<Room> cartRooms = new ArrayList<>();
        for (Room r : allRooms) {
            Integer qty = selectedQuantities.get(r.getId());
            if (qty == null) continue;
            for (int i = 0; i < qty; i++) cartRooms.add(r);
        }
        if (cartRooms.isEmpty()) return;
        isNavigatingToBookingFlow = true;
        PendingWizardRooms.set(cartRooms);
        PendingWizardRooms.setDates(checkInDate, checkOutDate);
        startActivity(StartingTransactionActivity.newIntent(this,
                reservation ? BookingWizardState.Mode.RESERVATION : BookingWizardState.Mode.BOOKING));
    }

    private void showRoomDetailsDialog(Room room, String action) {
        String occupiedRangeText = (checkInDate != null && checkOutDate != null)
                ? getString(R.string.occupied_from_until_format,
                        dateFormat.format(checkInDate.getTime()), dateFormat.format(checkOutDate.getTime()))
                : null;

        RoomDetailsDialog.show(this, room, action, occupiedRangeText, new RoomDetailsDialog.ActionListener() {
            @Override
            public void onBook(Room room) {
                navigateToBooking(room, false);
            }

            @Override
            public void onReserve(Room room) {
                navigateToBooking(room, true);
            }
        });
    }

    /** The header greets the guest by first name; the intro text stays plain so the greeting is not repeated. */
    private void updatePersonalizedGreeting() {
        TextView screenDescription = findViewById(R.id.screenDescription);
        if (screenDescription != null) {
            screenDescription.setText(R.string.room_browsing_desc);
        }
    }
}
