package com.example.velocitysuites.ui;

import android.content.Context;

import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.network.ApiService;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.NotificationDto;
import com.example.velocitysuites.network.dto.PaginatedResponse;
import com.example.velocitysuites.network.dto.PaymentDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.example.velocitysuites.network.dto.RoomTypeDto;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * Plumbing for the Robolectric SCREEN tests: a fresh RoomRepository wired to a recording fake ApiService (no
 * network), so a test can launch a real Activity, see exactly which requests it makes, and answer them -
 * success, 404, offline - whenever and in whatever order it likes.
 */
final class ScreenTestSupport {

    private ScreenTestSupport() {
    }

    // ---- fake Retrofit call ----

    static final class FakeCall<T> implements Call<T> {
        private Callback<T> callback;
        private boolean canceled;

        @Override public Response<T> execute() { throw new UnsupportedOperationException("only enqueue() is used"); }
        @Override public void enqueue(Callback<T> cb) { this.callback = cb; }
        @Override public boolean isExecuted() { return callback != null; }
        @Override public void cancel() { canceled = true; }
        @Override public boolean isCanceled() { return canceled; }
        @Override public Call<T> clone() { return new FakeCall<>(); }
        @Override public Request request() { return new Request.Builder().url("https://example.test/").build(); }
        @Override public Timeout timeout() { return Timeout.NONE; }

        boolean pending() { return callback != null; }
        void succeed(T body) { callback.onResponse(this, Response.success(body)); }
        void http(int code) { callback.onResponse(this, Response.<T>error(code, emptyBody())); }
        void offline() { callback.onFailure(this, new java.net.UnknownHostException("offline")); }
    }

    private static ResponseBody emptyBody() {
        return new ResponseBody() {
            @Override public MediaType contentType() { return null; }
            @Override public long contentLength() { return 0; }
            @Override public BufferedSource source() { return new Buffer(); }
        };
    }

    // ---- fake API ----

    /** Answers every ApiService method with a pending FakeCall and remembers it, by method name, in call order. */
    static final class FakeApi {
        private final Map<String, List<FakeCall<?>>> calls = new HashMap<>();
        final List<String> log = new ArrayList<>();

        ApiService proxy() {
            return (ApiService) Proxy.newProxyInstance(ApiService.class.getClassLoader(), new Class<?>[]{ApiService.class},
                    (proxy, method, args) -> {
                        if (method.getReturnType() != Call.class) {
                            throw new UnsupportedOperationException("unexpected non-Call API method: " + method.getName());
                        }
                        FakeCall<Object> call = new FakeCall<>();
                        calls.computeIfAbsent(method.getName(), k -> new ArrayList<>()).add(call);
                        log.add(method.getName() + (args != null && args.length > 0 && args[0] != null ? "(" + args[0] + ")" : ""));
                        return call;
                    });
        }

        int count(String method) {
            List<FakeCall<?>> list = calls.get(method);
            return list == null ? 0 : list.size();
        }

        @SuppressWarnings("unchecked")
        <T> FakeCall<T> call(String method, int index) {
            return (FakeCall<T>) calls.get(method).get(index);
        }

        <T> FakeCall<T> last(String method) {
            return call(method, count(method) - 1);
        }
    }

    /**
     * A brand-new RoomRepository (the real one is a process-wide singleton that would carry one test's state
     * into the next) whose ApiService is {@code api}. Uses the real application context, so error messages
     * resolve real strings.
     */
    static RoomRepository freshRepository(Context applicationContext, FakeApi api) {
        try {
            Field instance = RoomRepository.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(null, null);
            RoomRepository repository = RoomRepository.getInstance(applicationContext);
            Field apiField = RoomRepository.class.getDeclaredField("api");
            apiField.setAccessible(true);
            apiField.set(repository, api.proxy());
            return repository;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // ---- DTO fixtures ----

    static <T> PaginatedResponse<T> page(List<T> items) {
        PaginatedResponse<T> page = new PaginatedResponse<>();
        page.data = new ArrayList<>(items);
        page.total = items.size();
        page.current_page = 1;
        page.last_page = 1;
        return page;
    }

    static PaginatedResponse<NotificationDto> notificationPage(int unread, NotificationDto... items) {
        PaginatedResponse<NotificationDto> page = page(java.util.Arrays.asList(items));
        page.unread_count = unread;
        return page;
    }

    static NotificationDto notification(long id, String title, String category, Long referenceId, boolean read) {
        NotificationDto n = new NotificationDto();
        n.id = id;
        n.title = title;
        n.message = "Message for " + title;
        n.category = category;
        n.reference_id = referenceId;
        n.is_read = read;
        n.created_at = "2026-09-30T14:09:00Z";
        return n;
    }

    static PaymentDto payment(long id, String amount, String status, String method) {
        PaymentDto p = new PaymentDto();
        p.id = id;
        p.amount_paid = amount;
        p.payment_status = status;
        p.payment_method = method;
        p.reference_number = "1234567890123";
        p.payment_date = "2026-09-30T14:09:00Z";
        p.verification_status = "completed".equals(status) ? "verified" : "pending_verification";
        return p;
    }

    /** A reservation (no booking yet) for a Deluxe room, Oct 1-2, total P1,800, with the given payments. */
    static ReservationDto reservation(long id, double total, PaymentDto... payments) {
        ReservationDto r = new ReservationDto();
        r.id = id;
        r.check_in = "2026-10-01";
        r.check_out = "2026-10-02";
        r.rooms_requested = 1;
        r.adults = 2;
        r.number_of_guests = 2;
        r.status = "AWAITING_GCASH_PAYMENT";
        r.total_amount_due = total;
        r.created_at = "2026-09-30T14:00:00Z";
        RoomTypeDto type = new RoomTypeDto();
        type.id = 1;
        type.name = "Deluxe";
        type.rate = "1800.00";
        r.room_type = type;
        r.room_type_id = 1;
        r.payments = new ArrayList<>(java.util.Arrays.asList(payments));
        return r;
    }

    static DirectBookingResponseDto directBooking(long id, double total) {
        DirectBookingResponseDto d = new DirectBookingResponseDto();
        d.id = id;
        d.check_in = "2026-10-01";
        d.check_out = "2026-10-02";
        d.rooms_requested = 1;
        d.adults = 2;
        d.number_of_guests = 2;
        d.booking_status = "ACTIVE_BOOKING";
        d.total_amount_due = total;
        d.confirmed_at = "2026-09-30T14:00:00Z";
        RoomTypeDto type = new RoomTypeDto();
        type.id = 1;
        type.name = "Suite";
        type.rate = "3000.00";
        d.room_type = type;
        d.room_type_id = 1;
        d.payments = new ArrayList<>();
        return d;
    }

    static List<ReservationDto> reservations(ReservationDto... items) {
        return new ArrayList<>(java.util.Arrays.asList(items));
    }

    static List<DirectBookingResponseDto> directBookings(DirectBookingResponseDto... items) {
        return items.length == 0 ? Collections.emptyList() : new ArrayList<>(java.util.Arrays.asList(items));
    }

    /** Answers the initial booking load (reservations + direct bookings) the History screen makes on open. */
    static void answerBookingLoad(FakeApi api, int callIndex, List<ReservationDto> reservations, List<DirectBookingResponseDto> direct) {
        FakeCall<PaginatedResponse<ReservationDto>> r = api.call("getReservations", callIndex);
        r.succeed(page(reservations));
        FakeCall<PaginatedResponse<DirectBookingResponseDto>> d = api.call("getDirectBookings", callIndex);
        d.succeed(page(direct));
    }

    static IOException offline() {
        return new IOException("offline");
    }
}
