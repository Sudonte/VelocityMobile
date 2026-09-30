package com.example.velocitysuites;

import com.example.velocitysuites.network.ApiService;
import com.example.velocitysuites.network.dto.ApiMessage;
import com.example.velocitysuites.network.dto.NotificationDto;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The "persists to the backend" half of per-notification Mark as read / Mark as unread, driven
 * end to end through RoomRepository against a FAKE ApiService: which endpoint is called for
 * which action, that the local change is applied before the server answers, that a failure
 * reverts exactly that change, and that a poll answered before the server applied a change
 * cannot flip the row back.
 * <p>
 * The fake records each request and holds its Retrofit Callback until the test completes it,
 * so ordering races (a poll landing mid-flight, two quick toggles) are reproducible. What this
 * deliberately cannot show is the server itself - that PUT notifications/{id}/read really
 * writes is_read to the database is backend behaviour (Api\NotificationController::markAsRead()
 * -> Notification::markAsRead() -> update(['is_read' => true])), verified by reading it, not here.
 */
public class RoomRepositoryNotificationPersistenceTest {

    /** A Retrofit Call that only records being enqueued - the test decides when, and how, it completes. */
    private static final class FakeCall<T> implements Call<T> {
        private Callback<T> callback;
        private boolean executed;

        @Override public Response<T> execute() { throw new UnsupportedOperationException("only enqueue() is used"); }
        @Override public void enqueue(Callback<T> cb) { this.callback = cb; this.executed = true; }
        @Override public boolean isExecuted() { return executed; }
        @Override public void cancel() { }
        @Override public boolean isCanceled() { return false; }
        @Override public Call<T> clone() { return new FakeCall<>(); }
        @Override public Request request() { return new Request.Builder().url("https://example.test/").build(); }
        @Override public Timeout timeout() { return Timeout.NONE; }

