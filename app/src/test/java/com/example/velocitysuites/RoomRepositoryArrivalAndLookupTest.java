package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.velocitysuites.network.ApiService;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.NotificationDto;
import com.example.velocitysuites.network.dto.PaginatedResponse;
import com.example.velocitysuites.network.dto.ReservationDto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Two things the screens rely on, driven through RoomRepository against a fake ApiService:
 * <ul>
 *   <li>a notification that genuinely ARRIVES (a newer id than everything known) is announced to listeners - never
 *       the first load of a session, never older rows a load-more pulls in - so Transaction History can refresh
 *       the moment a receptionist acts; and the unread count is "unknown" (not zero) until a fetch succeeds;</li>
 *   <li>the by-id transaction lookup behind "View Transaction": tables tried in the category's order, a 404 moves
 *       on, all-404 = not found.</li>
 * </ul>
 */
public class RoomRepositoryArrivalAndLookupTest {

    private static final class FakeCall<T> implements Call<T> {
        private Callback<T> callback;

        @Override public Response<T> execute() { throw new UnsupportedOperationException(); }
        @Override public void enqueue(Callback<T> cb) { this.callback = cb; }
        @Override public boolean isExecuted() { return callback != null; }
        @Override public void cancel() { }
        @Override public boolean isCanceled() { return false; }
        @Override public Call<T> clone() { return new FakeCall<>(); }
        @Override public Request request() { return new Request.Builder().url("https://example.test/").build(); }
        @Override public Timeout timeout() { return Timeout.NONE; }

        void succeed(T body) { callback.onResponse(this, Response.success(body)); }
        void http(int code) { callback.onResponse(this, Response.<T>error(code, emptyBody())); }
        void network() { callback.onFailure(this, new IOException("offline")); }
    }

    private static ResponseBody emptyBody() {
        return new ResponseBody() {
            @Override public MediaType contentType() { return null; }
            @Override public long contentLength() { return 0; }
            @Override public BufferedSource source() { return new Buffer(); }
        };
    }

    private final List<String> requests = new ArrayList<>();
    private final List<FakeCall<?>> calls = new ArrayList<>();
    private RoomRepository repository;
    private ApiService previousApi;

    private ApiService fakeApi() {
        return (ApiService) Proxy.newProxyInstance(ApiService.class.getClassLoader(), new Class<?>[]{ApiService.class},
                (proxy, method, args) -> {
                    FakeCall<?> call;
                    switch (method.getName()) {
                        case "getNotifications":
                            requests.add("notifications");
                            call = new FakeCall<PaginatedResponse<NotificationDto>>();
                            break;
                        case "getReservation":
                            requests.add("reservation:" + args[0]);
                            call = new FakeCall<ReservationDto>();
                            break;
                        case "getDirectBooking":
                            requests.add("direct:" + args[0]);
                            call = new FakeCall<DirectBookingResponseDto>();
                            break;
                        default:
                            throw new UnsupportedOperationException("unexpected API call: " + method.getName());
                    }
                    calls.add(call);
                    return call;
                });
    }

    @Before
    public void setUp() {
        repository = RoomRepository.getInstance(null);
        previousApi = repository.setApiForTesting(fakeApi());
        repository.clearAccountSpecificCache();
    }

    @After
    public void tearDown() {
        repository.setApiForTesting(previousApi);
        repository.clearAccountSpecificCache();
    }

    @SuppressWarnings("unchecked")
    private <T> FakeCall<T> call(int index) {
        return (FakeCall<T>) calls.get(index);
    }

    private static NotificationDto dto(long id) {
        NotificationDto d = new NotificationDto();
        d.id = id;
        d.title = "Title " + id;
        d.message = "Message " + id;
        d.category = "payment";
        d.reference_id = 588L;
        return d;
    }

    private static PaginatedResponse<NotificationDto> page(int unread, long... ids) {
        PaginatedResponse<NotificationDto> p = new PaginatedResponse<>();
        p.unread_count = unread;
        p.total = ids.length;
        for (long id : ids) p.data.add(dto(id));
        return p;
    }

    private static final class Collector implements RoomRepository.NotificationArrivalListener {
        final List<List<String>> batches = new ArrayList<>();

