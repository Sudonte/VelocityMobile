package com.example.velocitysuites;

import android.app.DatePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
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

public class BookingAndReservationActivity extends BaseNavigationActivity {

    public static final String EXTRA_OPEN_SECTION = "EXTRA_OPEN_SECTION";
    public static final String SECTION_BOOKING = "SECTION_BOOKING";
    public static final String SECTION_RESERVATION = "SECTION_RESERVATION";
    /** Reservation/booking id to scroll to and highlight in the bottom list. */
    public static final String EXTRA_HIGHLIGHT_ID = "EXTRA_HIGHLIGHT_ID";
    /**
     * Narrows the Booking List/Reservation List to a status subset (used by the
     * Dashboard's Booking & Reservation Summary tiles to deep-link straight into
     * the matching Cancel/Past/Active list instead of just opening the tab).
     */
    public static final String EXTRA_LIST_FILTER = "EXTRA_LIST_FILTER";
    public static final String FILTER_ACTIVE = "FILTER_ACTIVE";
    public static final String FILTER_CANCELLED = "FILTER_CANCELLED";
    public static final String FILTER_COMPLETED = "FILTER_COMPLETED";
    /**
     * Set alongside MODIFY_BOOKING_ID when the Payment screen's "Modify"
     * button opens this Activity: once the edit is saved, skip the normal
     * "Reservation Updated" dialog and instead return straight to
     * PaymentActivity so the billing summary recalculates immediately.
     */
    public static final String EXTRA_RETURN_TO_PAYMENT = "RETURN_TO_PAYMENT";

    /** How many list items are shown before the user taps Expand. */
    private static final int COLLAPSED_LIST_COUNT = 3;

    /** Bundle keys for restoring a manually-picked tab/filter across rotation - see onSaveInstanceState(). */
    private static final String STATE_CATEGORY_BOOKING = "state_category_booking";
    private static final String STATE_CATEGORY_RESERVATION = "state_category_reservation";
    private static final String STATE_TAB_POSITION = "state_tab_position";

    /** Guest payload consumed by RoomRepository when creating/updating reservations. */
    public static class AdditionalGuest {
        public String name;
        public int age;
        public String gender;
        public String relationship;

        public AdditionalGuest(String name, int age, String gender, String relationship) {
            this.name = name;
            this.age = age;
            this.gender = gender;
            this.relationship = relationship;
        }
    }

    private TabLayout tabLayout;
    private NestedScrollView screenContent;
    private SwipeRefreshLayout swipeRefresh;
    private View layoutNewBooking, loadingOverlay, cardIdPreview;
    private MaterialButton btnAddRoom;
    private TextView tvRoomSelectionHelper;
    private TextInputLayout tilCheckIn, tilCheckOut, tilPrimaryGuestFirstName, tilPrimaryGuestLastName;
    private TextInputEditText etCheckIn, etCheckOut, etPrimaryGuestFirstName, etPrimaryGuestMiddleName, etPrimaryGuestLastName;
    private TextView tvCapacityIndicator, tvRoomsSelectedCount;
    private ChipGroup cgIdType;
    private MaterialButton btnUploadId, btnAddGuest, btnConfirmBooking, btnCancelEdit, btnExpandList;
    private View layoutTermsAgreement;
    private MaterialCheckBox cbTermsAgreement;
    private View tvViewTerms;
    private LinearLayout layoutSummaryAmenities, layoutSummaryAmenitiesRows;
    private View layoutAmenitiesSection;
    private LinearLayout layoutAmenitiesItemsContainer;
    private View tvAmenitiesEmptyState;
    private TextView tvSummaryTax;
    private List<AddOnAmenity> addOnCatalog = new ArrayList<>();
    private final List<AddOnAmenity> selectedAmenities = new ArrayList<>();
    private View layoutBookingStatusFilter, layoutReservationStatusFilter;
    private AutoCompleteTextView bookingStatusFilter, reservationStatusFilter;
    private View cardCancellationPolicy;
    /**
     * Independent per-tab Status Filter state - a Reservation status pick
     * must never affect the Booking tab's filter and vice versa (each tab
     * has its own combo box, see layoutBookingStatusFilter/
     * layoutReservationStatusFilter). "All" is the default on first landing
     * and always restored after a new booking/reservation is created, so the
     * guest immediately sees it without hunting through statuses.
     */
    private boolean showAllBookingCategories = true;
    private TransactionCategorizer.Category currentBookingCategory = TransactionCategorizer.Category.ACTIVE;
    private boolean showAllReservationCategories = true;
    private TransactionCategorizer.Category currentReservationCategory = TransactionCategorizer.Category.ACTIVE;
    private TextInputEditText etSearchTransactions;
    /** Lowercased search text combined with the active tab/status filter in itemsForCurrentTab() - "" matches everything. */
    private String searchQuery = "";
    /** Two visually separate exposed-dropdown controls (Sort By, Room Type) - deliberately not combined into a single "Filter" control, per spec. */
    private AutoCompleteTextView dropdownTransactionSort, dropdownTransactionRoomType;
    /** "NEWEST" | "OLDEST" - from the Sort By dropdown; combined with the status/search/room-type filters below. */
    private String sortOrder = "NEWEST";
    /** Room type selected in the Room Type dropdown, or null for "All Room Types" - no restriction. */
    private String selectedRoomTypeFilter = null;
    private ImageView ivIdPreview;
    private TextView tvIdUploadStatus, tvSummaryRoom, tvSummaryTotal, screenTitle, screenDescription, tvSummaryDiscount;
    private TextView tvListTitle, tvListDesc, tvStepDetails, tvListCount;
    private View tvListEmpty;
    private TextView tvListEmptyTitle, tvListEmptyDesc;
    private ImageView ivListEmptyIcon;
    private TextView tvSummaryDates, tvSummaryGuests;
    private TextView tvProgressStep1, tvProgressStep2, tvProgressStep3;
    private View progressLine1, progressLine2;
    private View layoutSummaryDiscount;
    private LinearLayout layoutAdditionalGuests;
    private LinearLayout layoutSelectedRooms, layoutSummaryRooms;
    private View layoutNoRoomsSelected;
    private TextView tvNoRoomsSelected;
    private RecyclerView rvItemList;
    /**
     * Landing actions (New Booking/New Reservation, launch BookingWizardActivity) vs. the
     * legacy inline form (layoutCreateForm) - see populateFormForEdit()/resetForm().
     *
     * CONFIRMED FULLY UNREACHABLE (traced end-to-end, not just "legacy"): layoutCreateForm
     * only ever becomes visible via populateFormForEdit(), which only ever runs when
     * getIntent().getStringExtra("MODIFY_BOOKING_ID") is non-null - and nothing in this
     * codebase ever calls .putExtra("MODIFY_BOOKING_ID", ...) or
     * .putExtra(EXTRA_RETURN_TO_PAYMENT, ...) (grep confirms zero writers for both). Every
     * live Modify action (btnModify's click listener, PaymentActivity's old Modify button)
     * goes through launchModifyWizard() -> BookingWizardActivity.newEditIntent() instead - a
     * completely different Activity. That means finalizeBooking()'s editingBookingId-set
     * branch, onAllRoomsCreated(), rollbackCreatedBookings(), groupSelectedRoomsByType(),
     * createRoomReservations(), and the one live call to BookingGroupState.saveGroup() are
     * ALL dead code today, exactly like PaymentActivity's own already-documented
     * EXTRA_PENDING_RESERVATION cluster (see that field's docblock for the same reasoning).
     * Left in place rather than removed - this is a large, interlinked block spanning
     * ~15 methods/fields plus layout XML, and per that same PaymentActivity precedent, the
     * risk of a surgical removal in a heavily-used Activity outweighs the benefit. If this
     * is ever revisited: BookingWizardActivity/PaymentActivity's createDirectBooking()/
     * createReservation(List<List<Room>>, ...) single-call paths are the sole live creation
     * mechanism this whole block would need to be proven redundant against.
     */
    private View layoutWizardEntryActions;
    private View layoutCreateForm;
    private MaterialButton btnNewBooking, btnNewReservation;

    private RoomRepository repository;
    private List<Room> allRooms = new ArrayList<>();
    /** Every room the guest has added to the current booking/reservation (0..N, one shared stay window). */
    private final List<Room> selectedRooms = new ArrayList<>();
    private Calendar checkInCal, checkOutCal;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private String selectedIdType = "None";
    private Uri uploadedIdUri;
    private String editingBookingId = null;
    private boolean returnToPaymentAfterSave = false;
    private double currentTotalAmount = 0;
    private List<Booking> allMyBookings = new ArrayList<>();
    private boolean listExpanded = false;
    /** Set from the dashboard, or from a just-created transaction; the matching list card is scrolled into view, flashed, and temporarily outlined. */
    private String highlightBookingId = null;
    private boolean highlightScrollPending = false;
    /** Clears highlightBookingId (and its card border) a few seconds after it's actually shown - the highlight is a one-time "here it is" cue, not a permanent marker. */
    private final android.os.Handler highlightClearHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable highlightClearRunnable;
    private static final long HIGHLIGHT_DURATION_MS = 4000L;
    /** Set from the dashboard's Booking & Reservation Summary tiles; narrows the bottom list to a status subset. */
    private String listFilter = null;
    private boolean listFilterScrollPending = false;
    /** Re-entrancy guard: taps on Confirm are ignored while a create/update is in flight. */
    private boolean isSubmitting = false;

