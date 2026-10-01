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
import java.util.LinkedHashMap;
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
     * Outcome of {@link #lookupTransaction} - unlike RepositoryCallback it tells "there is no such
     * transaction" (a definite 404 - show a friendly message) apart from "couldn't ask" (offline/server
     * error - offer a retry), which a single error string can't do reliably.
     */
    public interface TransactionLookupCallback {
        void onFound(Booking booking);

        /** Every endpoint tried answered 404: the record was removed or was never this guest's. */
        void onNotFound();

        void onError(String message);
    }

    /** Which table a transaction id belongs to - reservations and direct bookings are separate tables with independent id sequences. */
    public enum TransactionFamily { RESERVATION, DIRECT_BOOKING }

    /**
     * Same-process pub/sub for "genuinely NEW notifications just arrived" (an id newer than every one
     * already known - never the older rows a load-more brings in, never the first load of a session).
     * Lets a screen that shows data a notification is about (Transaction History) refresh that data
     * right away instead of waiting for its own next poll. Always called on the main thread.
     */
    public interface NotificationArrivalListener {
        void onNewNotifications(List<Notification> arrived);
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

    /** Not final only so RoomRepositoryNotificationPersistenceTest can swap in a fake via setApiForTesting() - production code assigns it exactly once, in the constructor. */
    private ApiService api;
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
    /**
     * Growing per_page window for refreshNotifications() - same "grow and
     * refetch as one shot" strategy as bookingsPerPage (see
     * refreshBookingsSplit()'s own doc for why). Deliberately NOT reset by
     * refreshNotifications() itself - every screen extending
     * BaseNavigationActivity calls refreshNotifications() on its own
     * onResume() (refreshNotificationBadge()), and the 30s foreground poll
     * and 15-min background poll both call it too; if any of those reset
     * this back to the default, a guest who scrolled past page 1 would see
     * their loaded-more notifications silently disappear the next time
     * ANY of those fired - not just on this screen, on literally any guest
     * screen. Only reset at the actual account boundary - see
     * clearAccountSpecificCache().
     */
    private int notificationsPerPage = DEFAULT_NOTIFICATIONS_PER_PAGE;
    private static final int DEFAULT_NOTIFICATIONS_PER_PAGE = 200;
    private int lastNotificationsTotal = 0;
    /** The guest's TRUE total unread count, straight from the backend (Api\NotificationController::index()'s unread_count - see PaginatedResponse's own doc) - never derived from however many of `notifications` happen to be loaded client-side, which under-counts once a guest has more unread than fit in one loaded window. Adjusted by exactly +/-1 on every real local read/unread transition (see applyReadStateLocally()) so it never lags a change the guest just made, then overwritten by the next fetch's authoritative value. */
    private int backendUnreadCount = 0;
    /**
     * Notification id -> the read state a still-in-flight mark-read/unread request
     * is trying to reach. A poll/refresh response that lands while such a request is
     * still travelling was computed by the server BEFORE it applied the change, so
     * taking that response at face value would silently flip the row (and the unread
     * count) back to the old state for up to a whole poll interval, even though the
     * request itself then succeeds - see reconcilePendingReadStates(). Entries live
     * only for the duration of the request itself.
     */
    private final Map<String, Boolean> pendingReadStates = new java.util.HashMap<>();
    private final List<BookingsChangedListener> bookingsChangedListeners = new ArrayList<>();
    private final List<NotificationArrivalListener> notificationArrivalListeners = new ArrayList<>();
    /**
     * True once a notifications fetch has succeeded for the current account. Until then the unread count is
     * UNKNOWN rather than zero - the header must not claim "You're all caught up" on a cold start or while
     * offline, which it did when it read the (still empty) cache. Reset at the account boundary.
     */
    private boolean notificationsLoaded = false;
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

    public void addNotificationArrivalListener(NotificationArrivalListener listener) {
        if (!notificationArrivalListeners.contains(listener)) notificationArrivalListeners.add(listener);
    }

    public void removeNotificationArrivalListener(NotificationArrivalListener listener) {
        notificationArrivalListeners.remove(listener);
    }

    private void notifyNotificationsArrived(List<Notification> arrived) {
        if (arrived.isEmpty()) return;
        // Iterate a copy - a listener may unregister itself while handling this.
        for (NotificationArrivalListener listener : new ArrayList<>(notificationArrivalListeners)) {
            listener.onNewNotifications(arrived);
        }
    }

    /**
     * The notifications in {@code fresh} that genuinely just arrived: newer (higher id - the backend's ids
     * are an auto-increment, so monotonic with creation) than everything already known. Empty on the very first
     * load (nothing to be newer than - everything would look "new") and for older rows a load-more pulls in.
     * Non-numeric ids fall back to "not seen before".
     */
    List<Notification> findArrivals(List<Notification> known, List<Notification> fresh) {
        List<Notification> arrived = new ArrayList<>();
        if (!notificationsLoaded) return arrived;
        long maxKnown = Long.MIN_VALUE;
        java.util.Set<String> knownIds = new java.util.HashSet<>();
        for (Notification n : known) {
            knownIds.add(n.getId());
            try {
                maxKnown = Math.max(maxKnown, Long.parseLong(n.getId()));
            } catch (NumberFormatException ignored) {
                // falls back to id membership below
            }
        }
        for (Notification n : fresh) {
            if (knownIds.contains(n.getId())) continue;
            try {
                if (Long.parseLong(n.getId()) > maxKnown) arrived.add(n);
            } catch (NumberFormatException e) {
                arrived.add(n);
            }
        }
        return arrived;
    }

    /** See {@link #notificationsLoaded}: false means the unread count is not known yet, which is not the same as zero. */
    public boolean hasLoadedNotifications() {
        return notificationsLoaded;
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
        // The actual account boundary for both "grow and replace" pagination
        // windows (bookingsPerPage/notificationsPerPage) - a new account
        // logging in has no relationship to how far the PREVIOUS account had
        // scrolled, so both windows go back to their defaults here rather
        // than staying grown (or, worse, than being reset on every ordinary
        // refresh - see each field's own doc for why that would be wrong).
        bookingsPerPage = DEFAULT_BOOKINGS_PER_PAGE;
        notificationsPerPage = DEFAULT_NOTIFICATIONS_PER_PAGE;
        lastReservationsTotal = 0;
        lastDirectBookingsTotal = 0;
        lastNotificationsTotal = 0;
        backendUnreadCount = 0;
        notificationsLoaded = false;
        pendingReadStates.clear();
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

    /** Test-only - seeds the notification cache and the backend-reported unread total, same reason as setRoomsForTesting(). */
    void setNotificationsForTesting(List<Notification> notifications, int backendUnreadCount) {
        this.notifications = notifications != null ? new ArrayList<>(notifications) : new ArrayList<>();
        this.backendUnreadCount = backendUnreadCount;
        this.notificationsLoaded = true;
        this.pendingReadStates.clear();
    }

    /** Test-only - registers an in-flight read/unread request without going through the network, so reconcilePendingReadStates() can be driven directly. */
    void putPendingReadStateForTesting(String notificationId, boolean read) {
        pendingReadStates.put(notificationId, read);
    }

    /**
     * Test-only - swaps the ApiService (a java.lang.reflect.Proxy fake that records each request and
     * lets the test decide when and how it completes) and returns the previous one, so a test can
     * put the shared singleton back exactly as it found it.
     */
    ApiService setApiForTesting(ApiService replacement) {
        ApiService previous = this.api;
        this.api = replacement;
        return previous;
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

    /** Reports both families independently - see refreshBookingsSplit()'s own doc for why. */
    public interface SplitRepositoryCallback {
        /**
         * Always called exactly once per refreshBookingsSplit() call (after the internal
         * retry, if any, has also settled). mergedBookings is the current best-known merged
         * list - freshly updated for whichever side(s) succeeded, still carrying the last
         * known good data for whichever side(s) didn't (see refreshBookingsSplitInternal()'s
         * partial-merge logic). reservationsError/directBookingsError are null exactly when
         * that side succeeded this round.
         */
        void onComplete(List<Booking> mergedBookings, @Nullable String reservationsError, @Nullable String directBookingsError);
    }

    /**
     * Fetches both transaction families that make up "My Bookings" -
     * reservation-derived bookings (guest/reservations, unchanged) and
     * direct "New Booking" transactions (guest/bookings, never a
     * Reservation - see Api\BookingController) - and merges them into one
     * cache. The two are genuinely independent record types server-side, so
     * there's no single endpoint that already returns both together.
     * <p>
     * Combined-result convenience wrapper over refreshBookingsSplit() for the ~6 callers
     * (Dashboard/Calendar/TransactionList/TransactionHistory/BillingSummary/PaymentActivity)
     * that only ever want "the list" and don't need to tell the two families' failures apart -
     * onSuccess() only fires when BOTH sides succeeded, onError() otherwise, matching this
     * method's original contract exactly. BookingAndReservationActivity uses
     * refreshBookingsSplit() directly instead, since its two tabs need to know which specific
     * family failed - see that method's own doc.
     */
    public void refreshBookings(RepositoryCallback<List<Booking>> callback) {
        refreshBookingsSplit((merged, reservationsError, directError) -> {
            if (callback == null) return;
            if (reservationsError == null && directError == null) {
                callback.onSuccess(merged);
            } else {
                callback.onError(reservationsError != null ? reservationsError : directError);
            }
        });
    }

    /**
     * Same two underlying network calls as refreshBookings() above, but reports each family's
     * outcome independently instead of collapsing them into one combined success/failure. A
     * real, live-confirmed failure mode this fixes: the Bookings and Reservations tabs
     * previously shared one all-or-nothing result, so a failure in EITHER call (e.g. one
     * guest's data tripping a parsing error - see AdditionalGuestListDeserializer) hid the
     * OTHER family's perfectly good data too, and the guest had no way to tell "genuinely no
     * bookings" apart from "bookings failed to load, only reservations came back" - both
     * rendered as the exact same generic error/empty state depending on timing. Each family's
     * own cache slice (identified via Booking#isDirectBooking() - see the partial-merge logic
     * below) now only ever gets replaced when THAT family's own fetch actually succeeds; a
     * failure on one side leaves its slice exactly as it was (last known good, or empty if
     * never loaded) while the other side still updates normally.
     */
    public void refreshBookingsSplit(SplitRepositoryCallback callback) {
        refreshBookingsSplitInternal(callback, true, bookingsPerPage, false);
    }

    /**
     * Current per_page window for both refreshBookings() calls - grown by
     * loadMoreBookings(). Deliberately NOT reset on every refreshBookingsSplit()/
     * refreshBookings() call (the original design here) - TransactionHistoryActivity's
     * own onResume() unconditionally calls loadTransactions(true) on every
     * visit, and the 30s foreground poll does too; resetting this window on
     * either would silently discard a guest's loaded-more progress the next
     * time they left and returned to this screen, or every 30 seconds while
     * they stayed on it. Only reset at the actual account boundary - see
     * clearAccountSpecificCache().
     */
    private int bookingsPerPage = DEFAULT_BOOKINGS_PER_PAGE;
    private static final int DEFAULT_BOOKINGS_PER_PAGE = 200;

    /**
     * "Load more" for Transaction History, for the rare guest with more
     * transactions than the default per_page window covers. Rather than a
     * true incremental page fetch (which would mean threading a second,
     * independent pagination cursor through the existing per-family
     * failure-isolation/retry logic above), this simply re-runs the exact
     * same already-correct dual-fetch with a LARGER per_page - since
     * refreshBookingsSplitInternal()'s merge step already fully REPLACES
     * each family's cache slice (removeIf + addAll, never append), asking
     * for a bigger window and replacing is exactly equivalent to "loading
     * more," with zero changes to that delicate merge/retry logic. total
     * from either PaginatedResponse tells the caller (TransactionHistoryActivity)
     * whether there's genuinely more left - see hasMoreBookings().
     */
    public void loadMoreBookings(SplitRepositoryCallback callback) {
        bookingsPerPage += DEFAULT_BOOKINGS_PER_PAGE;
        refreshBookingsSplitInternal(callback, true, bookingsPerPage, false);
    }

    private int lastReservationsTotal = 0;
    private int lastDirectBookingsTotal = 0;

    /** True when the last fetch's own reported totals exceed the current per_page window on either side - i.e. loadMoreBookings() would actually return something new. */
    public boolean hasMoreBookings() {
        return lastReservationsTotal > bookingsPerPage || lastDirectBookingsTotal > bookingsPerPage;
    }

    /**
     * Lightweight "check for changes" poll for Transaction History - fetches
     * only DEFAULT_BOOKINGS_PER_PAGE of each family (never the possibly-much-
     * larger bookingsPerPage a guest may have scrolled to) and MERGES the
     * result into `bookings` by id (mergeMode=true below) instead of
     * replacing each family's whole cache slice. Used by every "automatic"
     * trigger (the 30s foreground timer, the 15-min background worker) -
     * none of those represent the guest asking for a fresh reload, so none
     * should pay for, or risk disturbing the guest's scroll position with, a
     * full re-fetch of however large bookingsPerPage has grown to.
     * refreshBookingsSplit()/loadMoreBookings() remain the "fresh load"
     * path (initial screen load, pull-to-refresh, onResume, and the load-
     * more growth itself) - see mergeBookingFamily()'s own doc for the
     * one real limitation this trades away (a status change on a booking
     * OLDER than this page isn't caught by the poll, only by a full refresh).
     */
    public void pollBookingsSplit(SplitRepositoryCallback callback) {
        refreshBookingsSplitInternal(callback, true, DEFAULT_BOOKINGS_PER_PAGE, true);
    }

    public void pollBookings(RepositoryCallback<List<Booking>> callback) {
        pollBookingsSplit((merged, reservationsError, directError) -> {
            if (callback == null) return;
            if (reservationsError == null && directError == null) {
                callback.onSuccess(merged);
            } else {
                callback.onError(reservationsError != null ? reservationsError : directError);
            }
        });
    }

    /**
     * Merges a freshly-polled family page into `bookings` by id: an id
     * already present gets its entry replaced AT ITS EXISTING INDEX (never
     * moved, so a RecyclerView bound to this list never sees an unrelated
     * row jump position or the list get rebuilt from scratch); an id not
     * yet present is appended (display order is re-sorted by date downstream
     * anyway - see TransactionHistoryActivity#buildPaymentTransactions() -
     * so append position doesn't matter here the way it would for
     * notifications, which have no such re-sort). A booking whose status
     * changed on the server but that isn't in this page (older than
     * DEFAULT_BOOKINGS_PER_PAGE) keeps its stale in-memory copy until the
     * next full refresh - the same accepted trade-off mergeNotifications()
     * documents, for the same reason (a real-time push would be needed to
     * close this gap completely; polling can only check what it actually
     * fetched).
     */
    private void mergeBookingFamily(List<Booking> fetched, boolean directFamily) {
        for (Booking fresh : fetched) {
            int existingIndex = -1;
            for (int i = 0; i < bookings.size(); i++) {
                Booking existing = bookings.get(i);
                if (existing.isDirectBooking() == directFamily && existing.getId().equals(fresh.getId())) {
                    existingIndex = i;
                    break;
                }
            }
            if (existingIndex >= 0) {
                bookings.set(existingIndex, fresh);
            } else {
                bookings.add(fresh);
            }
        }
    }

    private void refreshBookingsSplitInternal(SplitRepositoryCallback callback, boolean allowRetry, int perPage, boolean mergeMode) {
        // Correlates this one refresh (both sub-calls) across the Android log and, via the
        // X-Request-Id header below, the backend's own log - see DiagnosticLog's own doc.
        // A fresh id every call (including the internal retry) is deliberate: a retry is a
        // genuinely new HTTP request, not a resend of the same one, and giving it its own id
        // avoids conflating "the first attempt's server-side log line" with "the retry's".
        final String requestId = com.example.velocitysuites.network.DiagnosticLog.newRequestId("BOOKINGS_LOAD");
        com.example.velocitysuites.network.DiagnosticLog.d("refreshBookings.start",
                "requestId=" + requestId + " hasToken=" + (com.example.velocitysuites.network.SessionManager.getToken(appContext) != null)
                        + " allowRetry=" + allowRetry + " accountGeneration=" + accountGeneration);
        logRuntimeIdentityIfDebug(requestId);

        List<Booking> reservationDerived = new ArrayList<>();
        List<Booking> direct = new ArrayList<>();
        List<Booking> historicalReservations = new ArrayList<>();
        boolean[] reservationsFailed = {false};
        boolean[] directFailed = {false};
        // Real reason each call failed, kept SEPARATE per family (previously one shared
        // "whichever failed first" string, back when only a single combined error was ever
        // reported) - see errorMessage(response)/networkErrorMessage()'s own docs for why
        // these are trusted over a bare generic string (e.g. a 401 here still correctly
        // triggers forceSessionExpiredLogout() as a side effect either way).
        String[] reservationsErrorMsg = {null};
        String[] directErrorMsg = {null};
        int[] remaining = {2};
        // Captured before either network call fires - see accountGeneration's own doc.
        final int requestGeneration = accountGeneration;

        Runnable finish = () -> {
            if (requestGeneration != accountGeneration) {
                // A logout (and possibly a different account's own login) happened
                // while this request was in flight - this response belongs to
                // whichever account started it, not to whoever is signed in now.
                // Silently dropped rather than reported: from the CURRENT
                // account's perspective nothing actually failed, there's simply
                // nothing to report from a request they never made.
                com.example.velocitysuites.network.DiagnosticLog.w("refreshBookings.staleDropped",
                        "requestId=" + requestId + " requestGeneration=" + requestGeneration + " currentGeneration=" + accountGeneration);
                return;
            }
            if ((reservationsFailed[0] || directFailed[0]) && allowRetry) {
                // Retry the WHOLE pair once, not just the failed side - mirrors this
                // codebase's existing one-retry convention (e.g.
                // PaymentActivity#refreshBookingsAfterPayment()) and keeps the retry logic
                // simple; the side that already succeeded just re-fetches redundantly, which
                // is harmless (a second, quick, already-warm request), not incorrect.
                com.example.velocitysuites.network.DiagnosticLog.w("refreshBookings.partialOrFullFailure.retrying",
                        "requestId=" + requestId + " reservationsFailed=" + reservationsFailed[0] + " directFailed=" + directFailed[0]);
                refreshBookingsSplitInternal(callback, false, perPage, mergeMode);
                return;
            }
            // Partial merge: each family's own slice of `bookings` (identified by
            // isDirectBooking() - true only for getDirectBookings()-sourced items, false for
            // every getReservations()-sourced item regardless of conversion status) is only
            // ever touched when THAT family's own fetch just succeeded. A failure on one
            // side leaves its slice - and therefore that tab's data - exactly as it was;
            // it never blocks or hides the other side's fresh data. mergeMode (pollBookingsSplit())
            // merges by id instead of replacing the whole slice - see mergeBookingFamily()'s own doc.
            if (!reservationsFailed[0]) {
                if (mergeMode) {
                    mergeBookingFamily(reservationDerived, false);
                } else {
                    bookings.removeIf(b -> !b.isDirectBooking());
                    bookings.addAll(reservationDerived);
                }
                completedHistoricalReservations = historicalReservations;
                // Fresh Booking objects from the server are room-cost-only again until
                // corrected - see correctPendingReservationTotal(). Only this family's own
                // ids are relevant - direct bookings and already-converted reservations never
                // enter correctedTotalIds in the first place (see
                // correctPendingReservationTotals()'s own guard).
                for (Booking b : reservationDerived) correctedTotalIds.remove(b.getId());
            }
            if (!directFailed[0]) {
                if (mergeMode) {
                    mergeBookingFamily(direct, true);
                } else {
                    bookings.removeIf(Booking::isDirectBooking);
                    bookings.addAll(direct);
                }
            }
            boolean anySucceeded = !reservationsFailed[0] || !directFailed[0];
            if (anySucceeded) {
                logRoomsShapeIfDebug(bookings);
                notifyBookingsChanged();
            }
            com.example.velocitysuites.network.DiagnosticLog.d("refreshBookings.settled",
                    "requestId=" + requestId + " reservationsOk=" + !reservationsFailed[0] + " directOk=" + !directFailed[0]
                            + " reservationDerivedCount=" + reservationDerived.size() + " directCount=" + direct.size()
                            + " totalCached=" + bookings.size() + " ids=" + bookingIdsForLog(bookings));
            if (callback != null) {
                callback.onComplete(
                        getBookings(),
                        reservationsFailed[0] ? (reservationsErrorMsg[0] != null ? reservationsErrorMsg[0] : "Failed to load reservations.") : null,
                        directFailed[0] ? (directErrorMsg[0] != null ? directErrorMsg[0] : "Failed to load bookings.") : null);
            }
        };

        api.getReservations(perPage, requestId).enqueue(new Callback<PaginatedResponse<ReservationDto>>() {
            @Override
            public void onResponse(Call<PaginatedResponse<ReservationDto>> call, Response<PaginatedResponse<ReservationDto>> response) {
                com.example.velocitysuites.network.DiagnosticLog.d("refreshBookings.reservations.response",
                        "requestId=" + requestId + " httpCode=" + response.code() + " successful=" + response.isSuccessful()
                                + " recordCount=" + (response.body() != null ? response.body().data.size() : -1));
                if (response.isSuccessful() && response.body() != null) {
                    lastReservationsTotal = response.body().total;
                    for (ReservationDto dto : response.body().data) {
                        // A single malformed/unexpected record must never take down the
                        // WHOLE list - see State F (JSON/model parsing failure) in the
                        // task spec. Before this try/catch, ApiMapper#toBooking() throwing
                        // for even one dto (e.g. an unanticipated null shape) propagated
                        // straight out of this Retrofit callback - which runs on the main
                        // thread - and crashed the entire app with zero diagnostic, while
                        // looking to the guest exactly like "Bookings/Reservations
                        // sometimes doesn't show" depending on which record tripped it.
                        try {
                            reservationDerived.add(ApiMapper.toBooking(dto));
                            if (dto.booking != null) {
                                historicalReservations.add(ApiMapper.toHistoricalReservation(dto));
                            }
                        } catch (RuntimeException mappingError) {
                            com.example.velocitysuites.network.DiagnosticLog.e("refreshBookings.reservations.mappingFailed",
                                    "requestId=" + requestId + " reservationId=" + dto.id, mappingError);
                        }
                    }
                } else {
                    reservationsFailed[0] = true;
                    reservationsErrorMsg[0] = errorMessage(response);
                }
                if (--remaining[0] <= 0) finish.run();
            }

            @Override
            public void onFailure(Call<PaginatedResponse<ReservationDto>> call, Throwable t) {
                com.example.velocitysuites.network.DiagnosticLog.e("refreshBookings.reservations.failure", "requestId=" + requestId, t);
                reservationsFailed[0] = true;
                reservationsErrorMsg[0] = networkErrorMessage(appContext, t);
                if (--remaining[0] <= 0) finish.run();
            }
        });

        api.getDirectBookings(perPage, requestId).enqueue(new Callback<PaginatedResponse<DirectBookingResponseDto>>() {
            @Override
            public void onResponse(Call<PaginatedResponse<DirectBookingResponseDto>> call, Response<PaginatedResponse<DirectBookingResponseDto>> response) {
                com.example.velocitysuites.network.DiagnosticLog.d("refreshBookings.directBookings.response",
                        "requestId=" + requestId + " httpCode=" + response.code() + " successful=" + response.isSuccessful()
                                + " recordCount=" + (response.body() != null ? response.body().data.size() : -1));
                if (response.isSuccessful() && response.body() != null) {
                    lastDirectBookingsTotal = response.body().total;
                    for (DirectBookingResponseDto dto : response.body().data) {
                        // Same State-F protection as the reservations loop above.
                        try {
                            direct.add(ApiMapper.toBooking(dto));
                        } catch (RuntimeException mappingError) {
                            com.example.velocitysuites.network.DiagnosticLog.e("refreshBookings.directBookings.mappingFailed",
                                    "requestId=" + requestId + " bookingId=" + dto.id, mappingError);
                        }
                    }
                } else {
                    directFailed[0] = true;
                    directErrorMsg[0] = errorMessage(response);
                }
                if (--remaining[0] <= 0) finish.run();
            }

            @Override
            public void onFailure(Call<PaginatedResponse<DirectBookingResponseDto>> call, Throwable t) {
                com.example.velocitysuites.network.DiagnosticLog.e("refreshBookings.directBookings.failure", "requestId=" + requestId, t);
                directFailed[0] = true;
                directErrorMsg[0] = networkErrorMessage(appContext, t);
                if (--remaining[0] <= 0) finish.run();
            }
        });
    }

    /** Non-sensitive booking/reservation ids only - see DiagnosticLog's own "never log tokens/payment secrets" contract. */
    private static String bookingIdsForLog(List<Booking> bookings) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bookings.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(bookings.get(i).getId());
        }
        return sb.append("]").toString();
    }

    /**
     * Debug-build-only runtime identity confirmation (task spec "Verify Runtime Identity") -
     * fired in PARALLEL with, never blocking, the real bookings/reservations calls above, so
     * this adds zero latency to the actual list load; it exists purely so a Logcat read during
     * testing can directly confirm "this refresh's data actually came back for guest id X",
     * tagged with the same requestId as the bookings/reservations calls it's confirming. This
     * is diagnostic only - the backend already derives ownership solely from the Bearer token
     * server-side (see BookingController/ReservationController::index()), and this method never
     * sends a guest id anywhere; it only reads one back to log it.
     */
    private void logRuntimeIdentityIfDebug(String correlationRequestId) {
        if (!com.example.velocitysuites.BuildConfig.DEBUG) return;
        api.getProfile(correlationRequestId).enqueue(new Callback<com.example.velocitysuites.network.dto.ProfileResponse>() {
            @Override
            public void onResponse(Call<com.example.velocitysuites.network.dto.ProfileResponse> call, Response<com.example.velocitysuites.network.dto.ProfileResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().user != null) {
                    com.example.velocitysuites.network.DiagnosticLog.d("identityCheck.confirmed",
                            "requestId=" + correlationRequestId + " authenticatedUserId=" + response.body().user.id);
                } else {
                    com.example.velocitysuites.network.DiagnosticLog.w("identityCheck.failed",
                            "requestId=" + correlationRequestId + " httpCode=" + response.code());
                }
            }

            @Override
            public void onFailure(Call<com.example.velocitysuites.network.dto.ProfileResponse> call, Throwable t) {
                com.example.velocitysuites.network.DiagnosticLog.e("identityCheck.networkFailure", "requestId=" + correlationRequestId, t);
            }
        });
    }

    /**
     * Re-fetches the current notificationsPerPage window in one shot and
     * REPLACES `notifications` with it - this is both the "normal refresh"
     * every screen/poller calls AND, via loadMoreNotifications() growing
     * the window first, the "load more" mechanism. Deliberately does not
     * reset notificationsPerPage itself - see that field's own doc for why
     * (every BaseNavigationActivity's onResume calls this).
     */
    public void refreshNotifications(RepositoryCallback<List<Notification>> callback) {
        // See accountGeneration's own doc - captured before the network call fires.
        final int requestGeneration = accountGeneration;
        api.getNotifications(notificationsPerPage).enqueue(new Callback<PaginatedResponse<NotificationDto>>() {
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
                    int unreadCount = reconcilePendingReadStates(mapped, response.body().unread_count);
                    List<Notification> arrived = findArrivals(notifications, mapped);
                    notifications = mapped;
                    lastNotificationsTotal = response.body().total;
                    backendUnreadCount = unreadCount;
                    notificationsLoaded = true;
                    // Single hook point for the whole app: every screen that refreshes
                    // notifications (dashboard, the Notification Module, the background
                    // poll worker) posts real system alerts for genuinely new/unread rows
                    // through here - see NotificationHelper for the dedup/grouping rules.
                    NotificationHelper.maybeAlertNewNotifications(appContext, mapped);
                    notifyNotificationsArrived(arrived);
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

    /** True when the guest's real total exceeds the currently-loaded window - see NotificationActivity's scroll-near-bottom trigger. */
    public boolean hasMoreNotifications() {
        return lastNotificationsTotal > notificationsPerPage;
    }

    /** See backendUnreadCount's own doc - 0 until the first successful refreshNotifications()/pollNotifications() call of this app session. */
    public int getBackendUnreadCount() {
        return backendUnreadCount;
    }

    /**
     * The one unread count every badge/counter should show - the header bell badge on
     * every guest screen, the Notifications screen's own header, and its filter
     * dropdown. backendUnreadCount already counts unread rows beyond the loaded window;
     * the loaded-window count is the floor it can never legitimately fall below (the
     * backend caches its total for a few seconds, so a brand-new unread row a poll just
     * delivered can briefly outrun it). Taking the larger of the two means the number
     * never undercounts rows the guest can literally see, and - because every local
     * read/unread change goes through applyReadStateLocally() - never lags a change the
     * guest just made either.
     */
    public int getUnreadNotificationCount() {
        int loadedUnread = 0;
        for (Notification n : notifications) {
            if (!n.isRead()) loadedUnread++;
        }
        return Math.max(backendUnreadCount, loadedUnread);
    }

    /**
     * Flips one cached notification's read flag and, only when that is a real
     * transition, moves backendUnreadCount by exactly one - so the count stays
     * correct however many times (or from however many racing code paths) the same
     * target state is applied. Id-based rather than taking a Notification: a poll may
     * have replaced the cached object with a fresh copy since a caller last held it.
     *
     * @return true if the row is in the cache and its state actually changed.
     */
    boolean applyReadStateLocally(String notificationId, boolean read) {
        if (notificationId == null) return false;
        for (Notification n : notifications) {
            if (notificationId.equals(n.getId())) {
                if (n.isRead() == read) return false;
                n.setRead(read);
                backendUnreadCount = Math.max(0, backendUnreadCount + (read ? -1 : 1));
                return true;
            }
        }
        return false;
    }

    /**
     * Re-applies every still-in-flight mark-read/unread change (pendingReadStates)
     * over a freshly fetched page BEFORE it replaces/merges into the cache, and
     * returns the unread total to store alongside it. A pending row that comes back
     * with its OLD state means the server answered before it processed our request:
     * the row is forced to the requested state and the server's own count - which still
     * counts that row the old way - is corrected by one to match. A row already showing
     * the requested state needs nothing: the request landed first. A no-op (returns
     * serverUnreadCount unchanged) whenever nothing is in flight, which is the
     * overwhelmingly common case. Package-private so the RoomRepositoryNotification*Test
     * classes can drive it directly.
     */
    int reconcilePendingReadStates(List<Notification> fresh, int serverUnreadCount) {
        if (pendingReadStates.isEmpty()) return serverUnreadCount;
        int adjusted = serverUnreadCount;
        for (Notification n : fresh) {
            Boolean wanted = pendingReadStates.get(n.getId());
            if (wanted == null || n.isRead() == wanted) continue;
            n.setRead(wanted);
            adjusted += wanted ? -1 : 1;
        }
        return Math.max(0, adjusted);
    }

    /**
     * Grows the per_page window and re-fetches it in one shot (same "grow
     * and replace" strategy as loadMoreBookings() - see that method's own
     * doc for why this is preferred over threading a second, independent
     * pagination cursor through refreshNotifications()). A no-op (immediate
     * onSuccess with the unchanged list) when hasMoreNotifications() is
     * already false, so a careless extra call from the UI can't fetch past
     * the real total.
     */
    public void loadMoreNotifications(RepositoryCallback<List<Notification>> callback) {
        if (!hasMoreNotifications()) {
            if (callback != null) callback.onSuccess(getNotifications());
            return;
        }
        notificationsPerPage += DEFAULT_NOTIFICATIONS_PER_PAGE;
        refreshNotifications(callback);
    }

    /**
     * Lightweight "check for changes" poll - fetches only
     * DEFAULT_NOTIFICATIONS_PER_PAGE (never the possibly-much-larger
     * notificationsPerPage a guest may have scrolled to) and MERGES the
     * result into `notifications` (see mergeNotifications()) instead of
     * replacing the whole list. Used by every "automatic" trigger (the 30s
     * foreground timer, the 15-min background worker, and every screen's
     * onResume badge refresh via BaseNavigationActivity) - none of those
     * represent the guest asking for a fresh reload, so none should pay
     * for, or risk disturbing the guest's scroll position with, a full
     * re-fetch of however large notificationsPerPage has grown to.
     * refreshNotifications() remains the "fresh load" path (initial screen
     * load, pull-to-refresh, and loadMoreNotifications()'s own growing
     * fetch) - it still fetches and replaces the FULL current window, since
     * those really are "start over" moments.
     */
    public void pollNotifications(RepositoryCallback<List<Notification>> callback) {
        final int requestGeneration = accountGeneration;
        api.getNotifications(DEFAULT_NOTIFICATIONS_PER_PAGE).enqueue(new Callback<PaginatedResponse<NotificationDto>>() {
            @Override
            public void onResponse(Call<PaginatedResponse<NotificationDto>> call, Response<PaginatedResponse<NotificationDto>> response) {
                if (requestGeneration != accountGeneration) return;
                if (response.isSuccessful() && response.body() != null) {
                    List<Notification> fetched = new ArrayList<>();
                    for (NotificationDto dto : response.body().data) {
                        fetched.add(ApiMapper.toNotification(dto));
                    }
                    int unreadCount = reconcilePendingReadStates(fetched, response.body().unread_count);
                    List<Notification> arrived = findArrivals(notifications, fetched);
                    mergeNotifications(fetched);
                    lastNotificationsTotal = response.body().total;
                    backendUnreadCount = unreadCount;
                    notificationsLoaded = true;
                    NotificationHelper.maybeAlertNewNotifications(appContext, fetched);
                    notifyNotificationsArrived(arrived);
                    if (callback != null) callback.onSuccess(getNotifications());
                } else {
                    if (callback != null) callback.onError("Failed to check for new notifications.");
                }
            }

            @Override
            public void onFailure(Call<PaginatedResponse<NotificationDto>> call, Throwable t) {
                if (callback != null) callback.onError(networkErrorMessage(t));
            }
        });
    }

    /**
     * Merges a freshly-polled page into the existing `notifications` list:
     * an id already present has its entry replaced AT ITS EXISTING INDEX
     * (never moved - so a NotificationAdapter bound to this list never sees
     * an unrelated row jump position, and no full-list rebuild is needed
     * just to reflect one row's is_read flag changing on another device);
     * an id not yet present is genuinely new and inserted at the front,
     * matching the backend's own newest-first order (NotificationController::index()'s
     * latest() query sorts by created_at, so a brand-new row always belongs
     * before every already-loaded one).
     * <p>
     * Real, accepted limitation: a status change to a notification OLDER
     * than this page (e.g. read on another device long after creation)
     * isn't caught by this poll, only by a full refreshNotifications() call -
     * this method only ever sees what it actually fetched. In practice this
     * is an unlikely combination (DEFAULT_NOTIFICATIONS_PER_PAGE is a lot of
     * history for a hotel guest), and this device's OWN read/unread taps are
     * already reflected instantly via their own optimistic update
     * (setNotificationReadState()) - they never wait on this poll at all, and
     * one still in flight when this poll's response lands is re-applied over it
     * by the caller (reconcilePendingReadStates()) before this method runs.
     */
    private void mergeNotifications(List<Notification> fetched) {
        for (Notification fresh : fetched) {
            int existingIndex = -1;
            for (int i = 0; i < notifications.size(); i++) {
                if (notifications.get(i).getId().equals(fresh.getId())) {
                    existingIndex = i;
                    break;
                }
            }
            if (existingIndex >= 0) {
                notifications.set(existingIndex, fresh);
            } else {
                notifications.add(0, fresh);
            }
        }
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
     * Creates a "New Reservation" transaction covering every selected room
     * type/quantity/amenity, same shape as createReservation(List, ...)
     * above, PLUS submits real GCash payment as part of this same atomic
     * request (reference number, mobile number, receipt image, amount).
     * No longer called by any caller as of 2026-09-28 (later same day) -
     * Step8ReviewPaymentFragment's Confirm Reservation now always uses plain
     * createReservation(List, ...) instead, per a product decision that
     * Confirm Reservation must never redirect into the payment workflow (see
     * PaymentActivity#EXTRA_PENDING_RESERVATION's docblock). Kept in place,
     * not removed, in case GCash-at-creation for Reservations is revived: the
     * resulting Reservation would NOT auto-convert into a Booking - see
     * Api\ReservationController::store()'s own doc: this deliberately does
     * not go through submitGcashPayment()/Api\PaymentController::store(),
     * which is what actually triggers
     * ReservationWorkflowService::tryAutoConvert(). The Reservation stays a
     * Reservation, with its payment sitting "Pending Verification" directly
     * against it - ApiMapper already reads a pre-conversion Reservation's
     * own payments array this exact way.
     */
    public void createReservationWithPayment(List<List<Room>> roomGroups, Calendar checkIn, Calendar checkOut, int adults, int children,
                                              String guestFirstName, String guestMiddleName, String guestLastName,
                                              String idCardType, List<BookingAndReservationActivity.AdditionalGuest> additionalGuests,
                                              List<AddOnAmenity> amenities, String referenceNumber, String gcashNumber, Uri receiptUri,
                                              double amountPaid, Integer selectedPaymentPercentage, @Nullable String idempotencyKey,
                                              RepositoryCallback<Booking> callback) {
        fileIoExecutor.execute(() -> {
            File receiptFile;
            try {
                receiptFile = copyUriToTempFile(receiptUri, "gcash_receipt_");
            } catch (IOException e) {
                if (callback != null) mainHandler().post(() -> callback.onError("Couldn't read the selected image."));
                return;
            }

            // LinkedHashMap, NOT HashMap - a plain HashMap does not preserve insertion
            // order, so multipart fields for additional_guests[0][*], additional_guests[1][*],
            // etc. could be written to the wire in hash-bucket order instead of index order
            // whenever there were 2+ additional guests. PHP's request parser builds
            // $request->input('additional_guests') in the order fields actually arrive on
            // the wire - out-of-order arrival produced a PHP array like ['1' => ..., '0' =>
            // ...] (keys 0/1 present, but not in that order), which json_encode() then
            // serializes as a JSON OBJECT ({"1":...,"0":...}) instead of a JSON ARRAY,
            // because PHP's list-detection requires the keys in EXACTLY 0..n-1 order, not
            // just that value set. Android's own additional_guest_details field is declared
            // List<AdditionalGuestDto> - Gson throws (Expected BEGIN_ARRAY but was
            // BEGIN_OBJECT) trying to parse a JSON object into a List, which fails Retrofit's
            // response conversion entirely (onFailure(), not onResponse()) and took down the
            // WHOLE combined bookings+reservations refresh for any guest who had ever
            // submitted 2+ additional guests through this multipart path - confirmed live via
            // 4 real corrupted booking rows (see repair script). A LinkedHashMap here
            // guarantees fields are written in insertion (== index) order, so this can never
            // happen again for a NEW submission; see BookingController/ReservationController's
            // array_values() normalization (backend) for the defense-in-depth fix that also
            // covers any other client/source, plus the one-time repair of already-affected rows.
            Map<String, RequestBody> fields = new LinkedHashMap<>();
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
            if (idCardType != null && !idCardType.equals("None")) {
                putText(fields, "id_card_type", idCardType);
            }
            putText(fields, "payment_method", "gcash");
            putText(fields, "reference_number", referenceNumber != null ? referenceNumber : "");
            putText(fields, "gcash_number", gcashNumber != null ? gcashNumber : "");
            putText(fields, "amount_paid", String.valueOf(amountPaid));
            if (selectedPaymentPercentage != null) {
                putText(fields, "selected_payment_percentage", String.valueOf(selectedPaymentPercentage));
            }
            if (idempotencyKey != null) {
                putText(fields, "idempotency_key", idempotencyKey);
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

            final File finalReceiptFile = receiptFile;
            RequestBody fileBody = RequestBody.create(finalReceiptFile, MediaType.parse("image/*"));
            MultipartBody.Part receiptPart = MultipartBody.Part.createFormData("receipt", finalReceiptFile.getName(), fileBody);

            api.createReservationWithPayment(fields, receiptPart).enqueue(new Callback<ReservationDto>() {
                @Override
                public void onResponse(Call<ReservationDto> call, Response<ReservationDto> response) {
                    finalReceiptFile.delete();
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
                    finalReceiptFile.delete();
                    reconcileAfterFailure(referenceNumber, t, callback);
                }
            });
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

            // LinkedHashMap, NOT HashMap - a plain HashMap does not preserve insertion
            // order, so multipart fields for additional_guests[0][*], additional_guests[1][*],
            // etc. could be written to the wire in hash-bucket order instead of index order
            // whenever there were 2+ additional guests. PHP's request parser builds
            // $request->input('additional_guests') in the order fields actually arrive on
            // the wire - out-of-order arrival produced a PHP array like ['1' => ..., '0' =>
            // ...] (keys 0/1 present, but not in that order), which json_encode() then
            // serializes as a JSON OBJECT ({"1":...,"0":...}) instead of a JSON ARRAY,
            // because PHP's list-detection requires the keys in EXACTLY 0..n-1 order, not
            // just that value set. Android's own additional_guest_details field is declared
            // List<AdditionalGuestDto> - Gson throws (Expected BEGIN_ARRAY but was
            // BEGIN_OBJECT) trying to parse a JSON object into a List, which fails Retrofit's
            // response conversion entirely (onFailure(), not onResponse()) and took down the
            // WHOLE combined bookings+reservations refresh for any guest who had ever
            // submitted 2+ additional guests through this multipart path - confirmed live via
            // 4 real corrupted booking rows (see repair script). A LinkedHashMap here
            // guarantees fields are written in insertion (== index) order, so this can never
            // happen again for a NEW submission; see BookingController/ReservationController's
            // array_values() normalization (backend) for the defense-in-depth fix that also
            // covers any other client/source, plus the one-time repair of already-affected rows.
            Map<String, RequestBody> fields = new LinkedHashMap<>();
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
                               Integer selectedPaymentPercentage, String idempotencyKey, RepositoryCallback<Void> callback) {
        PaymentRequest request = new PaymentRequest(paymentMethod, paymentType, referenceNumber, amount);
        request.selected_payment_percentage = selectedPaymentPercentage;
        request.idempotency_key = idempotencyKey;
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
                                    String gcashNumber, Uri receiptUri, Integer selectedPaymentPercentage, String idempotencyKey, RepositoryCallback<Void> callback) {
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
            RequestBody idempotencyKeyBody = RequestBody.create(idempotencyKey != null ? idempotencyKey : "", MediaType.parse("text/plain"));
            RequestBody fileBody = RequestBody.create(uploadFile, MediaType.parse("image/*"));
            MultipartBody.Part receiptPart = MultipartBody.Part.createFormData("receipt", uploadFile.getName(), fileBody);

            api.submitGcashPayment(reservationId, textBody, typeBody, refBody, amountBody, gcashNumberBody, percentageBody, idempotencyKeyBody, receiptPart)
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

    /** Same as {@link #setNotificationReadState(String, boolean, java.util.function.Consumer)} with read=true - kept as its own entry point for callers that only ever mark read (e.g. the dashboard's "View Details"). */
    public void markNotificationAsRead(String notificationId, java.util.function.Consumer<Boolean> onDone) {
        setNotificationReadState(notificationId, true, onDone);
    }

    /** Same as {@link #setNotificationReadState(String, boolean, java.util.function.Consumer)} with read=false. */
    public void markNotificationAsUnread(String notificationId, java.util.function.Consumer<Boolean> onDone) {
        setNotificationReadState(notificationId, false, onDone);
    }

    /**
     * Marks one notification read or unread, persisted server-side (PUT
     * notifications/{id}/read | /unread - ownership-checked, and the backend busts its
     * cached unread total on both). The change is applied to the local cache
     * IMMEDIATELY (before the request resolves) so every screen reading the cache - the
     * row itself, the header bell badge, the filter counts - reflects it on its very
     * next repaint, then confirmed in the background: a failed request reverts exactly
     * this one change. While it is in flight the requested state is remembered in
     * pendingReadStates, so a poll/refresh that lands in that window cannot flip the row
     * back (see reconcilePendingReadStates()).
     * <p>
     * Always sends the request, even when the cache already shows the target state -
     * the PUTs are idempotent, and "the cache says so" is not the same as "the server
     * has been told". Callers that want to skip a redundant request (e.g. tapping an
     * already-read row) check isRead() themselves first.
     *
     * @param onDone receives true only if the server actually confirmed the change; false
     *               means the local change was reverted.
     */
    public void setNotificationReadState(String notificationId, boolean read, java.util.function.Consumer<Boolean> onDone) {
        final boolean previousState = !read;
        final boolean changedLocally = applyReadStateLocally(notificationId, read);
        pendingReadStates.put(notificationId, read);

        Call<NotificationDto> request = read
                ? api.markNotificationRead(notificationId)
                : api.markNotificationUnread(notificationId);
        request.enqueue(new Callback<NotificationDto>() {
            @Override
            public void onResponse(Call<NotificationDto> call, Response<NotificationDto> response) {
                finishReadStateRequest(notificationId, read, previousState, changedLocally, response.isSuccessful(), onDone);
            }

            @Override
            public void onFailure(Call<NotificationDto> call, Throwable t) {
                finishReadStateRequest(notificationId, read, previousState, changedLocally, false, onDone);
            }
        });
    }

    private void finishReadStateRequest(String notificationId, boolean requestedState, boolean previousState,
                                        boolean changedLocally, boolean success,
                                        java.util.function.Consumer<Boolean> onDone) {
        Boolean stillPending = pendingReadStates.get(notificationId);
        // A LATER request for this same row (a quick second toggle) has replaced our
        // entry - that request now owns the row's state, so this earlier one must
        // neither clear its protection nor revert underneath it.
        boolean superseded = stillPending != null && stillPending != requestedState;
        if (!superseded) {
            pendingReadStates.remove(notificationId);
            if (!success && changedLocally) {
                applyReadStateLocally(notificationId, previousState);
            }
        }
        if (onDone != null) onDone.accept(success);
    }

    /**
     * Marks every notification read - one-way, so confirmed by the caller first (see
     * NotificationActivity#confirmMarkAllRead()). Optimistic exactly like
     * setNotificationReadState(): every currently-unread cached row and the unread total
     * flip at once, remembering precisely which rows that was so a failure reverts those
     * and never a row that was already read beforehand.
     *
     * @param onDone receives true only if the server actually confirmed the change.
     */
    public void markAllNotificationsAsRead(java.util.function.Consumer<Boolean> onDone) {
        final List<String> flippedIds = new ArrayList<>();
        for (Notification n : notifications) {
            if (!n.isRead()) {
                n.setRead(true);
                flippedIds.add(n.getId());
                pendingReadStates.put(n.getId(), true);
            }
        }
        // Unread rows beyond the loaded window (counted only by the backend total) are
        // read now too, so the whole total goes to zero - remembered so a failure can put
        // the not-loaded remainder back as well.
        final int unreadBeyondLoadedWindow = Math.max(0, backendUnreadCount - flippedIds.size());
        backendUnreadCount = 0;

        api.markAllNotificationsRead().enqueue(new Callback<ApiMessage>() {
            @Override
            public void onResponse(Call<ApiMessage> call, Response<ApiMessage> response) {
                finishMarkAll(flippedIds, unreadBeyondLoadedWindow, response.isSuccessful(), onDone);
            }

            @Override
            public void onFailure(Call<ApiMessage> call, Throwable t) {
                finishMarkAll(flippedIds, unreadBeyondLoadedWindow, false, onDone);
            }
        });
    }

    private void finishMarkAll(List<String> flippedIds, int unreadBeyondLoadedWindow, boolean success,
                               java.util.function.Consumer<Boolean> onDone) {
        for (String id : flippedIds) {
            Boolean stillPending = pendingReadStates.get(id);
            // Same "a later request owns the row" rule as finishReadStateRequest().
            boolean superseded = stillPending != null && !stillPending;
            if (superseded) continue;
            pendingReadStates.remove(id);
            if (!success) applyReadStateLocally(id, false);
        }
        if (!success) backendUnreadCount += unreadBeyondLoadedWindow;
        if (onDone != null) onDone.accept(success);
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
     * An EXACT check-in/check-out match against another active stay is
     * deliberately NOT reported as a conflict (a guest may create multiple
     * Bookings/Reservations using the same dates, as long as room
     * availability and every other condition is independently satisfied) -
     * only a genuinely different, overlapping range is returned. This
     * exclusion happens per-record inside the loop below, not by inspecting
     * whichever single record this method happens to return: `bookings` is
     * an unordered merged cache, so if a guest has both an exact-duplicate
     * stay and a separate, genuinely-overlapping stay on file, only
     * filtering exact matches out of every candidate (rather than picking
     * one arbitrary match and asking whether that one happened to be exact)
     * guarantees the real overlap still gets returned regardless of
     * iteration order.
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
                if (isGenuineOverlap(newIn, newOut, existingIn, existingOut)) {
                    return b;
                }
            } catch (Exception ignored) {
                // Unparseable dates on a record can't be compared - don't block on them.
            }
        }
        return null;
    }

    /**
     * True for a genuine, non-identical date-range overlap; false for an exact
     * check-in/check-out match (deliberately not a conflict - see
     * findConflictingStayForGuest()'s class doc) and false for a non-overlapping
     * range. Extracted as a pure static predicate (package-private) so this
     * exact logic - including the exact-match exclusion - is unit-testable
     * without a RoomRepository instance (which needs a Context).
     */
    static boolean isGenuineOverlap(long newIn, long newOut, long existingIn, long existingOut) {
        if (newIn == existingIn && newOut == existingOut) return false;
        return newIn < existingOut && existingIn < newOut;
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
     *
     * Public/static so screens that talk to the API directly instead of
     * through this repository (e.g. ProfileManagementActivity) can route
     * their own 401/419 responses through the same clear+redirect instead of
     * each silently no-oping or showing a dead-end error toast.
     */
    public static void forceSessionExpiredLogout(Context context) {
        if (!com.example.velocitysuites.network.SessionManager.claimSessionExpiredHandling()) return;
        com.example.velocitysuites.network.SessionManager.clear(context);
        android.content.Intent intent = new android.content.Intent(context, LoginActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
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
            forceSessionExpiredLogout(appContext);
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
                forceSessionExpiredLogout(appContext);
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
     * Direct single-record fetch by id, used only when a deep-linked
     * transaction (e.g. "View Transaction" from a notification)
     * isn't present in the already-loaded bookings/reservations cache -
     * most likely because it's older than the per_page window both live
     * fetches cap at. preferReservation comes from the caller's own
     * category-derived guess (see NotificationPrimaryActionResolver#
     * transactionHistoryFilterFor()) - a definite 404 from the "wrong"
     * endpoint is treated as "not found" rather than silently retried
     * against the other table, since a guessed id could otherwise resolve
     * to an unrelated real record on the other side.
     */
    public void fetchTransactionById(String id, boolean preferReservation, RepositoryCallback<Booking> callback) {
        if (preferReservation) {
            api.getReservation(id).enqueue(new Callback<ReservationDto>() {
                @Override
                public void onResponse(Call<ReservationDto> call, Response<ReservationDto> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Booking mapped = ApiMapper.toBooking(response.body());
                        mergeBookingIntoCache(mapped);
                        if (callback != null) callback.onSuccess(mapped);
                    } else if (response.code() == 404) {
                        if (callback != null) callback.onError(appContext.getString(R.string.deep_link_transaction_not_found));
                    } else {
                        if (callback != null) callback.onError(errorMessage(response));
                    }
                }

                @Override
                public void onFailure(Call<ReservationDto> call, Throwable t) {
                    if (callback != null) callback.onError(networkErrorMessage(t));
                }
            });
        } else {
            api.getDirectBooking(id).enqueue(new Callback<DirectBookingResponseDto>() {
                @Override
                public void onResponse(Call<DirectBookingResponseDto> call, Response<DirectBookingResponseDto> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Booking mapped = ApiMapper.toBooking(response.body());
                        mergeBookingIntoCache(mapped);
                        if (callback != null) callback.onSuccess(mapped);
                    } else if (response.code() == 404) {
                        if (callback != null) callback.onError(appContext.getString(R.string.deep_link_transaction_not_found));
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
    }

    /**
     * Finds one transaction by id for a deep link (a notification's "View Transaction") when it is not in the
     * loaded window. {@code order} is the tables to try, most likely first - a reservation's id and a direct
     * booking's id are different records, so the caller's category hint decides which to ask first; only a
     * definite 404 moves on to the next table, any other failure stops and reports an error. All 404 =
     * {@link TransactionLookupCallback#onNotFound()}. Found records are merged into the shared cache, like
     * fetchTransactionById().
     */
    public void lookupTransaction(String id, List<TransactionFamily> order, TransactionLookupCallback callback) {
        lookupTransactionAt(id, order, 0, callback);
    }

    private void lookupTransactionAt(String id, List<TransactionFamily> order, int index, TransactionLookupCallback callback) {
        if (id == null || id.trim().isEmpty() || index >= order.size()) {
            if (callback != null) callback.onNotFound();
            return;
        }
        final String trimmedId = id.trim();
        if (order.get(index) == TransactionFamily.RESERVATION) {
            api.getReservation(trimmedId).enqueue(new Callback<ReservationDto>() {
                @Override
                public void onResponse(Call<ReservationDto> call, Response<ReservationDto> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            Booking mapped = ApiMapper.toBooking(response.body());
                            mergeBookingIntoCache(mapped);
                            if (callback != null) callback.onFound(mapped);
                        } catch (RuntimeException e) {
                            if (callback != null) callback.onError(appContext.getString(R.string.error_unexpected_response));
                        }
                    } else if (response.code() == 404) {
                        lookupTransactionAt(id, order, index + 1, callback);
                    } else if (callback != null) {
                        callback.onError(errorMessage(response));
                    }
                }

                @Override
                public void onFailure(Call<ReservationDto> call, Throwable t) {
                    if (callback != null) callback.onError(networkErrorMessage(t));
                }
            });
        } else {
            api.getDirectBooking(trimmedId).enqueue(new Callback<DirectBookingResponseDto>() {
                @Override
                public void onResponse(Call<DirectBookingResponseDto> call, Response<DirectBookingResponseDto> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            Booking mapped = ApiMapper.toBooking(response.body());
                            mergeBookingIntoCache(mapped);
                            if (callback != null) callback.onFound(mapped);
                        } catch (RuntimeException e) {
                            if (callback != null) callback.onError(appContext.getString(R.string.error_unexpected_response));
                        }
                    } else if (response.code() == 404) {
                        lookupTransactionAt(id, order, index + 1, callback);
                    } else if (callback != null) {
                        callback.onError(errorMessage(response));
                    }
                }

                @Override
                public void onFailure(Call<DirectBookingResponseDto> call, Throwable t) {
                    if (callback != null) callback.onError(networkErrorMessage(t));
                }
            });
        }
    }

    /** Replaces the cached copy of this booking (by id) if present, else appends it - keeps a fetchTransactionById() result visible to every other screen reading getBookings() too, not just the caller that fetched it. */
    private void mergeBookingIntoCache(Booking booking) {
        for (int i = 0; i < bookings.size(); i++) {
            if (bookings.get(i).getId().equals(booking.getId())) {
                bookings.set(i, booking);
                return;
            }
        }
        bookings.add(booking);
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
        return networkErrorMessage(appContext, t);
    }

    /**
     * Public/static so screens that talk to the API directly instead of
     * through this repository (LoginActivity, RegistrationActivity,
     * ProfileManagementActivity) can show the same specific timeout/offline/
     * unreachable/malformed-response message instead of one flat generic
     * string regardless of actual cause.
     */
    public static String networkErrorMessage(Context context, Throwable t) {
        if (t instanceof java.net.UnknownHostException || t instanceof java.net.ConnectException) {
            return context.getString(R.string.error_no_internet_connection);
        }
        if (t instanceof java.net.SocketTimeoutException) {
            return context.getString(R.string.error_request_timed_out);
        }
        if (t instanceof javax.net.ssl.SSLException) {
            return context.getString(R.string.error_server_unreachable);
        }
        if (t instanceof com.google.gson.JsonParseException || t instanceof java.io.IOException) {
            // A successful-looking response whose body Gson/OkHttp couldn't parse as expected -
            // not a connectivity problem, but still not something to show the guest verbatim.
            return context.getString(R.string.error_unexpected_response);
        }
        return context.getString(R.string.error_server_unreachable);
    }
}