        @Override
        public void onNewNotifications(List<Notification> arrived) {
            List<String> ids = new ArrayList<>();
            for (Notification n : arrived) ids.add(n.getId());
            batches.add(ids);
        }
    }

    private static final RoomRepository.RepositoryCallback<List<Notification>> IGNORE = new RoomRepository.RepositoryCallback<List<Notification>>() {
        @Override public void onSuccess(List<Notification> result) { }
        @Override public void onError(String message) { }
    };

    // ---- unknown vs zero ----

    @Test
    public void theUnreadCountIsUnknown_notZero_untilAFetchSucceeds() {
        assertFalse(repository.hasLoadedNotifications());
        repository.pollNotifications(IGNORE);
        assertFalse("a request in flight is still unknown", repository.hasLoadedNotifications());
        // (Left unanswered on purpose: completing it with a failure would build a user-facing error string,
        // which needs an Android Context this plain-JVM test doesn't have.)
    }

    @Test
    public void afterASuccessfulFetch_theCountIsKnown() {
        repository.pollNotifications(IGNORE);
        FakeCall<PaginatedResponse<NotificationDto>> c = call(0);
        c.succeed(page(0));
        assertTrue(repository.hasLoadedNotifications());
        assertEquals(0, repository.getUnreadNotificationCount());
    }

    @Test
    public void accountBoundaryForgetsWhatWasLoaded() {
        repository.setNotificationsForTesting(Collections.<Notification>emptyList(), 0);
        assertTrue(repository.hasLoadedNotifications());
        repository.clearAccountSpecificCache();
        assertFalse(repository.hasLoadedNotifications());
    }

    // ---- arrivals ----

    @Test
    public void theVeryFirstLoadAnnouncesNothing() {
        Collector listener = new Collector();
        repository.addNotificationArrivalListener(listener);
        repository.pollNotifications(IGNORE);
        FakeCall<PaginatedResponse<NotificationDto>> c = call(0);
        c.succeed(page(3, 12, 11, 10));
        assertTrue("everything looks new on a first load - it is not an arrival", listener.batches.isEmpty());
        repository.removeNotificationArrivalListener(listener);
    }

    @Test
    public void aNewerNotificationIsAnnounced_exactlyOnce() {
        Collector listener = new Collector();
        repository.setNotificationsForTesting(Arrays.asList(
                new Notification("10", "t", "m", "x", Notification.TYPE_PAYMENT, true)), 0);
        repository.addNotificationArrivalListener(listener);

        repository.pollNotifications(IGNORE);
        FakeCall<PaginatedResponse<NotificationDto>> first = call(0);
        first.succeed(page(1, 11, 10));
        assertEquals(Collections.singletonList(Collections.singletonList("11")), listener.batches);

        // The same page again: nothing new.
        repository.pollNotifications(IGNORE);
        FakeCall<PaginatedResponse<NotificationDto>> second = call(1);
        second.succeed(page(1, 11, 10));
        assertEquals(1, listener.batches.size());
        repository.removeNotificationArrivalListener(listener);
    }

    @Test
    public void olderRowsThatALoadMoreBringsIn_areNotArrivals() {
        Collector listener = new Collector();
        repository.setNotificationsForTesting(Arrays.asList(
                new Notification("50", "t", "m", "x", Notification.TYPE_PAYMENT, true),
                new Notification("49", "t", "m", "x", Notification.TYPE_PAYMENT, true)), 0);
        repository.addNotificationArrivalListener(listener);

        repository.refreshNotifications(IGNORE); // a bigger window: the same two plus OLDER ones
        FakeCall<PaginatedResponse<NotificationDto>> c = call(0);
        c.succeed(page(0, 50, 49, 48, 47));

        assertTrue(listener.batches.isEmpty());
        repository.removeNotificationArrivalListener(listener);
    }

    @Test
    public void severalNewOnesAreAnnouncedTogether() {
        Collector listener = new Collector();
        repository.setNotificationsForTesting(Arrays.asList(
                new Notification("10", "t", "m", "x", Notification.TYPE_PAYMENT, true)), 0);
        repository.addNotificationArrivalListener(listener);
        repository.pollNotifications(IGNORE);
        FakeCall<PaginatedResponse<NotificationDto>> c = call(0);
        c.succeed(page(2, 12, 11, 10));
        assertEquals(1, listener.batches.size());
        assertEquals(Arrays.asList("12", "11"), listener.batches.get(0));
        repository.removeNotificationArrivalListener(listener);
    }