    private ActivityResultLauncher<String> idPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bookingandreservation);
        setupGuestNavigation(R.id.nav_booking_reservation);
        animateScreenContent();

        repository = RoomRepository.getInstance(this);

        // Initialize dates early to avoid nulls during room loading. The
        // fields themselves stay empty - the guest must pick check-in first,
        // and the earliest allowed check-in is tomorrow.
        checkInCal = Calendar.getInstance();
        checkInCal.add(Calendar.DAY_OF_YEAR, 1);
        checkOutCal = Calendar.getInstance();
        checkOutCal.add(Calendar.DAY_OF_YEAR, 2);

        initViews();

        // Pre-fill the stay-guest name from the account - editable, since
        // whoever is actually staying may differ from the account holder.
        String accountFirstName = getSharedPreferences("VelocityPrefs", MODE_PRIVATE).getString("userFirstName", "").trim();
        String accountMiddleName = getSharedPreferences("VelocityPrefs", MODE_PRIVATE).getString("userMiddleName", "").trim();
        String accountLastName = getSharedPreferences("VelocityPrefs", MODE_PRIVATE).getString("userLastName", "").trim();
        etPrimaryGuestFirstName.setText(accountFirstName);
        etPrimaryGuestMiddleName.setText(accountMiddleName);
        etPrimaryGuestLastName.setText(accountLastName);

        setupTabs();
        setupDatePickers();
        setupIdentificationLogic();
        setupAdditionalGuests();
        setupBookingConfirmation();
        setupListSection();
        setupStatusFilterDropdowns();
        setupSwipeRefresh();

        // A manually-picked tab/filter isn't part of the launch Intent, so without this
        // it would silently reset to Bookings/Active on rotation. Deep-link extras
        // (EXTRA_OPEN_SECTION/EXTRA_LIST_FILTER) are re-read from the same Intent on every
        // recreation regardless, so they still take precedence once handleIntentExtras()
        // runs after rooms finish loading below - unaffected by this restore.
        if (savedInstanceState != null) {
            int savedTabPosition = savedInstanceState.getInt(STATE_TAB_POSITION, 0);
            if (tabLayout.getTabAt(savedTabPosition) != null) {
                tabLayout.getTabAt(savedTabPosition).select();
            }
            restoreCategoryState(false, savedInstanceState.getString(STATE_CATEGORY_BOOKING));
            restoreCategoryState(true, savedInstanceState.getString(STATE_CATEGORY_RESERVATION));
        }
        applyMode();

        loadingOverlay.setVisibility(View.VISIBLE);
        repository.refreshRooms(new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> result) {
                loadingOverlay.setVisibility(View.GONE);
                allRooms = result;
                setupRoomSelection();
                refreshTransactionRoomTypeDropdown();
                handleIntentExtras();
            }

            @Override
            public void onError(String message) {
                loadingOverlay.setVisibility(View.GONE);
                Toast.makeText(BookingAndReservationActivity.this, "Couldn't load rooms: " + message, Toast.LENGTH_LONG).show();
            }
        });

        idPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                uploadedIdUri = uri;
                tvIdUploadStatus.setText(R.string.id_uploaded_success);
                tvIdUploadStatus.setTextColor(getResources().getColor(R.color.velocity_red_primary));
                ivIdPreview.setImageURI(uri);
                cardIdPreview.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_CATEGORY_BOOKING, showAllBookingCategories ? "ALL" : currentBookingCategory.name());
        outState.putString(STATE_CATEGORY_RESERVATION, showAllReservationCategories ? "ALL" : currentReservationCategory.name());
        outState.putInt(STATE_TAB_POSITION, tabLayout != null ? tabLayout.getSelectedTabPosition() : 0);
    }

    /** Restores one tab's Status Filter state from a saved Bundle string ("ALL" or a TransactionCategorizer.Category name) - the Booking and Reservation tabs are restored independently, each from its own key. */
    private void restoreCategoryState(boolean reservation, String saved) {
        if (saved == null || "ALL".equals(saved)) {
            if (reservation) showAllReservationCategories = true; else showAllBookingCategories = true;
            return;
        }
        try {
            TransactionCategorizer.Category category = TransactionCategorizer.Category.valueOf(saved);
            if (reservation) {
                currentReservationCategory = category;
                showAllReservationCategories = false;
            } else {
                currentBookingCategory = category;
                showAllBookingCategories = false;
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        // A successful new booking/reservation redirects back here with a
        // fresh Intent (see navigateToBookingSection()/
        // navigateToReservationSection() in PaymentActivity) - but this
        // Activity is still sitting on the back stack the whole time the
        // guest is in the wizard (New Booking/New Reservation never
        // finish() it), so FLAG_ACTIVITY_SINGLE_TOP reuses this instance
        // instead of recreating it. Without this override, onCreate() (and
        // its one-time handleIntentExtras() call) never runs again for the
        // new Intent, so the guest would land back on whatever tab/status/
        // search/filter they'd left before starting the wizard - not the
        // required Bookings/Reservations -> All with every filter reset
        // (see spec: "successful new transaction creation should override"
        // the normal state-preservation rule).
        searchQuery = "";
        if (etSearchTransactions != null) etSearchTransactions.setText("");
        selectedRoomTypeFilter = null;
        if (dropdownTransactionRoomType != null) dropdownTransactionRoomType.setText(getString(R.string.all_room_types_label), false);
        sortOrder = "NEWEST";
        if (dropdownTransactionSort != null) dropdownTransactionSort.setText(getString(R.string.transaction_sort_newest), false);
        showAllBookingCategories = true;
        currentBookingCategory = TransactionCategorizer.Category.ACTIVE;
        showAllReservationCategories = true;
        currentReservationCategory = TransactionCategorizer.Category.ACTIVE;
        listExpanded = false;

        highlightBookingId = intent.getStringExtra(EXTRA_HIGHLIGHT_ID);
        highlightScrollPending = highlightBookingId != null;
        listFilter = null;
        listFilterScrollPending = false;

        String openSection = intent.getStringExtra(EXTRA_OPEN_SECTION);
        if (SECTION_RESERVATION.equals(openSection) && tabLayout.getTabAt(1) != null) {
            tabLayout.getTabAt(1).select();
        } else if (SECTION_BOOKING.equals(openSection) && tabLayout.getTabAt(0) != null) {
            tabLayout.getTabAt(0).select();
        }
        // Tab selection above only triggers applyMode()/applyListFilterLabels()
        // when the tab actually changes - call explicitly so the reset above
        // (currentCategory/search/filters) is always reflected even when the
        // guest was already on the right tab. onResume() (invoked right after
        // by the platform) performs the actual refreshMyBookings() fetch so
        // the newly created transaction's authoritative server state is shown.
        applyListFilterLabels();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMyBookings();
    }

    /**
     * Real-time cross-screen sync: whenever RoomRepository's shared cache
     * changes for any reason (this screen's own actions, or DashboardActivity's),
     * re-render immediately from the shared cache - no manual refresh needed.
     */
    private final RoomRepository.BookingsChangedListener bookingsChangedListener = () -> {
        allMyBookings = repository.getBookings();
        renderList();
    };

    @Override
    protected void onStart() {
        super.onStart();
        repository.addBookingsChangedListener(bookingsChangedListener);
    }

    @Override
    protected void onStop() {
        repository.removeBookingsChangedListener(bookingsChangedListener);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        // Backgrounding briefly (onStop/onStart) should NOT cancel the
        // pending highlight-clear - the guest may switch apps for a moment
        // and come back well within the highlight window, and it should
        // still clear on schedule. Only true teardown needs to stop it, to
        // avoid a Handler callback touching views after the Activity is gone.
        if (highlightClearRunnable != null) {
            highlightClearHandler.removeCallbacks(highlightClearRunnable);
        }
        super.onDestroy();
    }

    private void initViews() {
        tabLayout = findViewById(R.id.tabLayout);
        screenContent = findViewById(R.id.screenContent);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        layoutNewBooking = findViewById(R.id.layoutNewBooking);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        layoutWizardEntryActions = findViewById(R.id.layoutWizardEntryActions);
        layoutCreateForm = findViewById(R.id.layoutCreateForm);
        btnNewBooking = findViewById(R.id.btnNewBooking);
        btnNewReservation = findViewById(R.id.btnNewReservation);
        btnNewBooking.setOnClickListener(NavUtils.debounce(v -> startActivity(StartingTransactionActivity.newIntent(this, BookingWizardState.Mode.BOOKING))));
        btnNewReservation.setOnClickListener(NavUtils.debounce(v -> startActivity(StartingTransactionActivity.newIntent(this, BookingWizardState.Mode.RESERVATION))));
        btnAddRoom = findViewById(R.id.btnAddRoom);
        tvRoomSelectionHelper = findViewById(R.id.tvRoomSelectionHelper);
        tilCheckIn = findViewById(R.id.tilCheckIn);
        tilCheckOut = findViewById(R.id.tilCheckOut);
        etCheckIn = findViewById(R.id.etCheckIn);
        etCheckOut = findViewById(R.id.etCheckOut);
        tilPrimaryGuestFirstName = findViewById(R.id.tilPrimaryGuestFirstName);
        tilPrimaryGuestLastName = findViewById(R.id.tilPrimaryGuestLastName);
        etPrimaryGuestFirstName = findViewById(R.id.etPrimaryGuestFirstName);
        etPrimaryGuestMiddleName = findViewById(R.id.etPrimaryGuestMiddleName);
        etPrimaryGuestLastName = findViewById(R.id.etPrimaryGuestLastName);
        tvCapacityIndicator = findViewById(R.id.tvCapacityIndicator);
        tvRoomsSelectedCount = findViewById(R.id.tvRoomsSelectedCount);
        cgIdType = findViewById(R.id.cgIdType);
        btnUploadId = findViewById(R.id.btnUploadId);
        btnAddGuest = findViewById(R.id.btnAddGuest);
        btnConfirmBooking = findViewById(R.id.btnConfirmBooking);
        btnCancelEdit = findViewById(R.id.btnCancelEdit);
        ivIdPreview = findViewById(R.id.ivIdPreview);
        cardIdPreview = findViewById(R.id.cardIdPreview);
        tvIdUploadStatus = findViewById(R.id.tvIdUploadStatus);
        tvSummaryRoom = findViewById(R.id.tvSummaryRoom);
        tvSummaryTotal = findViewById(R.id.tvSummaryTotal);
        layoutSummaryDiscount = findViewById(R.id.layoutSummaryDiscount);
        tvSummaryDiscount = findViewById(R.id.tvSummaryDiscount);
        layoutAdditionalGuests = findViewById(R.id.layoutAdditionalGuests);
        layoutSelectedRooms = findViewById(R.id.layoutSelectedRooms);
        layoutNoRoomsSelected = findViewById(R.id.layoutNoRoomsSelected);
        tvNoRoomsSelected = findViewById(R.id.tvNoRoomsSelected);
        layoutSummaryRooms = findViewById(R.id.layoutSummaryRooms);
        screenTitle = findViewById(R.id.screenTitle);
        screenDescription = findViewById(R.id.screenDescription);
        tvListTitle = findViewById(R.id.tvListTitle);
        tvListDesc = findViewById(R.id.tvListDesc);
        tvListCount = findViewById(R.id.tvListCount);
        tvListEmpty = findViewById(R.id.tvListEmpty);
        tvListEmptyTitle = findViewById(R.id.tvListEmptyTitle);
        tvListEmptyDesc = findViewById(R.id.tvListEmptyDesc);
        ivListEmptyIcon = findViewById(R.id.ivListEmptyIcon);
        tvStepDetails = findViewById(R.id.tvStepDetails);
        tvSummaryDates = findViewById(R.id.tvSummaryDates);
        tvSummaryGuests = findViewById(R.id.tvSummaryGuests);
        tvProgressStep1 = findViewById(R.id.tvProgressStep1);
        tvProgressStep2 = findViewById(R.id.tvProgressStep2);
        tvProgressStep3 = findViewById(R.id.tvProgressStep3);
        progressLine1 = findViewById(R.id.progressLine1);
        progressLine2 = findViewById(R.id.progressLine2);

        TextWatcher stepProgressWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { updateStepProgress(); }
        };
        etPrimaryGuestFirstName.addTextChangedListener(stepProgressWatcher);
        etPrimaryGuestLastName.addTextChangedListener(stepProgressWatcher);
        btnExpandList = findViewById(R.id.btnExpandList);
        rvItemList = findViewById(R.id.rvItemList);

        layoutTermsAgreement = findViewById(R.id.layoutTermsAgreement);
        cbTermsAgreement = findViewById(R.id.cbTermsAgreement);
        tvViewTerms = findViewById(R.id.tvViewTerms);
        tvViewTerms.setOnClickListener(v -> showTermsAgreementDialog());
        cbTermsAgreement.setOnCheckedChangeListener((buttonView, isChecked) -> updateConfirmButtonEnabledState());

        layoutSummaryAmenities = findViewById(R.id.layoutSummaryAmenities);
        layoutSummaryAmenitiesRows = findViewById(R.id.layoutSummaryAmenitiesRows);
        tvSummaryTax = findViewById(R.id.tvSummaryTax);
        layoutAmenitiesSection = findViewById(R.id.layoutAmenitiesSection);
        layoutAmenitiesItemsContainer = findViewById(R.id.layoutAmenitiesItemsContainer);
        tvAmenitiesEmptyState = findViewById(R.id.tvAmenitiesEmptyState);
        loadAmenityCatalog();

        layoutBookingStatusFilter = findViewById(R.id.layoutBookingStatusFilter);
        layoutReservationStatusFilter = findViewById(R.id.layoutReservationStatusFilter);
        bookingStatusFilter = findViewById(R.id.bookingStatusFilter);
        reservationStatusFilter = findViewById(R.id.reservationStatusFilter);
        cardCancellationPolicy = findViewById(R.id.cardCancellationPolicy);

        etSearchTransactions = findViewById(R.id.etSearchTransactions);
        if (etSearchTransactions != null) {
            etSearchTransactions.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                @Override
                public void afterTextChanged(Editable s) {
                    // The full transaction list is already resident in
                    // allMyBookings (loaded once by refreshMyBookings()) -
                    // filtering happens entirely in-memory per keystroke,
                    // no network call per character. Only the search text
                    // changes here - the active Bookings/Reservations tab and
                    // Confirmed/Completed/Cancelled status are left exactly
                    // as they were, per spec ("search must apply only to the
                    // currently selected transaction type and status").
                    searchQuery = s.toString().trim().toLowerCase(Locale.getDefault());
                    listExpanded = true;
                    renderList();
                }
            });
        }

        dropdownTransactionSort = findViewById(R.id.dropdownTransactionSort);
        dropdownTransactionRoomType = findViewById(R.id.dropdownTransactionRoomType);
        setupTransactionSortDropdown();
        // Room Type options depend on allRooms, which isn't loaded yet at
        // this point in onCreate() - populated once refreshRooms() first
        // succeeds (see onCreate()) and refreshed every time it's reloaded.
    }

    /** Newest First / Oldest First - single-select, mirrors RoomBrowsingActivity's own dropdownSort wiring pattern. */
    private void setupTransactionSortDropdown() {
        String[] options = {getString(R.string.transaction_sort_newest), getString(R.string.transaction_sort_oldest)};
        dropdownTransactionSort.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, options));
        dropdownTransactionSort.setOnItemClickListener((parent, view, position, id) -> {
            sortOrder = position == 1 ? "OLDEST" : "NEWEST";
            renderList();
        });
    }

    /**
     * Room Type filter options must never be a fixed list - the System
     * Administrator can create/rename/retire room types at any time through
     * the web Rooms Module. Sourced from allRooms (the active room catalog
     * this screen already loads for Add Room - see RoomBrowsingActivity's
     * own refreshRoomTypeDropdownOptions(), the same pattern), not from the
     * guest's own past transactions, so a since-deactivated room type
     * correctly stops being offered as a new filter choice while any
     * historical transaction that used it stays visible in the list
     * untouched (see matchesRoomTypeFilter()). Rebuilt every time allRooms
     * is (re)loaded.
     */
    private void refreshTransactionRoomTypeDropdown() {
        if (dropdownTransactionRoomType == null) return;
        java.util.TreeSet<String> distinctTypes = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Room r : allRooms) {
            if (r.getType() != null && !r.getType().trim().isEmpty()) {
                distinctTypes.add(r.getType().trim());
            }
        }
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.all_room_types_label));
        options.addAll(distinctTypes);

        String previousSelection = dropdownTransactionRoomType.getText() != null ? dropdownTransactionRoomType.getText().toString() : null;
        dropdownTransactionRoomType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, options));
        dropdownTransactionRoomType.setOnItemClickListener((parent, view, position, id) -> {
            String selected = options.get(position);
            selectedRoomTypeFilter = selected.equals(getString(R.string.all_room_types_label)) ? null : selected;
            renderList();
        });

        if (previousSelection != null && options.contains(previousSelection)) {
            dropdownTransactionRoomType.setText(previousSelection, false);
        } else {
            dropdownTransactionRoomType.setText(getString(R.string.all_room_types_label), false);
            selectedRoomTypeFilter = null;
        }
    }

    private boolean isReservationMode() {
        return tabLayout != null && tabLayout.getSelectedTabPosition() == 1;
    }

    private void handleIntentExtras() {
        if (getIntent() == null) {
            return;
        }
        highlightBookingId = getIntent().getStringExtra(EXTRA_HIGHLIGHT_ID);
        highlightScrollPending = highlightBookingId != null;

        // Tab selection must happen before the status filter below, since the
        // filter now needs to know which tab (Booking vs Reservation) it's
        // targeting - each has its own independent category state.
        String openSection = getIntent().getStringExtra(EXTRA_OPEN_SECTION);
        if (openSection != null) {
            if (openSection.equals(SECTION_BOOKING)) {
                if (tabLayout.getTabAt(0) != null) tabLayout.getTabAt(0).select();
            } else if (openSection.equals(SECTION_RESERVATION)) {
                if (tabLayout.getTabAt(1) != null) tabLayout.getTabAt(1).select();
            }
        }

        listFilter = getIntent().getStringExtra(EXTRA_LIST_FILTER);
        listFilterScrollPending = listFilter != null;
        // A Dashboard summary tile deep-linking in with a specific status
        // subset overrides the "All" default; a plain open (fresh landing,
        // or the redirect after creating a new booking/reservation - see
        // navigateToBookingSection()/navigateToReservationSection(), neither
        // of which sets EXTRA_LIST_FILTER) leaves showAllCategories at its
        // default true, per spec. Only the tab this deep link actually opened
        // has its category state changed - the other tab's own filter is
        // left exactly as it was.
        boolean reservation = isReservationMode();
        if (FILTER_CANCELLED.equals(listFilter)) {
            setCategoryState(reservation, TransactionCategorizer.Category.CANCELLED);
        } else if (FILTER_COMPLETED.equals(listFilter)) {
            setCategoryState(reservation, TransactionCategorizer.Category.COMPLETED);
        } else if (FILTER_ACTIVE.equals(listFilter)) {
            setCategoryState(reservation, TransactionCategorizer.Category.ACTIVE);
        }

        if (listFilter != null) {
            listExpanded = true;
            applyListFilterLabels();
            renderList();
        }

        String roomId = getIntent().getStringExtra("ROOM_ID");
        String checkIn = getIntent().getStringExtra("CHECK_IN");
        String checkOut = getIntent().getStringExtra("CHECK_OUT");

        try {
            if (checkIn != null) {
                checkInCal.setTime(dateFormat.parse(checkIn));
                etCheckIn.setText(checkIn);
            }
            if (checkOut != null) {
                checkOutCal.setTime(dateFormat.parse(checkOut));
                etCheckOut.setText(checkOut);
            }
            // If only a check-in was handed over, keep the internal check-out
            // window valid (one night later) without filling the field
            if (!checkOutCal.after(checkInCal)) {
                checkOutCal.setTime(checkInCal.getTime());
                checkOutCal.add(Calendar.DAY_OF_YEAR, 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Refresh available rooms based on dates from intent before selecting the room
        refreshAvailableRooms();

        if (roomId != null) {
            for (Room r : allRooms) {
                if (r.getId().equals(roomId)) {
                    addRoomsToSelection(r, 1);
                    tilCheckIn.setEnabled(true);
                    tilCheckOut.setEnabled(true);

                    if (!repository.isAvailableForDates(r, checkInCal, checkOutCal, null)) {
                        Toast.makeText(this, R.string.error_dates_not_available, Toast.LENGTH_LONG).show();
                    }
                    break;
                }
            }
        }

        updateSummary();

        String modifyId = getIntent().getStringExtra("MODIFY_BOOKING_ID");
        returnToPaymentAfterSave = getIntent().getBooleanExtra(EXTRA_RETURN_TO_PAYMENT, false);
        if (modifyId != null) {
            repository.refreshBookings(new RoomRepository.RepositoryCallback<List<Booking>>() {
                @Override
                public void onSuccess(List<Booking> bookings) {
                    allMyBookings = bookings != null ? bookings : new ArrayList<>();
                    renderList();
                    for (Booking booking : allMyBookings) {
                        if (booking.getId().equals(modifyId)) {
                            populateFormForEdit(booking);
                            return;
                        }
                    }
                    Toast.makeText(BookingAndReservationActivity.this, "Error loading booking: not found", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(BookingAndReservationActivity.this, "Error loading booking: " + message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (editingBookingId != null) {
                    cancelEditMode();
                }
                applyMode();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    /**
     * Booking tab = the same form followed by payment; Reservation tab =
     * identical form but the stay is only held, with no payment step.
     * The bottom list mirrors the active tab (Booking List vs Reservation List).
     */
    private void applyMode() {
        boolean reservation = isReservationMode();
        if (editingBookingId == null) {
            btnConfirmBooking.setText(reservation ? R.string.confirm_reservation_label : R.string.confirm_booking_label);
            btnConfirmBooking.setIconResource(reservation ? R.drawable.ic_reservation : R.drawable.ic_payment_card);
        }
        if (screenDescription != null) {
            screenDescription.setText(reservation ? R.string.reservation_tab_desc : R.string.booking_tab_desc);
        }
        if (tvStepDetails != null) {
            tvStepDetails.setText(reservation ? R.string.step_details_title : R.string.step_booking_details_title);
        }
        tvListDesc.setText(reservation ? R.string.reservation_items_desc : R.string.booking_items_desc);
        if (tvListEmptyTitle != null) {
            tvListEmptyTitle.setText(reservation ? R.string.reservation_list_empty_title : R.string.booking_list_empty_title);
        }
        if (tvListEmptyDesc != null) {
            tvListEmptyDesc.setText(reservation ? R.string.no_reservations_yet : R.string.no_bookings_yet);
        }
        if (ivListEmptyIcon != null) {
            ivListEmptyIcon.setImageResource(reservation ? R.drawable.ic_reservation : R.drawable.ic_booking);
        }
        if (layoutBookingStatusFilter != null) {
            layoutBookingStatusFilter.setVisibility(reservation ? View.GONE : View.VISIBLE);
        }
        if (layoutReservationStatusFilter != null) {
            layoutReservationStatusFilter.setVisibility(reservation ? View.VISIBLE : View.GONE);
        }
        // Only one of New Booking/New Reservation is ever relevant to the
        // active tab - GONE (not INVISIBLE) so the other button expands to
        // fill the freed width instead of leaving a blank half-row.
        if (btnNewBooking != null) {
            btnNewBooking.setVisibility(reservation ? View.GONE : View.VISIBLE);
        }
        if (btnNewReservation != null) {
            btnNewReservation.setVisibility(reservation ? View.VISIBLE : View.GONE);
        }
        // Only the Booking List carries an existing payment, so the
        // cancellation/refund policy notice is only relevant there.
        if (cardCancellationPolicy != null) {
            cardCancellationPolicy.setVisibility(reservation ? View.GONE : View.VISIBLE);
        }
        // The Terms/Cancellation-Policy gate only applies to the Booking tab -
        // the Reservation tab has no payment step to gate.
        if (layoutTermsAgreement != null) {
            layoutTermsAgreement.setVisibility(reservation ? View.GONE : View.VISIBLE);
        }
        if (tvNoRoomsSelected != null) {
            tvNoRoomsSelected.setText(reservation ? R.string.no_rooms_selected_hint_reservation : R.string.no_rooms_selected_hint);
        }
        updateConfirmButtonEnabledState();
        applyListFilterLabels();
        listExpanded = false;
        // Switching between New Booking/New Reservation is a pure, instant
        // local swap of which cached list is shown (renderList() re-filters
        // the already-loaded allMyBookings via itemsForCurrentTab()) - no
        // network refetch here. Data freshness is still guaranteed by
        // onResume() and the shared bookingsChangedListener pub/sub, which
        // both already keep allMyBookings current in real time.
        renderList();
    }

    /**
     * tvListTitle now shows the full, category-specific list heading
     * ("Confirmed Booking List"/"Completed Reservation List"/etc., see the
     * list_title_* strings) instead of a fixed "Booking List"/"Reservation
     * List" - the currently selected status (see currentBookingCategory/
     * currentReservationCategory) also drives the "Showing: X" subtitle
     * below it, and is reflected in that tab's own Status Filter combo box
     * (see syncStatusFilterDisplay()). Defaults to Active, or whichever
     * subset a Dashboard tile deep-linked in with (see
     * {@link #EXTRA_LIST_FILTER}).
     */
    private void applyListFilterLabels() {
        if (tvListDesc == null) {
            return;
        }
        boolean reservation = isReservationMode();
        boolean showAll = reservation ? showAllReservationCategories : showAllBookingCategories;
        TransactionCategorizer.Category currentCategory = reservation ? currentReservationCategory : currentBookingCategory;
        int categoryLabelRes;
        int listTitleRes;
        int emptyDescRes;
        if (showAll) {
            listTitleRes = reservation ? R.string.list_title_all_reservations : R.string.list_title_all_bookings;
            categoryLabelRes = listTitleRes;
            emptyDescRes = reservation ? R.string.no_reservations_yet : R.string.no_bookings_yet;
            tvListTitle.setText(listTitleRes);
            tvListDesc.setText(getString(R.string.showing_category_format, getString(categoryLabelRes)));
            if (tvListEmptyDesc != null) {
                tvListEmptyDesc.setText(emptyDescRes);
            }
            syncStatusFilterDisplay();
            return;
        }
        switch (currentCategory) {
            case COMPLETED:
                categoryLabelRes = reservation ? R.string.completed_reservations_label : R.string.completed_bookings_label;
                listTitleRes = reservation ? R.string.list_title_completed_reservations : R.string.list_title_completed_bookings;
                emptyDescRes = reservation ? R.string.no_completed_reservations : R.string.no_completed_bookings;
                break;
            case CANCELLED:
                categoryLabelRes = reservation ? R.string.cancelled_reservations_label : R.string.cancelled_bookings_label;
                listTitleRes = reservation ? R.string.list_title_cancelled_reservations : R.string.list_title_cancelled_bookings;
                emptyDescRes = reservation ? R.string.no_cancelled_reservations : R.string.no_cancelled_bookings;
                break;
            default:
                // ACTIVE is a combined "current, not yet completed/cancelled"
                // bucket that also includes future-dated (Upcoming) items - see
                // itemsForCurrentTab(). UPCOMING is not reachable as its own
                // selectable category from this screen (no button drives it),
                // but the enum value itself stays alive for
                // TransactionCategorizer's date-based classification and other
                // screens (CalendarActivity, UpcomingTransactionsActivity,
                // TransactionListActivity).
                categoryLabelRes = reservation ? R.string.active_reservations_label : R.string.active_bookings_label;
                listTitleRes = reservation ? R.string.list_title_confirmed_reservations : R.string.list_title_confirmed_bookings;
                emptyDescRes = reservation ? R.string.no_confirmed_reservations : R.string.no_confirmed_bookings;
                break;
        }
        tvListTitle.setText(listTitleRes);
        tvListDesc.setText(getString(R.string.showing_category_format, getString(categoryLabelRes)));
        // Empty state now reflects the active status filter, not just the
        // active tab (see no_confirmed_bookings/no_completed_bookings/etc.) -
        // applyMode() still sets the generic "no bookings/reservations yet"
        // text first on every tab switch, this overrides it once a specific
        // status filter is known.
        if (tvListEmptyDesc != null) {
            tvListEmptyDesc.setText(emptyDescRes);
        }
        syncStatusFilterDisplay();
    }

    /**
     * Status Filter combo boxes replace the old four-button toggle row; same
     * end effect (filters rvItemList in place, see itemsForCurrentTab()) via
     * one exposed-dropdown per tab instead of four buttons per tab. Upcoming
     * is folded into Active/Confirmed here (see itemsForCurrentTab()) rather
     * than being its own option. TransactionListActivity itself is still
     * reachable from UpcomingTransactionsActivity's separate nav buttons,
     * unaffected by this.
     */
    private void setupStatusFilterDropdowns() {
        String[] bookingStatuses = {
                getString(R.string.booking_status_all),
                getString(R.string.booking_status_confirmed),
                getString(R.string.booking_status_completed),
                getString(R.string.booking_status_cancelled),
        };
        bookingStatusFilter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, bookingStatuses));
        bookingStatusFilter.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 1: selectCategory(false, TransactionCategorizer.Category.ACTIVE); break;
                case 2: selectCategory(false, TransactionCategorizer.Category.COMPLETED); break;
                case 3: selectCategory(false, TransactionCategorizer.Category.CANCELLED); break;
                default: selectAllStatuses(false); break;
            }
        });

        String[] reservationStatuses = {
                getString(R.string.reservation_status_all),
                getString(R.string.reservation_status_confirmed),
                getString(R.string.reservation_status_completed),
                getString(R.string.reservation_status_cancelled),
        };
        reservationStatusFilter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, reservationStatuses));
        reservationStatusFilter.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 1: selectCategory(true, TransactionCategorizer.Category.ACTIVE); break;
                case 2: selectCategory(true, TransactionCategorizer.Category.COMPLETED); break;
                case 3: selectCategory(true, TransactionCategorizer.Category.CANCELLED); break;
                default: selectAllStatuses(true); break;
            }
        });

        syncStatusFilterDisplay();
    }

    /** Sets one tab's category state without triggering a render - for handleIntentExtras()'s deep-link setup, before the first renderList() has even happened yet. Interactive selection goes through selectCategory() below instead. */
    private void setCategoryState(boolean reservation, TransactionCategorizer.Category category) {
        if (reservation) {
            showAllReservationCategories = false;
            currentReservationCategory = category;
        } else {
            showAllBookingCategories = false;
            currentBookingCategory = category;
        }
    }

    private void selectCategory(boolean reservation, TransactionCategorizer.Category category) {
        setCategoryState(reservation, category);
        listExpanded = false;
        applyListFilterLabels();
        renderList();
    }

    private void selectAllStatuses(boolean reservation) {
        if (reservation) {
            showAllReservationCategories = true;
        } else {
            showAllBookingCategories = true;
        }
        listExpanded = false;
        applyListFilterLabels();
        renderList();
    }

    /** Reflects each tab's OWN remembered filter in its own combo box - both are kept in sync regardless of which one is currently visible, so switching tabs never shows a stale value. */
    private void syncStatusFilterDisplay() {
        if (bookingStatusFilter != null) {
            bookingStatusFilter.setText(getString(bookingStatusLabelRes(showAllBookingCategories, currentBookingCategory)), false);
        }
        if (reservationStatusFilter != null) {
            reservationStatusFilter.setText(getString(reservationStatusLabelRes(showAllReservationCategories, currentReservationCategory)), false);
        }
    }

    private int bookingStatusLabelRes(boolean showAll, TransactionCategorizer.Category category) {
        if (showAll) return R.string.booking_status_all;
        switch (category) {
            case COMPLETED: return R.string.booking_status_completed;
            case CANCELLED: return R.string.booking_status_cancelled;
            default: return R.string.booking_status_confirmed;
        }
    }

    private int reservationStatusLabelRes(boolean showAll, TransactionCategorizer.Category category) {
        if (showAll) return R.string.reservation_status_all;
        switch (category) {
            case COMPLETED: return R.string.reservation_status_completed;
            case CANCELLED: return R.string.reservation_status_cancelled;
            default: return R.string.reservation_status_confirmed;
        }
    }

    private void setupListSection() {
        rvItemList.setLayoutManager(new LinearLayoutManager(this));
        btnExpandList.setOnClickListener(v -> {
            listExpanded = !listExpanded;
            renderList();
        });
    }


    /**
     * Reservation ids already checked (successfully or not) for the amenities-total
     * correction below - keeps a 30s auto-poll/pull-to-refresh from re-fetching the same
     * reservation's amenities on every single refresh once it's been checked once.
     */
    private final java.util.Set<String> amenitiesTotalCheckedIds = new java.util.HashSet<>();

    /**
     * booking.getTotalAmount() undercounts paid add-on amenities for a reservation that
     * hasn't converted into a Booking yet (see ApiMapper#toBooking(ReservationDto) - with no
     * Billing row yet, it falls back to room-cost-only, since the reservation list/detail API
     * response carries no amenities/total field of its own). This is exactly why the All
     * Reservations card could show ₱7,000 while Reservation Details (which independently
     * applies the same correction - see BookingDetailsActivity) correctly showed ₱7,200.
     *
     * Corrects each affected Booking's total in place using the same requestable-amenities
     * endpoint BillingSummaryActivity/BookingDetailsActivity already rely on, then re-renders
     * just the list - scoped to this screen (not RoomRepository's shared refreshBookings())
     * so the extra per-reservation network calls only happen where a total is actually shown
     * in a list, not on every screen that merely reads the shared bookings cache.
     */
    private void correctPendingReservationTotals(List<Booking> bookingList) {
        if (bookingList == null) return;
        for (Booking b : bookingList) {
            if (b.isHasBooking() || b.isDirectBooking()) continue;
            if (!amenitiesTotalCheckedIds.add(b.getId())) continue;
            // Single source of truth for this correction - see
            // RoomRepository#correctPendingReservationTotal(). The id stays
            // marked "checked" above regardless of success/failure so a 30s
            // auto-poll/pull-to-refresh doesn't refetch it every time.
            repository.correctPendingReservationTotal(b, new RoomRepository.RepositoryCallback<Booking>() {
                @Override
                public void onSuccess(Booking corrected) {
                    renderList();
                }

                @Override
                public void onError(String message) {
                    // correctPendingReservationTotal() itself never calls onError - present for
                    // RepositoryCallback's contract only.
                }
            });
        }
    }

    private void refreshMyBookings() {
        repository.refreshBookings(new RoomRepository.RepositoryCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> bookings) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                allMyBookings = bookings != null ? bookings : new ArrayList<>();
                renderList();
                correctPendingReservationTotals(allMyBookings);
            }

            @Override
            public void onError(String message) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                // allMyBookings deliberately keeps its last-known contents on error -
                // the visible list stays as-is rather than going blank; Retry re-runs
                // the same refresh in place.
                com.google.android.material.snackbar.Snackbar.make(
                                findViewById(R.id.bookingRoot),
                                "Couldn't refresh bookings: " + message,
                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .setAction(R.string.retry_label, v -> refreshMyBookings())
                        .show();
            }
        });
    }

    /**
     * Swipe-down-to-refresh over the whole scrollable screen (form + list) -
     * only fires when the guest is scrolled to the very top (SwipeRefreshLayout's
     * own canChildScrollUp() gate), so it never interrupts filling out the New
     * Booking/Reservation form mid-scroll. Reuses refreshMyBookings() - the same
     * real network refetch onResume()/post-action refreshes already use - so a
     * pull-down always shows genuine server state (e.g. a just-deleted booking
     * staying gone, or a receptionist's status change), never just a cached redraw.
     */
    private void setupSwipeRefresh() {
        if (swipeRefresh == null) return;
        swipeRefresh.setColorSchemeResources(R.color.velocity_red_primary);
        swipeRefresh.setOnRefreshListener(this::refreshMyBookings);
    }

    /**
     * Reservations = no payment record yet; Bookings = a payment has been
     * made (even if still pending staff verification). Mirrors the
     * website's My Reservations / My Bookings split. Further narrowed to
     * currentCategory (Upcoming/Active/Completed/Cancelled - see
     * TransactionCategorizer), the single source of truth this screen,
     * DashboardActivity, and TransactionListActivity all now share. Hidden
     * (deleted) transactions never reach allMyBookings in the first place -
     * the server excludes them from GET guest/reservations - so no local
     * filtering for that is needed here.
     */
    private List<Booking> itemsForCurrentTab() {
        boolean reservation = isReservationMode();
        boolean showAllCategories = reservation ? showAllReservationCategories : showAllBookingCategories;
        TransactionCategorizer.Category currentCategory = reservation ? currentReservationCategory : currentBookingCategory;
        List<Booking> filtered = new ArrayList<>();
        for (Booking b : allMyBookings) {
            // Keep only items whose actual type matches the active tab: the
            // Reservation tab wants isHasBooking()==false, the Booking tab
            // wants isHasBooking()==true - i.e. skip when they're EQUAL to
            // "reservation" (a reservation-tab item that IS a booking, or a
            // booking-tab item that ISN'T). Mirrors the (correct) pattern
            // already used in TransactionListActivity#loadTransactions.
            if (b.isHasBooking() == reservation) continue;
            TransactionCategorizer.Category category = TransactionCategorizer.categorize(b);
            // "All" (the default landing state, and always restored after a
            // new booking/reservation is created - see onNewIntent()) skips
            // the status filter entirely. Otherwise the Active bucket
            // ("Confirmed Booking"/"Pending/Confirmed Reservation" in the
            // Status Filter combo box) is a combined feed that includes both
            // true-Active and future-dated
            // (Upcoming) items - there's no separate Upcoming button/list;
            // a transaction with tomorrow's check-in already shows here
            // today, and simply re-renders under the same Active bucket
            // once its check-in date arrives (TransactionCategorizer
            // re-derives this live on every render, so no explicit "move"
            // step is needed).
            boolean matches = showAllCategories
                    || (currentCategory == TransactionCategorizer.Category.ACTIVE
                            ? (category == TransactionCategorizer.Category.ACTIVE || category == TransactionCategorizer.Category.UPCOMING)
                            : category == currentCategory);
            if (!matches) continue;
            if (!matchesSearch(b, searchQuery)) continue;
            if (!matchesRoomTypeFilter(b)) continue;
            filtered.add(b);
        }
        // The Reservation tab's Completed category (and All, which includes
        // every category) also includes every reservation that has since
        // converted into a Booking, shown as a frozen, view-only historical
        // record (see ApiMapper#toHistoricalReservation()) - these
        // deliberately never live in allMyBookings itself (would double-
        // count in Transaction History and clash with the real converted
        // Booking elsewhere), so they're merged in here, at render time,
        // for these buckets only.
        if (reservation && (showAllCategories || currentCategory == TransactionCategorizer.Category.COMPLETED)) {
            for (Booking historical : repository.getCompletedHistoricalReservations()) {
                if (matchesSearch(historical, searchQuery) && matchesRoomTypeFilter(historical)) {
                    filtered.add(historical);
                }
            }
        }
        sortByNewestCreatedFirst(filtered);
        if ("OLDEST".equals(sortOrder)) {
            Collections.reverse(filtered);
        }
        // Collapse a multi-room-type transaction's separate sibling records (see
        // BookingGroupState's own doc) down to one card in the default,
        // unfiltered view - BookingsAdapter renders the combined room/total
        // summary on whichever sibling survives here. Skipped while a search or
        // room-type filter is active, so a query matching only one room type
        // within a group still surfaces that exact record instead of being
        // hidden inside a collapsed card representing a different room type.
        if (searchQuery.isEmpty() && selectedRoomTypeFilter == null) {
            filtered = collapseGroupedTransactions(filtered);
        }
        return filtered;
    }

    /** See itemsForCurrentTab()'s own comment on when this runs. The first member encountered (already newest/oldest-sorted) represents the whole group; every later sibling is dropped from the visible list. */
    private List<Booking> collapseGroupedTransactions(List<Booking> source) {
        List<Booking> result = new ArrayList<>();
        java.util.Set<String> seenGroupRefs = new java.util.HashSet<>();
        for (Booking b : source) {
            String groupRef = BookingGroupState.getGroupRef(this, b.getId());
            if (groupRef == null || seenGroupRefs.add(groupRef)) {
                result.add(b);
            }
        }
        return result;
    }

    /** null (the default, "All Room Types") means no room-type restriction - see refreshTransactionRoomTypeDropdown(). */
    private boolean matchesRoomTypeFilter(Booking b) {
        return selectedRoomTypeFilter == null || b.hasRoomType(selectedRoomTypeFilter);
    }

    /**
     * Case-insensitive, null-safe match across every field a guest would
     * plausibly search by: reference/transaction number, room type, room
     * name, representative (stay-guest) name, status, and check-in/check-out
     * dates. Empty query always matches (search is opt-in). Combined with
     * the active status filter/tab by itemsForCurrentTab() above, so
     * "Completed" + "Executive" narrows to completed Executive-room
     * transactions only, per spec.
     */
    private boolean matchesSearch(Booking b, String query) {
        if (query == null || query.isEmpty()) return true;
        String q = query.toLowerCase(Locale.getDefault());
        return containsIgnoreCase(b.getId(), q)
                || b.anyRoomTypeContains(q)
                || containsIgnoreCase(b.getRoomName(), q)
                || containsIgnoreCase(b.getRepresentativeName(), q)
                || containsIgnoreCase(computeStatusLabel(b), q)
                || containsIgnoreCase(b.getCheckInDate(), q)
                || containsIgnoreCase(b.getCheckOutDate(), q);
    }

    private boolean containsIgnoreCase(String value, String lowerCaseQuery) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(lowerCaseQuery);
    }

    /**
     * Newest transaction first, by actual creation timestamp - the guest
     * must immediately see a transaction they just created at the top of
     * the list, not wherever its check-in date happens to sort it. Uses
     * Booking#getCreatedAtMillis() (parsed server-side created_at, see
     * ApiMapper) as the primary key; when that's unavailable (0 - an older
     * cached response predating this field), falls back to comparing the
     * numeric backend id, which is still monotonic with insertion order for
     * an auto-increment primary key even though it isn't itself a
     * timestamp.
     */
    private void sortByNewestCreatedFirst(List<Booking> items) {
        Collections.sort(items, (a, b) -> {
            long millisA = a.getCreatedAtMillis();
            long millisB = b.getCreatedAtMillis();
            if (millisA != 0 && millisB != 0) {
                return Long.compare(millisB, millisA);
            }
            if (millisA != 0) return -1;
            if (millisB != 0) return 1;
            return Long.compare(parseIdSafe(b.getId()), parseIdSafe(a.getId()));
        });
    }

    private long parseIdSafe(String id) {
        try {
            return id != null ? Long.parseLong(id) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void renderList() {
        List<Booking> source = itemsForCurrentTab();
        if (tvListCount != null) {
            tvListCount.setText(String.valueOf(source.size()));
        }

        if (source.isEmpty()) {
            rvItemList.setVisibility(View.GONE);
            tvListEmpty.setVisibility(View.VISIBLE);
            btnExpandList.setVisibility(View.GONE);
            // A search with no matches is a different situation from "you have
            // no bookings/reservations at all yet" - the guest may well have
            // transactions, just none matching this query, so say that instead
            // of implying they need to create one. Always re-derived here
            // (rather than only overriding when a search starts) so clearing
            // the search box correctly restores the normal empty-state title -
            // this is the single source of truth for tvListEmptyTitle's text,
            // not just a conditional patch on top of applyMode()'s default.
            if (tvListEmptyTitle != null && tvListEmptyDesc != null) {
                if (!searchQuery.isEmpty()) {
                    tvListEmptyTitle.setText(isReservationMode()
                            ? R.string.search_no_reservations_found_title
                            : R.string.search_no_bookings_found_title);
                    tvListEmptyDesc.setText(R.string.search_empty_state_desc);
                } else {
                    tvListEmptyTitle.setText(isReservationMode()
                            ? R.string.reservation_list_empty_title
                            : R.string.booking_list_empty_title);
                    // The category-specific description (no_confirmed_bookings/
                    // no_completed_reservations/etc., or the generic "yet"
                    // message for All) was already set by applyListFilterLabels(),
                    // which runs on every tab/filter change - left untouched here.
                }
            }
            consumePendingListFilterScroll();
            return;
        }

        tvListEmpty.setVisibility(View.GONE);
        rvItemList.setVisibility(View.VISIBLE);

        // Locate a highlighted item so it can be revealed even if it sits
        // beyond the collapsed window.
        int highlightIndex = -1;
        if (highlightBookingId != null) {
            for (int i = 0; i < source.size(); i++) {
                if (highlightBookingId.equals(source.get(i).getId())) {
                    highlightIndex = i;
                    break;
                }
            }
            if (highlightIndex >= COLLAPSED_LIST_COUNT) {
                listExpanded = true;
            }
        }

        List<Booking> display = (listExpanded || source.size() <= COLLAPSED_LIST_COUNT)
                ? source
                : new ArrayList<>(source.subList(0, COLLAPSED_LIST_COUNT));
        rvItemList.setAdapter(new BookingsAdapter(display, !isReservationMode(), highlightBookingId));

        if (highlightIndex >= 0 && highlightIndex < display.size() && highlightScrollPending) {
            highlightScrollPending = false;
            final int position = highlightIndex;
            rvItemList.post(() -> {
                RecyclerView.ViewHolder holder = rvItemList.findViewHolderForAdapterPosition(position);
                if (holder != null) {
                    smoothScrollToChild(holder.itemView);
                    pulseHighlight(holder.itemView);
                } else {
                    scrollToList();
                }
            });
            scheduleHighlightClear();
        }

        if (source.size() <= COLLAPSED_LIST_COUNT) {
            btnExpandList.setVisibility(View.GONE);
        } else {
            btnExpandList.setVisibility(View.VISIBLE);
            if (listExpanded) {
                btnExpandList.setText(R.string.collapse_label);
                btnExpandList.setIconResource(R.drawable.ic_expand_less);
            } else {
                btnExpandList.setText(getString(R.string.expand_count_label, source.size()));
                btnExpandList.setIconResource(R.drawable.ic_expand_more);
            }
        }

        consumePendingListFilterScroll();
    }

    /**
     * Fires once, HIGHLIGHT_DURATION_MS after the highlighted card is first
     * shown - clears highlightBookingId and re-renders so the red outline
     * disappears on its own, matching the "temporary highlight, not a
     * permanent marker" spec. Any previously-scheduled clear is cancelled
     * first so a rapid second highlight (e.g. the guest creates another
     * transaction before the first clear fires) restarts the full duration
     * instead of being cut short by the earlier one.
     */
    private void scheduleHighlightClear() {
        if (highlightClearRunnable != null) {
            highlightClearHandler.removeCallbacks(highlightClearRunnable);
        }
        highlightClearRunnable = () -> {
            highlightBookingId = null;
            renderList();
        };
        highlightClearHandler.postDelayed(highlightClearRunnable, HIGHLIGHT_DURATION_MS);
    }

    /** One-time scroll to the list section when arriving via a Dashboard summary tile (see {@link #EXTRA_LIST_FILTER}). */
    private void consumePendingListFilterScroll() {
        if (listFilterScrollPending) {
            listFilterScrollPending = false;
            scrollToList();
        }
    }

    private void setupRoomSelection() {
        refreshAvailableRooms();
        btnAddRoom.setOnClickListener(v -> onAddRoomClicked());
    }

    /** Number of physical rooms of {@code id} already in the current selection. */
    private int currentQtyForId(String id) {
        int count = 0;
        for (Room r : selectedRooms) {
            if (r.getId().equals(id)) count++;
        }
        return count;
    }

    /**
     * Opens the Available Room Form. Refreshes room inventory for the current
     * stay window first (the confirmed "refetch-on-open" real-time-availability
     * approach), then shows every room type that still has at least one unit
     * free after subtracting what's already in this booking/reservation.
     */
    private void onAddRoomClicked() {
        loadingOverlay.setVisibility(View.VISIBLE);
        repository.refreshRooms(checkInCal, checkOutCal, new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> result) {
                loadingOverlay.setVisibility(View.GONE);
                allRooms = result;
                refreshAvailableRooms();
                openAvailableRoomsDialogOrToast();
            }

            @Override
            public void onError(String message) {
                loadingOverlay.setVisibility(View.GONE);
                openAvailableRoomsDialogOrToast();
            }
        });
    }

    private void openAvailableRoomsDialogOrToast() {
        List<Room> browsable = new ArrayList<>();
        for (Room r : allRooms) {
            if (!repository.isAvailableForDates(r, checkInCal, checkOutCal, editingBookingId)) continue;
            if (r.getAvailableCount() - currentQtyForId(r.getId()) <= 0) continue;
            browsable.add(r);
        }
        if (browsable.isEmpty()) {
            Toast.makeText(this, R.string.no_rooms_available, Toast.LENGTH_LONG).show();
            return;
        }
        showAvailableRoomsDialog(browsable);
    }

    /**
     * Shows the Available Room List: one card per browsable room type, each with its
     * own qty stepper and a toggleable Select control. Tapping Select stages that room
     * type/quantity into {@code stagedRooms}/{@code stagedQuantities} without closing
     * the dialog, so the guest can pick from one or several room types before pressing
     * the top-right Book Now/Reserve Now button (label depends on the active tab) to
     * commit everything at once.
     */
    private void showAvailableRoomsDialog(List<Room> rooms) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_available_rooms, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.VelocityDialogTheme)
                .setView(dialogView)
                .create();

        TextView tvSubtitle = dialogView.findViewById(R.id.tvAvailableRoomsSubtitle);
        View btnClose = dialogView.findViewById(R.id.btnCloseAvailableRooms);
        LinearLayout listContainer = dialogView.findViewById(R.id.layoutAvailableRoomsList);
        View emptyState = dialogView.findViewById(R.id.layoutAvailableRoomsEmpty);
        TextView tvStagedCount = dialogView.findViewById(R.id.tvStagedRoomsCount);
        MaterialButton btnCommit = dialogView.findViewById(R.id.btnBookOrReserveRoom);
        btnCommit.setText(isReservationMode() ? R.string.reserve_now : R.string.book_now);

        if (hasEnteredCheckIn() && hasEnteredCheckOut()) {
            tvSubtitle.setText(getString(R.string.available_rooms_subtitle_format,
                    dateFormat.format(checkInCal.getTime()), dateFormat.format(checkOutCal.getTime()), (int) selectedNights()));
        } else {
            tvSubtitle.setText(R.string.available_rooms_subtitle_placeholder);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // id -> representative Room / staged quantity, populated as cards are toggled on.
        Map<String, Room> stagedRooms = new LinkedHashMap<>();
        Map<String, Integer> stagedQuantities = new LinkedHashMap<>();
        Runnable updateFooter = () -> {
            int typeCount = stagedRooms.size();
            tvStagedCount.setText(typeCount == 0
                    ? getString(R.string.rooms_staged_count_none)
                    : getResources().getQuantityString(R.plurals.rooms_staged_count, typeCount, typeCount));
            btnCommit.setEnabled(typeCount > 0);
        };
        updateFooter.run();

        listContainer.removeAllViews();
        for (Room room : rooms) {
            listContainer.addView(buildAvailableRoomCard(room, stagedRooms, stagedQuantities, updateFooter));
        }
        emptyState.setVisibility(rooms.isEmpty() ? View.VISIBLE : View.GONE);

        btnCommit.setOnClickListener(v -> {
            List<String> trimmedOrDropped = new ArrayList<>();
            for (Map.Entry<String, Room> entry : stagedRooms.entrySet()) {
                String roomId = entry.getKey();
                Room staged = entry.getValue();
                int requestedQty = stagedQuantities.getOrDefault(roomId, 0);
                if (requestedQty <= 0) continue;

                // Re-validate against the freshest known inventory in case another
                // guest booked some of this type while the dialog was open.
                Room fresh = staged;
                for (Room r : allRooms) {
                    if (r.getId().equals(roomId)) {
                        fresh = r;
                        break;
                    }
                }
                int remaining = fresh.getAvailableCount() - currentQtyForId(roomId);
                int qtyToAdd = Math.min(requestedQty, Math.max(0, remaining));
                if (qtyToAdd <= 0) {
                    trimmedOrDropped.add(fresh.getName());
                    continue;
                }
                if (qtyToAdd < requestedQty) {
                    trimmedOrDropped.add(fresh.getName());
                }
                addRoomsToSelection(fresh, qtyToAdd);
            }
            if (!trimmedOrDropped.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_more_rooms_available_toast,
                        String.join(", ", trimmedOrDropped)), Toast.LENGTH_LONG).show();
            }
            dialog.dismiss();
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    /** Builds one room-type card for the Available Room List, with a bounded qty stepper and a toggleable Select control. */
    private View buildAvailableRoomCard(Room room, Map<String, Room> stagedRooms,
                                         Map<String, Integer> stagedQuantities, Runnable updateFooter) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_available_room, null, false);

        TextView tvName = card.findViewById(R.id.roomName);
        TextView tvTypeAndCapacity = card.findViewById(R.id.roomTypeAndCapacity);
        TextView tvBedType = card.findViewById(R.id.roomBedType);
        TextView tvRoomSize = card.findViewById(R.id.roomSizeText);
        TextView tvDescription = card.findViewById(R.id.roomDescription);
        TextView tvPrice = card.findViewById(R.id.roomPrice);
        TextView tvAvailableBadge = card.findViewById(R.id.roomAvailableBadge);
        ImageView ivImage = card.findViewById(R.id.roomImage);
        ImageView ivTypeIcon = card.findViewById(R.id.roomTypeIcon);
        LinearLayout amenitiesRow = card.findViewById(R.id.amenitiesIconsRow);
        TextView tvQty = card.findViewById(R.id.tvQty);
        MaterialButton btnQtyMinus = card.findViewById(R.id.btnQtyMinus);
        MaterialButton btnQtyPlus = card.findViewById(R.id.btnQtyPlus);
        MaterialButton btnSelectRoom = card.findViewById(R.id.btnSelectRoom);

        int remainingBase = Math.max(0, room.getAvailableCount() - currentQtyForId(room.getId()));
        int maxQty = Math.max(1, remainingBase);
        final int[] qty = {1};
        final boolean[] selected = {false};

        tvName.setText(room.getName());
        tvTypeAndCapacity.setText(getString(R.string.room_type_capacity_format, room.getType(), room.getCapacity()));
        tvBedType.setText(room.getBedType() == null || room.getBedType().isEmpty()
                ? RoomVisuals.getBedType(this, room.getType(), room.getCapacity())
                : room.getBedType());
        tvRoomSize.setText(room.getRoomSize() == null || room.getRoomSize().isEmpty()
                ? getString(R.string.not_specified)
                : room.getRoomSize());
        tvDescription.setText(room.getDescription());
        tvPrice.setText(getString(R.string.price_format_per_night, room.getPricePerNight()));
        ivTypeIcon.setImageResource(RoomVisuals.getTypeIcon(room.getType()));
        tvQty.setText(String.valueOf(qty[0]));

        int fallbackImage = RoomVisuals.getRoomImage(room.getType());
        if (room.getImageUrl() != null && !room.getImageUrl().isEmpty()) {
            Glide.with(this).load(room.getImageUrl()).placeholder(fallbackImage).error(fallbackImage).into(ivImage);
        } else if (room.getImageResId() != 0) {
            ivImage.setImageResource(room.getImageResId());
        } else {
            ivImage.setImageResource(fallbackImage);
        }

        bindAvailableRoomAmenities(amenitiesRow, room);

        // Live "remaining after this pick" badge: starts at the true remaining count
        // (already-selected quantity subtracted) and ticks down further while this
        // card is toggled Selected, e.g. "Available: 5" -> "Available: 4" the moment
        // 1 unit of this room type is selected - matching real-time availability.
        Runnable updateBadge = () -> tvAvailableBadge.setText(getString(R.string.available_qty_format,
                Math.max(0, remainingBase - (selected[0] ? qty[0] : 0))));
        updateBadge.run();

        Runnable updateSelectButtonStyle = () -> {
            if (selected[0]) {
                btnSelectRoom.setText(R.string.select_room_button_selected);
                btnSelectRoom.setIconResource(R.drawable.ic_close);
                btnSelectRoom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.velocity_red_primary)));
                btnSelectRoom.setTextColor(ContextCompat.getColor(this, R.color.white));
                btnSelectRoom.setIconTint(android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.white)));
            } else {
                btnSelectRoom.setText(R.string.select_room_button);
                btnSelectRoom.setIconResource(R.drawable.ic_add);
                btnSelectRoom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, android.R.color.transparent)));
                btnSelectRoom.setTextColor(ContextCompat.getColor(this, R.color.velocity_text_primary));
                btnSelectRoom.setIconTint(android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.velocity_red_primary)));
            }
        };
        updateSelectButtonStyle.run();

        btnQtyMinus.setOnClickListener(v -> {
            if (qty[0] > 1) {
                qty[0]--;
                tvQty.setText(String.valueOf(qty[0]));
                updateBadge.run();
                if (selected[0]) {
                    stagedQuantities.put(room.getId(), qty[0]);
                }
            }
        });
        btnQtyPlus.setOnClickListener(v -> {
            if (qty[0] < maxQty) {
                qty[0]++;
                tvQty.setText(String.valueOf(qty[0]));
                updateBadge.run();
                if (selected[0]) {
                    stagedQuantities.put(room.getId(), qty[0]);
                }
            } else {
                Toast.makeText(this, getString(R.string.no_more_rooms_available_toast, room.getName()), Toast.LENGTH_SHORT).show();
            }
        });
        btnSelectRoom.setOnClickListener(v -> {
            selected[0] = !selected[0];
            if (selected[0]) {
                stagedRooms.put(room.getId(), room);
                stagedQuantities.put(room.getId(), qty[0]);
            } else {
                stagedRooms.remove(room.getId());
                stagedQuantities.remove(room.getId());
            }
            updateSelectButtonStyle.run();
            updateFooter.run();
        });

        return card;
    }

    /** Mirrors RoomAdapter.bindAmenities's icon-row rendering for the Available Room Form's cards. */
    private void bindAvailableRoomAmenities(LinearLayout amenitiesRow, Room room) {
        amenitiesRow.removeAllViews();
        List<RoomAmenity> amenities = room.getAmenities();
        if (amenities == null) return;
        int maxIcons = 4;
        int shown = Math.min(amenities.size(), maxIcons);
        float density = getResources().getDisplayMetrics().density;
        int iconSize = Math.round(16 * density);
        int iconMarginEnd = Math.round(12 * density);
        android.content.res.ColorStateList grayTint = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.velocity_inactive_gray));

        for (int i = 0; i < shown; i++) {
            RoomAmenity amenity = amenities.get(i);
            ImageView icon = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(iconSize, iconSize);
            params.setMarginEnd(iconMarginEnd);
            icon.setLayoutParams(params);
            icon.setImageResource(RoomVisuals.getAmenityIcon(amenity.getName()));
            icon.setImageTintList(grayTint);
            icon.setContentDescription(amenity.getName());
            amenitiesRow.addView(icon);
        }
        int remaining = amenities.size() - shown;
        if (remaining > 0) {
            TextView more = new TextView(this);
            more.setText(getString(R.string.amenities_more_format, remaining));
            more.setTextColor(ContextCompat.getColor(this, R.color.velocity_inactive_gray));
            more.setTextSize(12f);
            amenitiesRow.addView(more);
        }
    }

    /**
     * Adds {@code quantity} physical rooms of {@code room} to the current
     * selection (duplicate id entries ARE the quantity mechanism - the
     * backend takes one reservation call per entry, see createRoomReservations).
     * Shared by the Available Room Form's Select Room button and the
     * ROOM_ID deep-link path in {@link #handleIntentExtras()}.
     */
    private void addRoomsToSelection(Room room, int quantity) {
        for (int i = 0; i < quantity; i++) {
            selectedRooms.add(room);
        }

        tilCheckIn.setEnabled(true);
        tilCheckOut.setEnabled(true);

        // Trim additional guests if the new combined capacity can no longer fit them all
        int allowedAdditional = totalSelectedCapacity() - 1;
        while (layoutAdditionalGuests.getChildCount() > allowedAdditional && allowedAdditional >= 0) {
            layoutAdditionalGuests.removeViewAt(layoutAdditionalGuests.getChildCount() - 1);
        }
        renumberGuests();

        renderSelectedRooms();
        refreshAvailableRooms();
        updateCapacityIndicator();
        updateSummary();
        Toast.makeText(this, getString(R.string.room_added_toast, room.getName()), Toast.LENGTH_SHORT).show();
    }

    /**
     * Rebuilds layoutSelectedRooms from scratch, grouping the flat
     * selectedRooms list by room id so each room TYPE gets one row showing
     * its quantity, image, price, and subtotal - with a stepper to adjust
     * quantity in place and a button to remove the whole group.
     */
    private void renderSelectedRooms() {
        if (layoutSelectedRooms == null) return;
        layoutSelectedRooms.removeAllViews();

        Map<String, List<Room>> grouped = new LinkedHashMap<>();
        for (Room r : selectedRooms) {
            grouped.computeIfAbsent(r.getId(), k -> new ArrayList<>()).add(r);
        }

        for (Map.Entry<String, List<Room>> entry : grouped.entrySet()) {
            List<Room> group = entry.getValue();
            Room representative = group.get(0);
            int qty = group.size();

            View row = LayoutInflater.from(this).inflate(R.layout.item_selected_room, layoutSelectedRooms, false);
            row.setTag(entry.getKey());

            TextView tvName = row.findViewById(R.id.tvSelectedRoomName);
            TextView tvMeta = row.findViewById(R.id.tvSelectedRoomMeta);
            TextView tvQty = row.findViewById(R.id.tvSelectedRoomQty);
            TextView tvSubtotal = row.findViewById(R.id.tvSelectedRoomSubtotal);
            ImageView ivImage = row.findViewById(R.id.ivSelectedRoomImage);
            MaterialButton btnDecrease = row.findViewById(R.id.btnDecreaseQty);
            MaterialButton btnIncrease = row.findViewById(R.id.btnIncreaseQty);
            View btnRemove = row.findViewById(R.id.btnRemoveSelectedRoom);

            tvName.setText(representative.getName());
            tvMeta.setText(getString(R.string.capacity_persons_format, representative.getCapacity())
                    + " · " + String.format(Locale.US, getString(R.string.price_format_per_night), representative.getPricePerNight()));
            tvQty.setText(String.valueOf(qty));
            tvSubtotal.setText(String.format(Locale.US, getString(R.string.price_format), roomSubtotal(representative) * qty));

            int fallbackImage = RoomVisuals.getRoomImage(representative.getType());
            if (representative.getImageUrl() != null && !representative.getImageUrl().isEmpty()) {
                Glide.with(this).load(representative.getImageUrl()).placeholder(fallbackImage).error(fallbackImage).into(ivImage);
            } else if (representative.getImageResId() != 0) {
                ivImage.setImageResource(representative.getImageResId());
            } else {
                ivImage.setImageResource(fallbackImage);
            }

            if (editingBookingId != null) {
                // The update endpoint can't change a reservation's room, so the
                // room row is locked (display-only) while editing.
                btnDecrease.setVisibility(View.GONE);
                btnIncrease.setVisibility(View.GONE);
                btnRemove.setVisibility(View.GONE);
            } else {
                btnDecrease.setOnClickListener(v -> adjustRoomQuantity(entry.getKey(), -1));
                btnIncrease.setOnClickListener(v -> adjustRoomQuantity(entry.getKey(), 1));
                btnRemove.setOnClickListener(v -> removeSelectedRoom(entry.getKey()));
            }

            layoutSelectedRooms.addView(row);
        }

        layoutNoRoomsSelected.setVisibility(selectedRooms.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Removes every physical room of {@code roomId} from the current selection. */
    private void removeSelectedRoom(String roomId) {
        String removedName = null;
        for (Room r : selectedRooms) {
            if (r.getId().equals(roomId)) {
                removedName = r.getName();
                break;
            }
        }
        selectedRooms.removeIf(existing -> existing.getId().equals(roomId));

        if (selectedRooms.isEmpty()) {
            tilCheckIn.setEnabled(false);
            tilCheckOut.setEnabled(false);
        }

        int allowedAdditional = Math.max(0, totalSelectedCapacity() - 1);
        while (layoutAdditionalGuests.getChildCount() > allowedAdditional) {
            layoutAdditionalGuests.removeViewAt(layoutAdditionalGuests.getChildCount() - 1);
        }
        renumberGuests();

        renderSelectedRooms();
        refreshAvailableRooms();
        updateCapacityIndicator();
        updateSummary();
        if (removedName != null) {
            Toast.makeText(this, getString(R.string.room_removed_toast, removedName), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * +1/-1 to the quantity of room type {@code roomId} already selected.
     * Increasing is capped at the freshest known availableCount so a guest
     * can never select more of a type than currently exists; hitting 0 on a
     * decrement removes the group entirely.
     */
    private void adjustRoomQuantity(String roomId, int delta) {
        if (delta > 0) {
            Room fresh = null;
            for (Room r : allRooms) {
                if (r.getId().equals(roomId)) {
                    fresh = r;
                    break;
                }
            }
            int cap = fresh != null ? fresh.getAvailableCount() : currentQtyForId(roomId);
            if (currentQtyForId(roomId) >= cap) {
                Toast.makeText(this, getString(R.string.no_more_rooms_available_toast,
                        fresh != null ? fresh.getName() : ""), Toast.LENGTH_SHORT).show();
                return;
            }
            Room toAdd = fresh;
            if (toAdd == null) {
                for (Room r : selectedRooms) {
                    if (r.getId().equals(roomId)) {
                        toAdd = r;
                        break;
                    }
                }
            }
            if (toAdd == null) return;
            selectedRooms.add(toAdd);

            int allowedAdditional = totalSelectedCapacity() - 1;
            while (layoutAdditionalGuests.getChildCount() > allowedAdditional && allowedAdditional >= 0) {
                layoutAdditionalGuests.removeViewAt(layoutAdditionalGuests.getChildCount() - 1);
            }
            renumberGuests();
        } else {
            for (int i = selectedRooms.size() - 1; i >= 0; i--) {
                if (selectedRooms.get(i).getId().equals(roomId)) {
                    selectedRooms.remove(i);
                    break;
                }
            }
            if (currentQtyForId(roomId) == 0 && selectedRooms.isEmpty()) {
                tilCheckIn.setEnabled(false);
                tilCheckOut.setEnabled(false);
            }
            int allowedAdditional = Math.max(0, totalSelectedCapacity() - 1);
            while (layoutAdditionalGuests.getChildCount() > allowedAdditional) {
                layoutAdditionalGuests.removeViewAt(layoutAdditionalGuests.getChildCount() - 1);
            }
            renumberGuests();
        }

        renderSelectedRooms();
        refreshAvailableRooms();
        updateCapacityIndicator();
        updateSummary();
    }

    private int totalSelectedCapacity() {
        int total = 0;
        for (Room r : selectedRooms) total += r.getCapacity();
        return total;
    }

    private long selectedNights() {
        return StayDateCalculator.nightsBetween(checkInCal, checkOutCal);
    }

    private double roomSubtotal(Room r) {
        return r.getPricePerNight() * selectedNights();
    }

    private void updateCapacityIndicator() {
        if (selectedRooms.isEmpty()) {
            tvCapacityIndicator.setText(R.string.add_room_hint);
        } else {
            tvCapacityIndicator.setText(getResources().getQuantityString(
                    R.plurals.total_capacity_format, totalSelectedCapacity(), totalSelectedCapacity()));
        }
        if (tvRoomsSelectedCount != null) {
            if (selectedRooms.isEmpty()) {
                tvRoomsSelectedCount.setText(R.string.no_rooms_selected_yet);
            } else {
                tvRoomsSelectedCount.setText(getResources().getQuantityString(
                        R.plurals.rooms_selected_count, selectedRooms.size(), selectedRooms.size()));
            }
        }
        updateAmenitiesSectionVisibility();
    }

    /**
     * Choose Amenity only makes sense once the guest has picked at least one
     * room, so it stays hidden until then (and hides again if every room is
     * removed). Called from updateCapacityIndicator(), the common point hit
     * by every selection mutation (add/remove/adjust/refresh).
     */
    private void updateAmenitiesSectionVisibility() {
        if (layoutAmenitiesSection == null) return;
        layoutAmenitiesSection.setVisibility(selectedRooms.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * Room availability is date-range-aware on the backend, so a date change
     * must re-query with the chosen stay window - the cached flags only
     * reflect whatever range was last requested. Falls back to filtering the
     * cache if the network call fails.
     */
    private void refreshRoomsForSelectedDates() {
        repository.refreshRooms(checkInCal, checkOutCal, new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> result) {
                allRooms = result;
                refreshAvailableRooms();
            }

            @Override
            public void onError(String message) {
                refreshAvailableRooms();
            }
        });
    }

    /**
     * Re-points every selected room GROUP at freshly fetched Room instances
     * (so price/capacity/availableCount reflect the latest server state),
     * drops a whole group if its type is no longer available for the current
     * date range, and clamps a group's quantity down if availableCount has
     * shrunk below what's already selected (another guest booked some of the
     * same type in the meantime) - the concrete "never let a guest hold more
     * than what's available" + real-time-shrink safety net.
     */
    private void refreshAvailableRooms() {
        Map<String, List<Room>> grouped = new LinkedHashMap<>();
        for (Room r : selectedRooms) {
            grouped.computeIfAbsent(r.getId(), k -> new ArrayList<>()).add(r);
        }

        List<String> droppedRoomNames = new ArrayList<>();
        List<String> trimmedRoomNames = new ArrayList<>();
        List<Room> rebuilt = new ArrayList<>();

        for (Map.Entry<String, List<Room>> entry : grouped.entrySet()) {
            List<Room> group = entry.getValue();
            Room selected = group.get(0);
            Room fresh = null;
            for (Room r : allRooms) {
                if (r.getId().equals(selected.getId())) {
                    fresh = r;
                    break;
                }
            }
            boolean stillAvailable = fresh != null
                    && (!hasEnteredCheckIn() || !hasEnteredCheckOut()
                        || repository.isAvailableForDates(fresh, checkInCal, checkOutCal, editingBookingId));
            if (!stillAvailable) {
                droppedRoomNames.add(selected.getName());
                continue;
            }
            int qty = group.size();
            if (fresh.getAvailableCount() < qty) {
                qty = Math.max(0, fresh.getAvailableCount());
                if (qty == 0) {
                    droppedRoomNames.add(selected.getName());
                    continue;
                }
                trimmedRoomNames.add(fresh.getName());
            }
            for (int i = 0; i < qty; i++) rebuilt.add(fresh);
        }

        selectedRooms.clear();
        selectedRooms.addAll(rebuilt);
        renderSelectedRooms();

        if (!droppedRoomNames.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_room_unavailable) + " (" + String.join(", ", droppedRoomNames) + ")", Toast.LENGTH_LONG).show();
        }
        if (!trimmedRoomNames.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_more_rooms_available_toast, String.join(", ", trimmedRoomNames)), Toast.LENGTH_LONG).show();
        }
        if (selectedRooms.isEmpty()) {
            tilCheckIn.setEnabled(false);
            tilCheckOut.setEnabled(false);
        }

        boolean anyBrowsable = false;
        for (Room r : allRooms) {
            if (repository.isAvailableForDates(r, checkInCal, checkOutCal, editingBookingId)
                    && r.getAvailableCount() - currentQtyForId(r.getId()) > 0) {
                anyBrowsable = true;
                break;
            }
        }
        if (tvRoomSelectionHelper != null) {
            if (!anyBrowsable) {
                tvRoomSelectionHelper.setText(R.string.no_rooms_available);
                tvRoomSelectionHelper.setTextColor(ContextCompat.getColor(this, R.color.velocity_red_primary));
            } else {
                tvRoomSelectionHelper.setText(R.string.booking_select_room_helper_multi);
                tvRoomSelectionHelper.setTextColor(ContextCompat.getColor(this, R.color.velocity_text_secondary));
            }
        }

        updateCapacityIndicator();
        updateSummary();
    }

    private void setupDatePickers() {
        tilCheckIn.setEnabled(false);
        tilCheckOut.setEnabled(false);
        etCheckIn.setOnClickListener(v -> showDatePicker(true));
        etCheckOut.setOnClickListener(v -> showDatePicker(false));
    }

    private boolean hasEnteredCheckIn() {
        return etCheckIn.getText() != null && !etCheckIn.getText().toString().trim().isEmpty();
    }

    private boolean hasEnteredCheckOut() {
        return etCheckOut.getText() != null && !etCheckOut.getText().toString().trim().isEmpty();
    }

    /** True when every currently selected room is available across the given range. */
    private boolean allSelectedRoomsAvailable(Calendar in, Calendar out) {
        for (Room r : selectedRooms) {
            if (!repository.isAvailableForDates(r, in, out, editingBookingId)) {
                return false;
            }
        }
        return true;
    }

    /** True when every currently selected room is available on the given single day. */
    private boolean allSelectedRoomsAvailableOnDay(long timeInMillis) {
        for (Room r : selectedRooms) {
            if (!repository.isDayAvailable(r.getId(), timeInMillis, editingBookingId)) {
                return false;
            }
        }
        return true;
    }

    private void showDatePicker(boolean isCheckIn) {
        if (selectedRooms.isEmpty()) {
            Toast.makeText(this, R.string.error_no_rooms_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isCheckIn && !hasEnteredCheckIn()) {
            tilCheckIn.setError(getString(R.string.error_select_checkin_first));
            Toast.makeText(this, R.string.error_select_checkin_first, Toast.LENGTH_SHORT).show();
            return;
        }
        Calendar activeCal = isCheckIn ? checkInCal : checkOutCal;
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar newDate = Calendar.getInstance();
            newDate.set(year, month, dayOfMonth, 0, 0, 0);
            newDate.set(Calendar.MILLISECOND, 0);

            if (isCheckIn) {
                if (!allSelectedRoomsAvailableOnDay(newDate.getTimeInMillis())) {
                    Toast.makeText(this, R.string.error_room_unavailable, Toast.LENGTH_SHORT).show();
                    return;
                }

                boolean hadCheckOut = hasEnteredCheckOut();
                checkInCal.setTime(newDate.getTime());
                etCheckIn.setText(dateFormat.format(checkInCal.getTime()));
                tilCheckIn.setError(null);

                // Adjust check-out if it's before or equal to check-in
                if (!checkOutCal.after(checkInCal)) {
                    checkOutCal.setTime(checkInCal.getTime());
                    checkOutCal.add(Calendar.DAY_OF_YEAR, 1);

                    // Keep moving checkout forward until a valid range is found (minimal 1 night)
                    int safetyCount = 0;
                    while (!allSelectedRoomsAvailable(checkInCal, checkOutCal) && safetyCount < 365) {
                        checkOutCal.add(Calendar.DAY_OF_YEAR, 1);
                        safetyCount++;
                    }
                    // Only surface the adjusted date if the guest had already
                    // chosen a check-out; otherwise they still pick it themselves
                    if (hadCheckOut) {
                        etCheckOut.setText(dateFormat.format(checkOutCal.getTime()));
                    }
                } else if (hadCheckOut) {
                    // Check if the existing range is still available with the new check-in
                    if (!allSelectedRoomsAvailable(checkInCal, checkOutCal)) {
                        // Find the maximum possible checkout date that is still available from this check-in
                        checkOutCal.setTime(checkInCal.getTime());
                        checkOutCal.add(Calendar.DAY_OF_YEAR, 1);

                        if (allSelectedRoomsAvailable(checkInCal, checkOutCal)) {
                            while (allSelectedRoomsAvailable(checkInCal, checkOutCal)) {
                                checkOutCal.add(Calendar.DAY_OF_YEAR, 1);
                            }
                            checkOutCal.add(Calendar.DAY_OF_YEAR, -1); // Back to last valid night
                        }
                        etCheckOut.setText(dateFormat.format(checkOutCal.getTime()));
                    }
                }
                updateSummary();
                refreshRoomsForSelectedDates();
            } else {
                if (newDate.before(checkInCal) || newDate.equals(checkInCal)) {
                    Toast.makeText(this, R.string.error_invalid_dates, Toast.LENGTH_SHORT).show();
                } else if (allSelectedRoomsAvailable(checkInCal, newDate)) {
                    checkOutCal.setTime(newDate.getTime());
                    etCheckOut.setText(dateFormat.format(checkOutCal.getTime()));
                    tilCheckOut.setError(null);
                    updateSummary();
                    refreshRoomsForSelectedDates();
                } else {
                    Toast.makeText(this, R.string.error_room_unavailable, Toast.LENGTH_SHORT).show();
                }
            }
        }, activeCal.get(Calendar.YEAR), activeCal.get(Calendar.MONTH), activeCal.get(Calendar.DAY_OF_MONTH));

        // Check-in can start no earlier than tomorrow; check-out no earlier
        // than one night after the chosen check-in
        Calendar minDate = Calendar.getInstance();
        if (isCheckIn) {
            minDate.add(Calendar.DAY_OF_YEAR, 1);
        } else {
            minDate.setTime(checkInCal.getTime());
            minDate.add(Calendar.DAY_OF_YEAR, 1);
        }
        minDate.set(Calendar.HOUR_OF_DAY, 0);
        minDate.set(Calendar.MINUTE, 0);
        minDate.set(Calendar.SECOND, 0);
        minDate.set(Calendar.MILLISECOND, 0);
        picker.getDatePicker().setMinDate(minDate.getTimeInMillis());
        picker.show();
    }

    private void setupIdentificationLogic() {
        cgIdType.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                selectedIdType = "None";
                btnUploadId.setVisibility(View.GONE);
                tvIdUploadStatus.setVisibility(View.GONE);
                cardIdPreview.setVisibility(View.GONE);
                uploadedIdUri = null;
                tvIdUploadStatus.setText(R.string.no_id_uploaded);
                updateSummary();
                return;
            }

            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chipSenior) {
                selectedIdType = "Senior Citizen";
                btnUploadId.setVisibility(View.VISIBLE);
                tvIdUploadStatus.setVisibility(View.VISIBLE);
                if (uploadedIdUri != null) {
                    cardIdPreview.setVisibility(View.VISIBLE);
                    ivIdPreview.setImageURI(uploadedIdUri);
                }
            } else if (checkedId == R.id.chipPwd) {
                selectedIdType = "PWD";
                btnUploadId.setVisibility(View.VISIBLE);
                tvIdUploadStatus.setVisibility(View.VISIBLE);
                if (uploadedIdUri != null) {
                    cardIdPreview.setVisibility(View.VISIBLE);
                    ivIdPreview.setImageURI(uploadedIdUri);
                }
            } else {
                selectedIdType = "None";
                btnUploadId.setVisibility(View.GONE);
                tvIdUploadStatus.setVisibility(View.GONE);
                cardIdPreview.setVisibility(View.GONE);
                uploadedIdUri = null;
                tvIdUploadStatus.setText(R.string.no_id_uploaded);
            }
            updateSummary();
        });

        btnUploadId.setOnClickListener(v -> idPickerLauncher.launch("image/*"));
    }

    private void setupAdditionalGuests() {
        btnAddGuest.setOnClickListener(v -> {
            if (selectedRooms.isEmpty()) {
                Toast.makeText(this, R.string.error_no_rooms_selected, Toast.LENGTH_SHORT).show();
                return;
            }

            int currentGuestCount = 1 + layoutAdditionalGuests.getChildCount();
            if (currentGuestCount < totalSelectedCapacity()) {
                addGuestField();
            } else {
                Toast.makeText(this, getString(R.string.capacity_reached_multi, totalSelectedCapacity()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addGuestField() {
        View guestView = LayoutInflater.from(this).inflate(R.layout.item_additional_guest, layoutAdditionalGuests, false);
        TextView tvGuestNumber = guestView.findViewById(R.id.tvGuestNumber);
        tvGuestNumber.setText(getString(R.string.guest_number_format, layoutAdditionalGuests.getChildCount() + 2));

        View layoutGuestDetails = guestView.findViewById(R.id.layoutGuestDetails);
        ImageButton btnExpandCollapse = guestView.findViewById(R.id.btnExpandCollapse);

        // Initially expanded for new guests
        layoutGuestDetails.setVisibility(View.VISIBLE);
        btnExpandCollapse.setImageResource(R.drawable.ic_expand_more);
        btnExpandCollapse.setRotation(180);

        bindGuestExpandToggle(layoutGuestDetails, btnExpandCollapse);

        setupRelationshipDropdown(guestView);

        guestView.findViewById(R.id.btnRemoveGuest).setOnClickListener(v -> {
            layoutAdditionalGuests.removeView(guestView);
            renumberGuests();
            updateSummary();
        });

        layoutAdditionalGuests.addView(guestView);
        updateSummary();
    }

    private void bindGuestExpandToggle(View layoutGuestDetails, ImageButton btnExpandCollapse) {
        btnExpandCollapse.setOnClickListener(v -> {
            if (layoutGuestDetails.getVisibility() == View.GONE) {
                layoutGuestDetails.setVisibility(View.VISIBLE);
                layoutGuestDetails.animate().alpha(1f).setDuration(300).start();
                btnExpandCollapse.animate().rotation(180).setDuration(300).start();
            } else {
                layoutGuestDetails.animate().alpha(0f).setDuration(300)
                        .withEndAction(() -> layoutGuestDetails.setVisibility(View.GONE)).start();
                btnExpandCollapse.animate().rotation(0).setDuration(300).start();
            }
        });
    }

    private void setupRelationshipDropdown(View guestView) {
        AutoCompleteTextView atvRelationship = guestView.findViewById(R.id.atvRelationship);
        String[] relationships = {
                getString(R.string.rel_spouse),
                getString(R.string.rel_child),
                getString(R.string.rel_parent),
                getString(R.string.rel_sibling),
                getString(R.string.rel_friend),
                getString(R.string.rel_other)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, relationships);
        atvRelationship.setAdapter(adapter);
    }

    private void renumberGuests() {
        for (int i = 0; i < layoutAdditionalGuests.getChildCount(); i++) {
            View v = layoutAdditionalGuests.getChildAt(i);
            TextView tv = v.findViewById(R.id.tvGuestNumber);
            tv.setText(getString(R.string.guest_number_format, i + 2));
        }
    }

    private void updateSummary() {
        if (tvSummaryDates != null) {
            if (hasEnteredCheckIn() && hasEnteredCheckOut()) {
                tvSummaryDates.setText(getString(R.string.summary_dates_format,
                        dateFormat.format(checkInCal.getTime()), dateFormat.format(checkOutCal.getTime())));
            } else {
                tvSummaryDates.setText(R.string.summary_dates_placeholder);
            }
        }
        if (tvSummaryGuests != null) {
            int guestCount = 1 + layoutAdditionalGuests.getChildCount();
            tvSummaryGuests.setText(getResources().getQuantityString(R.plurals.summary_guest_count, guestCount, guestCount));
        }
        updateStepProgress();

        if (layoutSummaryRooms != null) {
            layoutSummaryRooms.removeAllViews();
        }

        if (selectedRooms.isEmpty()) {
            tvSummaryRoom.setText(R.string.no_room_selected);
            tvSummaryTotal.setText(R.string.zero_price);
            layoutSummaryDiscount.setVisibility(View.GONE);
            currentTotalAmount = 0;
            return;
        }

        long nights = selectedNights();
        double total = 0;

        tvSummaryRoom.setText(getString(R.string.summary_rooms_header_format, selectedRooms.size()));

        Map<String, List<Room>> grouped = new LinkedHashMap<>();
        for (Room r : selectedRooms) {
            grouped.computeIfAbsent(r.getId(), k -> new ArrayList<>()).add(r);
        }
        for (List<Room> group : grouped.values()) {
            Room representative = group.get(0);
            int qty = group.size();
            double roomTotal = roomSubtotal(representative) * qty;
            total += roomTotal;
            if (layoutSummaryRooms != null) {
                layoutSummaryRooms.addView(buildSummaryRoomRow(representative, qty, nights, roomTotal));
            }
        }

        // Selected Paid/Additional amenities are submitted with the
        // reservation and snapshotted server-side at creation time (see
        // RoomRepository#createReservation) - this total matches exactly
        // what the backend will charge, not just a client-side estimate.
        if (layoutSummaryAmenitiesRows != null) {
            layoutSummaryAmenitiesRows.removeAllViews();
        }
        double amenitiesTotal = 0;
        for (AddOnAmenity amenity : selectedAmenities) {
            amenitiesTotal += amenity.getSubtotal();
            if (layoutSummaryAmenitiesRows != null) {
                layoutSummaryAmenitiesRows.addView(buildSummaryAmenityRow(amenity));
            }
        }
        if (layoutSummaryAmenities != null) {
            layoutSummaryAmenities.setVisibility(selectedAmenities.isEmpty() ? View.GONE : View.VISIBLE);
        }
        total += amenitiesTotal;

        // No discount is ever applied here, or reflected in the total - a
        // discount request just flags the reservation for staff review;
        // only a receptionist can apply a specific Discount, after
        // verifying the uploaded ID, at billing. Showing this row is purely
        // informational so the guest knows their request was captured.
        if (!selectedIdType.equals("None")) {
            layoutSummaryDiscount.setVisibility(View.VISIBLE);
            tvSummaryDiscount.setText(R.string.discount_pending_verification);
        } else {
            layoutSummaryDiscount.setVisibility(View.GONE);
        }

        currentTotalAmount = total;
        tvSummaryTotal.setText(String.format(Locale.US, getString(R.string.price_format), total));
    }

    /** One "Nx Room Name — N nights ............ ₱subtotal" row for the summary card's room list. */
    private View buildSummaryRoomRow(Room r, int qty, long nights, double roomTotal) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int marginBottom = (int) (getResources().getDisplayMetrics().density * 6);
        rowParams.bottomMargin = marginBottom;
        row.setLayoutParams(rowParams);

        TextView label = new TextView(this);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.setMarginEnd((int) (getResources().getDisplayMetrics().density * 12));
        label.setLayoutParams(labelParams);
        label.setText(qty > 1
                ? getString(R.string.summary_room_row_qty_format, qty, r.getName(), (int) nights)
                : getString(R.string.summary_room_row_format, r.getName(), (int) nights));
        label.setTextColor(ContextCompat.getColor(this, R.color.velocity_text_secondary));
        label.setTextSize(13f);

        TextView price = new TextView(this);
        price.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        price.setText(String.format(Locale.US, getString(R.string.price_format), roomTotal));
        price.setTextColor(ContextCompat.getColor(this, R.color.velocity_text_primary));
        price.setTextSize(13f);
        price.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));

        row.addView(label);
        row.addView(price);
        return row;
    }

    /** One "Amenity Name × Qty ............ ₱subtotal" row for the summary card's add-on amenities list. */
    private View buildSummaryAmenityRow(AddOnAmenity amenity) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int marginBottom = (int) (getResources().getDisplayMetrics().density * 6);
        rowParams.bottomMargin = marginBottom;
        row.setLayoutParams(rowParams);

        TextView label = new TextView(this);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.setMarginEnd((int) (getResources().getDisplayMetrics().density * 12));
        label.setLayoutParams(labelParams);
        label.setText(amenity.getQuantity() > 1
                ? getString(R.string.summary_amenity_row_qty_format, amenity.getName(), amenity.getQuantity())
                : amenity.getName());
        label.setTextColor(ContextCompat.getColor(this, R.color.velocity_text_secondary));
        label.setTextSize(13f);

        TextView price = new TextView(this);
        price.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        price.setText(String.format(Locale.US, getString(R.string.price_format), amenity.getSubtotal()));
        price.setTextColor(ContextCompat.getColor(this, R.color.velocity_text_primary));
        price.setTextSize(13f);
        price.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));

        row.addView(label);
        row.addView(price);
        return row;
    }

    /**
     * Reflects form completion in the horizontal stepper: step 1 is done once a
     * room is picked and both stay dates are chosen, step 2 once the primary
     * guest's first and last names are filled, and step 3 lights up when the
     * form is ready to confirm.
     */
    private void updateStepProgress() {
        if (tvProgressStep1 == null) {
            return;
        }
        boolean step1Done = !selectedRooms.isEmpty() && hasEnteredCheckIn() && hasEnteredCheckOut();
        boolean step2Done = step1Done
                && etPrimaryGuestFirstName.getText() != null
                && !etPrimaryGuestFirstName.getText().toString().trim().isEmpty()
                && etPrimaryGuestLastName.getText() != null
                && !etPrimaryGuestLastName.getText().toString().trim().isEmpty();

        styleStepBadge(tvProgressStep1, getString(R.string.step_number_1), step1Done, true);
        styleStepBadge(tvProgressStep2, getString(R.string.step_number_2), step2Done, step1Done);
        styleStepBadge(tvProgressStep3, getString(R.string.step_number_3), false, step1Done && step2Done);

        int doneColor = ContextCompat.getColor(this, R.color.velocity_red_primary);
        int pendingColor = ContextCompat.getColor(this, R.color.velocity_red_subtle);
        progressLine1.setBackgroundColor(step1Done ? doneColor : pendingColor);
        progressLine2.setBackgroundColor(step2Done ? doneColor : pendingColor);
    }

    private void styleStepBadge(TextView badge, String number, boolean done, boolean reached) {
        if (badge == null) {
            return;
        }
        if (done) {
            badge.setBackgroundResource(R.drawable.shape_step_badge);
            badge.setTextColor(ContextCompat.getColor(this, R.color.white));
            badge.setText(R.string.step_done_mark);
        } else if (reached) {
            badge.setBackgroundResource(R.drawable.shape_step_badge);
            badge.setTextColor(ContextCompat.getColor(this, R.color.white));
            badge.setText(number);
        } else {
            badge.setBackgroundResource(R.drawable.shape_step_badge_inactive);
            badge.setTextColor(ContextCompat.getColor(this, R.color.velocity_red_primary));
            badge.setText(number);
        }
    }

    private boolean validateForm() {
        boolean isValid = true;

        if (selectedRooms.isEmpty()) {
            Toast.makeText(this, R.string.error_no_rooms_selected, Toast.LENGTH_SHORT).show();
            isValid = false;
        }

        if (etCheckIn.getText().toString().isEmpty()) {
            tilCheckIn.setError(getString(R.string.error_required));
            isValid = false;
        } else if (isBeforeToday(checkInCal)) {
            // Defensive re-check: dates can arrive stale via an Intent extra from Room
            // Browsing, or from "Modify" on an old Pending reservation whose check-in date
            // has since passed - the date pickers alone can't catch either case.
            tilCheckIn.setError(getString(R.string.error_checkin_past));
            isValid = false;
        } else {
            tilCheckIn.setError(null);
        }

        if (etCheckOut.getText().toString().isEmpty()) {
            tilCheckOut.setError(getString(R.string.error_required));
            isValid = false;
        } else if (!etCheckIn.getText().toString().isEmpty() && !checkOutCal.after(checkInCal)) {
            tilCheckOut.setError(getString(R.string.error_invalid_dates));
            isValid = false;
        } else {
            tilCheckOut.setError(null);
        }

        if (etPrimaryGuestFirstName.getText().toString().trim().isEmpty()) {
            tilPrimaryGuestFirstName.setError(getString(R.string.error_required));
            isValid = false;
        } else {
            tilPrimaryGuestFirstName.setError(null);
        }

        if (etPrimaryGuestLastName.getText().toString().trim().isEmpty()) {
            tilPrimaryGuestLastName.setError(getString(R.string.error_required));
            isValid = false;
        } else {
            tilPrimaryGuestLastName.setError(null);
        }

        if (!selectedRooms.isEmpty() && !allSelectedRoomsAvailable(checkInCal, checkOutCal)) {
            Toast.makeText(this, R.string.error_room_unavailable, Toast.LENGTH_SHORT).show();
            isValid = false;
        }

        // Defensive capacity re-check: additional guest fields are already capped when
        // added/room-changed, but this guards against any state drift before submit.
        if (!selectedRooms.isEmpty()) {
            int totalGuests = 1 + layoutAdditionalGuests.getChildCount();
            if (totalGuests > totalSelectedCapacity()) {
                Toast.makeText(this, getString(R.string.capacity_reached_multi, totalSelectedCapacity()), Toast.LENGTH_SHORT).show();
                isValid = false;
            }
        }

        if (!selectedIdType.equals("None") && uploadedIdUri == null) {
            tvIdUploadStatus.setText(R.string.error_upload_id);
            tvIdUploadStatus.setVisibility(View.VISIBLE);
            isValid = false;
        }

        // Validate additional guests
        for (int i = 0; i < layoutAdditionalGuests.getChildCount(); i++) {
            View v = layoutAdditionalGuests.getChildAt(i);
            TextInputEditText etLastName = v.findViewById(R.id.etGuestLastName);
            TextInputEditText etFirstName = v.findViewById(R.id.etGuestFirstName);
            TextInputEditText etAge = v.findViewById(R.id.etGuestAge);
            TextInputLayout tilLastName = v.findViewById(R.id.tilGuestLastName);
            TextInputLayout tilFirstName = v.findViewById(R.id.tilGuestFirstName);
            TextInputLayout tilAge = v.findViewById(R.id.tilGuestAge);

            if (etLastName.getText().toString().trim().isEmpty()) {
                tilLastName.setError(getString(R.string.error_required));
                isValid = false;
            } else {
                tilLastName.setError(null);
            }

            if (etFirstName.getText().toString().trim().isEmpty()) {
                tilFirstName.setError(getString(R.string.error_required));
                isValid = false;
            } else {
                tilFirstName.setError(null);
            }

            String ageStr = etAge.getText().toString().trim();
            if (ageStr.isEmpty()) {
                tilAge.setError(getString(R.string.error_required));
                isValid = false;
            } else if (parseGuestAge(ageStr) == null) {
                // inputType="number" keeps out letters, but doesn't stop an out-of-range
                // value like "0" or "999" from silently becoming age 0 (a child) at submit.
                tilAge.setError(getString(R.string.invalid_age));
                isValid = false;
            } else {
                tilAge.setError(null);
            }
        }

        // Defense-in-depth: btnConfirmBooking is already disabled until this is
        // checked (Booking tab only, see updateConfirmButtonEnabledState()), but
        // guard here too in case state ever drifts.
        if (editingBookingId == null && !isReservationMode() && cbTermsAgreement != null && !cbTermsAgreement.isChecked()) {
            Toast.makeText(this, R.string.error_terms_not_accepted, Toast.LENGTH_LONG).show();
            isValid = false;
        }

        return isValid;
    }

    /**
     * The Booking tab's Confirm/Proceed-to-Payment button stays disabled until
     * the Terms/Cancellation-Policy checkbox is checked; the Reservation tab
     * has no payment step so nothing to gate, and an in-progress edit is
     * always allowed to save (it already went through this gate when created).
     */
    private void updateConfirmButtonEnabledState() {
        if (btnConfirmBooking == null) return;
        boolean enabled = editingBookingId != null
                || isReservationMode()
                || (cbTermsAgreement != null && cbTermsAgreement.isChecked());
        btnConfirmBooking.setEnabled(enabled);
    }

    /** Fetches the admin-managed, active-only Paid/Additional amenity catalog and inflates one row per item. */
    private void loadAmenityCatalog() {
        repository.refreshAmenities("paid", new RoomRepository.RepositoryCallback<List<AddOnAmenity>>() {
            @Override
            public void onSuccess(List<AddOnAmenity> amenities) {
                addOnCatalog = amenities;
                renderAmenityCatalog();
            }

            @Override
            public void onError(String message) {
                addOnCatalog = new ArrayList<>();
                renderAmenityCatalog();
            }
        });
    }

    private void renderAmenityCatalog() {
        layoutAmenitiesItemsContainer.removeAllViews();
        selectedAmenities.clear();

        if (addOnCatalog.isEmpty()) {
            tvAmenitiesEmptyState.setVisibility(View.VISIBLE);
            layoutAmenitiesItemsContainer.setVisibility(View.GONE);
            return;
        }

        tvAmenitiesEmptyState.setVisibility(View.GONE);
        layoutAmenitiesItemsContainer.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < addOnCatalog.size(); i++) {
            AddOnAmenity amenity = addOnCatalog.get(i);
            View row = inflater.inflate(R.layout.item_amenity_addon, layoutAmenitiesItemsContainer, false);

            MaterialCheckBox checkBox = row.findViewById(R.id.cbAmenityItem);
            TextView priceLabel = row.findViewById(R.id.tvAmenityItemPrice);
            TextView descLabel = row.findViewById(R.id.tvAmenityItemDesc);
            View quantityRow = row.findViewById(R.id.layoutAmenityItemQuantity);
            MaterialButton btnMinus = row.findViewById(R.id.btnAmenityQtyMinus);
            MaterialButton btnPlus = row.findViewById(R.id.btnAmenityQtyPlus);
            TextView qtyLabel = row.findViewById(R.id.tvAmenityItemQty);
            TextView subtotalLabel = row.findViewById(R.id.tvAmenityItemSubtotal);

            checkBox.setText(amenity.getCategory() != null && !amenity.getCategory().isEmpty()
                    ? amenity.getName() + " — " + amenity.getCategory()
                    : amenity.getName());
            priceLabel.setText(getString(R.string.addon_amenity_price_format, amenity.getPrice()));
            descLabel.setText(amenity.getDescription());

            Runnable refreshQtyUi = () -> {
                qtyLabel.setText(String.valueOf(amenity.getQuantity()));
                subtotalLabel.setText(getString(R.string.addon_amenity_subtotal_format, amenity.getSubtotal()));
            };
            refreshQtyUi.run();

            btnMinus.setOnClickListener(v -> {
                if (amenity.getQuantity() > 1) {
                    amenity.setQuantity(amenity.getQuantity() - 1);
                    refreshQtyUi.run();
                    updateSummary();
                }
            });
            btnPlus.setOnClickListener(v -> {
                if (amenity.getQuantity() < amenity.getMaxQuantity()) {
                    amenity.setQuantity(amenity.getQuantity() + 1);
                    refreshQtyUi.run();
                    updateSummary();
                } else {
                    Toast.makeText(this, getString(R.string.no_more_amenity_stock_toast, amenity.getName()), Toast.LENGTH_SHORT).show();
                }
            });

            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (!selectedAmenities.contains(amenity)) selectedAmenities.add(amenity);
                    quantityRow.setVisibility(View.VISIBLE);
                } else {
                    selectedAmenities.remove(amenity);
                    amenity.setQuantity(1);
                    refreshQtyUi.run();
                    quantityRow.setVisibility(View.GONE);
                }
                updateSummary();
            });

            layoutAmenitiesItemsContainer.addView(row);
        }
    }

    /** Read-only Terms &amp; Agreement viewer, same dialog/pattern RegistrationActivity uses. */
    private void showTermsAgreementDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_terms_agreement, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();
        View closeButton = dialogView.findViewById(R.id.termsCloseButton);
        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /** True when {@code cal}'s calendar day is strictly before today's. */
    private boolean isBeforeToday(Calendar cal) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        Calendar dayOnly = (Calendar) cal.clone();
        dayOnly.set(Calendar.HOUR_OF_DAY, 0);
        dayOnly.set(Calendar.MINUTE, 0);
        dayOnly.set(Calendar.SECOND, 0);
        dayOnly.set(Calendar.MILLISECOND, 0);
        return dayOnly.before(today);
    }

    /** Mirrors the age-sanity check used elsewhere (Registration/Profile): 1-119 inclusive. */
    private Integer parseGuestAge(String value) {
        try {
            int age = Integer.parseInt(value);
            return (age > 0 && age < 120) ? age : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void setupBookingConfirmation() {
        if (btnCancelEdit != null) {
            btnCancelEdit.setOnClickListener(v -> {
                if (isSubmitting) return;
                new MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.confirm_discard_edit_msg)
                        .setPositiveButton(R.string.confirm_dialog_positive, (dialog, which) -> cancelEditMode())
                        .setNegativeButton(R.string.cancel_label, null)
                        .show();
            });
        }

        btnConfirmBooking.setOnClickListener(v -> {
            if (isSubmitting) {
                return;
            }
            if (!validateForm()) {
                Toast.makeText(this, R.string.error_form_incomplete, Toast.LENGTH_SHORT).show();
                return;
            }

            // Double-booking guard: the same guest may not hold two active
            // stays of the same room type over overlapping dates. Checked
            // before the generic availability check so they get told WHICH
            // reservation is in the way instead of a vague "unavailable".
            // Checked against every selected room type, since any one of
            // them could collide with an existing stay.
            Booking foundDuplicate = null;
            for (Room r : selectedRooms) {
                foundDuplicate = repository.findOverlappingBooking(r.getType(), checkInCal, checkOutCal, editingBookingId);
                if (foundDuplicate != null) break;
            }
            final Booking duplicate = foundDuplicate;
            if (duplicate != null) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.duplicate_reservation_title)
                        .setMessage(getString(R.string.error_duplicate_reservation,
                                duplicate.getId(), duplicate.getCheckInDate(), duplicate.getCheckOutDate()))
                        .setPositiveButton(R.string.close_label, null)
                        .setNeutralButton(R.string.modify_label, (dialog, which) -> launchModifyWizard(duplicate))
                        .show();
                return;
            }

            // Double check availability - if editing, we exclude the current booking from the check
            if (!allSelectedRoomsAvailable(checkInCal, checkOutCal)) {
                Toast.makeText(this, R.string.error_room_unavailable, Toast.LENGTH_LONG).show();
                refreshAvailableRooms();
                return;
            }

            // Submitting is a one-way action (creates/updates a real reservation server-side),
            // so confirm before firing it - same pattern as the other risky actions in
            // ProfileManagementActivity.
            int confirmMessageRes;
            if (editingBookingId != null) {
                confirmMessageRes = R.string.confirm_update_reservation_msg;
            } else if (isReservationMode()) {
                confirmMessageRes = R.string.confirm_create_reservation_msg;
            } else {
                confirmMessageRes = R.string.confirm_create_booking_msg;
            }

            new MaterialAlertDialogBuilder(this)
                    .setMessage(confirmMessageRes)
                    .setPositiveButton(R.string.confirm_dialog_positive, (dialog, which) -> {
                        setSubmitting(true);
                        layoutNewBooking.postDelayed(this::finalizeBooking, 500);
                    })
                    .setNegativeButton(R.string.cancel_label, null)
                    .show();
        });
    }

    private void setSubmitting(boolean submitting) {
        isSubmitting = submitting;
        btnConfirmBooking.setEnabled(!submitting);
        loadingOverlay.setVisibility(submitting ? View.VISIBLE : View.GONE);
    }

    private void finalizeBooking() {
        // Adults/children split the real backend needs: the primary guest
        // plus any additional guest aged 18+ counts as an adult, younger
        // counts as a child.
        int adults = 1;
        int children = 0;
        List<AdditionalGuest> guests = new ArrayList<>();
        for (int i = 0; i < layoutAdditionalGuests.getChildCount(); i++) {
            View v = layoutAdditionalGuests.getChildAt(i);
            String lastName = ((TextInputEditText) v.findViewById(R.id.etGuestLastName)).getText().toString().trim();
            String firstName = ((TextInputEditText) v.findViewById(R.id.etGuestFirstName)).getText().toString().trim();
            String middleName = ((TextInputEditText) v.findViewById(R.id.etGuestMiddleName)).getText().toString().trim();

            String fullName = lastName + ", " + firstName;
            if (!middleName.isEmpty()) {
                fullName += " " + middleName;
            }

            String ageStr = ((TextInputEditText) v.findViewById(R.id.etGuestAge)).getText().toString();
            AutoCompleteTextView atvRel = v.findViewById(R.id.atvRelationship);
            String rel = atvRel.getText() != null ? atvRel.getText().toString() : "";
            int age = 0;
            try { age = Integer.parseInt(ageStr); } catch (Exception ignored) { }
            if (age >= 18) adults++; else children++;
            guests.add(new AdditionalGuest(fullName, age, getString(R.string.not_specified), rel));
        }

        if (editingBookingId != null) {
            repository.updateReservation(editingBookingId, checkInCal, checkOutCal, adults, children,
                    selectedIdType, guests,
                    new RoomRepository.RepositoryCallback<Booking>() {
                        @Override
                        public void onSuccess(Booking updated) {
                            String updatedId = editingBookingId;
                            LocalTransactionState.markModifiedOnce(BookingAndReservationActivity.this, updatedId);
                            Runnable showConfirmation = () -> {
                                setSubmitting(false);
                                if (returnToPaymentAfterSave) {
                                    // Came here via Payment List's "Modify" button - skip the
                                    // usual dialog and go straight back so the billing summary
                                    // recalculates against the updated booking.
                                    Toast.makeText(BookingAndReservationActivity.this,
                                            getString(R.string.reservation_updated_msg, updatedId), Toast.LENGTH_LONG).show();
                                    cancelEditMode();
                                    goToPaymentFor(updated);
                                    return;
                                }
                                new MaterialAlertDialogBuilder(BookingAndReservationActivity.this)
                                        .setTitle(R.string.reservation_updated_title)
                                        .setMessage(getString(R.string.reservation_updated_msg, updatedId))
                                        .setPositiveButton(R.string.close_label, (dialog, which) -> {
                                            cancelEditMode();
                                            refreshMyBookings();
                                            scrollToList();
                                        })
                                        .show();
                            };

                            if (!selectedIdType.equals("None") && uploadedIdUri != null && !uploadedIdUri.toString().startsWith("http")) {
                                repository.uploadIdCard(updated.getId(), uploadedIdUri, new RoomRepository.RepositoryCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void result) {
                                        showConfirmation.run();
                                    }

                                    @Override
                                    public void onError(String message) {
                                        Toast.makeText(BookingAndReservationActivity.this,
                                                "Reservation updated, but the new ID photo failed to upload: " + message, Toast.LENGTH_LONG).show();
                                        showConfirmation.run();
                                    }
                                });
                            } else {
                                showConfirmation.run();
                            }
                        }

                        @Override
                        public void onError(String message) {
                            setSubmitting(false);
                            Toast.makeText(BookingAndReservationActivity.this, "Couldn't update reservation: " + message, Toast.LENGTH_LONG).show();
                        }
                    });
        } else {
            String guestFirstName = etPrimaryGuestFirstName.getText().toString().trim();
            String guestMiddleName = etPrimaryGuestMiddleName.getText().toString().trim();
            String guestLastName = etPrimaryGuestLastName.getText().toString().trim();
            createRoomReservations(groupSelectedRoomsByType(selectedRooms), 0, new ArrayList<>(), adults, children,
                    guestFirstName, guestMiddleName, guestLastName, guests);
        }
    }

    /** One room-type group from {@link #selectedRooms}: the type card plus how many of it were picked. */
    private static final class RoomGroup {
        final Room room;
        final int quantity;

        RoomGroup(Room room, int quantity) {
            this.room = room;
            this.quantity = quantity;
        }
    }

    /**
     * Collapses {@code selectedRooms} (one entry per unit - picking "2x
     * Deluxe" leaves two Deluxe entries in that list) into one group per
     * distinct room type, counting how many of each were picked. Order of
     * first selection is preserved.
     */
    private List<RoomGroup> groupSelectedRoomsByType(List<Room> rooms) {
        LinkedHashMap<String, RoomGroup> grouped = new LinkedHashMap<>();
        for (Room r : rooms) {
            RoomGroup existing = grouped.get(r.getId());
            grouped.put(r.getId(), new RoomGroup(r, existing == null ? 1 : existing.quantity + 1));
        }
        return new ArrayList<>(grouped.values());
    }

    /**
     * The backend accepts a quantity (rooms_requested) of the same room
     * type in one reservation call, but a checkout spanning more than one
     * distinct room TYPE still needs one call per type - submitted
     * sequentially so a failure partway through can be rolled back cleanly
     * (see {@link #handleRoomCreationFailure}) instead of racing N
     * in-flight requests against each other.
     */
    private void createRoomReservations(List<RoomGroup> groups, int index, List<Booking> createdSoFar,
                                         int adults, int children, String guestFirstName, String guestMiddleName,
                                         String guestLastName, List<AdditionalGuest> guests) {
        if (index >= groups.size()) {
            onAllRoomsCreated(createdSoFar);
            return;
        }
        RoomGroup group = groups.get(index);
        // Selected amenities are a single flat list for the whole checkout,
        // not per room type - attach them only to the first reservation
        // created so a multi-room-type booking doesn't submit (and get
        // billed for) the same amenity selection more than once.
        List<AddOnAmenity> amenitiesForThisCall = index == 0 ? selectedAmenities : new ArrayList<>();
        // This legacy inline create form is not reachable from the current UI
        // (superseded by the 7-step wizard) - "cash" is a safe placeholder
        // payment_method so this still compiles against the now-required
        // backend field if it's ever re-enabled.
        repository.createReservation(group.room, group.quantity, checkInCal, checkOutCal, adults, children,
                guestFirstName, guestMiddleName, guestLastName,
                selectedIdType, guests, amenitiesForThisCall, "cash", null,
                new RoomRepository.RepositoryCallback<Booking>() {
                    @Override
                    public void onSuccess(Booking booking) {
                        createdSoFar.add(booking);
                        createRoomReservations(groups, index + 1, createdSoFar, adults, children,
                                guestFirstName, guestMiddleName, guestLastName, guests);
                    }

                    @Override
                    public void onError(String message) {
                        handleRoomCreationFailure(createdSoFar, groups.size(), message);
                    }
                });
    }

    /**
     * A room failed to create mid-transaction. If nothing was created yet
     * this is just a normal failure; if some rooms already succeeded
     * server-side, they're cancelled again so the guest isn't left holding
     * a partial multi-room booking they didn't confirm.
     */
    private void handleRoomCreationFailure(List<Booking> createdSoFar, int totalRooms, String message) {
        if (createdSoFar.isEmpty()) {
            setSubmitting(false);
            Toast.makeText(this, "Couldn't create reservation: " + message, Toast.LENGTH_LONG).show();
            return;
        }
        rollbackCreatedBookings(createdSoFar, 0, new ArrayList<>(), totalRooms, message);
    }

    private void rollbackCreatedBookings(List<Booking> createdSoFar, int index, List<String> failedRollbackIds,
                                          int totalRooms, String originalError) {
        if (index >= createdSoFar.size()) {
            setSubmitting(false);
            refreshMyBookings();
            int titleRes = R.string.multi_room_partial_failure_title;
            String msg = failedRollbackIds.isEmpty()
                    ? getString(R.string.multi_room_partial_failure_msg, createdSoFar.size(), totalRooms, originalError)
                    : getString(R.string.multi_room_rollback_failed_msg, createdSoFar.size(), totalRooms, originalError);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(titleRes)
                    .setMessage(msg)
                    .setPositiveButton(R.string.close_label, null)
                    .show();
            return;
        }
        String bookingId = createdSoFar.get(index).getId();
        repository.cancelReservation(bookingId, new RoomRepository.RepositoryCallback<Booking>() {
            @Override
            public void onSuccess(Booking result) {
                rollbackCreatedBookings(createdSoFar, index + 1, failedRollbackIds, totalRooms, originalError);
            }

            @Override
            public void onError(String msg) {
                failedRollbackIds.add(bookingId);
                rollbackCreatedBookings(createdSoFar, index + 1, failedRollbackIds, totalRooms, originalError);
            }
        });
    }

    /**
     * Every room in the batch was created successfully. Tags them under one
     * client-side group reference (if more than one), uploads the ID photo
     * to each (the uploaded ID belongs to the primary guest for the whole
     * transaction), then routes to payment for the primary room - both
     * New Booking and New Reservation always proceed to payment now (the
     * rest of a multi-room batch stay Pending and payable individually).
     */
    private void onAllRoomsCreated(List<Booking> createdBookings) {
        // The rooms' availability just changed server-side - refresh the
        // cached inventory immediately so this device doesn't let the same
        // guest attempt to double-book a room again this session.
        refreshRoomsForSelectedDates();

        Booking primary = createdBookings.get(0);
        String groupRef = null;
        if (createdBookings.size() > 1) {
            groupRef = "GRP-" + primary.getId();
            List<String> ids = new ArrayList<>();
            for (Booking b : createdBookings) ids.add(b.getId());
            BookingGroupState.saveGroup(this, groupRef, ids);
        }
        final String groupRefFinal = groupRef;

        // New Booking is GCash-only (a Booking only exists once paid, so "Pay Later"
        // isn't a meaningful choice there) and proceeds straight to PaymentActivity as
        // before. New Reservation genuinely can be paid later, so it gets an explicit
        // "Reservation Created - Pay Now or Pay Later?" choice: Pay Now hands off to
        // PaymentActivity (GCash) same as always; Pay Later skips PaymentActivity
        // entirely and returns to the dashboard, leaving the reservation exactly as
        // Pending with no payment_method recorded yet - the receptionist collects cash
        // in person, or the guest can later use the one-time Cash->GCash switch.
        boolean reservationMode = isReservationMode();
        Runnable showConfirmation = () -> {
            String multiRoomMsg = createdBookings.size() > 1
                    ? getString(R.string.multi_room_booked_msg, createdBookings.size(), createdBookings.size() - 1, groupRefFinal)
                    : null;

            if (!reservationMode) {
                if (multiRoomMsg != null) {
                    new MaterialAlertDialogBuilder(BookingAndReservationActivity.this)
                            .setTitle(R.string.multi_room_created_title)
                            .setMessage(multiRoomMsg)
                            .setPositiveButton("OK", (dialog, which) -> goToPaymentFor(primary))
                            .show();
                } else {
                    goToPaymentFor(primary);
                }
                return;
            }

            String baseMessage = getString(R.string.reservation_created_msg, primary.getId());
            String message = multiRoomMsg != null ? multiRoomMsg + "\n\n" + baseMessage : baseMessage;

            new MaterialAlertDialogBuilder(BookingAndReservationActivity.this)
                    .setTitle(R.string.reservation_created_title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.pay_now, (dialog, which) -> goToPaymentFor(primary))
                    .setNegativeButton(R.string.pay_later, (dialog, which) -> {
                        resetForm();
                        android.content.Intent intent = new android.content.Intent(
                                BookingAndReservationActivity.this, DashboardActivity.class);
                        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .show();
        };

        if (!selectedIdType.equals("None") && uploadedIdUri != null) {
            uploadIdCardToAll(createdBookings, () -> {
                setSubmitting(false);
                showConfirmation.run();
            });
        } else {
            setSubmitting(false);
            showConfirmation.run();
        }
    }

    /**
     * New Booking is GCash-only; New Reservation offers GCash and Cash
     * (Cash always stays Pay Later - see PaymentActivity/Api\PaymentController).
     * The mode is captured at creation time (isReservationMode() reflects
     * whichever tab was active when the room was submitted), not
     * re-evaluated later, since resetForm() below may flip the tab back.
     */
    private void goToPaymentFor(Booking booking) {
        if (!NavUtils.allowClick()) return;
        boolean allowCash = isReservationMode();
        resetForm();
        android.content.Intent intent = new android.content.Intent(
                BookingAndReservationActivity.this, PaymentActivity.class);
        intent.putExtra("BOOKING_ID", booking.getId());
        intent.putExtra("ALLOW_CASH", allowCash);
        startActivity(intent);
    }

    /** Fires the ID-card upload for every room in the batch and calls {@code onDone} once all have finished (success or not). */
    private void uploadIdCardToAll(List<Booking> bookings, Runnable onDone) {
        if (bookings.isEmpty()) {
            onDone.run();
            return;
        }
        final int[] remaining = { bookings.size() };
        for (Booking b : bookings) {
            repository.uploadIdCard(b.getId(), uploadedIdUri, new RoomRepository.RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    remaining[0]--;
                    if (remaining[0] == 0) onDone.run();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(BookingAndReservationActivity.this,
                            "Reservation created, but the ID photo failed to upload for one of the rooms: " + message, Toast.LENGTH_LONG).show();
                    remaining[0]--;
                    if (remaining[0] == 0) onDone.run();
                }
            });
        }
    }

    private void scrollToList() {
        View listCard = findViewById(R.id.cardListSection);
        if (screenContent != null && listCard != null) {
            screenContent.post(() -> screenContent.smoothScrollTo(0, listCard.getTop()));
        }
    }

    /** Scrolls the outer NestedScrollView so {@code target} is comfortably in view. */
    private void smoothScrollToChild(View target) {
        if (screenContent == null || target == null) {
            return;
        }
        int y = 0;
        View current = target;
        while (current != null && current != screenContent) {
            y += current.getTop();
            android.view.ViewParent parent = current.getParent();
            if (!(parent instanceof View)) {
                break;
            }
            current = (View) parent;
        }
        final int targetY = Math.max(0, y - (int) (getResources().getDisplayMetrics().density * 16));
        screenContent.post(() -> screenContent.smoothScrollTo(0, targetY));
    }

    /** Brief scale + stroke pulse so the highlighted card catches the eye once. */
    private void pulseHighlight(View target) {
        if (target == null) {
            return;
        }
        target.animate()
                .scaleX(1.03f).scaleY(1.03f)
                .setDuration(220L)
                .withEndAction(() -> target.animate().scaleX(1f).scaleY(1f).setDuration(220L).start())
                .start();

        if (target instanceof com.google.android.material.card.MaterialCardView) {
            final com.google.android.material.card.MaterialCardView card =
                    (com.google.android.material.card.MaterialCardView) target;
            final int density = (int) getResources().getDisplayMetrics().density;
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(2f, 4f, 2f);
            animator.setDuration(900L);
            animator.addUpdateListener(animation ->
                    card.setStrokeWidth((int) ((float) animation.getAnimatedValue() * density)));
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    card.setStrokeWidth(2 * density);
                }
            });
            animator.start();
        }
    }

    private void resetForm() {
        editingBookingId = null;
        if (layoutCreateForm != null) layoutCreateForm.setVisibility(View.GONE);
        if (layoutWizardEntryActions != null) layoutWizardEntryActions.setVisibility(View.VISIBLE);
        if (btnCancelEdit != null) btnCancelEdit.setVisibility(View.GONE);
        selectedRooms.clear();
        renderSelectedRooms();
        if (cbTermsAgreement != null) cbTermsAgreement.setChecked(false);
        renderAmenityCatalog();
        updateCapacityIndicator();
        tilCheckIn.setEnabled(false);
        tilCheckOut.setEnabled(false);
        cgIdType.check(R.id.chipNone);
        uploadedIdUri = null;
        tvIdUploadStatus.setText(R.string.no_id_uploaded);
        cardIdPreview.setVisibility(View.GONE);
        layoutAdditionalGuests.removeAllViews();

        checkInCal = Calendar.getInstance();
        checkInCal.add(Calendar.DAY_OF_YEAR, 1);
        checkOutCal = Calendar.getInstance();
        checkOutCal.add(Calendar.DAY_OF_YEAR, 2);
        etCheckIn.setText("");
        etCheckOut.setText("");
        tilCheckIn.setError(null);
        tilCheckOut.setError(null);

        String accountFirstName = getSharedPreferences("VelocityPrefs", MODE_PRIVATE).getString("userFirstName", "").trim();
        String accountMiddleName = getSharedPreferences("VelocityPrefs", MODE_PRIVATE).getString("userMiddleName", "").trim();
        String accountLastName = getSharedPreferences("VelocityPrefs", MODE_PRIVATE).getString("userLastName", "").trim();
        etPrimaryGuestFirstName.setText(accountFirstName);
        etPrimaryGuestMiddleName.setText(accountMiddleName);
        etPrimaryGuestLastName.setText(accountLastName);
        tilPrimaryGuestFirstName.setError(null);
        tilPrimaryGuestLastName.setError(null);

        btnConfirmBooking.setText(isReservationMode() ? R.string.confirm_reservation_label : R.string.confirm_booking_label);
        btnConfirmBooking.setIconResource(isReservationMode() ? R.drawable.ic_reservation : R.drawable.ic_payment_card);
        screenTitle.setText(R.string.title_booking_reservation);
        updateConfirmButtonEnabledState();
        updateSummary();
    }

    private void cancelEditMode() {
        resetForm();
    }

    private void populateFormForEdit(Booking b) {
        editingBookingId = b.getId();
        if (layoutCreateForm != null) layoutCreateForm.setVisibility(View.VISIBLE);
        if (layoutWizardEntryActions != null) layoutWizardEntryActions.setVisibility(View.GONE);
        screenTitle.setText(R.string.modify_reservation_label);
        btnConfirmBooking.setText(R.string.modify_reservation_label);
        if (btnCancelEdit != null) btnCancelEdit.setVisibility(View.VISIBLE);
        if (layoutTermsAgreement != null) layoutTermsAgreement.setVisibility(View.GONE);
        updateConfirmButtonEnabledState();

        // Set Room: a pending reservation only has a room TYPE recorded
        // (no specific room_id - that's assigned by a receptionist at
        // confirmation), and the real update endpoint only accepts
        // dates/guest counts, not a room change (so editing always stays
        // single-room, regardless of whether the original booking was part
        // of a multi-room group). So we just match any representative room
        // of the same type for display purposes.
        selectedRooms.clear();
        for (Room r : allRooms) {
            if (r.getType().equals(b.getRoomType())) {
                selectedRooms.add(r);
                renderSelectedRooms();
                updateCapacityIndicator();
                tilCheckIn.setEnabled(true);
                tilCheckOut.setEnabled(true);
                break;
            }
        }

        // Set Dates
        try {
            checkInCal.setTime(dateFormat.parse(b.getCheckInDate()));
            checkOutCal.setTime(dateFormat.parse(b.getCheckOutDate()));
            etCheckIn.setText(b.getCheckInDate());
            etCheckOut.setText(b.getCheckOutDate());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // ID Card
        selectedIdType = b.getIdCardType() != null ? b.getIdCardType() : "None";
        if (b.getIdCardUri() != null) {
            uploadedIdUri = android.net.Uri.parse(b.getIdCardUri());
            ivIdPreview.setImageURI(uploadedIdUri);
            cardIdPreview.setVisibility(View.VISIBLE);
            tvIdUploadStatus.setText(R.string.id_uploaded_success);
            tvIdUploadStatus.setVisibility(View.VISIBLE);
        } else {
            uploadedIdUri = null;
            cardIdPreview.setVisibility(View.GONE);
            tvIdUploadStatus.setVisibility(View.GONE);
        }

        if (selectedIdType.equals("Senior Citizen") || "Senior".equalsIgnoreCase(selectedIdType)) {
            cgIdType.check(R.id.chipSenior);
            btnUploadId.setVisibility(View.VISIBLE);
        } else if (selectedIdType.equals("PWD")) {
            cgIdType.check(R.id.chipPwd);
            btnUploadId.setVisibility(View.VISIBLE);
        } else {
            cgIdType.check(R.id.chipNone);
            btnUploadId.setVisibility(View.GONE);
        }

        // Primary guest names are not stored on the reservation record, so the
        // account-prefilled name fields are left as-is while editing.

        // Additional Guests
        layoutAdditionalGuests.removeAllViews();
        for (Booking.AdditionalGuest ag : b.getAdditionalGuests()) {
            addGuestFieldWithData(ag);
        }

        updateSummary();
        if (screenContent != null) {
            screenContent.smoothScrollTo(0, 0);
        }
    }

    /**
     * Book/Reserve Again (Cancelled cards only): seeds a brand-new booking/
     * reservation from the room type, additional guests, and ID type of a
     * cancelled transaction. Dates are deliberately left blank - the
     * original stay is necessarily in the past - and the guest re-uploads
     * their own ID rather than carrying over the old file. Reuses the exact
     * same form/submit path as any new booking/reservation
     * (btnConfirmBooking); the original cancelled record is never read from
     * again after this point, let alone modified.
     */
    /**
     * "Book Again"/"Reserve Again" on a Cancelled transaction - carries the
     * same room type into a brand-new transaction via the same
     * PendingWizardRooms handoff every other Book Now/Reserve Now entry
     * point uses, landing on StartingTransactionActivity and then Step 1 of
     * BookingWizardActivity pre-filled with that room. Previously populated
     * the legacy inline layoutCreateForm - but resetForm() (called first)
     * always hides that form and this never re-showed it, so nothing was
     * ever visible to the guest; that form is only for editing an existing
     * reservation now (see populateFormForEdit()), not for starting a new one.
     */
    private void startBookAgain(Booking original) {
        boolean wantBooking = original.isHasBooking();

        Room match = null;
        for (Room r : allRooms) {
            if (r.getType().equals(original.getRoomType())) {
                match = r;
                break;
            }
        }
        if (match == null || match.getAvailableCount() <= 0) {
            Toast.makeText(this, R.string.book_again_room_unavailable, Toast.LENGTH_LONG).show();
            return;
        }

        int qty = Math.min(Math.max(1, original.getRoomsRequested()), match.getAvailableCount());
        List<Room> matches = new ArrayList<>();
        for (int i = 0; i < qty; i++) {
            matches.add(match);
        }
        if (!NavUtils.allowClick()) return;
        PendingWizardRooms.set(matches);
        PendingBookAgainPrefill.set(original);
        startActivity(StartingTransactionActivity.newIntent(this,
                wantBooking ? BookingWizardState.Mode.BOOKING : BookingWizardState.Mode.RESERVATION));
    }

    private void addGuestFieldWithData(Booking.AdditionalGuest ag) {
        View guestView = LayoutInflater.from(this).inflate(R.layout.item_additional_guest, layoutAdditionalGuests, false);
        ((TextView) guestView.findViewById(R.id.tvGuestNumber)).setText(getString(R.string.guest_number_format, layoutAdditionalGuests.getChildCount() + 2));

        View layoutGuestDetails = guestView.findViewById(R.id.layoutGuestDetails);
        ImageButton btnExpandCollapse = guestView.findViewById(R.id.btnExpandCollapse);

        // Initially collapsed for existing guests to keep the list clean
        layoutGuestDetails.setVisibility(View.GONE);
        layoutGuestDetails.setAlpha(0f);
        btnExpandCollapse.setImageResource(R.drawable.ic_expand_more);

        bindGuestExpandToggle(layoutGuestDetails, btnExpandCollapse);

        String lastName = "";
        String firstName = "";
        String middleName = "";

        if (ag.name != null && ag.name.contains(",")) {
            String[] parts = ag.name.split(",", 2);
            lastName = parts[0].trim();
            String rest = parts[1].trim();

            // Assume the last word is the middle name when there are multiple words
            String[] nameParts = rest.split(" ");
            if (nameParts.length > 1) {
                StringBuilder firstBuilder = new StringBuilder();
                for (int i = 0; i < nameParts.length - 1; i++) {
                    firstBuilder.append(nameParts[i]).append(" ");
                }
                firstName = firstBuilder.toString().trim();
                middleName = nameParts[nameParts.length - 1];
            } else {
                firstName = rest;
            }
        } else {
            firstName = ag.name;
        }

        ((TextInputEditText) guestView.findViewById(R.id.etGuestLastName)).setText(lastName);
        ((TextInputEditText) guestView.findViewById(R.id.etGuestFirstName)).setText(firstName);
        ((TextInputEditText) guestView.findViewById(R.id.etGuestMiddleName)).setText(middleName);
        ((TextInputEditText) guestView.findViewById(R.id.etGuestAge)).setText(String.valueOf(ag.age));

        setupRelationshipDropdown(guestView);
        AutoCompleteTextView atvRelationship = guestView.findViewById(R.id.atvRelationship);
        atvRelationship.setText(ag.relationship, false);

        guestView.findViewById(R.id.btnRemoveGuest).setOnClickListener(v -> {
            layoutAdditionalGuests.removeView(guestView);
            renumberGuests();
            updateSummary();
        });

        layoutAdditionalGuests.addView(guestView);
        updateSummary();
    }

    /** Same status label logic used on the transaction card badge - shared with showBookingDetails() so both stay consistent. Delegates to BookingStatusPresenter, also reused by BookingDetailsActivity. */
    private String computeStatusLabel(Booking b) {
        return BookingStatusPresenter.computeStatusLabel(this, b);
    }

    /** Same status badge coloring used on the transaction card - shared with showBookingDetails() so both stay consistent. Delegates to BookingStatusPresenter, also reused by BookingDetailsActivity. */
    private void styleStatusBadge(TextView badge, Booking b) {
        BookingStatusPresenter.styleStatusBadge(this, badge, b);
    }

    /** Plain payment-status word for the dedicated Payment Status pill (no "Payment: " prefix). Delegates to BookingStatusPresenter, also reused by BookingDetailsActivity. */
    private String paymentStatusPillText(Booking b) {
        return BookingStatusPresenter.paymentStatusPillText(this, b);
    }

    private void stylePaymentStatusPill(TextView pill, Booking b) {
        BookingStatusPresenter.stylePaymentStatusPill(this, pill, b);
    }

    /** Resolves a BookingGroupState member-id list back to the actual Booking objects still resident in the guest's own cache - a member may be missing (e.g. filtered into a different tab already), so this can return fewer entries than requested. */
    private List<Booking> resolveGroupMembers(List<String> memberIds) {
        List<Booking> result = new ArrayList<>();
        for (String id : memberIds) {
            for (Booking candidate : allMyBookings) {
                if (candidate.getId().equals(id)) {
                    result.add(candidate);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Compact room summary for the legacy client-side-grouped case
     * (BookingGroupState) - every selected room type and its quantity,
     * e.g. "Deluxe ×3" for a single type, or "Bryan Dela Cruz ×1 • Deluxe
     * ×1" for multiple - never collapsed into a generic "N Rooms • M Room
     * Types" count, which told the guest nothing about which rooms they
     * actually selected. tvBookingRoomName is no longer line/ellipsize-
     * constrained (see item_booking_card.xml) so this can wrap onto a
     * second line for a long selection instead of truncating. A sibling's
     * own rooms_requested/getGuests() isn't the room quantity, room name
     * repetition across siblings IS, since the backend only accepts one
     * room type per record here, see BookingGroupState's own doc.
     */
    private String buildGroupSummaryText(List<Booking> groupMembers) {
        java.util.LinkedHashMap<String, Integer> countsByRoomName = new java.util.LinkedHashMap<>();
        for (Booking member : groupMembers) {
            countsByRoomName.merge(member.getRoomName(), 1, Integer::sum);
        }
        return Booking.formatRoomSelectionSummary(countsByRoomName);
    }

    /**
     * Compact room summary for the true, single-record multi-room-type
     * case (Booking#getRooms() non-empty) - see
     * MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md. Every selected room type and
     * its quantity, e.g. "Deluxe ×2" for a single type, or "Bryan Dela
     * Cruz ×1 • Deluxe ×1" for multiple - never collapsed into a generic
     * "N Rooms • M Room Types" count (the guest must be able to identify
     * exactly which rooms are part of the transaction directly from the
     * list card, not just a count). Deliberately kept separate from
     * buildGroupSummaryText() (the legacy client-side-grouped case) even
     * though the output looks similar, so the two data sources are never
     * accidentally mixed - this one reads real backend line items, that
     * one reconstructs an approximation from N sibling records.
     */
    private String buildTrueMultiRoomSummaryText(Booking b) {
        java.util.LinkedHashMap<String, Integer> countsByRoomName = new java.util.LinkedHashMap<>();
        for (BookingRoom room : b.getRooms()) {
            countsByRoomName.merge(room.getRoomTypeName(), room.getQuantity(), Integer::sum);
        }
        return Booking.formatRoomSelectionSummary(countsByRoomName);
    }

    private class BookingsAdapter extends RecyclerView.Adapter<BookingsAdapter.ViewHolder> {
        private final List<Booking> mBookings;
        private final boolean isBookingTab;
        private final String highlightId;

        BookingsAdapter(List<Booking> bookings, boolean isBookingTab, String highlightId) {
            this.mBookings = bookings;
            this.isBookingTab = isBookingTab;
            this.highlightId = highlightId;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_booking_card, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Booking b = mBookings.get(position);
            String groupRef = BookingGroupState.getGroupRef(BookingAndReservationActivity.this, b.getId());
            // "Booking #X" for any actual Booking (direct or converted-from-
            // reservation), "Reservation #X" for a still-unconverted
            // Reservation (or a frozen historical record of one) - never the
            // generic "Ref: X" here, which used to be indistinguishable from
            // a real booking reference.
            String refText = b.isHasBooking()
                    ? getString(R.string.direct_booking_ref_format, b.getId())
                    : getString(R.string.reservation_ref_format, b.getId());
            List<Booking> groupMembers = null;
            if (groupRef != null) {
                List<String> memberIds = BookingGroupState.getGroupMembers(BookingAndReservationActivity.this, b.getId());
                int position1Based = memberIds.indexOf(b.getId()) + 1;
                refText += getString(R.string.group_reference_suffix_format, groupRef, position1Based, memberIds.size());
                groupMembers = resolveGroupMembers(memberIds);
            }
            holder.tvBookingId.setText(refText);
            holder.tvBookingStatus.setText(computeStatusLabel(b));
            if (holder.tvModifiedBadge != null) {
                holder.tvModifiedBadge.setVisibility(
                        (b.isEditedOnce() || LocalTransactionState.hasModifiedOnce(BookingAndReservationActivity.this, b.getId()))
                                ? View.VISIBLE : View.GONE);
            }
            holder.tvBookingDates.setText(getString(R.string.date_range_format, b.getCheckInDate(), b.getCheckOutDate()));
            // Payment method/status pill, amount paid, remaining balance, and
            // created date/time all moved into BookingDetailsActivity's Guest
            // Info/Payment tabs - the compact card no longer shows them
            // directly (see showBookingDetails() below and section 3 of the
            // simplified-card requirement this card now follows).
            holder.tvBookingGuests.setText(getString(R.string.guests_count_format, b.getGuests()));
            // Three mutually-exclusive cases, deliberately kept isolated (see
            // MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md's "Temporary legacy
            // compatibility" section). tvBookingRoomName is always visible -
            // one compact line, never the full per-type/amenity breakdown
            // (see buildTrueMultiRoomSummaryText()/buildGroupSummaryText()'s
            // own doc), since Booking ID is now this card's primary heading.
            //  1. True backend multi-room transaction (b.getRooms() non-empty) -
            //     one real record already carries its own correct grand total,
            //     nothing to sum. Never coexists with case 2, since a single-call
            //     creation is never tagged by BookingGroupState in the first place.
            //  2. Legacy client-side grouping (groupMembers != null) - N separate
            //     sibling records tied by a local tag, see BookingGroupState's own
            //     doc; the total here must be summed across every sibling.
            //  3. A normal single-room transaction - unchanged, original behavior.
            if (!b.getRooms().isEmpty()) {
                holder.tvBookingRoomName.setText(buildTrueMultiRoomSummaryText(b));
                holder.tvBookingPrice.setText(String.format(Locale.US, getString(R.string.price_format), b.getTotalAmount()));
            } else if (groupMembers != null && !groupMembers.isEmpty()) {
                holder.tvBookingRoomName.setText(buildGroupSummaryText(groupMembers));
                double grandTotal = 0;
                for (Booking member : groupMembers) grandTotal += member.getTotalAmount();
                holder.tvBookingPrice.setText(String.format(Locale.US, getString(R.string.price_format), grandTotal));
            } else {
                int quantity = Math.max(1, b.getRoomsRequested());
                holder.tvBookingRoomName.setText(quantity > 1 ? b.getRoomName() + " ×" + quantity : b.getRoomName());
                holder.tvBookingPrice.setText(String.format(Locale.US, getString(R.string.price_format), b.getTotalAmount()));
            }

            holder.itemView.setOnClickListener(v -> showBookingDetails(b));
            holder.btnViewDetails.setOnClickListener(v -> showBookingDetails(b));

            // Pay Now only makes sense for a plain Reservation (no payment yet)
            // that's still active - the Booking List's items already have a
            // payment on record. See PaymentEligibility.canPayNow() for the
            // full, single-source-of-truth eligibility rule (also used by
            // DashboardActivity) - it already excludes Cancelled/Rejected and
            // an already-converted Booking, so only the Booking-tab and
            // Checked-Out exclusions need to be added here.
            boolean canPayNow = !isBookingTab
                    && !b.getStatus().equalsIgnoreCase("Checked-Out")
                    && PaymentEligibility.canPayNow(b);
            if (holder.btnPayNow != null) {
                holder.btnPayNow.setVisibility(canPayNow ? View.VISIBLE : View.GONE);
                holder.btnPayNow.setOnClickListener(v -> showPayNowConfirmation(b));
            }

            // Delete is only offered for Cancelled/Rejected transactions on
            // either tab, plus Completed on the Booking tab specifically -
            // a Reservation never offers Delete for Category.COMPLETED, even
            // though a staff-verified-but-not-yet-converted reservation does
            // reach that category (see TransactionCategorizer's own doc on
            // the "Completed Reservation List" tab): that bucket is still an
            // active, in-progress reservation from the guest's perspective,
            // not a terminal state, so only an actual Cancelled/Rejected
            // reservation may ever be permanently deleted. Cancel/Modify only
            // make sense while a transaction is still Upcoming/Active - see
            // TransactionCategorizer for the shared definition every list/
            // dashboard screen now agrees on.
            TransactionCategorizer.Category category = TransactionCategorizer.categorize(b);
            boolean deletable = category == TransactionCategorizer.Category.CANCELLED
                    || (isBookingTab && category == TransactionCategorizer.Category.COMPLETED);
            styleStatusBadge(holder.tvBookingStatus, b);
            if (deletable) {
                holder.btnModify.setVisibility(View.GONE);
                holder.btnCancel.setVisibility(View.GONE);
                // A historical (post-conversion) reservation record is
                // view/record-only - it must never offer Delete either, since
                // its "-completed"-suffixed id isn't a real backend row this
                // app can call delete against, and the record must stay
                // available as a permanent historical reference regardless.
                if (holder.btnDelete != null) {
                    holder.btnDelete.setVisibility(b.isHistoricalReservation() ? View.GONE : View.VISIBLE);
                    holder.btnDelete.setEnabled(true);
                    holder.btnDelete.setText(R.string.delete_permanently_button_label);
                    holder.btnDelete.setOnClickListener(v -> confirmDeleteTransaction(b));
                }
                // Book/Reserve Again only makes sense for a Cancelled item -
                // a Completed stay is already done, nothing to redo.
                if (holder.btnBookAgain != null) {
                    boolean canBookAgain = category == TransactionCategorizer.Category.CANCELLED;
                    holder.btnBookAgain.setVisibility(canBookAgain ? View.VISIBLE : View.GONE);
                    if (canBookAgain) {
                        holder.btnBookAgain.setText(isBookingTab ? R.string.book_again_button : R.string.reserve_again_button);
                        holder.btnBookAgain.setOnClickListener(v -> startBookAgain(b));
                    }
                }
            } else {
                if (holder.btnDelete != null) {
                    holder.btnDelete.setVisibility(View.GONE);
                }
                if (holder.btnBookAgain != null) {
                    holder.btnBookAgain.setVisibility(View.GONE);
                }
                boolean staffVerified = b.isStaffVerified();
                // A fully-paid active Booking may only be View Details'd from
                // here on - cancellation/refund/rescheduling for it becomes a
                // Receptionist-handled request instead (btnModify is already
                // never shown for a Booking-tab item, see canModify below, so
                // this Cancel guard is the only piece needed to satisfy that).
                // Fully paid -> cancellation is never allowed (see
                // PaymentStateUtil#isFullyPaid()'s dual status/amount check) -
                // both branches explicitly set every piece of state a
                // recycled ViewHolder could have inherited from a previous,
                // differently-eligible item (visibility, enabled, alpha, and
                // the click listener itself), not just visibility, so a
                // stale listener/alpha can never survive a rebind.
                boolean fullyPaid = PaymentStateUtil.isFullyPaid(b);
                if (fullyPaid) {
                    holder.btnCancel.setVisibility(View.GONE);
                    holder.btnCancel.setEnabled(false);
                    holder.btnCancel.setAlpha(1f);
                    holder.btnCancel.setOnClickListener(null);
                } else {
                    // Stays clickable even once staff-verified - showCancelDialog()
                    // already shows a clear "our staff already verified this"
                    // explanation in that case; setEnabled(false) would make this
                    // a dead, unexplained control instead of a clear indication.
                    holder.btnCancel.setVisibility(View.VISIBLE);
                    holder.btnCancel.setEnabled(true);
                    holder.btnCancel.setAlpha(staffVerified ? 0.4f : 1f);
                    holder.btnCancel.setOnClickListener(v -> showCancelDialog(b));
                }

                // Booking List (a payment already exists) only ever shows
                // View Details + Cancel. The Reservation List additionally
                // allows a single Modify while the stay is still Pending (not
                // once it's Confirmed - narrowed from Pending-or-Confirmed).
                // b.isEditedOnce() is the server-authoritative one-time-edit lock
                // (Reservation::edited_at, see Api\ReservationController::update())
                // - the real enforcement, unbypassable by reinstall/another device.
                // LocalTransactionState.hasModifiedOnce() is kept alongside it purely
                // as an optimistic UI flag so the button disappears instantly right
                // after a save, before the next server refresh lands.
                boolean canModify = !isBookingTab
                        && !b.isHasBooking()
                        && b.getStatus().equalsIgnoreCase("Pending")
                        && !b.isEditedOnce()
                        && !LocalTransactionState.hasModifiedOnce(BookingAndReservationActivity.this, b.getId());
                if (canModify) {
                    holder.btnModify.setVisibility(View.VISIBLE);
                    holder.btnModify.setOnClickListener(v -> launchModifyWizard(b));
                } else {
                    holder.btnModify.setVisibility(View.GONE);
                }

            }

            // Highlight the card the guest selected from the dashboard.
            if (holder.itemView instanceof com.google.android.material.card.MaterialCardView) {
                com.google.android.material.card.MaterialCardView card =
                        (com.google.android.material.card.MaterialCardView) holder.itemView;
                boolean highlighted = highlightId != null && highlightId.equals(b.getId());
                card.setStrokeColor(getResources().getColor(
                        highlighted ? R.color.velocity_red_primary : R.color.velocity_red_subtle));
                card.setStrokeWidth((int) (getResources().getDisplayMetrics().density * (highlighted ? 2 : 1)));
            }
        }

        @Override
        public int getItemCount() {
            return mBookings.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvBookingId, tvBookingStatus, tvModifiedBadge, tvBookingRoomName, tvBookingDates, tvBookingGuests, tvBookingPrice;
            LinearLayout layoutActionButtons;
            MaterialButton btnModify, btnCancel, btnViewDetails, btnPayNow, btnDelete, btnBookAgain;

            ViewHolder(View itemView) {
                super(itemView);
                tvBookingId = itemView.findViewById(R.id.tvBookingId);
                tvBookingStatus = itemView.findViewById(R.id.tvBookingStatus);
                tvModifiedBadge = itemView.findViewById(R.id.tvModifiedBadge);
                tvBookingRoomName = itemView.findViewById(R.id.tvBookingRoomName);
                tvBookingDates = itemView.findViewById(R.id.tvBookingDates);
                tvBookingGuests = itemView.findViewById(R.id.tvBookingGuests);
                tvBookingPrice = itemView.findViewById(R.id.tvBookingPrice);
                layoutActionButtons = itemView.findViewById(R.id.layoutActionButtons);
                btnModify = itemView.findViewById(R.id.btnModify);
                btnCancel = itemView.findViewById(R.id.btnCancel);
                btnViewDetails = itemView.findViewById(R.id.btnViewDetails);
                btnPayNow = itemView.findViewById(R.id.btnPayNow);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                btnBookAgain = itemView.findViewById(R.id.btnBookAgain);
            }
        }
    }

    /**
     * Read-only transaction details view - no Modify/Edit action here by
     * design (modifying only ever happens via the card's own dedicated,
     * correctly-gated Modify button). Purely displays the given Booking's
     * current data; does not alter, cancel, duplicate, or otherwise touch
     * the transaction in any way.
     */
    /** Opens the full-detail screen for one Booking/Reservation - replaces the old inline dialog (dialog_booking_reservation_details.xml) with BookingDetailsActivity, which reuses all the same field logic across its Details/Guest Info/Payment/Timeline sections. */
    private void showBookingDetails(Booking b) {
        if (!NavUtils.allowClick()) return;
        startActivity(BookingDetailsActivity.newIntent(this, b));
    }

    /**
     * Pay Now on a Reservation List card: confirms intent, then hands off to
     * BillingSummaryActivity, which gates the actual payment behind a
     * Terms & Cancellation Policy acknowledgement before routing to Payment.
     */
    private void showPayNowConfirmation(Booking booking) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.pay_now_confirm_title)
                .setMessage(getString(R.string.pay_now_confirm_msg, booking.getId()))
                .setPositiveButton(R.string.confirm_dialog_positive, (dialog, which) -> {
                    if (!NavUtils.allowClick()) return;
                    android.content.Intent intent = new android.content.Intent(BookingAndReservationActivity.this, BillingSummaryActivity.class);
                    intent.putExtra(BillingSummaryActivity.EXTRA_RESERVATION_ID, booking.getId());
                    startActivity(intent);
                })
                .setNegativeButton(R.string.no_label, null)
                .show();
    }

    /**
     * Launches BookingWizardActivity for a Reservation's one-time Modify -
     * Step 1 of 7 through Step 7 of 7, replacing the old inline edit form
     * (populateFormForEdit()/finalizeBooking()'s editingBookingId branch,
     * left in place as dead code rather than removed in this pass).
     */
    private void launchModifyWizard(Booking b) {
        if (!NavUtils.allowClick()) return;
        startActivity(BookingWizardActivity.newEditIntent(this, b));
    }

    /**
     * A direct Booking (New Booking, never derived from a Reservation) has
     * no parent Reservation row - cancelReservation() would 404 against it
     * (or worse, hit an unrelated Reservation that happens to share the
     * same numeric id). Routes to the correct server endpoint for either
     * booking shape.
     */
    private void cancelAnyBooking(Booking b, RoomRepository.RepositoryCallback<Booking> callback) {
        if (b.isDirectBooking()) {
            repository.cancelDirectBooking(b.getId(), callback);
        } else {
            repository.cancelReservation(b.getId(), callback);
        }
    }

    private void showCancelDialog(Booking booking) {
        boolean isBooking = booking.isHasBooking();

        // Only a fully paid booking is locked in - mirrors the server-side
        // gate in Api\BookingController::cancel()/ReservationWorkflowService::
        // cancelConvertedBooking(), both keyed off Booking.isFullyPaid(), not
        // verified_at. Staff verifying a partial/deposit payment doesn't make
        // it a full payment, so a verified-but-partial booking must still
        // reach the confirmation dialog below rather than being refused
        // outright here.
        if (PaymentStateUtil.isFullyPaid(booking)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.cancel_not_allowed_title)
                    .setMessage(R.string.cancel_not_allowed_verified_msg)
                    .setPositiveButton(R.string.close_label, null)
                    .show();
            return;
        }

        // A partially paid booking can still be cancelled, but the guest must be
        // explicitly warned that the partial payment already made is forfeited.
        int messageRes = PaymentStateUtil.isPartiallyPaid(booking)
                ? R.string.cancel_partial_payment_confirm
                : (isBooking ? R.string.cancel_booking_confirm : R.string.cancel_reservation_confirm);

        new MaterialAlertDialogBuilder(this)
                .setTitle(isBooking ? R.string.cancel_booking_title : R.string.cancel_reservation_title)
                .setMessage(messageRes)
                .setPositiveButton(R.string.yes_cancel, (dialog, which) -> cancelAnyBooking(booking,
                        new RoomRepository.RepositoryCallback<Booking>() {
                            @Override
                            public void onSuccess(Booking result) {
                                new MaterialAlertDialogBuilder(BookingAndReservationActivity.this)
                                        .setTitle(isBooking ? R.string.cancel_booking_success_title : R.string.cancel_reservation_success_title)
                                        .setMessage(isBooking ? R.string.cancel_booking_success_msg : R.string.cancel_reservation_success_msg)
                                        .setPositiveButton(R.string.close_label, null)
                                        .show();
                                refreshMyBookings();
                                // The cancelled room's availability just changed
                                // server-side - refresh the cached inventory so
                                // Add Room reflects it without needing a full
                                // screen restart.
                                if (hasEnteredCheckIn() && hasEnteredCheckOut()) {
                                    refreshRoomsForSelectedDates();
                                } else {
                                    repository.refreshRooms(new RoomRepository.RepositoryCallback<List<Room>>() {
                                        @Override
                                        public void onSuccess(List<Room> result) {
                                            allRooms = result;
                                        }

                                        @Override
                                        public void onError(String message) {
                                            // Best-effort refresh; the next Add Room open still re-fetches.
                                        }
                                    });
                                }
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(BookingAndReservationActivity.this, getString(R.string.cancel_failed_format, message), Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton(R.string.no_label, null)
                .show();
    }

    /**
     * Real, permanent, non-recoverable deletion (a hard DELETE, not a
     * client-only flag or a server-side soft hide) - the row and its owned
     * child records are actually removed from the hotel's database. Only
     * reachable from the UI for Cancelled/Rejected/Completed cards (see
     * BookingsAdapter); the server independently re-validates ownership and
     * status too (see TRANSACTION_PERMANENT_DELETE_BACKEND_SPEC.md), so a
     * tampered client request can never delete another guest's transaction
     * or an ineligible one. The confirm button is disabled for the duration
     * of the request to prevent duplicate delete taps.
     */
    private void confirmDeleteTransaction(Booking booking) {
        // Wording reflects the guest-facing nature (Booking vs Reservation,
        // same isHasBooking() split the ref-text/status labels already use)
        // - orthogonal to isDirectBooking() below, which decides which
        // backend table's endpoint the delete call itself must hit.
        int messageRes = booking.isHasBooking() ? R.string.delete_booking_transaction_confirm : R.string.delete_reservation_transaction_confirm;
        int successMsgRes = booking.isHasBooking() ? R.string.delete_booking_transaction_success : R.string.delete_reservation_transaction_success;

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_transaction_permanently_title)
                .setMessage(messageRes)
                .setPositiveButton(R.string.delete_permanently_button_label, null)
                .setNegativeButton(R.string.cancel_label, null)
                .create();

        dialog.setOnShowListener(shownDialog -> {
            android.widget.Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            android.widget.Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            positive.setOnClickListener(v -> {
                // Disable both buttons and swap in a "Deleting..." label so a
                // repeated tap while the request is in flight can't fire it twice.
                positive.setEnabled(false);
                negative.setEnabled(false);
                positive.setText(R.string.deleting_in_progress_label);

                RoomRepository.RepositoryCallback<Void> deleteCallback = new RoomRepository.RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        dismissSafely(dialog);
                        if (isFinishing() || isDestroyed()) return;
                        new MaterialAlertDialogBuilder(BookingAndReservationActivity.this)
                                .setTitle(R.string.delete_transaction_success_title)
                                .setMessage(successMsgRes)
                                .setPositiveButton(R.string.close_label, null)
                                .show();
                        // No manual renderList() needed - RoomRepository's
                        // change notification (see onBookingsChanged) refreshes it.
                    }

                    @Override
                    public void onError(String message) {
                        // Leave the dialog open and re-enable controls so the
                        // guest can retry - the item must NOT disappear from
                        // the list on failure.
                        positive.setEnabled(true);
                        negative.setEnabled(true);
                        positive.setText(R.string.delete_permanently_button_label);
                        Toast.makeText(BookingAndReservationActivity.this,
                                getString(R.string.delete_transaction_failed_format, message), Toast.LENGTH_LONG).show();
                    }
                };

                // A direct Booking (no parent Reservation row) must delete through
                // the bookings table's own endpoint - deleteReservationPermanently()
                // always resolves against reservations, and bookings/reservations each
                // have their own independent id sequence, so reusing it here could
                // delete a completely unrelated reservation that happens to share the
                // same numeric id (see RoomRepository#deleteBookingPermanently()).
                // Every reservation-derived transaction - converted or not, Cancelled
                // or Completed - goes through the reservations endpoint instead (see
                // Booking#getBookingEndpointDeleteId()'s doc: confirmed directly
                // against the live backend that Api\BookingController only ever
                // handles direct bookings).
                String bookingEndpointId = booking.getBookingEndpointDeleteId();
                if (bookingEndpointId != null) {
                    repository.deleteBookingPermanently(bookingEndpointId, deleteCallback);
                } else {
                    repository.deleteReservationPermanently(booking.getId(), deleteCallback);
                }
            });
        });
        dialog.show();
    }
}
