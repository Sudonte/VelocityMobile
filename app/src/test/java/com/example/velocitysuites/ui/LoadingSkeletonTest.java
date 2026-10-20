package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingAndReservationActivity;
import com.example.velocitysuites.NotificationActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.TransactionHistoryActivity;
import com.example.velocitysuites.network.SessionManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** While a list loads the screen shows placeholder cards - never a blank page or a flash of "nothing yet". */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LoadingSkeletonTest {

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "t", 1L, "Ana", "Reyes", "", "Ana Reyes", "a@b.c", "09171234567", "Female", "Jan 01, 1990", "");
    }

    @Test
    public void transactionHistory_showsPlaceholderCardsUntilTheFirstAnswer() {
        TransactionHistoryActivity a = Robolectric.buildActivity(TransactionHistoryActivity.class).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        View loading = a.findViewById(R.id.layoutInitialLoading);
        assertEquals(View.VISIBLE, loading.getVisibility());
        LinearLayout cards = loading.findViewById(R.id.skeletonCards);
        assertEquals(4, cards.getChildCount());
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(), ScreenTestSupport.directBookings());
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("gone once the answer is in", View.GONE, loading.getVisibility());
    }

    @Test
    public void notifications_showPlaceholderCardsWhileLoading() {
        NotificationActivity a = Robolectric.buildActivity(NotificationActivity.class).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        LinearLayout cards = a.findViewById(R.id.skeletonCards);
        assertNotNull(cards);
        assertEquals(4, cards.getChildCount());
    }

    @Test
    public void bookingAndReservation_showsPlaceholdersBeforeTheFirstAnswer_thenTheRealStateNotAFlashOfEmpty() {
        BookingAndReservationActivity a = Robolectric.buildActivity(BookingAndReservationActivity.class).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        LinearLayout skeleton = a.findViewById(R.id.skeletonBookings);
        assertEquals(View.VISIBLE, skeleton.getVisibility());
        assertEquals(4, skeleton.getChildCount());
        assertEquals("not the empty state yet", View.GONE, a.findViewById(R.id.tvListEmpty).getVisibility());
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(), ScreenTestSupport.directBookings());
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.GONE, skeleton.getVisibility());
        assertEquals("a genuinely empty list now says so", View.VISIBLE, a.findViewById(R.id.tvListEmpty).getVisibility());
        assertFalse(skeleton.getVisibility() == View.VISIBLE);
        assertTrue(true);
    }
}
