package com.example.velocitysuites;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.velocitysuites.network.SessionManager;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class LandingActivity extends AppCompatActivity implements RoomAdapter.OnRoomClickListener, OnMapReadyCallback {

    private static final int COLLAPSED_ROOM_COUNT = 3;
    private static final String MAP_VIEW_BUNDLE_KEY = "MapViewBundleKey";
    // Exact Velocity Suites landmark pin, Surallah, South Cotabato.
    private static final double HOTEL_LATITUDE = 6.368977;
    private static final double HOTEL_LONGITUDE = 124.7428036;

    private RecyclerView rvLandingRooms;
    private ProgressBar landingRoomsLoading;
    private View landingRoomsEmpty;
    private View layoutRoomError;
    private View roomErrorState;
    private TextView tvLandingRoomsCount;
    private RoomAdapter roomAdapter;
    private RecyclerView rvLandingAnnouncements;
    private ProgressBar announcementsLoading;
    private View announcementsStateView;
    private AnnouncementAdapter announcementAdapter;
    private GridLayout gridLandingAmenities;
    private ProgressBar amenitiesLoading;
    private View amenitiesStateView;
    private RecyclerView rvLandingPromotions;
    private ProgressBar promotionsLoading;
    private View promotionsStateView;
    private OffersAdapter offersAdapter;
    // Promotions and Discounts load independently (two separate endpoints,
    // same as the web Home page's own two Eloquent queries) and are merged
    // into one combined list once both have returned - see refreshOffers().
    // The merged list is built from the LATEST SUCCESSFUL copy of each half, so
    // a refresh where only one of the two fails keeps showing the other half's
    // previous content instead of silently dropping it.
    private List<Promotion> loadedPromotions;
    private List<Discount> loadedDiscounts;
    private boolean promotionsLoadFailed;
    private boolean discountsLoadFailed;
    private List<Promotion> lastPromotions = new ArrayList<>();
    private List<Discount> lastDiscounts = new ArrayList<>();
    private AutoCompleteTextView dropdownRoomTypeFilter;
    private MaterialButton btnToggleRooms;
    private MapView hotelMapView;
    private GoogleMap hotelGoogleMap;
    private List<Room> rooms = new ArrayList<>();
    private List<Room> allAvailableRooms = new ArrayList<>();
    private List<String> roomTypeFilterOptions = new ArrayList<>();
    private String currentTypeFilter = "All";
    private boolean roomsExpanded = false;

    // ---- Refresh ----
    // Pull-to-refresh, the silent catch-up when the page comes back on screen, and the 30s beat while it stays
    // there all go through one LandingRefreshCoordinator, so they can never overlap or double-fetch.
    private SwipeRefreshLayout landingSwipeRefresh;
    private LandingRefreshCoordinator refreshCoordinator;
    private VisiblePoller visiblePoller;
    private Snackbar refreshFailedSnackbar;
    private final OfflineGate offlineGate = new OfflineGate(this);

    /** onCreate() starts the first load itself, so the very first onResume() must not start a second one. */
    private boolean firstResume = true;
    // A section that has never loaded has nothing to show, so it shows its own spinner and, if it fails, its own
    // error panel. A section that has loaded keeps its content on screen through every later refresh - a failed
    // refresh only reports the failure (see beginSectionLoad() and the refresh*() methods).
    private boolean roomsLoaded;
    private boolean announcementsLoaded;
    private boolean amenitiesLoaded;
    private boolean offersLoaded;

    /** Multi-room cart: ids checked via each card's selection checkbox (see RoomAdapter). */
    /** Room id -> selected quantity (never left at 0 - see RoomAdapter's selectedQuantities docblock). */
    private final Map<String, Integer> selectedQuantities = new LinkedHashMap<>();
    private View cardSelectionSummaryBar;
    private TextView tvSelectionSummaryCount, tvSelectionSummaryTotal;
    /** Debounces rapid double-taps on Book Now/Reserve Now so a fast double-tap can't launch two Activities. Reset in onResume(). */
    private boolean isNavigatingToBookingFlow = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.landing);

        View landingRoot = findViewById(R.id.landingRoot);
        ViewCompat.setOnApplyWindowInsetsListener(landingRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), bars.bottom);
            return insets;
        });

        initializeViews();
        setupRoomsList();
        setupAnnouncementsList();
        setupPromotionsList();
        setupActions();
        setupHotelMap(savedInstanceState);
        animateLanding();
        setupRefresh();
        // The first load: silent (no pull spinner) - each section shows its own spinner until it has content.
        refreshCoordinator.poll();
    }

    private void initializeViews() {
        rvLandingRooms = findViewById(R.id.rvLandingRooms);
        landingRoomsLoading = findViewById(R.id.landingRoomsLoading);
        landingRoomsEmpty = findViewById(R.id.landingRoomsEmpty);
        layoutRoomError = findViewById(R.id.layoutRoomError);
        roomErrorState = findViewById(R.id.roomErrorState);
        // Retry on the rooms error panel: a silent refresh of the page (the rooms section shows its own spinner,
        // no pull spinner). Ignored while a refresh is already running - that one will settle the panel anyway.
        findViewById(R.id.btnRetryRooms).setOnClickListener(v -> refreshCoordinator.poll());
        tvLandingRoomsCount = findViewById(R.id.tvLandingRoomsCount);
        rvLandingAnnouncements = findViewById(R.id.rvLandingAnnouncements);
        announcementsLoading = findViewById(R.id.announcementsLoading);
        announcementsStateView = findViewById(R.id.announcementsStateView);
        gridLandingAmenities = findViewById(R.id.gridLandingAmenities);
        amenitiesLoading = findViewById(R.id.progressLandingAmenities);
        amenitiesStateView = findViewById(R.id.amenitiesStateView);
        rvLandingPromotions = findViewById(R.id.rvLandingPromotions);
        promotionsLoading = findViewById(R.id.promotionsLoading);
        promotionsStateView = findViewById(R.id.promotionsStateView);
        dropdownRoomTypeFilter = findViewById(R.id.dropdownRoomTypeFilter);
        btnToggleRooms = findViewById(R.id.btnToggleRooms);
        hotelMapView = findViewById(R.id.hotelMapView);
        cardSelectionSummaryBar = findViewById(R.id.cardSelectionSummaryBar);
        tvSelectionSummaryCount = findViewById(R.id.tvSelectionSummaryCount);
        tvSelectionSummaryTotal = findViewById(R.id.tvSelectionSummaryTotal);
    }

    /**
     * Pull-to-refresh, plus the silent refreshes (back on the page, and the 30s beat while it stays on screen).
     * <p>
     * What the page loads from the server: Available Rooms (GET rooms), Promotions and Discounts (GET promotions +
     * GET discounts, merged), Announcements (GET announcements) and Amenities (GET amenities). Everything else
     * - the hero, the hotel details/contact card, the map pin, the footer - is fixed text in the app: the backend
     * has no endpoint for it, so there is nothing to refresh there.
     * <p>
     * LandingRefreshCoordinator owns the rules (one refresh at a time, report once when every section has
     * answered); this method only turns its two events into UI: the pull spinner and the failure message belong
     * to the guest's own refresh, a silent one stays invisible unless a section has nothing to show yet.
     */
    private void setupRefresh() {
        landingSwipeRefresh = findViewById(R.id.landingSwipeRefresh);
        landingSwipeRefresh.setColorSchemeResources(R.color.velocity_red_primary);
        FlatUi.removeSpinnerShadow(landingSwipeRefresh);

        Map<String, LandingRefreshCoordinator.Section> sections = new LinkedHashMap<>();
        sections.put("rooms", this::refreshRooms);
        sections.put("announcements", this::refreshAnnouncements);
        sections.put("amenities", this::refreshAmenities);
        sections.put("offers", this::refreshOffers);
        final int sectionCount = sections.size();

        refreshCoordinator = new LandingRefreshCoordinator(sections, new LandingRefreshCoordinator.Listener() {
            @Override
            public void onRefreshStarted(boolean userInitiated) {
                if (!userInitiated) return;
                dismissRefreshFailure();
                // Already true after a pull gesture; this is what shows it for the Retry button.
                landingSwipeRefresh.setRefreshing(true);
            }

            @Override
            public void onRefreshFinished(boolean userInitiated, List<String> failed) {
                if (!userInitiated || !isUiAlive()) return;
                landingSwipeRefresh.setRefreshing(false);
                if (!failed.isEmpty()) showRefreshFailure(failed.size() >= sectionCount);
            }
        });

        // While a refresh is running the layout ignores further pulls itself; the coordinator also ignores a second
        // request (e.g. a Retry tap) so nothing can start twice.
        landingSwipeRefresh.setOnRefreshListener(() -> refreshCoordinator.refresh());

        // TalkBack users cannot pull down, so the page offers the same refresh as an accessibility action.
        ViewCompat.addAccessibilityAction(findViewById(R.id.landingScroll), getString(R.string.landing_refresh_action),
                (view, arguments) -> {
                    if (refreshCoordinator.refresh()) landingSwipeRefresh.setRefreshing(true);
                    return true;
                });

        visiblePoller = new VisiblePoller(() -> refreshCoordinator.poll());
    }

    /** The guest's own refresh failed in at least one section: say so, offer Retry, and leave the old content alone. */
    private void showRefreshFailure(boolean everythingFailed) {
        Snackbar snackbar = Snackbar.make(findViewById(R.id.landingRoot),
                everythingFailed ? R.string.landing_refresh_failed_all : R.string.landing_refresh_failed_some,
                Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.retry_label, v -> refreshCoordinator.refresh());
        // Keep clear of the Reserve/Book bar when it is showing.
        if (cardSelectionSummaryBar != null && cardSelectionSummaryBar.getVisibility() == View.VISIBLE) {
            snackbar.setAnchorView(cardSelectionSummaryBar);
        }
        refreshFailedSnackbar = snackbar;
        snackbar.show();
    }

    private void dismissRefreshFailure() {
        if (refreshFailedSnackbar != null) {
            refreshFailedSnackbar.dismiss();
            refreshFailedSnackbar = null;
        }
    }

    private void setupHotelMap(Bundle savedInstanceState) {
        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAP_VIEW_BUNDLE_KEY);
        }
        hotelMapView.onCreate(mapViewBundle);
        hotelMapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        hotelGoogleMap = googleMap;
        LatLng hotelLocation = new LatLng(HOTEL_LATITUDE, HOTEL_LONGITUDE);

        googleMap.addMarker(new MarkerOptions()
                .position(hotelLocation)
                .title(getString(R.string.landing_hotel_name))
                .snippet(getString(R.string.landing_hotel_address)));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(hotelLocation, 16f));

        UiSettings uiSettings = googleMap.getUiSettings();
        uiSettings.setZoomControlsEnabled(false);
        uiSettings.setMapToolbarEnabled(false);
        uiSettings.setRotateGesturesEnabled(false);
        uiSettings.setTiltGesturesEnabled(false);
        uiSettings.setScrollGesturesEnabled(false);
        uiSettings.setZoomGesturesEnabled(true);

        googleMap.setOnMapClickListener(latLng -> openHotelLocationInMaps());
        googleMap.setOnMarkerClickListener(marker -> {
            openHotelLocationInMaps();
            return true;
        });
    }

    private void setupRoomsList() {
        rvLandingRooms.setLayoutManager(new LinearLayoutManager(this));
        rvLandingRooms.setNestedScrollingEnabled(false);
        roomAdapter = new RoomAdapter(rooms, this, false, selectedQuantities);
        rvLandingRooms.setAdapter(roomAdapter);

        findViewById(R.id.btnCartClear).setOnClickListener(v -> {
            selectedQuantities.clear();
            roomAdapter.notifyDataSetChanged();
            updateSelectionSummaryBar();
        });
        findViewById(R.id.btnCartReserve).setOnClickListener(v -> handleCartAction(PendingRoomSelection.ACTION_RESERVE));
        findViewById(R.id.btnCartBook).setOnClickListener(v -> handleCartAction(PendingRoomSelection.ACTION_BOOK));
        findViewById(R.id.layoutSelectionSummaryDetails).setOnClickListener(v -> showSelectedRoomsDialog());
    }

    private void showSelectedRoomsDialog() {
        List<Room> selected = resolveSelectedRooms();
        if (selected.isEmpty()) return;
        SelectedRoomsDialog.show(this, selected, room -> {
            selectedQuantities.remove(room.getId());
            roomAdapter.notifyDataSetChanged();
            updateSelectionSummaryBar();
        });
    }

    private void setupAnnouncementsList() {
        rvLandingAnnouncements.setLayoutManager(new LinearLayoutManager(this));
        rvLandingAnnouncements.setNestedScrollingEnabled(false);
        announcementAdapter = new AnnouncementAdapter(this::showAnnouncementDetail);
        rvLandingAnnouncements.setAdapter(announcementAdapter);
    }

    private void setupPromotionsList() {
        rvLandingPromotions.setLayoutManager(new LinearLayoutManager(this));
        rvLandingPromotions.setNestedScrollingEnabled(false);
        offersAdapter = new OffersAdapter();
        rvLandingPromotions.setAdapter(offersAdapter);
    }

    /**
     * Published, guest-audience announcements - Api\CatalogController::announcements()
     * (Announcement::visibleTo('guest')), the exact same centralized rows that also
     * drive this guest's Notification-module entries and the web public Home page's
     * own Announcements section - never a separate mobile-only record.
     */
    private void refreshAnnouncements(Consumer<Boolean> onDone) {
        beginSectionLoad(announcementsLoaded, announcementsLoading, rvLandingAnnouncements, announcementsStateView);
        RoomRepository.getInstance(this).refreshAnnouncementsFresh(new RoomRepository.RepositoryCallback<List<Announcement>>() {
            @Override
            public void onSuccess(List<Announcement> result) {
                if (!isUiAlive()) return;
                announcementsLoaded = true;
                if (result == null || result.isEmpty()) {
                    showSectionEmpty(announcementsLoading, rvLandingAnnouncements, announcementsStateView,
                            getString(R.string.announcements_empty_title), getString(R.string.announcements_empty_desc));
                } else {
                    announcementAdapter.updateList(result);
                    showSectionContent(announcementsLoading, rvLandingAnnouncements, announcementsStateView);
                }
                onDone.accept(true);
            }

            @Override
            public void onError(String message) {
                if (!isUiAlive()) return;
                // Nothing on screen yet -> say so. Something already there -> leave it exactly as it is.
                if (!announcementsLoaded) {
                    showSectionError(announcementsLoading, rvLandingAnnouncements, announcementsStateView,
                            getString(R.string.announcements_error_title), getString(R.string.announcements_error_desc));
                }
                onDone.accept(false);
            }
        });
    }

    /**
     * Active-only, admin-managed Amenities catalog (Api\CatalogController::amenities(),
     * same GET /amenities the booking screen's add-on picker and the web public
     * Amenities page both use) - never a hardcoded list, so an amenity the System
     * Administrator adds/edits/activates/deactivates is reflected here automatically
     * on next load, with no separate mobile-only copy to keep in sync.
     */
    private void refreshAmenities(Consumer<Boolean> onDone) {
        beginSectionLoad(amenitiesLoaded, amenitiesLoading, gridLandingAmenities, amenitiesStateView);
        RoomRepository.getInstance(this).refreshAmenitiesFresh(new RoomRepository.RepositoryCallback<List<AddOnAmenity>>() {
            @Override
            public void onSuccess(List<AddOnAmenity> result) {
                if (!isUiAlive()) return;
                amenitiesLoaded = true;
                amenitiesLoading.setVisibility(View.GONE);
                if (result == null || result.isEmpty()) {
                    gridLandingAmenities.setVisibility(View.GONE);
                    applyStateMessage(amenitiesStateView, getString(R.string.landing_amenities_empty_title),
                            getString(R.string.landing_amenities_empty_desc), false);
                } else {
                    renderAmenities(result);
                    gridLandingAmenities.setVisibility(View.VISIBLE);
                    amenitiesStateView.setVisibility(View.GONE);
                }
                onDone.accept(true);
            }

            @Override
            public void onError(String message) {
                if (!isUiAlive()) return;
                if (!amenitiesLoaded) {
                    amenitiesLoading.setVisibility(View.GONE);
                    gridLandingAmenities.setVisibility(View.GONE);
                    applyStateMessage(amenitiesStateView, getString(R.string.landing_amenities_error_title),
                            getString(R.string.landing_amenities_error_desc), true);
                }
                onDone.accept(false);
            }
        });
    }

    private void renderAmenities(List<AddOnAmenity> amenities) {
        gridLandingAmenities.removeAllViews();
        int marginPx = (int) (getResources().getDisplayMetrics().density * 6);
        for (AddOnAmenity amenity : amenities) {
            TextView item = new TextView(this, null, 0, R.style.Widget_Velocity_LandingAmenity);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(marginPx, marginPx, marginPx, marginPx);
            item.setLayoutParams(params);
            item.setText(amenity.getCategory() != null && !amenity.getCategory().isEmpty()
                    ? amenity.getName() + "\n" + amenity.getCategory()
                    : amenity.getName());
            item.setCompoundDrawablesWithIntrinsicBounds(0, RoomVisuals.getAmenityIcon(amenity.getName()), 0, 0);
            item.setOnClickListener(v -> showAmenityDetail(amenity));
            gridLandingAmenities.addView(item);
        }
    }

    /** Full-record details for a tapped amenity tile - the grid itself only shows name/category. */
    private void showAmenityDetail(AddOnAmenity amenity) {
        StringBuilder message = new StringBuilder();
        if (amenity.getCategory() != null && !amenity.getCategory().isEmpty()) {
            message.append(amenity.getCategory()).append("\n\n");
        }
        if (amenity.getDescription() != null && !amenity.getDescription().isEmpty()) {
            message.append(amenity.getDescription()).append("\n\n");
        }
        if (amenity.getPrice() > 0) {
            message.append("₱").append(String.format(java.util.Locale.getDefault(), "%.2f", amenity.getPrice()))
                    .append(" - Paid/Additional\n");
        } else {
            message.append("Free / Included\n");
        }
        message.append(amenity.getMaxQuantity()).append(" available");

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(amenity.getName())
                .setMessage(message.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    /**
     * Active, currently-in-date-range promotions (Api\CatalogController::promotions())
     * plus active standing discounts (Api\CatalogController::discounts()) - the exact
     * same two centralized queries the web public Home page's own "Promotions &
     * Discounts" section uses, merged into one combined list here so the mobile
     * section matches that unified grid instead of only ever showing Promotions.
     * Guests can only ever view these - creating/editing/deleting stays an
     * admin-only, web-only capability. The two calls run independently; the
     * combined list only renders once both have returned, and the section only
     * shows an error if BOTH fail (a lone hiccup on one endpoint still shows
     * whatever the other one loaded).
     */
    private void refreshOffers(Consumer<Boolean> onDone) {
        beginSectionLoad(offersLoaded, promotionsLoading, rvLandingPromotions, promotionsStateView);
        loadedPromotions = null;
        loadedDiscounts = null;
        promotionsLoadFailed = false;
        discountsLoadFailed = false;

        RoomRepository.getInstance(this).refreshPromotionsFresh(new RoomRepository.RepositoryCallback<List<Promotion>>() {
            @Override
            public void onSuccess(List<Promotion> result) {
                loadedPromotions = result != null ? result : new ArrayList<>();
                tryRenderOffers(onDone);
            }

            @Override
            public void onError(String message) {
                promotionsLoadFailed = true;
                loadedPromotions = new ArrayList<>();
                tryRenderOffers(onDone);
            }
        });

        RoomRepository.getInstance(this).refreshDiscountsFresh(new RoomRepository.RepositoryCallback<List<Discount>>() {
            @Override
            public void onSuccess(List<Discount> result) {
                loadedDiscounts = result != null ? result : new ArrayList<>();
                tryRenderOffers(onDone);
            }

            @Override
            public void onError(String message) {
                discountsLoadFailed = true;
                loadedDiscounts = new ArrayList<>();
                tryRenderOffers(onDone);
            }
        });
    }

    /**
     * Called after each of the two offer calls finishes - only actually renders once both have returned.
     * <p>
     * The list shown is the latest successful copy of each half, so a refresh where one call fails keeps that
     * half's previous content. A list that came back empty because one half FAILED is not "no offers" (it is
     * unknown), so it never replaces content and never shows the friendly empty message on its own.
     * Reports success to the coordinator only when both calls succeeded.
     */
    private void tryRenderOffers(Consumer<Boolean> onDone) {
        if (loadedPromotions == null || loadedDiscounts == null) return;
        if (!isUiAlive()) return;

        boolean bothLoaded = !promotionsLoadFailed && !discountsLoadFailed;
        if (!promotionsLoadFailed) lastPromotions = loadedPromotions;
        if (!discountsLoadFailed) lastDiscounts = loadedDiscounts;

        List<Object> combined = new ArrayList<>();
        combined.addAll(lastPromotions);
        combined.addAll(lastDiscounts);

        if (combined.isEmpty() && !bothLoaded) {
            // Nothing to show, and at least one half is unknown: an error, unless something is already on screen.
            if (!offersLoaded) {
                showSectionError(promotionsLoading, rvLandingPromotions, promotionsStateView,
                        getString(R.string.promotions_error_title), getString(R.string.promotions_error_desc));
            }
            onDone.accept(false);
            return;
        }

        offersLoaded = true;
        if (combined.isEmpty()) {
            showSectionEmpty(promotionsLoading, rvLandingPromotions, promotionsStateView,
                    getString(R.string.promotions_empty_title), getString(R.string.promotions_empty_desc));
        } else {
            offersAdapter.updateList(combined);
            showSectionContent(promotionsLoading, rvLandingPromotions, promotionsStateView);
        }
        onDone.accept(bothLoaded);
    }

    /**
     * A section that already has something on screen (content, or its friendly "nothing here" message) keeps it
     * untouched while it refreshes. A section with nothing yet shows its own spinner.
     */
    private void beginSectionLoad(boolean hasContent, ProgressBar loading, View content, View stateView) {
        if (hasContent) return;
        loading.setVisibility(View.VISIBLE);
        content.setVisibility(View.GONE);
        stateView.setVisibility(View.GONE);
    }

    private boolean isUiAlive() {
        return !isFinishing() && !isDestroyed();
    }

    /** Loading finished, real content to show - hide the spinner and the empty/error message, reveal the list. */
    private void showSectionContent(ProgressBar loading, RecyclerView recyclerView, View stateView) {
        loading.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        stateView.setVisibility(View.GONE);
    }

    /** Loading finished, zero items - hide the spinner and list, show a friendly "nothing here yet" message. */
    private void showSectionEmpty(ProgressBar loading, RecyclerView recyclerView, View stateView, String title, String desc) {
        loading.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        applyStateMessage(stateView, title, desc, false);
    }

    /** The request failed - hide the spinner and list, show a distinct network-error message. */
    private void showSectionError(ProgressBar loading, RecyclerView recyclerView, View stateView, String title, String desc) {
        loading.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        applyStateMessage(stateView, title, desc, true);
    }

    private void applyStateMessage(View stateView, String title, String desc, boolean isError) {
        TextView tvTitle = stateView.findViewById(R.id.stateTitle);
        TextView tvDesc = stateView.findViewById(R.id.stateDesc);
        ImageView icon = stateView.findViewById(R.id.stateIcon);
        tvTitle.setText(title);
        tvDesc.setText(desc);
        icon.setImageResource(isError ? R.drawable.ic_wifi_off : R.drawable.ic_info);
        stateView.setVisibility(View.VISIBLE);
    }

    private void showAnnouncementDetail(Announcement announcement) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_announcement_details, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.VelocityDialogTheme)
                .setView(dialogView)
                .create();

        TextView tvTitle = dialogView.findViewById(R.id.tvAnnouncementDetailTitle);
        TextView tvDate = dialogView.findViewById(R.id.tvAnnouncementDetailDate);
        TextView tvMessage = dialogView.findViewById(R.id.tvAnnouncementDetailMessage);
        View imageContainer = dialogView.findViewById(R.id.announcementDetailImageContainer);
        ImageView ivImage = dialogView.findViewById(R.id.ivAnnouncementDetailImage);
        View btnClose = dialogView.findViewById(R.id.btnAnnouncementClose);
        View btnDismiss = dialogView.findViewById(R.id.btnAnnouncementDismiss);

        tvTitle.setText(announcement.getTitle());
        tvDate.setText(getString(R.string.announcement_published_format, announcement.getPublishedAt()));
        tvMessage.setText(announcement.getContent());

        String imageUrl = announcement.getFirstImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            imageContainer.setVisibility(View.VISIBLE);
            com.bumptech.glide.Glide.with(this).load(imageUrl).centerCrop().into(ivImage);
        } else {
            imageContainer.setVisibility(View.GONE);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnDismiss.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void setupActions() {
        // "Next" is the no-room auth path: it clears any previously selected
        // room so the guest lands on the dashboard after signing in/up.
        View.OnClickListener authRequiredClick = v -> {
            PendingRoomSelection.clear(this);
            navigateToWelcome();
        };
        findViewById(R.id.btnProceed).setOnClickListener(authRequiredClick);
        // "Explore Now" smoothly scrolls to the Available Rooms section
        // instead of leaving the app - the real hotel website is still one
        // tap away via the Hotel Info section's own website card
        // (cardContactWebsite, unchanged, see openHotelWebsite() below).
        findViewById(R.id.btnExploreNow).setOnClickListener(v -> scrollToAvailableRooms());

        btnToggleRooms.setOnClickListener(v -> {
            roomsExpanded = !roomsExpanded;
            updateRoomsDisplay();
        });

        findViewById(R.id.mapPreviewCard).setOnClickListener(v -> openHotelLocationInMaps());
        findViewById(R.id.btnViewLocation).setOnClickListener(v -> openHotelLocationInMaps());
        findViewById(R.id.cardContactPhone).setOnClickListener(v -> callHotel());
        findViewById(R.id.cardContactEmail).setOnClickListener(v -> emailHotel());
        findViewById(R.id.cardContactWebsite).setOnClickListener(v -> openHotelWebsite());
    }

    private void scrollToAvailableRooms() {
        View scrollTarget = findViewById(R.id.availableRoomsSection);
        View scrollView = findViewById(R.id.landingScroll);
        if (scrollTarget == null || !(scrollView instanceof androidx.core.widget.NestedScrollView)) return;
        ((androidx.core.widget.NestedScrollView) scrollView).smoothScrollTo(0, scrollTarget.getTop());
    }

    private void openHotelLocationInMaps() {
        String label = Uri.encode(getString(R.string.landing_hotel_name));
        Uri geoUri = Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)",
                HOTEL_LATITUDE, HOTEL_LONGITUDE, HOTEL_LATITUDE, HOTEL_LONGITUDE, label));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, geoUri);
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
            return;
        }
        Uri webUri = Uri.parse(String.format(Locale.US,
                "https://www.google.com/maps/search/?api=1&query=%f,%f", HOTEL_LATITUDE, HOTEL_LONGITUDE));
        startActivity(new Intent(Intent.ACTION_VIEW, webUri));
    }

    private void callHotel() {
        Uri phoneUri = Uri.parse("tel:" + getString(R.string.landing_hotel_phone));
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, phoneUri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_phone_app_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void emailHotel() {
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:" + getString(R.string.landing_hotel_email)));
        try {
            startActivity(emailIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_email_app_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void openHotelWebsite() {
        String website = getString(R.string.landing_hotel_website);
        String url = website.startsWith("http") ? website : "https://" + website;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_browser_app_found, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Room Type filter options must never be a fixed list - the System
     * Administrator can create/rename/retire room types at any time through
     * the web Rooms Module, and the guest app has to reflect that without an
     * XML/code change. Rebuilt every time the room list is (re)loaded, from
     * the distinct types actually present in that list.
     */
    private void setupRoomTypeFilterDynamic(List<Room> sourceRooms) {
        java.util.TreeSet<String> distinctTypes = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Room r : sourceRooms) {
            if (r.getType() != null && !r.getType().trim().isEmpty()) {
                distinctTypes.add(r.getType().trim());
            }
        }
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.all_categories));
        options.addAll(distinctTypes);

        String previousSelection = dropdownRoomTypeFilter.getText() != null
                ? dropdownRoomTypeFilter.getText().toString() : null;
        // The silent refresh runs every 30 seconds: only touch the dropdown when its choices really changed, so a
        // guest who has it open (or is part-way through picking) isn't interrupted by an identical new adapter.
        if (!options.equals(roomTypeFilterOptions)) {
            roomTypeFilterOptions = options;
            dropdownRoomTypeFilter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, options));
            dropdownRoomTypeFilter.setOnItemClickListener((parent, view, position, id) -> {
                String selected = options.get(position);
                currentTypeFilter = selected.equals(getString(R.string.all_categories)) ? "All" : selected;
                roomsExpanded = false;
                updateRoomsDisplay();
            });
        }

        if (previousSelection != null && options.contains(previousSelection)) {
            dropdownRoomTypeFilter.setText(previousSelection, false);
        } else {
            dropdownRoomTypeFilter.setText(getString(R.string.all_categories), false);
            currentTypeFilter = "All";
        }
    }

    private void navigateToWelcome() {
        startActivity(new Intent(this, WelcomeActivity.class));
        overridePendingTransition(R.anim.fade_in_slide_up, R.anim.fade_out_scale);
    }

    private void animateLanding() {
        View root = findViewById(R.id.landingRoot);
        root.setAlpha(0f);
        root.setTranslationY(18f);
        root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(520L)
                .start();
    }

    private void refreshRooms(Consumer<Boolean> onDone) {
        if (!roomsLoaded) {
            // Nothing on screen yet (first load, or retry after an error panel): show this section's spinner.
            // Once rooms HAVE loaded, a refresh leaves the list exactly where it is until fresh data replaces it.
            landingRoomsLoading.setVisibility(View.VISIBLE);
            landingRoomsEmpty.setVisibility(View.GONE);
            layoutRoomError.setVisibility(View.GONE);
            rvLandingRooms.setVisibility(View.GONE);
        }
        RoomRepository.getInstance(this).refreshRoomsFresh(new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> result) {
                if (!isUiAlive()) return;
                roomsLoaded = true;
                landingRoomsLoading.setVisibility(View.GONE);
                layoutRoomError.setVisibility(View.GONE);
                allAvailableRooms = filterAvailableRooms(result);
                setupRoomTypeFilterDynamic(allAvailableRooms);
                // A room that sold out (or has fewer left) since the guest picked it must not stay in their cart.
                if (CartSelectionReconciler.reconcile(selectedQuantities, allAvailableRooms)) {
                    updateSelectionSummaryBar();
                }
                updateRoomsDisplay();
                onDone.accept(true);
            }

            @Override
            public void onError(String message) {
                if (!isUiAlive()) return;
                if (!roomsLoaded) {
                    landingRoomsLoading.setVisibility(View.GONE);
                    rvLandingRooms.setVisibility(View.GONE);
                    landingRoomsEmpty.setVisibility(View.GONE);
                    if (tvLandingRoomsCount != null) tvLandingRoomsCount.setVisibility(View.GONE);
                    btnToggleRooms.setVisibility(View.GONE);
                    applyStateMessage(roomErrorState, getString(R.string.room_error_title),
                            getString(R.string.room_error_message), true);
                    layoutRoomError.setVisibility(View.VISIBLE);
                }
                onDone.accept(false);
            }
        });
    }

    private List<Room> filterByType(List<Room> source, String type) {
        if (type == null || type.equalsIgnoreCase("All")) return new ArrayList<>(source);
        List<Room> result = new ArrayList<>();
        for (Room room : source) {
            if (room.getType() != null && room.getType().toLowerCase(Locale.US).contains(type.toLowerCase(Locale.US))) {
                result.add(room);
            }
        }
        return result;
    }

    private void updateRoomsDisplay() {
        List<Room> typeFiltered = filterByType(allAvailableRooms, currentTypeFilter);
        boolean hasRooms = !typeFiltered.isEmpty();

        rvLandingRooms.setVisibility(hasRooms ? View.VISIBLE : View.GONE);
        if (hasRooms) {
            landingRoomsEmpty.setVisibility(View.GONE);
        } else {
            boolean noRoomsAtAll = allAvailableRooms.isEmpty();
            applyStateMessage(landingRoomsEmpty, getString(R.string.landing_empty_title),
                    getString(noRoomsAtAll ? R.string.landing_no_available_rooms : R.string.landing_no_rooms_for_filter), false);
        }
        if (tvLandingRoomsCount != null) {
            tvLandingRoomsCount.setVisibility(hasRooms ? View.VISIBLE : View.GONE);
            tvLandingRoomsCount.setText(getString(R.string.landing_rooms_count_badge, typeFiltered.size()));
        }

        boolean canCollapse = typeFiltered.size() > COLLAPSED_ROOM_COUNT;
        rooms = (canCollapse && !roomsExpanded)
                ? new ArrayList<>(typeFiltered.subList(0, COLLAPSED_ROOM_COUNT))
                : typeFiltered;
        roomAdapter.updateList(rooms);

        if (canCollapse) {
            btnToggleRooms.setVisibility(View.VISIBLE);
            btnToggleRooms.setText(roomsExpanded ? R.string.show_less_rooms : R.string.view_all_rooms);
            btnToggleRooms.setIconResource(roomsExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
        } else {
            btnToggleRooms.setVisibility(View.GONE);
        }
    }

    private List<Room> filterAvailableRooms(List<Room> sourceRooms) {
        List<Room> availableRooms = new ArrayList<>();
        if (sourceRooms == null) return availableRooms;

        for (Room room : sourceRooms) {
            if (room != null && room.isAvailable()) {
                availableRooms.add(room);
            }
        }
        return availableRooms;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (hotelMapView != null) hotelMapView.onStart();
        // Landing is never usable offline - also when it is reopened from the back stack after the connection dropped.
        offlineGate.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hotelMapView != null) hotelMapView.onResume();
        isNavigatingToBookingFlow = false;
        // onCreate() already started the first load - skip that first resume. Every later return to the page (after
        // a booking elsewhere, a sign-in, another app) catches up silently, so a now-unavailable room drops off the
        // list and a new announcement appears without the guest doing anything - and without blanking the page.
        if (firstResume) {
            firstResume = false;
        } else {
            refreshCoordinator.poll();
        }
        // Then keep it current for as long as the page stays on screen.
        visiblePoller.start();
    }

    @Override
    protected void onPause() {
        // Nothing polls while the page is covered or the app is in the background.
        visiblePoller.stop();
        if (hotelMapView != null) hotelMapView.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        offlineGate.stop();
        if (hotelMapView != null) hotelMapView.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        // Belt and braces: onPause() already stopped the poller; cancel() silences any request still in flight.
        visiblePoller.stop();
        refreshCoordinator.cancel();
        offlineGate.destroy();
        dismissRefreshFailure();
        if (hotelMapView != null) hotelMapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (hotelMapView != null) hotelMapView.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle mapViewBundle = outState.getBundle(MAP_VIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAP_VIEW_BUNDLE_KEY, mapViewBundle);
        }
        if (hotelMapView != null) hotelMapView.onSaveInstanceState(mapViewBundle);
    }

    /**
     * Book Now / Reserve Now (whether tapped on the room card or from inside
     * the in-page details dialog) require authentication and hand off into
     * RoomBrowsingActivity to complete there. If the guest is already logged
     * in, skip straight there; otherwise remember the room + chosen action and
     * send them to sign in first, and PendingRoomSelection.createPostAuthIntent()
     * picks the same destination back up after a successful login/registration.
     * Carries whatever quantity the guest already staged on this room's own
     * card stepper (defaulting to 1, never 0) - tapping Book Now shouldn't
     * silently discard a quantity the guest already set.
     */
    private void handleRoomAction(Room room, String action) {
        if (isNavigatingToBookingFlow) return;
        isNavigatingToBookingFlow = true;
        int qty = Math.max(1, selectedQuantities.getOrDefault(room.getId(), 0));
        if (SessionManager.isLoggedIn(this)) {
            Intent intent = new Intent(this, RoomBrowsingActivity.class);
            intent.putExtra("ROOM_ID", room.getId());
            intent.putExtra("ROOM_QTY", qty);
            intent.putExtra("OPEN_ROOM_DETAILS", true);
            intent.putExtra("ROOM_ACTION", action);
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in_slide_up, R.anim.fade_out_scale);
        } else {
            List<Room> toSave = new ArrayList<>();
            for (int i = 0; i < qty; i++) toSave.add(room);
            PendingRoomSelection.saveMultiple(this, toSave, action);
            navigateToWelcome();
        }
    }

    /**
     * View Details (and a plain room-card tap) show the details dialog right
     * here on the landing page - no login required just to look. Only Book
     * Now / Reserve Now, tapped from inside that dialog, require signing in.
     */
    private void showRoomDetailsDialog(Room room, String action) {
        RoomDetailsDialog.show(this, room, action, null, new RoomDetailsDialog.ActionListener() {
            @Override
            public void onBook(Room room) {
                handleRoomAction(room, PendingRoomSelection.ACTION_BOOK);
            }

            @Override
            public void onReserve(Room room) {
                handleRoomAction(room, PendingRoomSelection.ACTION_RESERVE);
            }
        });
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
        handleRoomAction(room, PendingRoomSelection.ACTION_BOOK);
    }

    @Override
    public void onReserveNowClick(Room room) {
        handleRoomAction(room, PendingRoomSelection.ACTION_RESERVE);
    }

    @Override
    public void onRoomSelectionChanged() {
        updateSelectionSummaryBar();
    }

    /**
     * Expands selectedQuantities into one Room entry per selected unit
     * (Deluxe x2 -> [deluxe, deluxe]) - the exact shape PendingWizardRooms/
     * PendingRoomSelection/BookingWizardState already expect, per their own
     * "duplicate list entries ARE the quantity" convention.
     */
    private List<Room> resolveSelectedRooms() {
        List<Room> resolved = new ArrayList<>();
        for (Room r : allAvailableRooms) {
            Integer qty = selectedQuantities.get(r.getId());
            if (qty == null) continue;
            for (int i = 0; i < qty; i++) resolved.add(r);
        }
        return resolved;
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
        List<Room> selected = resolveSelectedRooms();
        int combinedCapacity = 0;
        double totalPerNight = 0;
        for (Room r : selected) {
            combinedCapacity += r.getCapacity();
            totalPerNight += r.getPricePerNight();
        }
        cardSelectionSummaryBar.setVisibility(View.VISIBLE);
        tvSelectionSummaryCount.setText(getResources().getQuantityString(
                R.plurals.cart_summary_capacity_format, selected.size(), selected.size(), combinedCapacity));
        tvSelectionSummaryTotal.setText(getString(R.string.cart_summary_total_format, totalPerNight));
    }

    /**
     * Multi-room cart Reserve/Book: same auth gate as the single-room path
     * (handleRoomAction), but carries the whole selection into
     * RoomBrowsingActivity so the guest can review/add more before entering
     * the wizard, per spec - rather than jumping straight into the wizard as
     * the single-room quick actions do.
     */
    private void handleCartAction(String action) {
        if (isNavigatingToBookingFlow) return;
        List<Room> selected = resolveSelectedRooms();
        if (selected.isEmpty()) return;
        isNavigatingToBookingFlow = true;

        if (SessionManager.isLoggedIn(this)) {
            List<String> ids = new ArrayList<>();
            for (Room r : selected) ids.add(r.getId());
            Intent intent = new Intent(this, RoomBrowsingActivity.class);
            intent.putExtra("ROOM_ID", ids.get(0));
            intent.putExtra("ROOM_IDS", String.join(",", ids));
            intent.putExtra("OPEN_ROOM_DETAILS", ids.size() == 1);
            intent.putExtra("ROOM_ACTION", action);
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in_slide_up, R.anim.fade_out_scale);
        } else {
            PendingRoomSelection.saveMultiple(this, selected, action);
            navigateToWelcome();
        }
    }
}
