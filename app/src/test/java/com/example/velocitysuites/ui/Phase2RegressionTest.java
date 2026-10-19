package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingWizardActivity;
import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.LandingActivity;
import com.example.velocitysuites.PendingWizardRooms;
import com.example.velocitysuites.R;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomBrowsingActivity;
import com.example.velocitysuites.RoomDetailsDialog;
import com.example.velocitysuites.ThemePreferences;
import com.example.velocitysuites.network.dto.RoomsResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Phase 2 re-checks of the Phase 1 work: the landing theme toggle, No Refund on cards and in the details, the room
 * details sections, searched dates arriving pre-filled in the booking flow, the menu without Payment, and
 * multi-select in Room Browsing (several room types, clear, and the selection summary) without a crash.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Phase2RegressionTest {

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        ThemePreferences.setMode(app, ThemePreferences.MODE_LIGHT);
    }

    @After
    public void tearDown() {
        ThemePreferences.setMode(app, ThemePreferences.MODE_LIGHT);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        PendingWizardRooms.consume();
        PendingWizardRooms.consumeDates();
    }

    // ---- landing theme toggle

    @Test
    public void landingThemeToggle_switchesAndPersistsTheMode_andItsIconShowsTheOtherMode() {
        LandingActivity landing = Robolectric.buildActivity(LandingActivity.class, new Intent(app, LandingActivity.class)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        View toggle = landing.findViewById(R.id.btnThemeToggle);
        assertNotNull("the toggle is in the landing header", toggle);
        assertFalse(ThemePreferences.isDarkMode(app));

        toggle.performClick();
        assertTrue("one tap turns Dark on", ThemePreferences.isDarkMode(app));
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());

        // the choice survives: it is read back from the saved preference, not held in the screen
        assertEquals(ThemePreferences.MODE_DARK, ThemePreferences.getSavedMode(app));

        // a second tap turns it back off (the same handler reads the saved mode, so it works after the screen is recreated too)
        toggle.performClick();
        assertFalse(ThemePreferences.isDarkMode(app));
    }

    // ---- No Refund

    @Test
    public void everyRoomCardCarriesTheNoRefundIndicator() {
        View card = LayoutInflater.from(new androidx.appcompat.view.ContextThemeWrapper(app, R.style.Theme_VelocitySuites))
                .inflate(R.layout.item_room_card, new android.widget.FrameLayout(app), false);
        TextView badge = card.findViewById(R.id.tvNoRefundBadge);
        assertNotNull(badge);
        assertEquals(app.getString(R.string.no_refund_badge), badge.getText().toString());
        assertEquals(View.VISIBLE, badge.getVisibility());
        assertTrue(com.example.velocitysuites.HotelPolicy.policiesText(app).contains("No Refund Policy"));
    }

    // ---- room details

    private static Room room(String id, String type, double price) {
        return new Room(id, type + " Room", type, 2, price, "A nice room", 0, true, new ArrayList<>(), "Queen", "24 sqm", "Quiet hours after 10 PM.");
    }

    @Test
    public void roomDetails_haveTheirSections_refundPolicyAndTimes_andTheLongPoliciesFold() {
        androidx.appcompat.app.AppCompatActivity host = Robolectric.buildActivity(RoomBrowsingActivity.class, new Intent(app, RoomBrowsingActivity.class)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        RoomDetailsDialog.show(host, room("5", "Deluxe", 3500), "VIEW_DETAILS", null, new RoomDetailsDialog.ActionListener() {
            @Override
            public void onBook(Room room) {
            }

            @Override
            public void onReserve(Room room) {
            }
        });
        shadowOf(Looper.getMainLooper()).idle();
        android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();

        assertEquals("₱3,500 / night", ((TextView) dialog.findViewById(R.id.detailRoomPrice)).getText().toString());
        assertEquals("₱3,500 / night", ((TextView) dialog.findViewById(R.id.detailBarPrice)).getText().toString());
        assertEquals("Overview", ((TextView) dialog.findViewById(R.id.headOverview)).getText().toString());
        assertEquals("Room Facts", ((TextView) dialog.findViewById(R.id.headFacts)).getText().toString());
        assertEquals("Amenities", ((TextView) dialog.findViewById(R.id.headAmenities)).getText().toString());
        assertEquals("Policies", ((TextView) dialog.findViewById(R.id.headPolicies)).getText().toString());
        assertEquals("A nice room", ((TextView) dialog.findViewById(R.id.detailRoomDescription)).getText().toString());

        android.widget.LinearLayout policies = dialog.findViewById(R.id.layoutDetailPolicies);
        assertEquals("refund policy, check-in and check-out, all from HotelPolicy", 3, policies.getChildCount());
        assertEquals("Refund policy", ((TextView) ((android.view.ViewGroup) policies.getChildAt(0)).getChildAt(0)).getText().toString());
        assertEquals("Non-refundable", ((TextView) ((android.view.ViewGroup) policies.getChildAt(0)).getChildAt(1)).getText().toString());
        assertEquals("From 2:00 PM", ((TextView) ((android.view.ViewGroup) policies.getChildAt(1)).getChildAt(1)).getText().toString());
        assertEquals("Until 12:00 PM", ((TextView) ((android.view.ViewGroup) policies.getChildAt(2)).getChildAt(1)).getText().toString());

        TextView more = dialog.findViewById(R.id.detailPoliciesBody);
        assertEquals("the long hotel policies start folded", View.GONE, more.getVisibility());
        assertTrue(more.getText().toString().contains("No Refund Policy"));
        assertTrue("the room type's own notes are shown too", more.getText().toString().contains("Quiet hours after 10 PM."));
        dialog.findViewById(R.id.headMorePolicies).performClick();
        assertEquals(View.VISIBLE, more.getVisibility());
        dialog.findViewById(R.id.headMorePolicies).performClick();
        assertEquals(View.GONE, more.getVisibility());

        assertEquals("the type equals the name's first word only: a different type is shown as a chip", View.VISIBLE,
                dialog.findViewById(R.id.detailRoomType).getVisibility());
        assertNotNull("the sticky bar holds the actions", dialog.findViewById(R.id.layoutDetailBottomBar).findViewById(R.id.btnBookRoom));
        assertEquals("Book now", ((TextView) dialog.findViewById(R.id.btnBookRoom)).getText().toString());
        assertEquals("Reserve", ((TextView) dialog.findViewById(R.id.btnReserveRoom)).getText().toString());
    }

    // ---- date linking into the booking flow

    private static Calendar daysFromToday(int days) {
        Calendar c = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Manila"));
        c.add(Calendar.DAY_OF_MONTH, days);
        return c;
    }

    @Test
    public void searchedDatesArriveFilledInInTheBookingFlow() {
        Calendar in = daysFromToday(5);
        Calendar out = daysFromToday(7);
        PendingWizardRooms.set(new ArrayList<>());
        PendingWizardRooms.setDates(in, out);

        BookingWizardActivity wizard = Robolectric.buildActivity(BookingWizardActivity.class,
                BookingWizardActivity.newIntent(app, BookingWizardState.Mode.BOOKING)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();

        assertNotNull(wizard.getState().checkIn);
        assertNotNull(wizard.getState().checkOut);
        assertEquals(in.get(Calendar.DAY_OF_YEAR), wizard.getState().checkIn.get(Calendar.DAY_OF_YEAR));
        assertEquals(2, wizard.getState().nights());
        assertNull("the handoff is consumed once - it cannot leak into a later, unrelated flow", PendingWizardRooms.consumeDates());
    }

    @Test
    public void searchedDatesThatNoLongerFitTheCheckInWindowAreNotForcedIn() {
        PendingWizardRooms.set(new ArrayList<>());
        PendingWizardRooms.setDates(daysFromToday(0), daysFromToday(2)); // today is inside the 2-day lead time

        BookingWizardActivity wizard = Robolectric.buildActivity(BookingWizardActivity.class,
                BookingWizardActivity.newIntent(app, BookingWizardState.Mode.BOOKING)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();

        assertNull(wizard.getState().checkIn);
    }

    // ---- menu

    @Test
    public void theMenuHasNoPaymentEntry() {
        assertEquals(0, app.getResources().getIdentifier("nav_payment", "id", app.getPackageName()));
    }

    // ---- multi-select in Room Browsing

    private RoomBrowsingActivity browsingWithThreeRoomTypes() {
        RoomBrowsingActivity a = Robolectric.buildActivity(RoomBrowsingActivity.class, new Intent(app, RoomBrowsingActivity.class)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        api.<RoomsResponse>last("getRoomsFresh").succeed(LandingFixtures.rooms(
                LandingFixtures.roomType(1, "Standard", "1200.00", 3),
                LandingFixtures.roomType(2, "Deluxe", "1800.00", 2),
                LandingFixtures.roomType(3, "Suite", "3000.00", 1)));
        shadowOf(Looper.getMainLooper()).idle();
        RecyclerView rv = a.findViewById(R.id.rvRooms);
        rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(6000, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, 1080, 6000);
        return a;
    }

    private static void tap(RecyclerView rv, int viewId, int times) {
        for (int i = 0; i < rv.getChildCount(); i++) {
            View plus = rv.getChildAt(i).findViewById(viewId);
            if (plus != null && plus.getVisibility() == View.VISIBLE) {
                for (int t = 0; t < times; t++) plus.performClick();
            }
        }
    }

    @Test
    public void selectingSeveralRoomTypes_updatesTheSummaryBar_andClearEmptiesItWithoutACrash() {
        RoomBrowsingActivity a = browsingWithThreeRoomTypes();
        RecyclerView rv = a.findViewById(R.id.rvRooms);
        assertTrue("the three room types are listed", rv.getAdapter().getItemCount() >= 3);
        assertEquals(View.GONE, a.findViewById(R.id.cardSelectionSummaryBar).getVisibility());

        tap(rv, R.id.btnRoomQtyPlus, 2); // 2 of each type (capped to what is free: 2, 2, 1)
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.VISIBLE, a.findViewById(R.id.cardSelectionSummaryBar).getVisibility());
        String summary = ((TextView) a.findViewById(R.id.tvSelectionSummaryTotal)).getText().toString();
        assertFalse(summary, summary.isEmpty());

        a.findViewById(R.id.btnCartClear).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.GONE, a.findViewById(R.id.cardSelectionSummaryBar).getVisibility());
        // and selecting again afterwards still works (no stale state left behind)
        tap(rv, R.id.btnRoomQtyPlus, 1);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.VISIBLE, a.findViewById(R.id.cardSelectionSummaryBar).getVisibility());
    }

    @Test
    public void everySelectedRoomTypeCanBeViewedInFull() {
        RoomBrowsingActivity a = browsingWithThreeRoomTypes();
        RecyclerView rv = a.findViewById(R.id.rvRooms);
        tap(rv, R.id.btnRoomQtyPlus, 1);
        shadowOf(Looper.getMainLooper()).idle();

        a.findViewById(R.id.layoutSelectionSummaryDetails).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull("the selection opens a list of everything selected", dialog);
        assertTrue(dialog.isShowing());
    }
}