    @Test
    public void aRemovedListenerHearsNothing() {
        Collector listener = new Collector();
        repository.setNotificationsForTesting(Arrays.asList(
                new Notification("10", "t", "m", "x", Notification.TYPE_PAYMENT, true)), 0);
        repository.addNotificationArrivalListener(listener);
        repository.removeNotificationArrivalListener(listener);
        repository.pollNotifications(IGNORE);
        FakeCall<PaginatedResponse<NotificationDto>> c = call(0);
        c.succeed(page(1, 11, 10));
        assertTrue(listener.batches.isEmpty());
    }

    // ---- by-id lookup ----

    private static final class Lookup implements RoomRepository.TransactionLookupCallback {
        Booking found;
        boolean notFound;
        String error;

        @Override public void onFound(Booking booking) { found = booking; }
        @Override public void onNotFound() { notFound = true; }
        @Override public void onError(String message) { error = message; }
    }

    private static ReservationDto reservationDto(long id) {
        ReservationDto r = new ReservationDto();
        r.id = id;
        r.check_in = "2026-10-01";
        r.check_out = "2026-10-02";
        r.rooms_requested = 1;
        r.status = "AWAITING_GCASH_PAYMENT";
        return r;
    }

    private static DirectBookingResponseDto directDto(long id) {
        DirectBookingResponseDto d = new DirectBookingResponseDto();
        d.id = id;
        d.check_in = "2026-10-01";
        d.check_out = "2026-10-02";
        d.rooms_requested = 1;
        d.booking_status = "ACTIVE_BOOKING";
        return d;
    }

    @Test
    public void lookupTriesTheFirstTable_andReportsWhatItFound() {
        Lookup result = new Lookup();
        repository.lookupTransaction("588", TransactionNavigator.lookupOrder(Notification.TYPE_RESERVATION), result);
        assertEquals(Collections.singletonList("reservation:588"), requests);
        FakeCall<ReservationDto> c = call(0);
        c.succeed(reservationDto(588));
        assertNotNull(result.found);
        assertEquals("588", result.found.getId());
        assertFalse(result.found.isDirectBooking());
    }

    @Test
    public void a404MovesOnToTheOtherTable() {
        Lookup result = new Lookup();
        repository.lookupTransaction("588", TransactionNavigator.lookupOrder(Notification.TYPE_BOOKING), result);
        assertEquals("direct booking is asked first for a Booking notification", "direct:588", requests.get(0));
        FakeCall<DirectBookingResponseDto> first = call(0);
        first.http(404);
        assertEquals(Arrays.asList("direct:588", "reservation:588"), requests);
        FakeCall<ReservationDto> second = call(1);
        second.succeed(reservationDto(588));
        assertNotNull(result.found);
        assertFalse(result.notFound);
    }

    @Test
    public void allTablesAnswering404_isNotFound() {
        Lookup result = new Lookup();
        repository.lookupTransaction("588", TransactionNavigator.lookupOrder(Notification.TYPE_PAYMENT), result);
        FakeCall<ReservationDto> first = call(0);
        first.http(404);
        FakeCall<DirectBookingResponseDto> second = call(1);
        second.http(404);
        assertTrue(result.notFound);
        assertEquals(null, result.found);
        assertEquals(null, result.error);
    }

    @Test
    public void aBlankIdIsNotFoundWithoutAnyRequest() {
        Lookup result = new Lookup();
        repository.lookupTransaction("  ", TransactionNavigator.lookupOrder(null), result);
        assertTrue(result.notFound);
        assertTrue(requests.isEmpty());
    }

    @Test
    public void aFoundRecordLandsInTheSharedCache() {
        Lookup result = new Lookup();
        repository.lookupTransaction("7", Collections.singletonList(RoomRepository.TransactionFamily.DIRECT_BOOKING), result);
        FakeCall<DirectBookingResponseDto> c = call(0);
        c.succeed(directDto(7));
        assertNotNull(result.found);
        assertTrue(result.found.isDirectBooking());
        boolean cached = false;
        for (Booking b : repository.getBookings()) cached |= "7".equals(b.getId()) && b.isDirectBooking();
        assertTrue(cached);
    }
}
