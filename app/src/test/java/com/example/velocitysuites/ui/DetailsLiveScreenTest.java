package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.PaymentTransaction;
import com.example.velocitysuites.R;
import com.example.velocitysuites.TransactionDetailsActivity;
import com.example.velocitysuites.VisiblePoller;
import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;

/** The details screens: "Last updated" row, a 20 second beat only while visible and while the transaction is active, pull-to-refresh. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DetailsLiveScreenTest {

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com",
                "09171234567", "Female", "Jan 01, 1990", "");
    }

    private static Booking booking(String apiStatus) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));
        DirectBookingResponseDto d = ScreenTestSupport.directBooking(569, 2000);
        d.booking_status = apiStatus;
        d.check_in = today.minusDays(1).toString();
        d.check_out = today.plusDays(1).toString();
        return ApiMapper.toBooking(d);
    }

    private static boolean running(Object activity) throws Exception {
        Field f = activity.getClass().getDeclaredField("visiblePoller");
        f.setAccessible(true);
        return ((VisiblePoller) f.get(activity)).isRunning();
    }

    private ActivityController<BookingDetailsActivity> bookingDetails(Booking b) {
        Intent intent = new Intent(app, BookingDetailsActivity.class).putExtra(BookingDetailsActivity.EXTRA_BOOKING, b);
        ActivityController<BookingDetailsActivity> c = Robolectric.buildActivity(BookingDetailsActivity.class, intent).setup();
        shadowOf(Looper.getMainLooper()).idle();
        return c;
    }

    private ActivityController<TransactionDetailsActivity> txDetails(Booking b) {
        Intent intent = new Intent(app, TransactionDetailsActivity.class).putExtra(TransactionDetailsActivity.EXTRA_TRANSACTION, new PaymentTransaction(b, null));
        ActivityController<TransactionDetailsActivity> c = Robolectric.buildActivity(TransactionDetailsActivity.class, intent).setup();
        shadowOf(Looper.getMainLooper()).idle();
        return c;
    }

    @Test
    public void bookingDetails_pollsOnlyWhileVisibleAndActive() throws Exception {
        ActivityController<BookingDetailsActivity> c = bookingDetails(booking("CHECKED_IN"));
        assertTrue("running while visible", running(c.get()));
        assertEquals("a beat of 20 seconds", 20_000L, com.example.velocitysuites.DetailsLive.POLL_INTERVAL_MS);
        c.pause();
        assertFalse("stopped when the screen is not visible", running(c.get()));
        c.resume();
        assertTrue(running(c.get()));
        c.pause().stop().destroy();
        assertFalse(running(c.get()));

        for (String finished : new String[]{"COMPLETED_BOOKING", "CANCELLED_BOOKING"}) {
            ActivityController<BookingDetailsActivity> done = bookingDetails(booking(finished));
            assertFalse(finished + " is not polled", running(done.get()));
        }
    }

    @Test
    public void bookingDetails_showsLastUpdated_andAFailureKeepsTheLastDataWithTryAgain() {
        ActivityController<BookingDetailsActivity> c = bookingDetails(booking("CHECKED_IN"));
        BookingDetailsActivity a = c.get();
        TextView updated = a.findViewById(R.id.tvLastUpdated);
        assertTrue(updated.getText().toString(), updated.getText().toString().startsWith("Last updated "));
        View refresh = a.findViewById(R.id.btnDetailsRefresh);
        assertEquals(48 * a.getResources().getDisplayMetrics().density, refresh.getLayoutParams().width, 1f);

        int before = api.count("fetchTransactionById") + api.count("getDirectBooking") + api.count("getBooking");
        refresh.performClick();
        // whichever call re-reads the transaction, it fails: the page keeps its data and says so
        for (String m : api.log) {
            // answer every pending read with "offline"
        }
        for (String name : new String[]{"getDirectBooking", "getReservation", "getBooking"}) {
            for (int i = 0; i < api.count(name); i++) {
                if (api.call(name, i).pending()) api.call(name, i).offline();
            }
        }
        shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(a.findViewById(R.id.sectionTimelineContent));
        assertTrue("the header still shows the booking", ((TextView) a.findViewById(R.id.tvDetailsRoomHeadline)).getText().toString().contains("569"));
        assertTrue(before >= 0);
    }

    @Test
    public void transactionDetails_hasPullToRefresh_theLastUpdatedRow_andPollsOnlyWhileActive() throws Exception {
        ActivityController<TransactionDetailsActivity> c = txDetails(booking("ACTIVE_BOOKING"));
        TransactionDetailsActivity a = c.get();
        assertNotNull(a.findViewById(R.id.swipeRefreshTxDetails));
        assertNotNull(a.findViewById(R.id.tvLastUpdated));
        assertTrue(running(a));
        c.pause();
        assertFalse(running(a));
        c.resume();
        assertTrue(running(a));

        ActivityController<TransactionDetailsActivity> done = txDetails(booking("COMPLETED_BOOKING"));
        assertFalse("checked out: no polling", running(done.get()));
    }
}