        void succeed(T body) { callback.onResponse(this, Response.success(body)); }
        void failWithHttp(int code) { callback.onResponse(this, Response.<T>error(code, emptyBody())); }
        void failWithNetworkError() { callback.onFailure(this, new IOException("offline")); }
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
                        case "markNotificationRead":
                            requests.add("read:" + args[0]);
                            call = new FakeCall<NotificationDto>();
                            break;
                        case "markNotificationUnread":
                            requests.add("unread:" + args[0]);
                            call = new FakeCall<NotificationDto>();
                            break;
                        case "markAllNotificationsRead":
                            requests.add("read-all");
                            call = new FakeCall<ApiMessage>();
                            break;
                        default:
                            throw new UnsupportedOperationException("unexpected API call in this test: " + method.getName());
                    }
                    calls.add(call);
                    return call;
                });
    }

    @Before
    public void setUp() {
        repository = RoomRepository.getInstance(null);
        previousApi = repository.setApiForTesting(fakeApi());
    }

    @After
    public void tearDown() {
        repository.setApiForTesting(previousApi);
        repository.setNotificationsForTesting(Collections.<Notification>emptyList(), 0);
    }

    // ---- helpers ----

    private static Notification notification(String id, boolean read) {
        return new Notification(id, "Title " + id, "Message " + id, "just now", Notification.TYPE_PAYMENT, read);
    }

    private void seed(int backendUnread, Notification... notifications) {
        repository.setNotificationsForTesting(Arrays.asList(notifications), backendUnread);
    }

    private boolean isRead(String id) {
        for (Notification n : repository.getNotifications()) {
            if (n.getId().equals(id)) return n.isRead();
        }
        throw new AssertionError("no notification with id " + id);
    }

    @SuppressWarnings("unchecked")
    private FakeCall<NotificationDto> notificationCall(int index) {
        return (FakeCall<NotificationDto>) calls.get(index);
    }

    @SuppressWarnings("unchecked")
    private FakeCall<ApiMessage> messageCall(int index) {
        return (FakeCall<ApiMessage>) calls.get(index);
    }

    // ---- Mark as read ----

    @Test
    public void markAsRead_callsTheReadEndpointForThatIdOnly_andAppliesItBeforeTheServerAnswers() {
        seed(2, notification("1", false), notification("2", false));
        List<Boolean> results = new ArrayList<>();

        repository.markNotificationAsRead("1", results::add);

        assertEquals(Collections.singletonList("read:1"), requests);
        assertTrue("the row flips at once, without waiting for the network", isRead("1"));
        assertFalse("the neighbouring row is untouched", isRead("2"));
        assertEquals("the badge count drops at once too", 1, repository.getUnreadNotificationCount());
        assertTrue("no result is reported until the server answers", results.isEmpty());
    }

    @Test
    public void markAsRead_serverConfirms_keepsItRead_andReportsSuccess() {
        seed(2, notification("1", false), notification("2", false));
        List<Boolean> results = new ArrayList<>();

        repository.markNotificationAsRead("1", results::add);
        notificationCall(0).succeed(new NotificationDto());

        assertEquals(Collections.singletonList(true), results);
        assertTrue(isRead("1"));
        assertEquals(1, repository.getUnreadNotificationCount());
    }

    // ---- Mark as unread ----

    @Test
    public void markAsUnread_callsTheUnreadEndpoint_notTheReadOne() {
        seed(0, notification("7", true));
        List<Boolean> results = new ArrayList<>();

        repository.markNotificationAsUnread("7", results::add);

        assertEquals(Collections.singletonList("unread:7"), requests);
        assertFalse(isRead("7"));
        assertEquals(1, repository.getUnreadNotificationCount());

        notificationCall(0).succeed(new NotificationDto());
        assertEquals(Collections.singletonList(true), results);
        assertFalse(isRead("7"));
    }

    // ---- Failure: the local change is reverted, and only that change ----

    @Test
    public void markAsRead_httpError_revertsTheRowAndTheCount_andReportsFailure() {
        seed(2, notification("1", false), notification("2", false));
        List<Boolean> results = new ArrayList<>();

        repository.markNotificationAsRead("1", results::add);
        assertTrue(isRead("1"));
        notificationCall(0).failWithHttp(403);

        assertEquals(Collections.singletonList(false), results);
        assertFalse("reverted to what the server still has", isRead("1"));
        assertEquals(2, repository.getUnreadNotificationCount());
    }

    @Test
    public void markAsUnread_networkFailure_revertsTheRowAndTheCount_andReportsFailure() {
        seed(0, notification("1", true));
        List<Boolean> results = new ArrayList<>();

        repository.markNotificationAsUnread("1", results::add);
        assertFalse(isRead("1"));
        notificationCall(0).failWithNetworkError();

        assertEquals(Collections.singletonList(false), results);
        assertTrue(isRead("1"));
        assertEquals(0, repository.getUnreadNotificationCount());
    }

    @Test
    public void aFailedChange_doesNotLeaveAStaleProtection_soTheNextPollIsTakenAtFaceValue() {
        seed(1, notification("1", false));
        repository.markNotificationAsRead("1", null);
        notificationCall(0).failWithNetworkError();

        // The change is over (and undone) - a later poll saying "unread" must simply be believed, not forced back to read.
        List<Notification> fresh = new ArrayList<>(Collections.singletonList(notification("1", false)));
        assertEquals(1, repository.reconcilePendingReadStates(fresh, 1));
        assertFalse(fresh.get(0).isRead());
    }

    // ---- A poll landing while the request is still in flight ----

    @Test
    public void aPollAnsweredBeforeTheServerAppliedTheChange_cannotFlipTheRowBack() {
        seed(1, notification("1", false));
        repository.markNotificationAsRead("1", null);

        // The poll was computed by the server BEFORE it processed our PUT: still unread, still counted.
        List<Notification> stale = new ArrayList<>(Collections.singletonList(notification("1", false)));
        int count = repository.reconcilePendingReadStates(stale, 1);

        assertTrue("the in-flight change survives the stale poll", stale.get(0).isRead());
        assertEquals(0, count);
    }

    @Test
    public void onceTheRequestHasFinished_aLaterPollIsAuthoritativeAgain() {
        seed(1, notification("1", false));
        repository.markNotificationAsRead("1", null);
        notificationCall(0).succeed(new NotificationDto());

        // e.g. the guest marked it unread again on another device - that must win over our old request.
        List<Notification> fresh = new ArrayList<>(Collections.singletonList(notification("1", false)));
        assertEquals(1, repository.reconcilePendingReadStates(fresh, 1));
        assertFalse(fresh.get(0).isRead());
    }

    // ---- Two quick toggles of the same row ----

    @Test
    public void twoQuickToggles_theLaterOneOwnsTheState_evenWhenTheEarlierOneFailsAfterwards() {
        seed(1, notification("1", false));

        repository.markNotificationAsRead("1", null);     // call 0: unread -> read
        repository.markNotificationAsUnread("1", null);   // call 1: read -> unread
        assertFalse(isRead("1"));

        notificationCall(0).failWithNetworkError();       // the EARLIER request fails after the later one started

        assertFalse("the later toggle's state stands", isRead("1"));
        assertEquals(1, repository.getUnreadNotificationCount());

        // ...and call 1 is still in flight, so it must still be protected from a stale poll.
        List<Notification> stale = new ArrayList<>(Collections.singletonList(notification("1", true)));
        repository.reconcilePendingReadStates(stale, 0);
        assertFalse("the still-pending 'unread' request keeps its protection", stale.get(0).isRead());
    }

    @Test
    public void twoQuickToggles_bothSucceed_endsInTheLastRequestedState() {
        seed(1, notification("1", false));

        repository.markNotificationAsRead("1", null);
        repository.markNotificationAsUnread("1", null);
        notificationCall(0).succeed(new NotificationDto());
        notificationCall(1).succeed(new NotificationDto());

        assertEquals(Arrays.asList("read:1", "unread:1"), requests);
        assertFalse(isRead("1"));
        assertEquals(1, repository.getUnreadNotificationCount());
    }

    // ---- Edge cases ----

    @Test
    public void anIdNotInTheCache_stillSendsTheRequest_butChangesNothingLocally() {
        seed(1, notification("1", false));

        repository.markNotificationAsRead("999", null);

        assertEquals(Collections.singletonList("read:999"), requests);
        assertFalse(isRead("1"));
        assertEquals(1, repository.getUnreadNotificationCount());
    }

    @Test
    public void aNullCallback_isFine() {
        seed(1, notification("1", false));

        repository.markNotificationAsRead("1", null);
        notificationCall(0).succeed(new NotificationDto());

        assertTrue(isRead("1"));
    }

    // ---- Mark all as read ----

    @Test
    public void markAll_flipsEveryLoadedUnreadRow_zeroesTheCount_andCallsReadAllOnce() {
        seed(3, notification("1", false), notification("2", true), notification("3", false));
        List<Boolean> results = new ArrayList<>();

        repository.markAllNotificationsAsRead(results::add);

        assertEquals(Collections.singletonList("read-all"), requests);
        assertTrue(isRead("1"));
        assertTrue(isRead("2"));
        assertTrue(isRead("3"));
        assertEquals(0, repository.getUnreadNotificationCount());

        messageCall(0).succeed(new ApiMessage());
        assertEquals(Collections.singletonList(true), results);
        assertEquals(0, repository.getUnreadNotificationCount());
    }

    @Test
    public void markAll_failure_revertsOnlyTheRowsItFlipped_andRestoresTheFullCount() {
        // 5 unread on the server; only 2 of them are in the loaded window (1 and 3), row 2 was already read.
        seed(5, notification("1", false), notification("2", true), notification("3", false));
        List<Boolean> results = new ArrayList<>();

        repository.markAllNotificationsAsRead(results::add);
        messageCall(0).failWithNetworkError();

        assertEquals(Collections.singletonList(false), results);
        assertFalse(isRead("1"));
        assertTrue("a row that was already read before the tap must stay read", isRead("2"));
        assertFalse(isRead("3"));
        assertEquals("the 3 unread rows beyond the loaded window come back too", 5, repository.getUnreadNotificationCount());
    }
}
