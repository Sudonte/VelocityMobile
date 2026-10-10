package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.PaymentActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowNetworkCapabilities;
import org.robolectric.shadows.ShadowToast;

import java.time.Duration;

/** Reaching the payment screen for a confirmed booking (e.g. from an old notification): a message that stays, not a toast. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FrontDeskPaymentScreenTest {

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities caps = ShadowNetworkCapabilities.newInstance();
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        shadowOf(cm).setNetworkCapabilities(cm.getActiveNetwork(), caps);
    }

    private PaymentActivity open(DirectBookingResponseDto booking) {
        Intent intent = new Intent(app, PaymentActivity.class).putExtra("BOOKING_ID", String.valueOf(booking.id));
        PaymentActivity a = Robolectric.buildActivity(PaymentActivity.class, intent).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(booking));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
        return a;
    }

    private static boolean visible(PaymentActivity a, int id) {
        return a.findViewById(id).getVisibility() == View.VISIBLE;
    }

    @Test
    public void confirmedBookingWithABalance_showsTheFrontDeskMessage_andAWayBack() {
        PaymentActivity a = open(ScreenTestSupport.directBooking(588, 2000));

        assertTrue(visible(a, R.id.frontDeskSection));
        assertEquals(a.getString(R.string.front_desk_balance_message, "₱2,000.00"),
                ((TextView) a.findViewById(R.id.tvFrontDeskMessage)).getText().toString());
        assertFalse("no payment form", visible(a, R.id.checkoutSummarySection));
        assertFalse(visible(a, R.id.emptyStateSection));
        assertEquals("no disappearing pop-up", null, ShadowToast.getTextOfLatestToast());

        a.findViewById(R.id.btnFrontDeskBack).performClick();
        assertTrue("the way back closes the screen", a.isFinishing());
    }

    @Test
    public void onlyVerifiedPaymentsReduceTheBalanceQuoted() {
        DirectBookingResponseDto booking = ScreenTestSupport.directBooking(588, 2000);
        booking.payments.add(ScreenTestSupport.payment(1, "800.00", "completed", "gcash"));
        booking.payments.add(ScreenTestSupport.payment(2, "500.00", "pending", "gcash"));
        PaymentActivity a = open(booking);

        assertEquals(a.getString(R.string.front_desk_balance_message, "₱1,200.00"),
                ((TextView) a.findViewById(R.id.tvFrontDeskMessage)).getText().toString());
    }

    @Test
    public void fullyPaidBooking_showsNoFrontDeskMessage() {
        DirectBookingResponseDto booking = ScreenTestSupport.directBooking(588, 2000);
        booking.payments.add(ScreenTestSupport.payment(1, "2000.00", "completed", "gcash"));
        PaymentActivity a = open(booking);

        assertFalse(visible(a, R.id.frontDeskSection));
        assertTrue("the usual nothing-due state instead", visible(a, R.id.emptyStateSection));
    }
}
