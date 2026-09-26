package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.velocitysuites.network.ApiClient;
import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.ApiService;
import com.example.velocitysuites.network.dto.AdditionalGuestDto;
import com.example.velocitysuites.network.dto.AmenityRequestDto;
import com.example.velocitysuites.network.dto.AmenityRequestSubmitRequest;
import com.example.velocitysuites.network.dto.AmenitySelectionDto;
import com.example.velocitysuites.network.dto.ApiMessage;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.NotificationDto;
import com.example.velocitysuites.network.dto.PaginatedResponse;
import com.example.velocitysuites.network.dto.PaymentRequest;
import com.example.velocitysuites.network.dto.PaymentSubmitResponse;
import com.example.velocitysuites.network.dto.PaymentsResponse;
import com.example.velocitysuites.network.dto.ReceiptDetailResponse;
import com.example.velocitysuites.network.dto.RequestableAmenityDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.example.velocitysuites.network.dto.ReservationRequest;
import com.example.velocitysuites.network.dto.ReservationUpdateRequest;
import com.example.velocitysuites.network.dto.RoomSelectionDto;
import com.example.velocitysuites.network.dto.RoomTypeDto;
import com.example.velocitysuites.network.dto.RoomsResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.net.Uri;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Single source of truth for room inventory, bookings and notifications,
 * backed by the live velocitysuites.com API. Screens read cached
 * snapshots via the synchronous getters (unchanged from the old mock
 * repository's interface) and trigger a refresh via the refresh*
 * methods, which update the cache and invoke a callback on completion.
 */
public final class RoomRepository {

    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    /**
     * Same-process pub/sub for "the shared bookings cache changed" - lets
     * BookingAndReservationActivity and DashboardActivity stay in sync in
     * real time (delete/cancel/pay on one screen instantly refreshes the
     * other) without either screen needing to be the one that mutated the
     * cache. No local DB/server push exists or is warranted here - both
     * screens run in the same app process and already share this one
     * in-memory cache.
     */
    public interface BookingsChangedListener {
        void onBookingsChanged();
    }

    private static RoomRepository instance;

    private final ApiService api;
    private final Context appContext;
    /** Off-loads the cache-file copy in submitGcashPayment()/uploadIdCard() so a multi-MB photo never blocks the UI thread. */
    private final java.util.concurrent.ExecutorService fileIoExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    /**
     * Lazily created (see mainHandler()) rather than eagerly at field-init
     * time - Looper.getMainLooper() is only ever reachable on a real Android
     * runtime, and eagerly touching it here made the constructor itself
     * (and therefore getInstance()) blow up under a plain JVM unit test
     * (RoomRepositoryTest) even for tests that never post to it.
     */
    private android.os.Handler mainHandler;

    /**
     * Bumped once by clearAccountSpecificCache() (logout). refreshBookings()/
     * refreshNotifications() snapshot this at request-start and compare it again
     * when their response lands - if it changed in between, a different account
     * has since logged in and this response is a stale User-A callback racing a
     * User-B login (the request itself was never cancelled - Retrofit doesn't
     * cancel in-flight calls just because the app logged out). Without this,
     * clearing the cache at logout alone isn't enough: a slow request started
     * right before logout could still land afterward and silently repopulate
     * `bookings`/`notifications` with the previous account's data over top of
     * the new account's already-fresh cache.
     */
    private volatile int accountGeneration = 0;

    private List<Room> rooms = new ArrayList<>();
    private List<Booking> bookings = new ArrayList<>();
    /** View-only "frozen" reservations that have converted into a Booking - see ApiMapper#toHistoricalReservation(). Kept out of `bookings` entirely so Transaction History/Dashboard/Notifications/Calendar never double-count them; only BookingAndReservationActivity's Reservation-tab Completed list reads this. */
    private List<Booking> completedHistoricalReservations = new ArrayList<>();
    private List<Notification> notifications = new ArrayList<>();
    private final List<BookingsChangedListener> bookingsChangedListeners = new ArrayList<>();
    /** Booking ids already run through correctPendingReservationTotal() - reset whenever refreshBookings() replaces `bookings` with fresh (uncorrected) server objects. */
    private final java.util.Set<String> correctedTotalIds = new java.util.HashSet<>();

    private android.os.Handler mainHandler() {
        if (mainHandler == null) {
            mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        return mainHandler;
    }

    /**
     * Temporary, debug-build-only verification aid for the still-open question of
     * whether the live API actually returns populated `rooms[]` for a given
     * transaction (see MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md and ApiMapper#
     * toBookingRooms()'s own doc on the current, DTO-shape-specific answer).
     * Logs only structural metadata - never guest/payment/identity data - so a
     * real device/emulator run can confirm at a glance, per booking, whether
     * getRooms() ended up populated or whether display fell through to the
     * legacy single roomType field. Compiled out of release builds entirely via
     * the BuildConfig.DEBUG guard, same convention ApiClient's own logging
     * interceptor already uses.
     */
    private void logRoomsShapeIfDebug(List<Booking> refreshed) {
        if (!com.example.velocitysuites.BuildConfig.DEBUG) return;
        for (Booking b : refreshed) {
            android.util.Log.d("RoomsShape", "booking=" + b.getId()
                    + " hasBooking=" + b.isHasBooking()
                    + " rooms.size=" + b.getRooms().size()
                    + " distinctTypesFromRooms=" + (b.getRooms().isEmpty() ? "-" : b.getAllRoomTypeNames())
                    + " legacyRoomType=" + b.getRoomType()
                    + " legacyRoomsRequested=" + b.getRoomsRequested());
        }
    }

    public void addBookingsChangedListener(BookingsChangedListener listener) {
        bookingsChangedListeners.add(listener);
    }

    public void removeBookingsChangedListener(BookingsChangedListener listener) {
        bookingsChangedListeners.remove(listener);
    }

    private void notifyBookingsChanged() {
        // Iterate a copy - a listener may unregister itself (Activity
        // finishing) as part of handling this same notification.
        for (BookingsChangedListener listener : new ArrayList<>(bookingsChangedListeners)) {
            listener.onBookingsChanged();
        }
    }

    private RoomRepository(Context appContext) {
        this.appContext = appContext;
        this.api = ApiClient.getService(appContext);
    }

    public static synchronized RoomRepository getInstance(Context context) {
        if (instance == null) {
            Context appContext = context != null ? context.getApplicationContext() : null;
            instance = new RoomRepository(appContext);
        }
        return instance;
    }

    // ---- Cached synchronous snapshots (existing interface) ----

    public List<Room> getAllRooms() {
        return new ArrayList<>(rooms);
    }

    /**
     * The main/profile image for a room TYPE, resolved from the already-cached
     * room-type catalog (refreshRooms(), the same list RoomBrowsingActivity/
     * the booking wizard already populate) rather than any per-transaction
     * field - BookingRoomDto (a transaction's itemized room-type line item)
     * carries no image of its own, only room_type_id/name/quantity/price.
     * Despite its name, Room here is a room TYPE (see ApiMapper#toRoom()'s own
     * doc - "guests browse/book by type, never an individual room/unit"; its
     * roomTypeId is RoomTypeDto's own id, and imageUrl is that type's
     * image_url directly), so this is an exact 1:1 lookup, not a search
     * across multiple physical units of the same type. Returns null (never
     * throws) when the catalog hasn't loaded yet or no matching type is
     * cached - callers must fall back to a placeholder, same as every other
     * room-image call site in this app already does.
     */
    @Nullable
    public String findRoomTypeImageUrl(@Nullable String roomTypeId) {
        if (roomTypeId == null || roomTypeId.isEmpty()) return null;
        for (Room room : rooms) {
            if (roomTypeId.equals(String.valueOf(room.getRoomTypeId()))) {
                return room.getImageUrl();
            }
        }
        return null;
    }

    public List<Booking> getBookings() {
        return new ArrayList<>(bookings);
    }

    /** See completedHistoricalReservations' own field doc - only ever meaningful on the Reservation tab's Completed category. */
    public List<Booking> getCompletedHistoricalReservations() {
        return new ArrayList<>(completedHistoricalReservations);
    }

    public List<Notification> getNotifications() {
        return new ArrayList<>(notifications);
    }

    public void clearBookings() {
        bookings.clear();
    }

    /**
     * Wipes every account-specific in-memory cache this singleton holds - called once
     * from BaseNavigationActivity#logout(), before handing off to LoginActivity. Without
     * this, a transient network error on the very next account's first
     * refreshBookings()/refreshNotifications() call (e.g. a brief connectivity blip right
     * after switching accounts) would fall back to onError()'s populateDashboard(repository.getBookings(), ...)
     * - which, had this never run, would still be the PREVIOUS account's cached list,
     * momentarily showing one guest's bookings/notifications/payment history to another.
     * `rooms` (room-type inventory) is deliberately NOT cleared here - it's shared,
     * non-account-specific catalog data, identical for every guest, so keeping it cached
     * across a logout/login is both safe and desirable (avoids an unnecessary refetch).
     */
    public void clearAccountSpecificCache() {
        bookings.clear();
        completedHistoricalReservations.clear();
        notifications.clear();
        correctedTotalIds.clear();
        // See accountGeneration's own doc - lets refreshBookings()/refreshNotifications()
        // detect and discard a still-in-flight previous account's response instead of
        // letting it silently repopulate these caches after this point.
        accountGeneration++;
    }

    /**
     * Test-only seams (package-private, exercised by RoomRepositoryTest -
     * same package). Both `rooms`/`bookings` are otherwise only ever
     * populated from the live API (refreshRooms()/refreshBookings()), which
     * a JVM unit test can't reach - these let availability-matching logic
     * (isAvailableForDates()/findOverlappingBooking()) be tested against
     * known in-memory data instead. Never called from production code.
     */
    void setRoomsForTesting(List<Room> rooms) {
        this.rooms = rooms != null ? rooms : new ArrayList<>();
    }

    void addBookingForTesting(Booking booking) {
        this.bookings.add(booking);
    }

    /** Test-only accessor for accountGeneration - see its own field doc. */
    int getAccountGenerationForTesting() {
        return accountGeneration;
    }

    // ---- Refresh from API ----

    public void refreshRooms(RepositoryCallback<List<Room>> callback) {
        refreshRooms(null, null, callback);
    }

    /**
     * Availability is date-range-aware on the backend (RoomAvailabilityService
     * accounts for existing bookings, not just a room's static status) - the
     * guest's actually-selected dates must be forwarded so available_count/
     * is_fully_booked reflect their real search, not the API's default
     * near-term window.
     */
    public void refreshRooms(Calendar checkIn, Calendar checkOut, RepositoryCallback<List<Room>> callback) {
        java.util.Map<String, String> filters = new java.util.HashMap<>();
        if (checkIn != null) filters.put("check_in", ApiMapper.toApiDate(checkIn));
        if (checkOut != null) filters.put("check_out", ApiMapper.toApiDate(checkOut));

        api.getRooms(filters).enqueue(new Callback<RoomsResponse>() {
            @Override
            public void onResponse(Call<RoomsResponse> call, Response<RoomsResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().room_types != null) {
                    List<Room> mapped = new ArrayList<>();
                    for (RoomTypeDto dto : response.body().room_types.data) {
                        mapped.add(ApiMapper.toRoom(dto));
                    }
                    rooms = mapped;
                    if (callback != null) callback.onSuccess(getAllRooms());
                } else {
                    if (callback != null) callback.onError("Failed to load rooms.");
                }
            }

            @Override
            public void onFailure(Call<RoomsResponse> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * Active-only, admin-managed amenity catalog - see Api\CatalogController::amenities().
     * pricingType: "paid" (booking screen's chargeable add-on picker - Free/Included
     * amenities must never appear as if they were chargeable), "free", or null (landing
     * page - shows everything).
     */
    public void refreshAmenities(String pricingType, RepositoryCallback<List<AddOnAmenity>> callback) {
        api.getAmenities(pricingType).enqueue(new Callback<List<com.example.velocitysuites.network.dto.AmenityDto>>() {
            @Override
            public void onResponse(Call<List<com.example.velocitysuites.network.dto.AmenityDto>> call, Response<List<com.example.velocitysuites.network.dto.AmenityDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<AddOnAmenity> mapped = new ArrayList<>();
                    for (com.example.velocitysuites.network.dto.AmenityDto dto : response.body()) {
                        mapped.add(AddOnAmenity.fromDto(dto));
                    }
                    if (callback != null) callback.onSuccess(mapped);
                } else {
                    if (callback != null) callback.onError("Failed to load amenities.");
                }
            }

            @Override
            public void onFailure(Call<List<com.example.velocitysuites.network.dto.AmenityDto>> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /** Active, currently-in-date-range promotions - see Api\CatalogController::promotions(). */
    public void refreshPromotions(RepositoryCallback<List<Promotion>> callback) {
        api.getPromotions().enqueue(new Callback<List<com.example.velocitysuites.network.dto.PromotionDto>>() {
            @Override
            public void onResponse(Call<List<com.example.velocitysuites.network.dto.PromotionDto>> call, Response<List<com.example.velocitysuites.network.dto.PromotionDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Promotion> mapped = new ArrayList<>();
                    for (com.example.velocitysuites.network.dto.PromotionDto dto : response.body()) {
                        mapped.add(Promotion.fromDto(dto));
                    }
                    if (callback != null) callback.onSuccess(mapped);
                } else {
                    if (callback != null) callback.onError("Failed to load promotions.");
                }
            }

            @Override
            public void onFailure(Call<List<com.example.velocitysuites.network.dto.PromotionDto>> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /** Active standing discounts - see Api\CatalogController::discounts(). */
    public void refreshDiscounts(RepositoryCallback<List<Discount>> callback) {
        api.getDiscounts().enqueue(new Callback<List<com.example.velocitysuites.network.dto.DiscountDto>>() {
            @Override
            public void onResponse(Call<List<com.example.velocitysuites.network.dto.DiscountDto>> call, Response<List<com.example.velocitysuites.network.dto.DiscountDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Discount> mapped = new ArrayList<>();
                    for (com.example.velocitysuites.network.dto.DiscountDto dto : response.body()) {
                        mapped.add(Discount.fromDto(dto));
                    }
                    if (callback != null) callback.onSuccess(mapped);
                } else {
                    if (callback != null) callback.onError("Failed to load discounts.");
                }
            }

            @Override
            public void onFailure(Call<List<com.example.velocitysuites.network.dto.DiscountDto>> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /** Published, guest-audience announcements - see Api\CatalogController::announcements(). */
    public void refreshAnnouncements(RepositoryCallback<List<Announcement>> callback) {
        api.getAnnouncements().enqueue(new Callback<List<com.example.velocitysuites.network.dto.AnnouncementDto>>() {
            @Override
            public void onResponse(Call<List<com.example.velocitysuites.network.dto.AnnouncementDto>> call, Response<List<com.example.velocitysuites.network.dto.AnnouncementDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Announcement> mapped = new ArrayList<>();
                    for (com.example.velocitysuites.network.dto.AnnouncementDto dto : response.body()) {
                        mapped.add(Announcement.fromDto(dto));
                    }
                    if (callback != null) callback.onSuccess(mapped);
                } else {
                    if (callback != null) callback.onError("Failed to load announcements.");
                }
            }

            @Override
            public void onFailure(Call<List<com.example.velocitysuites.network.dto.AnnouncementDto>> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * Fetches both transaction families that make up "My Bookings" -
     * reservation-derived bookings (guest/reservations, unchanged) and
     * direct "New Booking" transactions (guest/bookings, never a
     * Reservation - see Api\BookingController) - and merges them into one
     * cache. The two are genuinely independent record types server-side, so
     * there's no single endpoint that already returns both together.
     */
    public void refreshBookings(RepositoryCallback<List<Booking>> callback) {
        List<Booking> reservationDerived = new ArrayList<>();
        List<Booking> direct = new ArrayList<>();
        List<Booking> historicalReservations = new ArrayList<>();
        boolean[] reservationsFailed = {false};
        boolean[] directFailed = {false};
        int[] remaining = {2};
        // Captured before either network call fires - see accountGeneration's own doc.
        final int requestGeneration = accountGeneration;

        Runnable finish = () -> {
            if (reservationsFailed[0] && directFailed[0]) {
                if (callback != null) callback.onError("Failed to load bookings.");
                return;
            }
            if (requestGeneration != accountGeneration) {
                // A logout (and possibly a different account's own login) happened
                // while this request was in flight - this response belongs to
                // whichever account started it, not to whoever is signed in now.
                // Silently dropped rather than onError()'d: from the CURRENT
                // account's perspective nothing actually failed, there's simply
                // nothing to report from a request they never made.
                return;
            }
            List<Booking> merged = new ArrayList<>(reservationDerived);
            merged.addAll(direct);
            bookings = merged;
            completedHistoricalReservations = historicalReservations;
            // Fresh Booking objects from the server are room-cost-only again
            // until corrected - see correctPendingReservationTotal().
            correctedTotalIds.clear();
            logRoomsShapeIfDebug(merged);
            notifyBookingsChanged();
            if (callback != null) callback.onSuccess(getBookings());
        };

        api.getReservations(200).enqueue(new Callback<PaginatedResponse<ReservationDto>>() {
            @Override
            public void onResponse(Call<PaginatedResponse<ReservationDto>> call, Response<PaginatedResponse<ReservationDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (ReservationDto dto : response.body().data) {
                        reservationDerived.add(ApiMapper.toBooking(dto));
                        // Once converted, also keep a frozen, view-only copy under
                        // the Reservation tab's Completed list - see
                        // ApiMapper#toHistoricalReservation()'s own docblock for why
                        // this doesn't just live in the main `bookings` cache.
                        if (dto.booking != null) {
                            historicalReservations.add(ApiMapper.toHistoricalReservation(dto));
                        }
                    }
                } else {
                    reservationsFailed[0] = true;
                }
                if (--remaining[0] <= 0) finish.run();
            }

            @Override
            public void onFailure(Call<PaginatedResponse<ReservationDto>> call, Throwable t) {
                reservationsFailed[0] = true;
                if (--remaining[0] <= 0) finish.run();
            }
        });

        api.getDirectBookings(200).enqueue(new Callback<PaginatedResponse<DirectBookingResponseDto>>() {
            @Override
            public void onResponse(Call<PaginatedResponse<DirectBookingResponseDto>> call, Response<PaginatedResponse<DirectBookingResponseDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (DirectBookingResponseDto dto : response.body().data) {
                        direct.add(ApiMapper.toBooking(dto));
                    }
                } else {
                    directFailed[0] = true;
                }
                if (--remaining[0] <= 0) finish.run();
            }

            @Override
            public void onFailure(Call<PaginatedResponse<DirectBookingResponseDto>> call, Throwable t) {
                directFailed[0] = true;
                if (--remaining[0] <= 0) finish.run();
            }
        });
    }

    public void refreshNotifications(RepositoryCallback<List<Notification>> callback) {
        // See accountGeneration's own doc - captured before the network call fires.
        final int requestGeneration = accountGeneration;
        api.getNotifications(200).enqueue(new Callback<PaginatedResponse<NotificationDto>>() {
            @Override
            public void onResponse(Call<PaginatedResponse<NotificationDto>> call, Response<PaginatedResponse<NotificationDto>> response) {
                if (requestGeneration != accountGeneration) {
                    // Stale - a logout happened while this request was in flight; see
                    // refreshBookings()'s identical guard for the full reasoning.
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    List<Notification> mapped = new ArrayList<>();
                    for (NotificationDto dto : response.body().data) {
                        mapped.add(ApiMapper.toNotification(dto));
                    }
                    notifications = mapped;
                    // Single hook point for the whole app: every screen that refreshes
                    // notifications (dashboard, the Notification Module, the background
                    // poll worker) posts real system alerts for genuinely new/unread rows
                    // through here - see NotificationHelper for the dedup/grouping rules.
                    NotificationHelper.maybeAlertNewNotifications(appContext, mapped);
                    if (callback != null) callback.onSuccess(getNotifications());
                } else {
                    if (callback != null) callback.onError("Failed to load notifications.");
                }
            }

            @Override
            public void onFailure(Call<PaginatedResponse<NotificationDto>> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    // ---- Mutations ----

    public void createReservation(Room room, int roomsRequested, Calendar checkIn, Calendar checkOut, int adults, int children,
                                   String guestFirstName, String guestMiddleName, String guestLastName,
                                   String idCardType, List<BookingAndReservationActivity.AdditionalGuest> additionalGuests,
                                   List<AddOnAmenity> amenities, String paymentMethod, @Nullable Integer selectedPaymentPercentage,
                                   RepositoryCallback<Booking> callback) {
        ReservationRequest request = new ReservationRequest(
                Long.parseLong(room.getId()),
                roomsRequested,
                ApiMapper.toApiDate(checkIn),
                ApiMapper.toApiDate(checkOut),
                adults,
                children,
                guestFirstName,
                guestLastName
        );
        request.payment_method = paymentMethod;
        request.selected_payment_percentage = selectedPaymentPercentage;
        if (guestMiddleName != null && !guestMiddleName.trim().isEmpty()) {
            request.guest_middle_name = guestMiddleName.trim();
        }
        if (idCardType != null && !idCardType.equals("None")) {
            request.id_card_type = idCardType;
        }
        if (additionalGuests != null && !additionalGuests.isEmpty()) {
            List<AdditionalGuestDto> guestDtos = new ArrayList<>();
            for (BookingAndReservationActivity.AdditionalGuest g : additionalGuests) {
                guestDtos.add(new AdditionalGuestDto(g.name, g.age, g.gender, g.relationship));
            }
            request.additional_guests = guestDtos;
        }
        if (amenities != null && !amenities.isEmpty()) {
            List<AmenitySelectionDto> amenityDtos = new ArrayList<>();
            for (AddOnAmenity a : amenities) {
                amenityDtos.add(new AmenitySelectionDto(Long.parseLong(a.getId()), a.getQuantity()));
            }
            request.amenities = amenityDtos;
        }
        api.createReservation(request).enqueue(new Callback<ReservationDto>() {
            @Override
            public void onResponse(Call<ReservationDto> call, Response<ReservationDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Booking booking = ApiMapper.toBooking(response.body());
                    bookings.add(0, booking);
                    notifyBookingsChanged();
                    if (callback != null) callback.onSuccess(booking);
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<ReservationDto> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * Creates a "New Reservation" transaction covering every room type the
     * guest selected in one wizard pass, as a single atomic request -
     * mirrors createDirectBooking() below exactly, for the Reservation side
     * instead of the direct-Booking side. Sends `rooms[]` (see
     * ReservationRequest#rooms / Api\ReservationController::store()), one
     * entry per DISTINCT room type; the backend creates exactly ONE
     * Reservation with one ReservationRoomLine per entry. Replaces the old
     * one-room-type-per-request loop
     * (Step8ReviewPaymentFragment#submitReservationGroups() used to call
     * the single-room createReservation() overload above once per group,
     * sequentially) - a guest who selects several room types no longer
     * produces several separate Reservations tied together only
     * client-side.
     *
     * `roomGroups` is one entry per DISTINCT room type the guest selected
     * (each inner List<Room> is that type's own list of identical Room
     * entries, one per physical unit - see BookingWizardState#
     * selectedRoomsGroupedByType()), never one entry per physical unit.
     * No payment percentage is collected here (mirrors the single-room
     * overload above) - nothing is being paid at reservation-creation
     * time for either Cash or GCash; a GCash reservation's percentage is
     * chosen later, inside payment.xml's Review Billing.
     */
    public void createReservation(List<List<Room>> roomGroups, Calendar checkIn, Calendar checkOut, int adults, int children,
                                   String guestFirstName, String guestMiddleName, String guestLastName,
                                   String idCardType, List<BookingAndReservationActivity.AdditionalGuest> additionalGuests,
                                   List<AddOnAmenity> amenities, String paymentMethod, @Nullable String idempotencyKey,
                                   RepositoryCallback<Booking> callback) {
        List<Room> firstGroup = roomGroups.get(0);
        ReservationRequest request = new ReservationRequest(
                Long.parseLong(firstGroup.get(0).getId()),
                firstGroup.size(),
                ApiMapper.toApiDate(checkIn),
                ApiMapper.toApiDate(checkOut),
                adults,
                children,
                guestFirstName,
                guestLastName
        );
        List<RoomSelectionDto> roomDtos = new ArrayList<>();
        for (List<Room> group : roomGroups) {
            roomDtos.add(new RoomSelectionDto(Long.parseLong(group.get(0).getId()), group.size()));
        }
        request.rooms = roomDtos;
        request.payment_method = paymentMethod;
        request.idempotency_key = idempotencyKey;
        if (guestMiddleName != null && !guestMiddleName.trim().isEmpty()) {
            request.guest_middle_name = guestMiddleName.trim();
        }
        if (idCardType != null && !idCardType.equals("None")) {
            request.id_card_type = idCardType;
        }
        if (additionalGuests != null && !additionalGuests.isEmpty()) {
            List<AdditionalGuestDto> guestDtos = new ArrayList<>();
            for (BookingAndReservationActivity.AdditionalGuest g : additionalGuests) {
                guestDtos.add(new AdditionalGuestDto(g.name, g.age, g.gender, g.relationship));
            }
            request.additional_guests = guestDtos;
        }
        if (amenities != null && !amenities.isEmpty()) {
            List<AmenitySelectionDto> amenityDtos = new ArrayList<>();
            for (AddOnAmenity a : amenities) {
                amenityDtos.add(new AmenitySelectionDto(Long.parseLong(a.getId()), a.getQuantity()));
            }
            request.amenities = amenityDtos;
        }
        api.createReservation(request).enqueue(new Callback<ReservationDto>() {
            @Override
            public void onResponse(Call<ReservationDto> call, Response<ReservationDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Booking booking = ApiMapper.toBooking(response.body());
                    bookings.add(0, booking);
                    notifyBookingsChanged();
                    if (callback != null) callback.onSuccess(booking);
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<ReservationDto> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * Creates a "New Booking" transaction directly - a genuinely independent
     * record, never a Reservation (see Api\BookingController::store() on the
     * server). Payment is submitted as part of this same call (paymentMethod/
     * referenceNumber/gcashNumber/receiptUri/amountPaid) - no Booking row
     * exists on the server until this succeeds. amountPaid must equal the
     * caller's own computed total (sum of every room type's rate x nights x
     * quantity + every selected amenity's subtotal) - the server
     * independently recomputes and rejects (422) on mismatch, so this is a
     * defensive client-side check, not the source of truth.
     *
     * `roomGroups` is one entry per DISTINCT room type the guest selected
     * (each inner List<Room> is that type's own list of identical Room
     * entries, one per physical unit - see BookingWizardState#
     * selectedRoomsGroupedByType()), never one entry per physical unit -
     * this single call sends every selected room type/quantity and every
     * selected amenity as one atomic request, producing exactly ONE Booking
     * transaction (see MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md). Replaces the
     * old one-room-type-per-request loop
     * (PaymentActivity#submitPendingBookingGroups() used to call this once
     * per group, sequentially) - a guest who selects several room types no
     * longer produces several separate Bookings tied together only
     * client-side.
     */
    public void createDirectBooking(List<List<Room>> roomGroups, Calendar checkIn, Calendar checkOut, int adults, int children,
                                     String guestFirstName, String guestMiddleName, String guestLastName,
                                     String idCardType, Uri idCardImageUri,
                                     List<BookingAndReservationActivity.AdditionalGuest> additionalGuests,
                                     List<AddOnAmenity> amenities,
                                     String paymentMethod, String referenceNumber, String gcashNumber, Uri receiptUri,
                                     double amountPaid, Integer selectedPaymentPercentage, @Nullable String idempotencyKey,
                                     RepositoryCallback<Booking> callback) {
        fileIoExecutor.execute(() -> {
            File idCardFile = null;
            File receiptFile = null;
            try {
                if (idCardImageUri != null) {
                    idCardFile = copyUriToTempFile(idCardImageUri, "id_card_");
                }
                if (receiptUri != null) {
                    receiptFile = copyUriToTempFile(receiptUri, "gcash_receipt_");
                }
            } catch (IOException e) {
                if (idCardFile != null) idCardFile.delete();
                if (callback != null) mainHandler().post(() -> callback.onError("Couldn't read the selected image."));
                return;
            }

            Map<String, RequestBody> fields = new HashMap<>();
            // One [room_type_id]/[quantity] pair per DISTINCT selected room
            // type - see this method's own doc. The legacy single
            // room_type_id/rooms_requested fields are intentionally not
            // sent at all; the backend treats a rooms[] array as the
            // authoritative shape whenever present.
            for (int i = 0; i < roomGroups.size(); i++) {
                List<Room> group = roomGroups.get(i);
                putText(fields, "rooms[" + i + "][room_type_id]", group.get(0).getId());
                putText(fields, "rooms[" + i + "][quantity]", String.valueOf(group.size()));
            }
            putText(fields, "check_in", ApiMapper.toApiDate(checkIn));
            putText(fields, "check_out", ApiMapper.toApiDate(checkOut));
            putText(fields, "adults", String.valueOf(adults));
            putText(fields, "children", String.valueOf(children));
            putText(fields, "guest_first_name", guestFirstName);
            if (guestMiddleName != null && !guestMiddleName.trim().isEmpty()) {
                putText(fields, "guest_middle_name", guestMiddleName.trim());
            }
            putText(fields, "guest_last_name", guestLastName);
            putText(fields, "id_card_type", idCardType != null ? idCardType : "None");
            putText(fields, "payment_method", paymentMethod);
            putText(fields, "amount_paid", String.valueOf(amountPaid));
            if (selectedPaymentPercentage != null) {
                putText(fields, "selected_payment_percentage", String.valueOf(selectedPaymentPercentage));
            }
            // One per Confirm-button tap (never per room/line) - lets a
            // double-tap or client/network retry of this same submission
            // attempt safely return the original booking instead of
            // creating a duplicate. See MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md
            // section 9b.
            if (idempotencyKey != null) {
                putText(fields, "idempotency_key", idempotencyKey);
            }
            if ("gcash".equalsIgnoreCase(paymentMethod)) {
                putText(fields, "reference_number", referenceNumber != null ? referenceNumber : "");
                putText(fields, "gcash_number", gcashNumber != null ? gcashNumber : "");
            }
            if (additionalGuests != null) {
                for (int i = 0; i < additionalGuests.size(); i++) {
                    BookingAndReservationActivity.AdditionalGuest g = additionalGuests.get(i);
                    putText(fields, "additional_guests[" + i + "][name]", g.name);
                    putText(fields, "additional_guests[" + i + "][age]", String.valueOf(g.age));
                    if (g.gender != null) putText(fields, "additional_guests[" + i + "][gender]", g.gender);
                    if (g.relationship != null) putText(fields, "additional_guests[" + i + "][relationship]", g.relationship);
                }
            }
            if (amenities != null) {
                for (int i = 0; i < amenities.size(); i++) {
                    AddOnAmenity a = amenities.get(i);
                    putText(fields, "amenities[" + i + "][amenity_id]", a.getId());
                    putText(fields, "amenities[" + i + "][quantity]", String.valueOf(a.getQuantity()));
                }
            }

            MultipartBody.Part idCardPart = null;
            final File finalIdCardFile = idCardFile;
            if (finalIdCardFile != null) {
                RequestBody fileBody = RequestBody.create(finalIdCardFile, MediaType.parse("image/*"));
                idCardPart = MultipartBody.Part.createFormData("id_card_image", finalIdCardFile.getName(), fileBody);
            }
            MultipartBody.Part receiptPart = null;
            final File finalReceiptFile = receiptFile;
            if (finalReceiptFile != null) {
                RequestBody fileBody = RequestBody.create(finalReceiptFile, MediaType.parse("image/*"));
                receiptPart = MultipartBody.Part.createFormData("receipt", finalReceiptFile.getName(), fileBody);
            }

            api.createDirectBooking(fields, idCardPart, receiptPart).enqueue(new Callback<DirectBookingResponseDto>() {
                @Override
                public void onResponse(Call<DirectBookingResponseDto> call, Response<DirectBookingResponseDto> response) {
                    if (finalIdCardFile != null) finalIdCardFile.delete();
                    if (finalReceiptFile != null) finalReceiptFile.delete();
                    if (response.isSuccessful() && response.body() != null) {
                        Booking booking = ApiMapper.toBooking(response.body());
                        bookings.add(0, booking);
                        notifyBookingsChanged();
                        if (callback != null) callback.onSuccess(booking);
                    } else {
                        if (callback != null) callback.onError(errorMessage(response));
                    }
                }

                @Override
                public void onFailure(Call<DirectBookingResponseDto> call, Throwable t) {
                    if (finalIdCardFile != null) finalIdCardFile.delete();
                    if (finalReceiptFile != null) finalReceiptFile.delete();
                    if (callback == null) return;
                    if ("gcash".equalsIgnoreCase(paymentMethod)) {
                        reconcileAfterFailure(referenceNumber, t, callback);
                    } else {
                        callback.onError(networkErrorMessage(t));
                    }
                }
            });
        });
    }

    private static void putText(Map<String, RequestBody> fields, String key, String value) {
        fields.put(key, RequestBody.create(value != null ? value : "", MediaType.parse("text/plain")));
    }

    /**
     * Copies a content Uri's bytes into a temp cache file (OkHttp can't
     * stream a content:// Uri directly) - must be called off the main thread
     * (see fileIoExecutor). Caller deletes the returned file once the upload
     * finishes or fails.
     */
    private File copyUriToTempFile(Uri uri, String prefix) throws IOException {
        File tempFile = File.createTempFile(prefix, ".jpg", appContext.getCacheDir());
        try (InputStream in = appContext.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tempFile)) {
            if (in == null) throw new IOException("Could not open selected image.");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            tempFile.delete();
            throw e;
        }
        return tempFile;
    }

    public void updateReservation(String reservationId, Calendar checkIn, Calendar checkOut, int adults, int children,
                                   String idCardType, List<BookingAndReservationActivity.AdditionalGuest> additionalGuests,
                                   RepositoryCallback<Booking> callback) {
        ReservationUpdateRequest request = new ReservationUpdateRequest(
                ApiMapper.toApiDate(checkIn),
                ApiMapper.toApiDate(checkOut),
                adults,
                children
        );
        if (idCardType != null && !idCardType.equals("None")) {
            request.id_card_type = idCardType;
        }
        if (additionalGuests != null && !additionalGuests.isEmpty()) {
            List<AdditionalGuestDto> guestDtos = new ArrayList<>();
            for (BookingAndReservationActivity.AdditionalGuest g : additionalGuests) {
                guestDtos.add(new AdditionalGuestDto(g.name, g.age, g.gender, g.relationship));
            }
            request.additional_guests = guestDtos;
        }

        api.updateReservation(reservationId, request).enqueue(new Callback<ReservationDto>() {
            @Override
            public void onResponse(Call<ReservationDto> call, Response<ReservationDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Booking booking = ApiMapper.toBooking(response.body());
                    replaceCachedBooking(booking);
                    if (callback != null) callback.onSuccess(booking);
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<ReservationDto> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * Full Modify save - the wizard-based counterpart to updateReservation()
     * above, additionally carrying the (possibly changed) room. Payment
     * method is intentionally NOT part of this call - Step8ReviewPaymentFragment
     * chains a separate switchReservationToGcash()/switchReservationToCash()
     * call afterward when needed, reusing that already-tested one-time-lock
     * logic instead of duplicating it here.
     */
    public void updateReservationFull(String reservationId, Room room, int roomQty, Calendar checkIn, Calendar checkOut,
                                       int adults, int children, String idCardType,
                                       List<BookingAndReservationActivity.AdditionalGuest> additionalGuests,
                                       RepositoryCallback<Booking> callback) {
        ReservationUpdateRequest request = new ReservationUpdateRequest(
                ApiMapper.toApiDate(checkIn),
                ApiMapper.toApiDate(checkOut),
                adults,
                children
        );
        if (idCardType != null && !idCardType.equals("None")) {
            request.id_card_type = idCardType;
        }
        if (additionalGuests != null && !additionalGuests.isEmpty()) {
            List<AdditionalGuestDto> guestDtos = new ArrayList<>();
            for (BookingAndReservationActivity.AdditionalGuest g : additionalGuests) {
                guestDtos.add(new AdditionalGuestDto(g.name, g.age, g.gender, g.relationship));
            }
            request.additional_guests = guestDtos;
        }
        if (room != null) {
            request.room_type_id = Long.parseLong(room.getId());
            request.rooms_requested = Math.max(1, roomQty);
        }

        api.updateReservation(reservationId, request).enqueue(new Callback<ReservationDto>() {
            @Override
            public void onResponse(Call<ReservationDto> call, Response<ReservationDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Booking booking = ApiMapper.toBooking(response.body());
                    replaceCachedBooking(booking);
                    if (callback != null) callback.onSuccess(booking);
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<ReservationDto> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * The multi-room-type/amenity-aware overload of updateReservationFull() above -
     * used by the one-time Modify flow (Step8ReviewPaymentFragment#saveEditedReservation())
     * once Step1RoomSelectionFragment's old same-type-only lock was lifted, now that
     * Api\ReservationController::update() accepts the same `rooms[]`/`amenities[]`
     * shape store() does. `roomGroups` is one entry per DISTINCT room type the guest
     * selected (each inner List<Room> is that type's own list of identical Room
     * entries, one per physical unit - see BookingWizardState#selectedRoomsGroupedByType()),
     * mirroring createReservation(List<List<Room>>, ...)'s exact same convention.
     * Passing an empty `amenities` list deliberately clears every amenity - see
     * ReservationUpdateRequest's own doc for why that's distinct from passing null.
     */
    public void updateReservationFull(String reservationId, List<List<Room>> roomGroups, Calendar checkIn, Calendar checkOut,
                                       int adults, int children, String idCardType,
                                       List<BookingAndReservationActivity.AdditionalGuest> additionalGuests,
                                       List<AddOnAmenity> amenities,
                                       RepositoryCallback<Booking> callback) {
        ReservationUpdateRequest request = new ReservationUpdateRequest(
                ApiMapper.toApiDate(checkIn),
                ApiMapper.toApiDate(checkOut),
                adults,
                children
        );
        if (idCardType != null && !idCardType.equals("None")) {
            request.id_card_type = idCardType;
        }
        if (additionalGuests != null && !additionalGuests.isEmpty()) {
            List<AdditionalGuestDto> guestDtos = new ArrayList<>();
            for (BookingAndReservationActivity.AdditionalGuest g : additionalGuests) {
                guestDtos.add(new AdditionalGuestDto(g.name, g.age, g.gender, g.relationship));
            }
            request.additional_guests = guestDtos;
        }
        if (roomGroups != null && !roomGroups.isEmpty()) {
            // Also populate the legacy single-room_type_id/rooms_requested pair from
            // the FIRST group - same "send both shapes together" convention
            // createReservation(List<List<Room>>, ...) already uses - so this still
            // saves correctly against a server that hasn't picked up the rooms[]-aware
            // update() endpoint yet, instead of leaving these fields unset (which, back
            // when they were primitives, silently serialized as 0 and tripped the
            // server's exists/min:1 validation - see ReservationUpdateRequest's doc).
            List<Room> firstGroup = roomGroups.get(0);
            request.room_type_id = Long.parseLong(firstGroup.get(0).getId());
            request.rooms_requested = firstGroup.size();

            List<RoomSelectionDto> roomDtos = new ArrayList<>();
            for (List<Room> group : roomGroups) {
                roomDtos.add(new RoomSelectionDto(Long.parseLong(group.get(0).getId()), group.size()));
            }
            request.rooms = roomDtos;
        }
        if (amenities != null) {
            List<AmenitySelectionDto> amenityDtos = new ArrayList<>();
            for (AddOnAmenity a : amenities) {
                amenityDtos.add(new AmenitySelectionDto(Long.parseLong(a.getId()), a.getQuantity()));
            }
            request.amenities = amenityDtos;
        }

        api.updateReservation(reservationId, request).enqueue(new Callback<ReservationDto>() {
            @Override
            public void onResponse(Call<ReservationDto> call, Response<ReservationDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Booking booking = ApiMapper.toBooking(response.body());
                    replaceCachedBooking(booking);
                    if (callback != null) callback.onSuccess(booking);
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<ReservationDto> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    public void cancelReservation(String reservationId, RepositoryCallback<Booking> callback) {
        api.cancelReservation(reservationId).enqueue(new Callback<ReservationDto>() {
            @Override
            public void onResponse(Call<ReservationDto> call, Response<ReservationDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Booking booking = ApiMapper.toBooking(response.body());
                    replaceCachedBooking(booking);
                    if (callback != null) callback.onSuccess(booking);
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<ReservationDto> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /** Direct Bookings (isDirectBooking()==true) have no parent Reservation row, so cancelReservation() above can't be used for them - see Api\BookingController::cancel(). */
    public void cancelDirectBooking(String bookingId, RepositoryCallback<Booking> callback) {
        api.cancelDirectBooking(bookingId).enqueue(new Callback<DirectBookingResponseDto>() {
            @Override
            public void onResponse(Call<DirectBookingResponseDto> call, Response<DirectBookingResponseDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Booking booking = ApiMapper.toBooking(response.body());
                    replaceCachedBooking(booking);
                    if (callback != null) callback.onSuccess(booking);
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<DirectBookingResponseDto> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * One-time Cash -> GCash payment-method switch - server-enforced (see
     * Api\ReservationController::switchToGcash()/ReservationWorkflowService::
     * switchToGcash()), never callable twice for the same reservation regardless of
     * app reinstalls/device changes. On success, Pay Now with GCash becomes available
     * immediately for this reservation.
     */
    public void switchReservationToGcash(String reservationId, RepositoryCallback<Booking> callback) {
        api.switchReservationToGcash(reservationId).enqueue(new Callback<ReservationDto>() {
            @Override
            public void onResponse(Call<ReservationDto> call, Response<ReservationDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Booking booking = ApiMapper.toBooking(response.body());
                    replaceCachedBooking(booking);
                    if (callback != null) callback.onSuccess(booking);
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<ReservationDto> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * One-time GCash -> Cash payment-method switch - the mirror of
     * switchReservationToGcash() above, server-enforced via the same
     * payment_method_locked_at DB field (see Api\ReservationController::switchToCash()/
     * ReservationWorkflowService::switchToCash()). On success the reservation is simply
     * eligible for walk-in cash settlement, same as any other Cash reservation - no
     * further unlock step.
     */
    public void switchReservationToCash(String reservationId, RepositoryCallback<Booking> callback) {
        api.switchReservationToCash(reservationId).enqueue(new Callback<ReservationDto>() {
            @Override
            public void onResponse(Call<ReservationDto> call, Response<ReservationDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Booking booking = ApiMapper.toBooking(response.body());
                    replaceCachedBooking(booking);
                    if (callback != null) callback.onSuccess(booking);
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<ReservationDto> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * Real, permanent, non-recoverable deletion (hard DELETE, not a
     * hidden_at-style soft hide) of this guest's own Reservation row and
     * its owned child records. The server independently re-validates
     * ownership + eligible status (Cancelled/Rejected) - see
     * TRANSACTION_PERMANENT_DELETE_BACKEND_SPEC.md - this call only
     * removes the local cache entry after the server confirms success.
     */
    public void deleteReservationPermanently(String reservationId, RepositoryCallback<Void> callback) {
        api.deleteReservationPermanently(reservationId).enqueue(new Callback<ApiMessage>() {
            @Override
            public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                if (response.isSuccessful()) {
                    // Scoped by !isDirectBooking() too, not just id - reservations
                    // and direct bookings come from independent backend id
                    // sequences and coexist in this same cache, so an id-only
                    // match could also wipe out an unrelated direct Booking that
                    // merely happens to share this reservation's numeric id.
                    bookings.removeIf(b -> b.getId().equals(reservationId) && !b.isDirectBooking());
                    notifyBookingsChanged();
                    if (callback != null) callback.onSuccess(null);
                } else {
                    if (callback != null) callback.onError(deleteErrorMessage("reservation", reservationId, call, response));
                }
            }

            @Override
            public void onFailure(Call<ApiMessage> call, Throwable t) {
                android.util.Log.e("RoomRepository", "Permanent delete network failure - reservationId=" + reservationId
                        + " method=" + call.request().method() + " url=" + call.request().url(), t);
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * Same real, permanent deletion as deleteReservationPermanently() above,
     * for a genuinely direct Booking (Booking#isDirectBooking()==true)
     * instead - confirmed directly against the live backend (2026-09-18):
     * Api\BookingController is exclusively a direct-booking controller (its
     * own cancel() hard-rejects any booking with a non-null reservation_id),
     * so this endpoint only ever exists for that case. bookings/reservations
     * each have their own independent id sequence, so calling
     * deleteReservationPermanently() with a bookings-table id here would be
     * a real - not just theoretical - risk of deleting an unrelated
     * reservation that happens to share the same numeric id. Callers must
     * check isDirectBooking() (or use Booking#getBookingEndpointDeleteId())
     * and call this instead for that case; see confirmDeleteTransaction()/
     * TransactionListActivity#confirmDelete() for both existing call sites.
     * Every reservation-derived transaction - converted or not, Cancelled or
     * Completed - goes through deleteReservationPermanently() instead.
     */
    public void deleteBookingPermanently(String bookingId, RepositoryCallback<Void> callback) {
        api.deleteBookingPermanently(bookingId).enqueue(new Callback<ApiMessage>() {
            @Override
            public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                if (response.isSuccessful()) {
                    // See deleteReservationPermanently()'s matching comment -
                    // scoped by isDirectBooking() too, not just id, for the same reason.
                    bookings.removeIf(b -> b.getId().equals(bookingId) && b.isDirectBooking());
                    notifyBookingsChanged();
                    if (callback != null) callback.onSuccess(null);
                } else {
                    if (callback != null) callback.onError(deleteErrorMessage("booking", bookingId, call, response));
                }
            }

            @Override
            public void onFailure(Call<ApiMessage> call, Throwable t) {
                android.util.Log.e("RoomRepository", "Permanent delete network failure - bookingId=" + bookingId
                        + " method=" + call.request().method() + " url=" + call.request().url(), t);
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * The Paid/Additional amenities originally selected for this
     * reservation, with how much has already been requested since - the
     * only amenities the guest may submit a further request for (see
     * Api\AmenityRequestController::requestable()).
     */
    public void fetchRequestableAmenities(String reservationId, RepositoryCallback<List<RequestableAmenityDto>> callback) {
        api.getRequestableAmenities(reservationId).enqueue(new Callback<List<RequestableAmenityDto>>() {
            @Override
            public void onResponse(Call<List<RequestableAmenityDto>> call, Response<List<RequestableAmenityDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (callback != null) callback.onSuccess(response.body());
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<List<RequestableAmenityDto>> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * booking.getTotalAmount() undercounts paid add-on amenities for a
     * Reservation that hasn't converted into a Booking yet - see
     * ApiMapper#toBooking(ReservationDto): with no Billing row yet, it falls
     * back to nightlyRate * nights * roomsRequested (room cost only), since
     * the reservation list/detail API response carries no amenities/total
     * field of its own. This corrects getTotalAmount() in place using the
     * same requestable-amenities endpoint Billing Summary's own breakdown is
     * built from, so every caller that reads getTotalAmount()/
     * getRemainingBalance() afterward agrees with Billing Summary's Grand
     * Total. Single source of truth for this correction - previously
     * duplicated separately in BookingAndReservationActivity and
     * BookingDetailsActivity; both now call this instead, and
     * PaymentActivity's existing-reservation Pay Now summary calls it too
     * (that path never had the correction at all, which is why its Review
     * Billing screen could show a room-only total while the reservation list
     * and Billing Summary already showed the correct one). A no-op (calls
     * back immediately with the unmodified booking) once the reservation has
     * converted to a real Booking, since the server-side Billing total
     * already includes amenities by then.
     *
     * Idempotent per booking id (tracked in {@link #correctedTotalIds}, reset
     * whenever {@link #refreshBookings} replaces the cache with fresh server
     * objects): the same Booking instance is shared across every screen's
     * view of {@link #bookings} (BookingAndReservationActivity's list,
     * BookingDetailsActivity, and PaymentActivity all now call this on the
     * SAME cached object), so without this guard, whichever of them runs
     * second would add the amenities total on top a second time.
     */
    public void correctPendingReservationTotal(Booking booking, RepositoryCallback<Booking> callback) {
        // isTotalIncludesAmenities() is true whenever ApiMapper already built
        // totalAmount from Reservation::total_amount_due (backend), which is
        // already amenities-inclusive - applying this top-up on top of that
        // would double-count paid amenities, inflating the total shown/used
        // above what PaymentController::store() actually validates a
        // submitted GCash percentage against (causing every tier to appear
        // to mismatch even though the guest's own on-screen math is
        // internally consistent). Only a genuinely legacy/cached response
        // without total_amount_due still needs this correction. Also
        // excludes a historical (post-conversion) reservation clone
        // explicitly, by name (isHistoricalReservation()), rather than
        // relying solely on the other two flags - see ApiMapper#
        // toHistoricalReservation()'s own docblock: it's built from an
        // already-correct, already amenities-inclusive totalAmount, so
        // topping it up again is always wrong for one of these regardless
        // of whatever isHasBooking()/isTotalIncludesAmenities() happen to
        // be set to (a past real bug: toHistoricalReservation() forced
        // isHasBooking() false without also copying isTotalIncludesAmenities(),
        // so both those conditions failed and this ran anyway, double-
        // counting the amenities total - e.g. an â‚±8,650 grand total
        // becoming â‚±8,800).
        if (booking == null || booking.isHasBooking() || booking.isTotalIncludesAmenities() || booking.isHistoricalReservation()) {
            if (callback != null) callback.onSuccess(booking);
            return;
        }
        if (!correctedTotalIds.add(booking.getId())) {
            if (callback != null) callback.onSuccess(booking);
            return;
        }
        fetchRequestableAmenities(booking.getId(), new RepositoryCallback<List<RequestableAmenityDto>>() {
            @Override
            public void onSuccess(List<RequestableAmenityDto> items) {
                double amenitiesTotal = 0;
                if (items != null) {
                    for (RequestableAmenityDto item : items) {
                        double price = Math.max(0, item.price);
                        int quantity = Math.max(0, item.original_quantity);
                        amenitiesTotal += price * quantity;
                    }
                }
                if (amenitiesTotal > 0.009) {
                    booking.setTotalAmount(booking.getTotalAmount() + amenitiesTotal);
                }
                if (callback != null) callback.onSuccess(booking);
            }

            @Override
            public void onError(String message) {
                // Best-effort correction only - still hand back the (possibly room-only) booking
                // rather than blocking the caller's whole screen on this one extra call.
                if (callback != null) callback.onSuccess(booking);
            }
        });
    }

    /** This reservation's own Additional Amenity Request history, most recent first. */
    public void fetchAmenityRequests(String reservationId, RepositoryCallback<List<AmenityRequestDto>> callback) {
        api.getAmenityRequests(reservationId).enqueue(new Callback<List<AmenityRequestDto>>() {
            @Override
            public void onResponse(Call<List<AmenityRequestDto>> call, Response<List<AmenityRequestDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (callback != null) callback.onSuccess(response.body());
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<List<AmenityRequestDto>> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * Submits a request for additional quantity of a Paid/Additional
     * amenity already selected during this reservation's original
     * booking. The server rejects (422, surfaced via errorMessage()) an
     * amenity_id not among that original selection - real backend
     * enforcement, not just a client-side filter.
     */
    public void submitAmenityRequest(String reservationId, long amenityId, int quantity, RepositoryCallback<AmenityRequestDto> callback) {
        api.submitAmenityRequest(reservationId, new AmenityRequestSubmitRequest(amenityId, quantity)).enqueue(new Callback<AmenityRequestDto>() {
            @Override
            public void onResponse(Call<AmenityRequestDto> call, Response<AmenityRequestDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (callback != null) callback.onSuccess(response.body());
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<AmenityRequestDto> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    public void submitPayment(String reservationId, String paymentMethod, String paymentType, String referenceNumber, double amount,
                               Integer selectedPaymentPercentage, RepositoryCallback<Void> callback) {
        PaymentRequest request = new PaymentRequest(paymentMethod, paymentType, referenceNumber, amount);
        request.selected_payment_percentage = selectedPaymentPercentage;
        api.submitPayment(reservationId, request).enqueue(new Callback<PaymentSubmitResponse>() {
            @Override
            public void onResponse(Call<PaymentSubmitResponse> call, Response<PaymentSubmitResponse> response) {
                if (response.isSuccessful()) {
                    notifyBookingsChanged();
                    if (callback != null) callback.onSuccess(null);
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<PaymentSubmitResponse> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * GCash-only submission: uploads the receipt image and the registered
     * GCash mobile number for real, alongside the same payment fields
     * submitPayment() sends for cash. Content picker results come back as a
     * content:// Uri - copy into a cache file first (same reasoning as
     * uploadIdCard() below).
     */
    public void submitGcashPayment(String reservationId, String paymentType, String referenceNumber, double amount,
                                    String gcashNumber, Uri receiptUri, Integer selectedPaymentPercentage, RepositoryCallback<Void> callback) {
        fileIoExecutor.execute(() -> {
            File tempFile;
            try {
                tempFile = copyUriToTempFile(receiptUri, "gcash_receipt_");
            } catch (IOException e) {
                if (callback != null) mainHandler().post(() -> callback.onError("Couldn't read the selected receipt image."));
                return;
            }
            final File uploadFile = tempFile;

            RequestBody textBody = RequestBody.create("gcash", MediaType.parse("text/plain"));
            RequestBody typeBody = RequestBody.create(paymentType, MediaType.parse("text/plain"));
            RequestBody refBody = RequestBody.create(referenceNumber != null ? referenceNumber : "", MediaType.parse("text/plain"));
            RequestBody amountBody = RequestBody.create(String.valueOf(amount), MediaType.parse("text/plain"));
            RequestBody gcashNumberBody = RequestBody.create(gcashNumber != null ? gcashNumber : "", MediaType.parse("text/plain"));
            RequestBody percentageBody = RequestBody.create(
                    selectedPaymentPercentage != null ? String.valueOf(selectedPaymentPercentage) : "",
                    MediaType.parse("text/plain"));
            RequestBody fileBody = RequestBody.create(uploadFile, MediaType.parse("image/*"));
            MultipartBody.Part receiptPart = MultipartBody.Part.createFormData("receipt", uploadFile.getName(), fileBody);

            api.submitGcashPayment(reservationId, textBody, typeBody, refBody, amountBody, gcashNumberBody, percentageBody, receiptPart)
                    .enqueue(new Callback<PaymentSubmitResponse>() {
                @Override
                public void onResponse(Call<PaymentSubmitResponse> call, Response<PaymentSubmitResponse> response) {
                    uploadFile.delete();
                    if (response.isSuccessful()) {
                        notifyBookingsChanged();
                        if (callback != null) callback.onSuccess(null);
                    } else {
                        if (callback != null) callback.onError(errorMessage(response));
                    }
                }

                @Override
                public void onFailure(Call<PaymentSubmitResponse> call, Throwable t) {
                    uploadFile.delete();
                    if (callback == null) return;
                    reconcileAfterFailure(referenceNumber, t, new RepositoryCallback<Booking>() {
                        @Override
                        public void onSuccess(Booking result) {
                            notifyBookingsChanged();
                            callback.onSuccess(null);
                        }

                        @Override
                        public void onError(String message) {
                            callback.onError(message);
                        }
                    });
                }
            });
        });
    }

    /** Cancels a still-pending (not yet verified) payment and its parent reservation/booking - marks it Transaction Failed. */
    public void cancelPayment(String paymentId, RepositoryCallback<Void> callback) {
        api.cancelPayment(paymentId).enqueue(new Callback<ApiMessage>() {
            @Override
            public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                if (response.isSuccessful()) {
                    notifyBookingsChanged();
                    if (callback != null) callback.onSuccess(null);
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<ApiMessage> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /** "Convert to Reservation": voids this in-progress payment attempt only, keeps the room held as a plain unpaid reservation. */
    public void voidPayment(String paymentId, RepositoryCallback<Void> callback) {
        api.voidPayment(paymentId).enqueue(new Callback<ApiMessage>() {
            @Override
            public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                if (response.isSuccessful()) {
                    notifyBookingsChanged();
                    if (callback != null) callback.onSuccess(null);
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<ApiMessage> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * Uploads the senior-citizen/PWD ID photo for a reservation. Content
     * picker/camera results come back as a content:// Uri, which OkHttp
     * can't stream directly - copy it into a cache file first, then wrap
     * that in the multipart body.
     */
    public void uploadIdCard(String reservationId, Uri imageUri, RepositoryCallback<Void> callback) {
        fileIoExecutor.execute(() -> {
            File tempFile;
            try {
                tempFile = copyUriToTempFile(imageUri, "id_card_");
            } catch (IOException e) {
                if (callback != null) mainHandler().post(() -> callback.onError("Couldn't read the selected ID image."));
                return;
            }
            final File uploadFile = tempFile;

            RequestBody fileBody = RequestBody.create(uploadFile, MediaType.parse("image/*"));
            MultipartBody.Part part = MultipartBody.Part.createFormData("id_card", uploadFile.getName(), fileBody);

            api.uploadIdCard(reservationId, part).enqueue(new Callback<ApiMessage>() {
                @Override
                public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                    uploadFile.delete();
                    if (response.isSuccessful()) {
                        if (callback != null) callback.onSuccess(null);
                    } else {
                        if (callback != null) callback.onError(errorMessage(response));
                    }
                }

                @Override
                public void onFailure(Call<ApiMessage> call, Throwable t) {
                    uploadFile.delete();
                    if (callback != null) callback.onError(networkErrorMessage(t));
                }
            });
        });
    }

    /** @param onDone receives true only if the server actually confirmed the read-state change. */
    public void markNotificationAsRead(String notificationId, java.util.function.Consumer<Boolean> onDone) {
        api.markNotificationRead(notificationId).enqueue(new Callback<NotificationDto>() {
            @Override
            public void onResponse(Call<NotificationDto> call, Response<NotificationDto> response) {
                boolean success = response.isSuccessful();
                if (success) {
                    for (Notification n : notifications) {
                        if (n.getId().equals(notificationId)) {
                            n.setRead(true);
                            break;
                        }
                    }
                }
                if (onDone != null) onDone.accept(success);
            }

            @Override
            public void onFailure(Call<NotificationDto> call, Throwable t) {
                if (onDone != null) onDone.accept(false);
            }
        });
    }

    /** @param onDone receives true only if the server actually confirmed the read-state change. */
    public void markAllNotificationsAsRead(java.util.function.Consumer<Boolean> onDone) {
        api.markAllNotificationsRead().enqueue(new Callback<ApiMessage>() {
            @Override
            public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                boolean success = response.isSuccessful();
                if (success) {
                    for (Notification n : notifications) {
                        n.setRead(true);
                    }
                }
                if (onDone != null) onDone.accept(success);
            }

            @Override
            public void onFailure(Call<ApiMessage> call, Throwable t) {
                if (onDone != null) onDone.accept(false);
            }
        });
    }

    // ---- Local-only search over cached rooms. Cross-guest date conflicts
    // are the backend's call (a reservation requests a room TYPE; staff
    // assign an actual room at confirmation), so the availability checks
    // below combine the room's backend-reported availability flag with the
    // one conflict the client CAN judge accurately: the signed-in guest's
    // own overlapping reservations (see findOverlappingBooking). ----

    public List<Room> searchRooms(String query, String type, int guests, float minPrice, float maxPrice, Calendar checkIn, Calendar checkOut) {
        List<Room> results = new ArrayList<>();
        String lowerQuery = query == null ? "" : query.toLowerCase(Locale.US);

        for (Room r : rooms) {
            boolean matchesQuery = r.getName().toLowerCase(Locale.US).contains(lowerQuery) || r.getDescription().toLowerCase(Locale.US).contains(lowerQuery);
            boolean matchesType = type == null || type.isEmpty() || type.equalsIgnoreCase("All")
                    || r.getType().toLowerCase(Locale.US).contains(type.toLowerCase(Locale.US));
            boolean matchesGuests = r.getCapacity() >= guests;
            boolean matchesPrice = r.getPricePerNight() >= minPrice && r.getPricePerNight() <= maxPrice;

            if (matchesQuery && matchesType && matchesGuests && matchesPrice) {
                results.add(r);
            }
        }
        return results;
    }

    public boolean isAvailableForDates(Room room, Calendar checkIn, Calendar checkOut, String excludeBookingId) {
        return room != null && room.isAvailable()
                && findOverlappingBooking(room.getType(), checkIn, checkOut, excludeBookingId) == null;
    }

    public boolean isDayAvailable(String roomId, long timeInMillis, String excludeBookingId) {
        for (Room r : rooms) {
            if (r.getId().equals(roomId)) {
                Calendar dayStart = Calendar.getInstance();
                dayStart.setTimeInMillis(timeInMillis);
                Calendar dayEnd = (Calendar) dayStart.clone();
                dayEnd.add(Calendar.DAY_OF_YEAR, 1);
                return r.isAvailable()
                        && findOverlappingBooking(r.getType(), dayStart, dayEnd, excludeBookingId) == null;
            }
        }
        return false;
    }

    /**
     * Cross-guest availability can only be judged by the backend (rooms are
     * assigned by staff per TYPE at confirmation), but the guest's OWN
     * reservations are fully known here - so double booking by the same
     * account is blocked client-side: returns the guest's active stay of the
     * same room type overlapping [checkIn, checkOut), or null when the range
     * is clear. Cancelled/Checked-Out/Rejected stays don't block, and the
     * ranges are half-open so a check-in on someone's check-out day is fine.
     */
    public Booking findOverlappingBooking(String roomType, Calendar checkIn, Calendar checkOut, String excludeBookingId) {
        if (roomType == null || checkIn == null || checkOut == null) {
            return null;
        }
        SimpleDateFormat displayDate = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        long newIn = startOfDay(checkIn);
        long newOut = startOfDay(checkOut);
        for (Booking b : bookings) {
            if (excludeBookingId != null && excludeBookingId.equals(b.getId())) continue;
            String status = b.getStatus();
            if ("Cancelled".equalsIgnoreCase(status)
                    || "Checked-Out".equalsIgnoreCase(status)
                    || "Rejected".equalsIgnoreCase(status)) continue;
            if (!b.hasRoomType(roomType)) continue;
            try {
                long existingIn = displayDate.parse(b.getCheckInDate()).getTime();
                long existingOut = displayDate.parse(b.getCheckOutDate()).getTime();
                if (newIn < existingOut && existingIn < newOut) {
                    return b;
                }
            } catch (Exception ignored) {
                // Unparseable dates on a record can't be compared - don't block on them.
            }
        }
        return null;
    }

    /**
     * Guest-level stay-date conflict, independent of room type - a guest
     * can't physically occupy two overlapping hotel stays regardless of
     * which room type either one is for, so unlike findOverlappingBooking()
     * above (which only blocks a repeat of the SAME room type, for the
     * "don't double-book this exact room type" check on the Room Selection/
     * Dates steps), this checks every one of the guest's own active
     * Bookings AND Reservations together (both already live in the merged
     * `bookings` cache - see refreshBookings()) regardless of type. This is
     * the Guest Transaction Date Validation from the Dates step's own
     * validateBeforeNext() - a completely separate concern from Room
     * Availability Validation (cross-guest inventory, judged server-side).
     * Same exclusions/half-open-range convention as findOverlappingBooking().
     */
    public Booking findConflictingStayForGuest(Calendar checkIn, Calendar checkOut, String excludeBookingId) {
        return findConflictingStayForGuest(checkIn, checkOut,
                excludeBookingId == null ? null : java.util.Collections.singletonList(excludeBookingId));
    }

    /**
     * Same as {@link #findConflictingStayForGuest(Calendar, Calendar, String)}, but excludes
     * every id in {@code excludeBookingIds} - needed when editing one sibling of a
     * BookingGroupState-grouped multi-room-type transaction, since every sibling shares the
     * exact same dates and must not be reported as a conflict with itself.
     */
    public Booking findConflictingStayForGuest(Calendar checkIn, Calendar checkOut, List<String> excludeBookingIds) {
        if (checkIn == null || checkOut == null) {
            return null;
        }
        SimpleDateFormat displayDate = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        long newIn = startOfDay(checkIn);
        long newOut = startOfDay(checkOut);
        for (Booking b : bookings) {
            if (excludeBookingIds != null && excludeBookingIds.contains(b.getId())) continue;
            String status = b.getStatus();
            if ("Cancelled".equalsIgnoreCase(status)
                    || "Checked-Out".equalsIgnoreCase(status)
                    || "Rejected".equalsIgnoreCase(status)) continue;
            try {
                long existingIn = displayDate.parse(b.getCheckInDate()).getTime();
                long existingOut = displayDate.parse(b.getCheckOutDate()).getTime();
                if (newIn < existingOut && existingIn < newOut) {
                    return b;
                }
            } catch (Exception ignored) {
                // Unparseable dates on a record can't be compared - don't block on them.
            }
        }
        return null;
    }

    private static long startOfDay(Calendar cal) {
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // ---- Helpers ----

    private void replaceCachedBooking(Booking updated) {
        for (int i = 0; i < bookings.size(); i++) {
            if (bookings.get(i).getId().equals(updated.getId())) {
                bookings.set(i, updated);
                notifyBookingsChanged();
                return;
            }
        }
        bookings.add(0, updated);
        notifyBookingsChanged();
    }

    /**
     * onFailure() fires both when a request never reached the server AND when it did,
     * the server fully saved the booking/payment, and only the confirmation response's
     * body failed to arrive/parse (dropped connection, timeout waiting for the reply,
     * malformed JSON) - Retrofit hands back just a Throwable here, with no Response to
     * inspect, so the two cases are indistinguishable from t alone. Since a GCash
     * reference number is unique per payment and refreshBookings() only ever returns
     * records belonging to the authenticated guest's own token, finding it there is a
     * safe, authoritative way to tell which case actually happened before reporting an
     * error (and risking the guest retrying into a false "duplicate reference" rejection
     * of their own already-saved payment).
     */
    private void reconcileAfterFailure(String referenceNumber, Throwable t, RepositoryCallback<Booking> resultCallback) {
        if (resultCallback == null) return;
        if (referenceNumber == null || referenceNumber.trim().isEmpty()) {
            resultCallback.onError(networkErrorMessage(t));
            return;
        }
        refreshBookings(new RepositoryCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> result) {
                Booking match = findBookingByReference(result, referenceNumber);
                if (match != null) {
                    resultCallback.onSuccess(match);
                } else {
                    resultCallback.onError(networkErrorMessage(t));
                }
            }

            @Override
            public void onError(String message) {
                resultCallback.onError(networkErrorMessage(t));
            }
        });
    }

    private Booking findBookingByReference(List<Booking> bookingList, String referenceNumber) {
        if (referenceNumber == null || bookingList == null) return null;
        for (Booking b : bookingList) {
            if (b.getPaymentHistory() == null) continue;
            for (Booking.PaymentRecord record : b.getPaymentHistory()) {
                if (referenceNumber.equalsIgnoreCase(record.referenceNumber)) {
                    return b;
                }
            }
        }
        return null;
    }

    /**
     * Parses the server's error body as real JSON (via Gson) instead of a
     * hand-rolled "message":" substring search - the old approach searched
     * for the next literal '"' character without any awareness of JSON
     * escaping, so a message containing an escaped character anywhere
     * before its real end (e.g. a field name quoted at the very start,
     * "\"gcash_reference_number\" must be exactly 13 digits.") would be
     * truncated to a single stray backslash, which is exactly the garbled
     * "Payment submission failed: \" guests were seeing. Field-level
     * validation errors (Laravel's {"errors":{"field":["..."]}} shape) are
     * preferred over the generic top-level "message" ("The given data was
     * invalid.") since they're the specific, actionable text guests need.
     */
    /**
     * Guards forceSessionExpiredLogout() so a burst of parallel requests that
     * all 401 around the same moment (e.g. a screen firing several calls at
     * once right after the token expires) only clears the session and
     * launches LoginActivity once, not once per failed call.
     */
    private static final java.util.concurrent.atomic.AtomicBoolean sessionExpiredHandled = new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * A 401/419 means the token this app is holding is no longer valid
     * (expired or revoked server-side) - previously this was only ever
     * translated into an "session expired" error STRING shown in a toast,
     * with the stale token left in SharedPreferences and kept on every
     * subsequent request, so the guest saw the same toast repeatedly on
     * every screen with no way back to login short of manually finding the
     * logout button. This clears the session and sends the guest to
     * LoginActivity immediately, same as a manual logout
     * (BaseNavigationActivity.logout()) - started with NEW_TASK|CLEAR_TASK
     * since this can fire from a background network callback with no
     * Activity in hand.
     */
    private void forceSessionExpiredLogout() {
        if (!sessionExpiredHandled.compareAndSet(false, true)) return;
        com.example.velocitysuites.network.SessionManager.clear(appContext);
        android.content.Intent intent = new android.content.Intent(appContext, LoginActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        appContext.startActivity(intent);
    }

    private String errorMessage(Response<?> response) {
        if (response.errorBody() != null) {
            try {
                String body = response.errorBody().string();
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                String fieldError = firstFieldError(json);
                if (fieldError != null && !fieldError.trim().isEmpty()) return fieldError;
                if (json.has("message") && !json.get("message").isJsonNull()) {
                    String msg = json.get("message").getAsString();
                    if (!msg.trim().isEmpty()) return msg;
                }
            } catch (Exception e) {
                // Malformed/non-JSON body (HTML error page, empty body, etc.) -
                // fall through to the HTTP-code-based generic message below
                // rather than surfacing raw/garbled text to the guest.
                android.util.Log.e("RoomRepository", "Failed to parse error response body", e);
            }
        }
        int code = response.code();
        if (code == 401 || code == 419) {
            forceSessionExpiredLogout();
            return appContext.getString(R.string.error_session_expired);
        }
        if (code >= 500) {
            return appContext.getString(R.string.error_server_error);
        }
        // No parseable JSON message/errors body for this status (HTML error page,
        // empty body, or a 4xx this method doesn't special-case) - log the raw code
        // for debugging but never surface it directly to the guest (see
        // deleteErrorMessage()'s identical rationale for the delete-only endpoints).
        android.util.Log.e("RoomRepository", "Unhandled error response with no usable message body, httpStatus=" + code);
        return appContext.getString(R.string.error_unexpected_response);
    }

    /**
     * Status-code-specific error mapping for the permanent-delete endpoints
     * only (not the shared errorMessage() above, which many other call
     * sites still use unchanged). Always logs the full technical detail
     * (transaction id, HTTP method/URL, status code, raw response body) for
     * developers, but never surfaces a raw backend/framework error string
     * to the guest - in particular a 405 (the backend route doesn't accept
     * this HTTP verb yet, e.g. DELETE not registered - see
     * TRANSACTION_DELETE_BACKEND_SPEC.md) must show a clean, generic
     * message instead of Laravel's own "The DELETE method is not
     * supported..." text.
     */
    private String deleteErrorMessage(String transactionKind, String transactionId, Call<ApiMessage> call, Response<ApiMessage> response) {
        int code = response.code();
        String rawBody = null;
        if (response.errorBody() != null) {
            try {
                rawBody = response.errorBody().string();
            } catch (Exception e) {
                rawBody = "<unreadable: " + e.getMessage() + ">";
            }
        }
        android.util.Log.e("RoomRepository", "Permanent delete failed - kind=" + transactionKind
                + " id=" + transactionId
                + " method=" + call.request().method()
                + " url=" + call.request().url()
                + " httpStatus=" + code
                + " body=" + rawBody);

        boolean isBooking = "booking".equals(transactionKind);
        switch (code) {
            case 401:
            case 419:
                forceSessionExpiredLogout();
                return appContext.getString(R.string.error_session_expired);
            case 403:
                return appContext.getString(R.string.delete_error_forbidden);
            case 404:
                return appContext.getString(isBooking ? R.string.delete_error_not_found_booking : R.string.delete_error_not_found_reservation);
            case 409:
            case 422:
                return appContext.getString(R.string.delete_error_conflict);
            case 405:
                android.util.Log.e("RoomRepository", "Backend route for " + transactionKind + "/" + transactionId
                        + " does not support the DELETE method - this is a server-side routing gap, not a guest-facing error. See TRANSACTION_DELETE_BACKEND_SPEC.md.");
                return appContext.getString(R.string.delete_error_unavailable);
            default:
                if (code >= 500) {
                    return appContext.getString(R.string.error_server_error);
                }
                return appContext.getString(R.string.delete_error_unavailable);
        }
    }

    /** First message found under a Laravel-style {"errors":{"field":["msg", ...]}} object, or null if absent/empty. */
    private String firstFieldError(com.google.gson.JsonObject json) {
        if (!json.has("errors") || !json.get("errors").isJsonObject()) return null;
        com.google.gson.JsonObject errors = json.getAsJsonObject("errors");
        for (String key : errors.keySet()) {
            com.google.gson.JsonElement value = errors.get(key);
            if (value.isJsonArray() && value.getAsJsonArray().size() > 0) {
                return value.getAsJsonArray().get(0).getAsString();
            }
            if (value.isJsonPrimitive()) {
                return value.getAsString();
            }
        }
        return null;
    }

    /**
     * Authorization-protected lookup of a single receipt by its
     * receipt_number (PR-.../FR-.../OR-...) - see ApiService#getReceipt()'s
     * own doc: a pure lookup, never mints a missing receipt, and the
     * backend rejects an unknown/not-yet-available/not-owned-by-this-guest
     * number with the same 404 either way. Prefer a Booking/Reservation's
     * own getPaymentTransactions()/getReceipts() (already attached by
     * refreshBookings()) for a transaction-detail screen's own receipts
     * list; use this specifically when a caller has only a bare
     * receiptNumber on hand (e.g. eventually, a notification deep-link -
     * see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md §17) and needs the full
     * receipt payload.
     * <p>
     * Not yet deployed to production as of this Android integration pass -
     * do not call expecting a real response until the backend branch ships.
     */
    public void getReceipt(String receiptNumber, RepositoryCallback<ReceiptDetail> callback) {
        api.getReceipt(receiptNumber).enqueue(new Callback<ReceiptDetailResponse>() {
            @Override
            public void onResponse(Call<ReceiptDetailResponse> call, Response<ReceiptDetailResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().receipt != null) {
                    if (callback != null) callback.onSuccess(ApiMapper.toReceiptDetail(response.body()));
                } else if (response.code() == 404) {
                    if (callback != null) callback.onError(appContext.getString(R.string.receipt_not_available_desc));
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<ReceiptDetailResponse> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * The guest's flat, cross-transaction payment ledger (Api\ProfileController::payments(),
     * GET guest/payments) - declared in ApiService for a while but never
     * wired into a repository method until now. Returns the raw
     * PaymentsResponse (paginated payments + pending_bills) rather than a
     * mapped domain list, since no screen consumes this yet - see
     * ApiService#getPayments()'s own doc for when to prefer this over a
     * specific Booking/Reservation's own payment_transactions (which is
     * richer - running totals, receipt linkage - for a single transaction's
     * detail view; this is for a guest-wide "all my payments" ledger, if/
     * when one is built).
     */
    public void refreshGuestPayments(RepositoryCallback<PaymentsResponse> callback) {
        api.getPayments().enqueue(new Callback<PaymentsResponse>() {
            @Override
            public void onResponse(Call<PaymentsResponse> call, Response<PaymentsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (callback != null) callback.onSuccess(response.body());
                } else {
                    if (callback != null) callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<PaymentsResponse> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * Maps the Throwable Retrofit hands to Call#onFailure() into a guest-facing message.
     * onFailure() fires both for real transport failures (no connectivity, timeout, TLS) and
     * for response-body conversion failures (a malformed/unexpected JSON shape) - both are
     * "the app couldn't get a usable response", so neither should ever surface a raw Java
     * exception class/message to the guest.
     */
    private String networkErrorMessage(Throwable t) {
        if (t instanceof java.net.UnknownHostException || t instanceof java.net.ConnectException) {
            return appContext.getString(R.string.error_no_internet_connection);
        }
        if (t instanceof java.net.SocketTimeoutException) {
            return appContext.getString(R.string.error_request_timed_out);
        }
        if (t instanceof javax.net.ssl.SSLException) {
            return appContext.getString(R.string.error_server_unreachable);
        }
        if (t instanceof com.google.gson.JsonParseException || t instanceof java.io.IOException) {
            // A successful-looking response whose body Gson/OkHttp couldn't parse as expected -
            // not a connectivity problem, but still not something to show the guest verbatim.
            return appContext.getString(R.string.error_unexpected_response);
        }
        return appContext.getString(R.string.error_server_unreachable);
    }
}
