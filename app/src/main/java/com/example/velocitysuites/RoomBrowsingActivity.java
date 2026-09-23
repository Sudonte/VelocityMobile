package com.example.velocitysuites;

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
    private AutoCompleteTextView dropdownSort, dropdownRoomType, dropdownStatus;
    private View noResultsView, loadingOverlay, layoutRoomError;
    private TextView tvResultsCount, tvCurrentSortLabel;
    private TextView emptyStateTitle, emptyStateDesc;
    private ImageView emptyStateIcon;
    private MaterialButton btnEmptyStateAction;

    private Calendar checkInDate, checkOutDate;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    // Filters and Sorting
    private int selectedCapacity = 0; // 0 for any, 1 for 1-2, 2 for 3-4, 3 for 5+
    private float selectedMaxPrice = 10000;
    private String currentSortMode = "Default"; // Default, PriceLow, PriceHigh, NameAZ
    private String selectedRoomTypeFilter = "All";
    private String selectedStatusFilter = "All"; // All, Available, Limited, Unavailable

    private com.google.android.material.appbar.AppBarLayout appBarLayout;
    private String selectedRoomIdFromLanding;
    private String selectedRoomActionFromLanding;
    private boolean shouldOpenSelectedRoom;
    private boolean shouldOpenSelectedRoomsSummary;
    private boolean hasLoadedRoomsOnce;

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
        if (roomIdsCsv != null && !roomIdsCsv.isEmpty()) {
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
        checkOutLayout = findViewById(R.id.checkOutLayout);
        btnSearchAvailability = findViewById(R.id.btnSearchAvailability);
        btnFilters = findViewById(R.id.btnFilters);
        btnResetSearch = findViewById(R.id.btnResetSearch);
        dropdownSort = findViewById(R.id.dropdownSort);
        dropdownRoomType = findViewById(R.id.dropdownRoomType);
        dropdownStatus = findViewById(R.id.dropdownStatus);
        noResultsView = findViewById(R.id.noResultsView);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        tvResultsCount = findViewById(R.id.tvResultsCount);
        tvCurrentSortLabel = findViewById(R.id.tvCurrentSortLabel);
        appBarLayout = findViewById(R.id.appBarLayout);

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
        if (emptyStateIcon != null) emptyStateIcon.setImageResource(R.drawable.ic_room_search);
        if (btnEmptyStateAction != null) {
            btnEmptyStateAction.setText(R.string.clear_filters);
            btnEmptyStateAction.setIconResource(R.drawable.ic_filter);
            btnEmptyStateAction.setVisibility(View.VISIBLE);
            btnEmptyStateAction.setOnClickListener(v -> clearAllFilters());
        }

        // Persistent network/API failure state - see roombrowsing.xml's layoutRoomError.
        layoutRoomError = findViewById(R.id.layoutRoomError);
        TextView roomErrorTitle = layoutRoomError.findViewById(R.id.emptyTitle);
        TextView roomErrorDesc = layoutRoomError.findViewById(R.id.emptyDesc);
        ImageView roomErrorIcon = layoutRoomError.findViewById(R.id.emptyIcon);
        MaterialButton btnRetryRooms = layoutRoomError.findViewById(R.id.btnEmptyAction);
        roomErrorTitle.setText(R.string.room_error_title);
        roomErrorDesc.setText(R.string.room_error_message);
        roomErrorIcon.setImageResource(R.drawable.ic_wifi_off);
        btnRetryRooms.setText(R.string.retry_label);
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
            adapter.notifyDataSetChanged();
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
            adapter.notifyDataSetChanged();
            updateSelectionSummaryBar();
        });
    }

    private void loadRooms() {
        loadingOverlay.setVisibility(View.VISIBLE);
        layoutRoomError.setVisibility(View.GONE);
        RoomRepository.getInstance(this).refreshRooms(new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> result) {
                loadingOverlay.setVisibility(View.GONE);
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                layoutRoomError.setVisibility(View.GONE);
                allRooms = result;
                refreshRoomTypeDropdownOptions();
                filteredRooms = new ArrayList<>(allRooms);
                adapter.updateList(filteredRooms);
                noResultsView.setVisibility(filteredRooms.isEmpty() ? View.VISIBLE : View.GONE);
                if (tvResultsCount != null) {
                    tvResultsCount.setText(filteredRooms.size() + " Rooms Found");
                }
                updateSelectionSummaryBar();
                openSelectedRoomFromLanding();
                openSelectedRoomsSummaryFromLanding();
            }

            @Override
            public void onError(String message) {
                loadingOverlay.setVisibility(View.GONE);
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                noResultsView.setVisibility(View.GONE);
                layoutRoomError.setVisibility(View.VISIBLE);
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
        rvRooms.postDelayed(this::showSelectedRoomsDialog, 450L);
    }

    private void setupDatePickers() {
        // Check-out stays disabled/inaccessible until a valid check-in is chosen.
        checkOutLayout.setEnabled(false);
        etCheckIn.setOnClickListener(v -> showCheckInPicker());
        etCheckOut.setOnClickListener(v -> showCheckOutPicker());

        btnSearchAvailability.setOnClickListener(v -> {
            boolean hasDates = (checkInDate != null && checkOutDate != null);

            if (!hasDates) {
                Toast.makeText(this, getString(R.string.error_fill_search_fields), Toast.LENGTH_LONG).show();
                // Expand app bar to show date pickers if they are hidden
                if (appBarLayout != null) {
                    appBarLayout.setExpanded(true, true);
                }
                
                // Highlight the date fields
                if (checkInDate == null) etCheckIn.setError(getString(R.string.error_required));
                if (checkOutDate == null) etCheckOut.setError(getString(R.string.error_required));
            } else {
                etCheckIn.setError(null);
                etCheckOut.setError(null);
                performSearchWithLoading();
            }
        });
    }

    private void performSearchWithLoading() {
        loadingOverlay.setVisibility(View.VISIBLE);

        // Re-fetch from the backend with the guest's actual dates first -
        // availability is date-range-aware server-side, so filtering the
        // stale default-window snapshot client-side would show the wrong
        // Fully Booked state for any dates other than the initial load's.
        RoomRepository.getInstance(this).refreshRooms(checkInDate, checkOutDate, new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> result) {
                allRooms = result;
                refreshRoomTypeDropdownOptions();
                finishSearch();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(RoomBrowsingActivity.this, "Couldn't refresh availability: " + message, Toast.LENGTH_LONG).show();
                finishSearch();
            }
        });
    }

    private void finishSearch() {
        applyFilters();
        loadingOverlay.setVisibility(View.GONE);
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

    private void showCheckInPicker() {
        // Stays start no earlier than tomorrow - same-day check-in is not allowed
        long earliestCheckInUtc = MaterialDatePicker.todayInUtcMilliseconds() + ONE_DAY_MS;
        com.google.android.material.datepicker.CalendarConstraints constraints =
                new com.google.android.material.datepicker.CalendarConstraints.Builder()
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

        picker.addOnPositiveButtonClickListener(selection -> {
            checkInDate = fromUtcMidnight(selection);
            etCheckIn.setText(dateFormat.format(checkInDate.getTime()));
            etCheckIn.setError(null);

            // A previously chosen check-out that is no longer at least one
            // night after the new check-in must be re-picked
            if (checkOutDate != null && !checkOutDate.after(checkInDate)) {
                checkOutDate = null;
                etCheckOut.setText("");
            }

            // Check-out becomes selectable now that check-in is valid, but the
            // guest picks it themselves rather than being forced into it here.
            checkOutLayout.setEnabled(true);
        });

        picker.show(getSupportFragmentManager(), "CHECK_IN_PICKER");
    }

    private void showCheckOutPicker() {
        if (checkInDate == null) {
            // Defensive guard: the field is disabled until check-in is set, so this
            // only fires if something (e.g. accessibility tooling) bypasses that.
            etCheckIn.setError(getString(R.string.error_select_checkin_first));
            Toast.makeText(this, R.string.error_select_checkin_first, Toast.LENGTH_SHORT).show();
            return;
        }

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

        picker.addOnPositiveButtonClickListener(selection -> {
            checkOutDate = fromUtcMidnight(selection);
            etCheckOut.setText(dateFormat.format(checkOutDate.getTime()));
            etCheckOut.setError(null);
        });

        picker.show(getSupportFragmentManager(), "CHECK_OUT_PICKER");
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

        setupSortDropdown();
        setupRoomTypeDropdown();
        setupStatusDropdown();

        btnFilters.setOnClickListener(v -> showFilterDialog());
        btnResetSearch.setOnClickListener(v -> {
            etCheckIn.setText("");
            etCheckOut.setText("");
            checkInDate = null;
            checkOutDate = null;
            checkOutLayout.setEnabled(false);
            performSearchWithLoading();
        });
    }

    private void setupSortDropdown() {
        String[] sortOptions = getResources().getStringArray(R.array.sort_options);
        dropdownSort.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, sortOptions));
        dropdownSort.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 1: currentSortMode = "PriceLow"; break;
                case 2: currentSortMode = "PriceHigh"; break;
                case 3: currentSortMode = "NameAZ"; break;
                default: currentSortMode = "Default"; break;
            }
            performSearchWithLoading();
        });
    }

    private void setupRoomTypeDropdown() {
        // Placeholder until the first room list arrives - real options are
        // filled in by refreshRoomTypeDropdownOptions() below.
        List<String> initial = new ArrayList<>();
        initial.add(getString(R.string.all_categories));
        dropdownRoomType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, initial));
    }

    /**
     * Room Type filter options must never be a fixed list - the System
     * Administrator can create/rename/retire room types at any time through
     * the web Rooms Module, and the guest app has to reflect that without an
     * XML/code change. Rebuilt every time allRooms is (re)loaded, from the
     * distinct types actually present in that list.
     */
    private void refreshRoomTypeDropdownOptions() {
        java.util.TreeSet<String> distinctTypes = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Room r : allRooms) {
            if (r.getType() != null && !r.getType().trim().isEmpty()) {
                distinctTypes.add(r.getType().trim());
            }
        }
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.all_categories));
        options.addAll(distinctTypes);

        String previousSelection = dropdownRoomType.getText() != null ? dropdownRoomType.getText().toString() : null;
        dropdownRoomType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, options));
        dropdownRoomType.setOnItemClickListener((parent, view, position, id) -> {
            String selected = options.get(position);
            selectedRoomTypeFilter = selected.equals(getString(R.string.all_categories)) ? "All" : selected;
            performSearchWithLoading();
        });

        if (previousSelection != null && options.contains(previousSelection)) {
            dropdownRoomType.setText(previousSelection, false);
        } else {
            dropdownRoomType.setText(getString(R.string.all_categories), false);
            selectedRoomTypeFilter = "All";
        }
    }

    private void setupStatusDropdown() {
        String[] statusOptions = getResources().getStringArray(R.array.status_filter_options);
        dropdownStatus.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, statusOptions));
        dropdownStatus.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 1: selectedStatusFilter = "Available"; break;
                case 2: selectedStatusFilter = "Limited"; break;
                case 3: selectedStatusFilter = "Unavailable"; break;
                default: selectedStatusFilter = "All"; break;
            }
            // Status is already known from the last fetch - filter the current list
            // locally instead of re-hitting the network, so the change is instant.
            applyFilters();
        });
    }


    private void showFilterDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_room_filters, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.VelocityDialogTheme)
                .setView(dialogView)
                .create();

        ChipGroup capacityGroup = dialogView.findViewById(R.id.capacityChipGroup);
        com.google.android.material.slider.Slider priceSlider = dialogView.findViewById(R.id.priceSlider);
        TextView tvPriceLabel = dialogView.findViewById(R.id.tvPriceLabel);
        MaterialButton btnApply = dialogView.findViewById(R.id.btnApplyFilters);
        MaterialButton btnClose = dialogView.findViewById(R.id.btnCloseFilters);
        MaterialButton btnClear = dialogView.findViewById(R.id.btnClearAll);

        // Set current values
        priceSlider.setValue(Math.min(selectedMaxPrice, priceSlider.getValueTo()));
        tvPriceLabel.setText(getString(R.string.budget_label, String.format(Locale.getDefault(), "%,.0f", selectedMaxPrice)));

        if (selectedCapacity == 0) capacityGroup.check(R.id.chipAnyCap);
        else if (selectedCapacity == 1) capacityGroup.check(R.id.chip12);
        else if (selectedCapacity == 2) capacityGroup.check(R.id.chip34);
        else if (selectedCapacity == 3) capacityGroup.check(R.id.chip5plus);

        priceSlider.addOnChangeListener((slider, value, fromUser) -> 
            tvPriceLabel.setText(getString(R.string.budget_label, String.format(Locale.getDefault(), "%,.0f", value)))
        );

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnClear.setOnClickListener(v -> {
            priceSlider.setValue(priceSlider.getValueTo());
            capacityGroup.check(R.id.chipAnyCap);
            tvPriceLabel.setText(getString(R.string.budget_label, String.format(Locale.getDefault(), "%,.0f", priceSlider.getValueTo())));
        });

        btnApply.setOnClickListener(v -> {
            selectedMaxPrice = priceSlider.getValue();
            int checkedId = capacityGroup.getCheckedChipId();
            if (checkedId == R.id.chipAnyCap) selectedCapacity = 0;
            else if (checkedId == R.id.chip12) selectedCapacity = 1;
            else if (checkedId == R.id.chip34) selectedCapacity = 2;
            else if (checkedId == R.id.chip5plus) selectedCapacity = 3;
            
            dialog.dismiss();
            performSearchWithLoading();
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private void clearAllFilters() {
        etCheckIn.setText("");
        etCheckOut.setText("");
        checkInDate = null;
        checkOutDate = null;
        checkOutLayout.setEnabled(false);
        dropdownSort.setText(getString(R.string.sort_default), false);
        dropdownRoomType.setText(getString(R.string.all_categories), false);
        dropdownStatus.setText(getString(R.string.filter_status_all_rooms), false);
        selectedRoomTypeFilter = "All";
        selectedStatusFilter = "All";
        selectedCapacity = 0;
        selectedMaxPrice = 15000;
        currentSortMode = "Default";
        applyFilters();
    }

    private void applyFilters() {
        // Type filter comes from the Room Type exposed dropdown; defaults to "All" so
        // an empty/"All" selection returns every room instead of matching nothing.
        String typeFilter = selectedRoomTypeFilter;

        // Use RoomRepository for the core filtering (query removed as requested, type, capacity bucket, price, dates)
        List<Room> newFilteredList = RoomRepository.getInstance(this).searchRooms(
                "",
                typeFilter,
                selectedCapacity,
                0,
                selectedMaxPrice,
                checkInDate,
                checkOutDate
        );

        boolean isDateSearch = checkInDate != null && checkOutDate != null;

        if (!isDateSearch) {
            // Pre-search browsing (no dates chosen yet): unchanged flat-list
            // behavior: the Available/Unavailable section split only applies
            // once the guest has actually searched a date range.
            if ("Available".equals(selectedStatusFilter)) {
                newFilteredList.removeIf(room -> room == null || !room.isAvailable());
            } else if ("Limited".equals(selectedStatusFilter)) {
                newFilteredList.removeIf(room -> room == null || RoomAvailabilityStatus.of(room) != RoomAvailabilityStatus.LIMITED);
            } else if ("Unavailable".equals(selectedStatusFilter)) {
                newFilteredList.removeIf(room -> room == null || room.isAvailable());
            }
            sortRoomsList(newFilteredList);

            adapter.updateList(newFilteredList);
            filteredRooms = newFilteredList;
            noResultsView.setVisibility(filteredRooms.isEmpty() ? View.VISIBLE : View.GONE);

            if (tvResultsCount != null) {
                String countText = filteredRooms.size() + (filteredRooms.size() == 1 ? " Room Found" : " Rooms Found");
                tvResultsCount.setText(countText);
            }
        } else {
            sortRoomsList(newFilteredList);
            filteredRooms = newFilteredList;
            // The Available/Unavailable split itself carries the "no results"
            // messaging now, so the full-screen empty state only fires for the
            // pre-search browsing case above.
            noResultsView.setVisibility(View.GONE);

            List<Room> trueAvailable = new ArrayList<>();
            List<Room> trueUnavailable = new ArrayList<>();
            for (Room r : newFilteredList) {
                (r.isAvailable() ? trueAvailable : trueUnavailable).add(r);
            }

            // The Room Status dropdown narrows which section(s) are shown, but must
            // not change whether the "no rooms available" verdict is triggered -
            // that verdict is about real availability, not the guest's own filter choice.
            // "Limited" is a refinement of Available (low-stock-but-bookable), not a
            // separate section, so it narrows displayAvailable and hides Unavailable.
            List<Room> displayAvailable;
            if ("Unavailable".equals(selectedStatusFilter)) {
                displayAvailable = new ArrayList<>();
            } else if ("Limited".equals(selectedStatusFilter)) {
                displayAvailable = new ArrayList<>();
                for (Room r : trueAvailable) {
                    if (RoomAvailabilityStatus.of(r) == RoomAvailabilityStatus.LIMITED) {
                        displayAvailable.add(r);
                    }
                }
            } else {
                displayAvailable = trueAvailable;
            }
            List<Room> displayUnavailable = ("Available".equals(selectedStatusFilter) || "Limited".equals(selectedStatusFilter))
                    ? new ArrayList<>() : trueUnavailable;

            String occupiedRangeText = getString(R.string.occupied_from_until_format,
                    dateFormat.format(checkInDate.getTime()), dateFormat.format(checkOutDate.getTime()));
            String noAvailableMessage = trueAvailable.isEmpty() ? getString(R.string.no_rooms_available_for_dates) : null;

            adapter.updateSectionedResults(
                    displayAvailable,
                    displayUnavailable,
                    getString(R.string.available_rooms_header_format, displayAvailable.size()),
                    getString(R.string.unavailable_rooms_header_format, displayUnavailable.size()),
                    occupiedRangeText,
                    noAvailableMessage
            );

            if (tvResultsCount != null) {
                tvResultsCount.setText(getString(R.string.results_count_split_format, trueAvailable.size(), trueUnavailable.size()));
            }
        }

        if (tvCurrentSortLabel != null) {
            String sortName = "Preferred";
            switch (currentSortMode) {
                case "PriceLow":
                    sortName = "Price: Low to High";
                    break;
                case "PriceHigh":
                    sortName = "Price: High to Low";
                    break;
                case "NameAZ":
                    sortName = "Name: A-Z";
                    break;
            }
            tvCurrentSortLabel.setText(getString(R.string.sorting_by_label, sortName));
        }
        updateSelectionSummaryBar();
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
                Collections.sort(roomsToSort, (r1, r2) -> r1.getName().compareToIgnoreCase(r2.getName()));
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

    private void updatePersonalizedGreeting() {
        TextView screenDescription = findViewById(R.id.screenDescription);
        if (screenDescription != null) {
            SharedPreferences prefs = getSharedPreferences("VelocityPrefs", MODE_PRIVATE);
            String fullName = prefs.getString("userName", "Guest");
            String firstName = fullName != null ? fullName.split(" ")[0] : "Guest";
            String description = getString(R.string.room_browsing_desc);
            screenDescription.setText(getString(R.string.personalized_greeting_format, firstName, description));
        }
    }
}
