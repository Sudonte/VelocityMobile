package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomAmenity;
import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * What the app does with the server's answer to creating a Booking: "that room is gone" (422 on the rooms_requested
 * key) reaches a callback that asked for it as its own outcome; every other failure is still a plain error.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class BookingRejectionRoutingTest {

    private ScreenTestSupport.FakeApi api;
    private RoomRepository repository;

    private String unavailable;
    private String error;

    @Before
    public void setUp() {
        Context app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        repository = ScreenTestSupport.freshRepository(app, api);
    }

    private void createBooking(boolean awareCallback) throws InterruptedException {
        Room room = new Room("1", "Deluxe", "Deluxe", 2, 2500.0, "", 0, true,
                Collections.<RoomAmenity>emptyList(), "Queen", "24 sqm", "");
        List<List<Room>> groups = new ArrayList<>();
        groups.add(Collections.singletonList(room));
        Calendar in = Calendar.getInstance();
        Calendar out = Calendar.getInstance();
        out.add(Calendar.DAY_OF_YEAR, 2);
        RoomRepository.RepositoryCallback<Booking> plain = new RoomRepository.RepositoryCallback<Booking>() {
            @Override public void onSuccess(Booking result) { }
            @Override public void onError(String message) { error = message; }
        };
        RoomRepository.RepositoryCallback<Booking> callback = awareCallback
                ? new RoomRepository.AvailabilityAwareCallback<Booking>() {
                    @Override public void onSuccess(Booking result) { }
                    @Override public void onError(String message) { error = message; }
                    @Override public void onRoomsUnavailable(String message) { unavailable = message; }
                }
                : plain;
        repository.createDirectBooking(groups, in, out, 2, 0, "A", null, "B", "None", null, null,
                null, null, "cash", null, null, null, 1000, null, "key-1", callback);
        for (int i = 0; i < 300 && api.count("createDirectBooking") == 0; i++) {
            Thread.sleep(20);
            shadowOf(Looper.getMainLooper()).idle();
        }
        assertEquals(1, api.count("createDirectBooking"));
    }

    @Test
    public void roomGone_reachesACallbackThatAsksForIt_withTheServersWords() throws Exception {
        createBooking(true);
        api.<DirectBookingResponseDto>last("createDirectBooking").httpWithBody(422,
                "{\"message\":\"Deluxe is fully booked for these dates.\",\"errors\":{\"rooms_requested\":[\"Deluxe is fully booked for these dates.\"]}}");
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("Deluxe is fully booked for these dates.", unavailable);
        assertNull("not reported as a plain error as well", error);
    }

    @Test
    public void roomGone_isStillAPlainErrorForACallbackThatDoesNotAsk() throws Exception {
        createBooking(false);
        api.<DirectBookingResponseDto>last("createDirectBooking").httpWithBody(422,
                "{\"message\":\"x\",\"errors\":{\"rooms_requested\":[\"Deluxe is fully booked for these dates.\"]}}");
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("Deluxe is fully booked for these dates.", error);
    }

    @Test
    public void otherRejections_stayPlainErrors_evenForACallbackThatAsks() throws Exception {
        createBooking(true);
        api.<DirectBookingResponseDto>last("createDirectBooking").httpWithBody(422,
                "{\"message\":\"x\",\"errors\":{\"adults\":[\"Too many guests for the selected room.\"]}}");
        shadowOf(Looper.getMainLooper()).idle();

        assertNull(unavailable);
        assertEquals("Too many guests for the selected room.", error);
    }
}
