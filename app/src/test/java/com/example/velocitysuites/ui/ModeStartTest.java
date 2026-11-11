package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.PendingWizardRooms;
import com.example.velocitysuites.R;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomBrowsingActivity;
import com.example.velocitysuites.StartingTransactionActivity;
import com.example.velocitysuites.network.dto.RoomsResponse;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * 1.73.2: starting from "New reservation" opens Room Browsing in reservation mode (a banner with "Switch to booking", only Reserve /
 * Reserve Selected), and from "New booking" in booking mode (only Book now / Book Selected). The dates rule stays; other entry points
 * keep offering both.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ModeStartTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        PendingWizardRooms.set(java.util.Collections.<Room>emptyList());
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private RoomBrowsingActivity open(BookingWizardState.Mode mode) {
        Intent in = StartingTransactionActivity.newIntentFromNewButton(app, mode);
        assertEquals(RoomBrowsingActivity.class.getName(), in.getComponent().getClassName());
        return openWith(in);
    }

    private RoomBrowsingActivity openWith(Intent in) {
        RoomBrowsingActivity a = Robolectric.buildActivity(RoomBrowsingActivity.class, in).setup().get();
        idle();
        RoomsResponse rooms = LandingFixtures.rooms(LandingFixtures.roomType(1, "Deluxe", "1850.00", 5));
        api.<RoomsResponse>last("getRoomsFresh").succeed(rooms);
        idle();
        View root = a.getWindow().getDecorView();
        int w = app.getResources().getDisplayMetrics().widthPixels, h = app.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        return a;
    }

    private static View firstCard(RoomBrowsingActivity a) {
        RecyclerView rv = a.findViewById(R.id.rvRooms);
        RecyclerView.ViewHolder h = null;
        for (int i = 0; i < rv.getAdapter().getItemCount() && h == null; i++) h = rv.findViewHolderForAdapterPosition(i);
        assertNotNull("a card is laid out", h);
        return h.itemView;
    }

    private static String text(RoomBrowsingActivity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    @Test
    public void newReservation_opensInReservationMode_withTheBannerAndOnlyReserve() {
        RoomBrowsingActivity a = open(BookingWizardState.Mode.RESERVATION);

        assertEquals(View.VISIBLE, a.findViewById(R.id.layoutModeBanner).getVisibility());
        assertEquals("You're making a reservation (pay later)", text(a, R.id.tvModeBanner));
        assertEquals("Switch to booking", text(a, R.id.tvSwitchMode));
        View card = firstCard(a);
        assertEquals(View.VISIBLE, card.findViewById(R.id.btnReserveNow).getVisibility());
        assertEquals("Book now is not offered", View.GONE, card.findViewById(R.id.btnQuickBook).getVisibility());
        assertEquals(View.VISIBLE, a.findViewById(R.id.btnCartReserve).getVisibility());
        assertEquals("Book Selected is not offered", View.GONE, a.findViewById(R.id.btnCartBook).getVisibility());
        assertEquals("Reserve Selected", ((TextView) a.findViewById(R.id.btnCartReserve)).getText().toString());
    }

    @Test
    public void newBooking_opensInBookingMode_withTheBannerAndOnlyBookNow() {
        RoomBrowsingActivity a = open(BookingWizardState.Mode.BOOKING);

        assertEquals("You're making a booking (pay now)", text(a, R.id.tvModeBanner));
        assertEquals("Switch to reservation", text(a, R.id.tvSwitchMode));
        View card = firstCard(a);
        assertEquals(View.VISIBLE, card.findViewById(R.id.btnQuickBook).getVisibility());
        assertEquals("Reserve is not offered", View.GONE, card.findViewById(R.id.btnReserveNow).getVisibility());
        assertEquals(View.GONE, a.findViewById(R.id.btnCartReserve).getVisibility());
        assertEquals(View.VISIBLE, a.findViewById(R.id.btnCartBook).getVisibility());
        assertEquals("Book Selected", ((TextView) a.findViewById(R.id.btnCartBook)).getText().toString());
    }

    @Test
    public void theSwitchLink_changesTheMode_bannerCardsAndBar() {
        RoomBrowsingActivity a = open(BookingWizardState.Mode.RESERVATION);

        a.findViewById(R.id.tvSwitchMode).performClick();
        idle();

        assertEquals("You're making a booking (pay now)", text(a, R.id.tvModeBanner));
        View card = firstCard(a);
        assertEquals(View.VISIBLE, card.findViewById(R.id.btnQuickBook).getVisibility());
        assertEquals(View.GONE, card.findViewById(R.id.btnReserveNow).getVisibility());
        assertEquals(View.GONE, a.findViewById(R.id.btnCartReserve).getVisibility());
        assertEquals(View.VISIBLE, a.findViewById(R.id.btnCartBook).getVisibility());

        a.findViewById(R.id.tvSwitchMode).performClick();
        idle();
        assertEquals("You're making a reservation (pay later)", text(a, R.id.tvModeBanner));
    }

    @Test
    public void theDatesRuleStays_inBothModes() {
        for (BookingWizardState.Mode mode : BookingWizardState.Mode.values()) {
            RoomBrowsingActivity a = open(mode);
            assertNull(mode + ": the pre-filled dates count, so there is no message", ((TextInputLayout) a.findViewById(R.id.checkInLayout)).getError());
            // the rule is for a guest who cleared the dates
            a.findViewById(R.id.checkInLayout).findViewById(com.google.android.material.R.id.text_input_end_icon).performClick();
            idle();
            a.onReserveNowClick(WizardDriver.room("1", 1000));
            a.onBookNowClick(WizardDriver.room("1", 1000));
            idle();
            assertNull("still no booking screen without dates", shadowOf(a).getNextStartedActivity());
            assertEquals(mode + ": the message is under the date field", "Choose your check-in and check-out dates first.",
                    ((TextInputLayout) a.findViewById(R.id.checkInLayout)).getError().toString());
        }
    }

    @Test
    public void otherEntryPoints_keepBothActions_andNoBanner() {
        // the plain way in (landing, Book Again, the payment screen's Book Now) carries no mode
        Intent plain = StartingTransactionActivity.newIntent(app, BookingWizardState.Mode.RESERVATION);
        assertNull(plain.getStringExtra(RoomBrowsingActivity.EXTRA_MODE));
        RoomBrowsingActivity a = openWith(new Intent(app, RoomBrowsingActivity.class));

        assertEquals(View.GONE, a.findViewById(R.id.layoutModeBanner).getVisibility());
        View card = firstCard(a);
        assertEquals(View.VISIBLE, card.findViewById(R.id.btnReserveNow).getVisibility());
        assertEquals(View.VISIBLE, card.findViewById(R.id.btnQuickBook).getVisibility());
        assertEquals(View.VISIBLE, a.findViewById(R.id.btnCartReserve).getVisibility());
        assertEquals(View.VISIBLE, a.findViewById(R.id.btnCartBook).getVisibility());
    }

    @Test
    public void theModeSurvivesRecreation() {
        RoomBrowsingActivity a = open(BookingWizardState.Mode.RESERVATION);
        a.findViewById(R.id.tvSwitchMode).performClick();
        android.os.Bundle saved = new android.os.Bundle();
        org.robolectric.android.controller.ActivityController<RoomBrowsingActivity> c = Robolectric.buildActivity(RoomBrowsingActivity.class,
                StartingTransactionActivity.newIntentFromNewButton(app, BookingWizardState.Mode.RESERVATION)).setup();
        idle();
        c.get().findViewById(R.id.tvSwitchMode).performClick();
        c.saveInstanceState(saved);
        RoomBrowsingActivity again = Robolectric.buildActivity(RoomBrowsingActivity.class,
                StartingTransactionActivity.newIntentFromNewButton(app, BookingWizardState.Mode.RESERVATION)).setup(saved).get();
        idle();
        assertEquals("booking mode was kept, not reset to the launch mode", "You're making a booking (pay now)", text(again, R.id.tvModeBanner));
        assertTrue(a != null);
    }
}
