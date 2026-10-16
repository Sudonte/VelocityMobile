package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.network.dto.NotificationDto;
import com.example.velocitysuites.network.dto.PaginatedResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Booking Details refreshes the moment a receipt / verification notification for it arrives - not only on the 30s beat. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BookingDetailsLiveRefreshTest {

    private Context app;
    private ScreenTestSupport.FakeApi api;
    private RoomRepository repository;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        repository = ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
    }

    private BookingDetailsActivity open() {
        Booking b = new Booking("77", "1", "Deluxe", "Deluxe", "Oct 20, 2026", "Oct 22, 2026", 2, 5000.0, "Confirmed", "Oct 10, 2026");
        b.setHasBooking(true);
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, BookingDetailsActivity.newIntent(app, b)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        return a;
    }

    private void pollWith(NotificationDto... items) {
        repository.pollNotifications(null);
        api.<PaginatedResponse<NotificationDto>>last("getNotifications").succeed(ScreenTestSupport.notificationPage(items.length, items));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private int detailFetches() {
        return api.count("getReservation");
    }

    @Test
    public void aReceiptNotificationForThisBooking_refreshesTheOpenPageAtOnce() {
        open();
        pollWith(ScreenTestSupport.notification(1, "Older", "booking", 77L, true)); // the first load: nothing counts as new
        int before = detailFetches();

        pollWith(ScreenTestSupport.notification(2, "Check-In Receipt Ready", "booking", 77L, false),
                ScreenTestSupport.notification(1, "Older", "booking", 77L, true));

        assertEquals("the page re-read the booking straight away", before + 1, detailFetches());
    }

    @Test
    public void aNotificationAboutAnotherBooking_doesNotRedrawThisPage() {
        open();
        pollWith(ScreenTestSupport.notification(1, "Older", "booking", 5L, true));
        int before = detailFetches();

        pollWith(ScreenTestSupport.notification(2, "Payment verified", "booking", 999L, false),
                ScreenTestSupport.notification(1, "Older", "booking", 5L, true));

        assertEquals(before, detailFetches());
    }

    @Test
    public void aNonTransactionNotification_doesNotRefresh() {
        open();
        pollWith(ScreenTestSupport.notification(1, "Older", "booking", 77L, true));
        int before = detailFetches();

        pollWith(ScreenTestSupport.notification(2, "Promo", "promotion", 77L, false),
                ScreenTestSupport.notification(1, "Older", "booking", 77L, true));

        assertEquals(before, detailFetches());
    }
}
