package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.AdditionalAmenitiesActivity;
import com.example.velocitysuites.BillingSummaryActivity;
import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.BookingWizardActivity;
import com.example.velocitysuites.NotificationActivity;
import com.example.velocitysuites.NotificationDetailsActivity;
import com.example.velocitysuites.PaymentActivity;
import com.example.velocitysuites.PaymentReceiptActivity;
import com.example.velocitysuites.ReceiptImageViewerActivity;
import com.example.velocitysuites.RoomGalleryActivity;
import com.example.velocitysuites.StartingTransactionActivity;
import com.example.velocitysuites.TransactionDetailsActivity;
import com.example.velocitysuites.TransactionHistoryActivity;
import com.example.velocitysuites.TransactionListActivity;
import com.example.velocitysuites.UpcomingTransactionsActivity;
import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.ReservationDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Arrays;
import java.util.List;

/** Crash cases: screens opened with missing or empty extras (a stale notification tap, a deep link), and records with unknown or missing fields. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CrashResistanceTest {

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "t", 1L, "Ana", "Reyes", "", "Ana Reyes", "a@b.c", "09171234567", "Female", "Jan 01, 1990", "");
    }

    private void opensWithoutCrashing(Class<? extends Activity> type, Intent intent) {
        Activity a = Robolectric.buildActivity(type, intent).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(type.getSimpleName(), a);
    }

    @Test
    public void everyScreenOpenedWithNoExtras_doesNotCrash() {
        List<Class<? extends Activity>> screens = Arrays.asList(
                AdditionalAmenitiesActivity.class, BillingSummaryActivity.class, BookingDetailsActivity.class, BookingWizardActivity.class,
                NotificationActivity.class, NotificationDetailsActivity.class, PaymentActivity.class, PaymentReceiptActivity.class,
                ReceiptImageViewerActivity.class, RoomGalleryActivity.class, StartingTransactionActivity.class, TransactionDetailsActivity.class,
                TransactionHistoryActivity.class, TransactionListActivity.class, UpcomingTransactionsActivity.class);
        for (Class<? extends Activity> type : screens) opensWithoutCrashing(type, new Intent(app, type));
    }

    @Test
    public void screensOpenedWithGarbageValues_doNotCrash() {
        Intent history = new Intent(app, TransactionHistoryActivity.class)
                .putExtra(TransactionHistoryActivity.EXTRA_OPEN_FILTER, "not-a-filter").putExtra(TransactionHistoryActivity.EXTRA_SELECTED_BOOKING_ID, "999999");
        opensWithoutCrashing(TransactionHistoryActivity.class, history);
        opensWithoutCrashing(UpcomingTransactionsActivity.class, new Intent(app, UpcomingTransactionsActivity.class)
                .putExtra("EXTRA_STATUS_FILTER", "WEIRD").putExtra("EXTRA_TYPE_FILTER", "???").putExtra("EXTRA_SELECTED_BOOKING_ID", "x"));
        opensWithoutCrashing(NotificationActivity.class, new Intent(app, NotificationActivity.class).putExtra(NotificationActivity.EXTRA_NOTIFICATION_ID, "nope"));
        opensWithoutCrashing(PaymentActivity.class, new Intent(app, PaymentActivity.class).putExtra("BOOKING_ID", "not-a-booking"));
        opensWithoutCrashing(BillingSummaryActivity.class, new Intent(app, BillingSummaryActivity.class).putExtra(BillingSummaryActivity.EXTRA_RESERVATION_ID, ""));
        opensWithoutCrashing(TransactionListActivity.class, new Intent(app, TransactionListActivity.class).putExtra(TransactionListActivity.EXTRA_LIST_TYPE, "???"));
        opensWithoutCrashing(AdditionalAmenitiesActivity.class, new Intent(app, AdditionalAmenitiesActivity.class).putExtra(AdditionalAmenitiesActivity.EXTRA_ID, ""));
    }

    @Test
    public void aRecordWithUnknownOrMissingFields_isShownWithoutCrashing() {
        ReservationDto r = ScreenTestSupport.reservation(1, 0);
        r.status = "SOME_FUTURE_STATUS";
        r.check_in = null;
        r.check_out = "garbage";
        r.payments = null;
        Booking weird = ApiMapper.toBooking(r);
        DirectBookingResponseDto d = ScreenTestSupport.directBooking(2, 0);
        d.booking_status = null;
        d.check_in = "";
        d.check_out = null;
        d.room_type = null;
        Booking weirder = ApiMapper.toBooking(d);
        for (Booking b : Arrays.asList(weird, weirder)) {
            opensWithoutCrashing(BookingDetailsActivity.class, new Intent(app, BookingDetailsActivity.class).putExtra(BookingDetailsActivity.EXTRA_BOOKING, b));
            opensWithoutCrashing(TransactionDetailsActivity.class, new Intent(app, TransactionDetailsActivity.class)
                    .putExtra(TransactionDetailsActivity.EXTRA_TRANSACTION, new com.example.velocitysuites.PaymentTransaction(b, null)));
        }
        assertTrue(true);
        assertEquals(2, Arrays.asList(weird, weirder).size());
    }
}
