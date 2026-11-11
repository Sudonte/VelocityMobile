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
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.CheckInWindow;
import com.example.velocitysuites.LandingActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.RoomBrowsingActivity;
import com.example.velocitysuites.StayDates;
import com.example.velocitysuites.network.dto.RoomsResponse;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

/**
 * 1.74.0 Part 1, auto-filled dates (the same on the guest web): the landing page's Available Rooms and Room Browsing open with
 * Check-in = today + 2 days on the hotel's clock (Manila) and Check-out the day after; the rooms are loaded for those dates at once;
 * and the pre-filled dates count as chosen, so Book now / Reserve work without touching them.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Release174Test {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        app.getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static String api(LocalDate d) {
        return d.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static LocalDate checkIn() {
        return CheckInWindow.today().plusDays(2);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> lastRoomsQuery() {
        return (Map<String, String>) api.lastArgs.get("getRoomsFresh")[0];
    }

    private RoomBrowsingActivity browsing(Intent intent) {
        RoomBrowsingActivity a = Robolectric.buildActivity(RoomBrowsingActivity.class, intent).setup().get();
        idle();
        return a;
    }

    // ---- Room Browsing

    @Test
    public void roomBrowsing_opensWithTodayPlusTwoAndOneNight_andLoadsTheRoomsForThoseDates() {
        RoomBrowsingActivity a = browsing(new Intent(app, RoomBrowsingActivity.class));

        String field = checkIn().format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()));
        assertEquals(field, ((TextView) a.findViewById(R.id.etCheckIn)).getText().toString());
        assertEquals(checkIn().plusDays(1).format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())),
                ((TextView) a.findViewById(R.id.etCheckOut)).getText().toString());
        assertEquals("one request, for the pre-filled dates", 1, api.count("getRoomsFresh"));
        assertEquals(api(checkIn()), lastRoomsQuery().get("check_in"));
        assertEquals(api(checkIn().plusDays(1)), lastRoomsQuery().get("check_out"));
        assertEquals("1 night", ((TextInputLayout) a.findViewById(R.id.checkOutLayout)).getHelperText().toString());
        api.<RoomsResponse>last("getRoomsFresh").succeed(LandingFixtures.rooms(LandingFixtures.roomType(1, "Deluxe", "1850.00", 5)));
        idle();
        assertTrue("once the rooms are in, Search is ready for other dates", a.findViewById(R.id.btnSearchAvailability).isEnabled());
    }

    @Test
    public void roomBrowsing_thePrefilledDatesCountAsChosen_bookNowAndReserveWorkWithoutTouchingThem() {
        RoomBrowsingActivity a = browsing(new Intent(app, RoomBrowsingActivity.class));
        api.<RoomsResponse>last("getRoomsFresh").succeed(LandingFixtures.rooms(LandingFixtures.roomType(1, "Deluxe", "1850.00", 5)));
        idle();

        assertTrue(a.requireDatesFirst());
        assertNull("no dates-first message", ((TextInputLayout) a.findViewById(R.id.checkInLayout)).getError());
        a.onBookNowClick(WizardDriver.room("1", 1850));
        idle();
        assertNotNull("Book now opens the room's sheet at once", org.robolectric.shadows.ShadowDialog.getLatestDialog());
        assertTrue(org.robolectric.shadows.ShadowDialog.getLatestDialog().isShowing());
    }

    @Test
    public void roomBrowsing_takesTheDatesCarriedFromTheLandingPage() {
        Calendar in = StayDates.defaultCheckIn();
        in.add(Calendar.DAY_OF_MONTH, 5);
        Calendar out = (Calendar) in.clone();
        out.add(Calendar.DAY_OF_MONTH, 3);
        RoomBrowsingActivity a = browsing(new Intent(app, RoomBrowsingActivity.class)
                .putExtra(StayDates.EXTRA_CHECK_IN_UTC, StayDates.toUtcMidnight(in)).putExtra(StayDates.EXTRA_CHECK_OUT_UTC, StayDates.toUtcMidnight(out)));

        assertEquals(api(checkIn().plusDays(5)), lastRoomsQuery().get("check_in"));
        assertEquals(api(checkIn().plusDays(8)), lastRoomsQuery().get("check_out"));
        assertEquals("3 nights", ((TextInputLayout) a.findViewById(R.id.checkOutLayout)).getHelperText().toString());
    }

    @Test
    public void roomBrowsing_carriedDatesThatAreNoLongerBookable_fallBackToThePrefilledOnes() {
        Calendar past = StayDates.defaultCheckIn();
        past.add(Calendar.DAY_OF_MONTH, -10);
        browsing(new Intent(app, RoomBrowsingActivity.class)
                .putExtra(StayDates.EXTRA_CHECK_IN_UTC, StayDates.toUtcMidnight(past)).putExtra(StayDates.EXTRA_CHECK_OUT_UTC, StayDates.toUtcMidnight(past) + 86_400_000L));

        assertEquals(api(checkIn()), lastRoomsQuery().get("check_in"));
    }

    // ---- the landing page's Available Rooms

    private LandingActivity landing() {
        LandingActivity a = Robolectric.buildActivity(LandingActivity.class).setup().get();
        idle();
        return a;
    }

    @Test
    public void landing_hasCheckInAndCheckOutPrefilled_andLoadsTheRoomsForThem() {
        LandingActivity a = landing();

        DateTimeFormatter field = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
        assertEquals(checkIn().format(field), ((TextView) a.findViewById(R.id.etLandingCheckIn)).getText().toString());
        assertEquals(checkIn().plusDays(1).format(field), ((TextView) a.findViewById(R.id.etLandingCheckOut)).getText().toString());
        assertEquals("Check-in", ((TextInputLayout) a.findViewById(R.id.tilLandingCheckIn)).getHint().toString());
        assertEquals("Check-out", ((TextInputLayout) a.findViewById(R.id.tilLandingCheckOut)).getHint().toString());
        String note = ((TextView) a.findViewById(R.id.tvLandingDatesNote)).getText().toString();
        assertTrue(note, note.startsWith("1 night · earliest check-in "));
        assertEquals(1, api.count("getRoomsFresh"));
        assertEquals(api(checkIn()), lastRoomsQuery().get("check_in"));
        assertEquals(api(checkIn().plusDays(1)), lastRoomsQuery().get("check_out"));
    }

    @Test
    public void landing_changingADate_reloadsTheRooms_andKeepsAtLeastOneNight() {
        LandingActivity a = landing();
        Calendar later = StayDates.defaultCheckIn();
        later.add(Calendar.DAY_OF_MONTH, 4);

        a.onDatePicked(true, later); // a check-in after the old check-out: the check-out moves to the day after
        idle();

        assertEquals(2, api.count("getRoomsFresh"));
        assertEquals(api(checkIn().plusDays(4)), lastRoomsQuery().get("check_in"));
        assertEquals(api(checkIn().plusDays(5)), lastRoomsQuery().get("check_out"));

        Calendar out = (Calendar) later.clone();
        out.add(Calendar.DAY_OF_MONTH, 3);
        a.onDatePicked(false, out);
        idle();
        assertEquals(api(checkIn().plusDays(7)), lastRoomsQuery().get("check_out"));
        assertTrue(((TextView) a.findViewById(R.id.tvLandingDatesNote)).getText().toString().startsWith("3 nights"));

        // a check-out on or before the check-in is not taken
        a.onDatePicked(false, later);
        idle();
        assertEquals(api(checkIn().plusDays(7)), lastRoomsQuery().get("check_out"));
    }

    @Test
    public void landing_bookNowBeforeSigningIn_keepsTheDatesWithTheRoom_forAfterSignIn() {
        LandingActivity a = landing();

        a.onBookNowClick(WizardDriver.room("1", 1850));
        idle();

        android.content.SharedPreferences prefs = app.getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE);
        assertEquals("1", prefs.getString("pendingLandingRoomIds", null));
        long in = StayDates.toUtcMidnight(StayDates.defaultCheckIn());
        assertEquals(in, prefs.getLong("pendingLandingCheckInUtc", -1L));
        assertEquals(in + 86_400_000L, prefs.getLong("pendingLandingCheckOutUtc", -1L));
        // signed in: the same selection opens Room Browsing on those dates
        Intent after = com.example.velocitysuites.PendingAccess.postAuthIntent(app, "Ana");
        assertEquals(RoomBrowsingActivity.class.getName(), after.getComponent().getClassName());
        assertEquals(in, after.getLongExtra(StayDates.EXTRA_CHECK_IN_UTC, -1L));
        assertEquals(in + 86_400_000L, after.getLongExtra(StayDates.EXTRA_CHECK_OUT_UTC, -1L));
        assertFalse("used once", prefs.contains("pendingLandingCheckInUtc"));
    }

    @Test
    public void landing_theDateFieldsFitSideBySideAt360dp_withoutCuttingTheDate() {
        LandingActivity a = landing();
        View decor = a.getWindow().getDecorView();
        int w = app.getResources().getDisplayMetrics().widthPixels, h = app.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);

        for (int id : new int[]{R.id.etLandingCheckIn, R.id.etLandingCheckOut}) {
            TextView field = a.findViewById(id);
            assertTrue("the field is laid out", field.getWidth() > 0);
            assertEquals("the whole date shows (no ellipsis)", 0, field.getLayout().getEllipsisCount(0));
            assertTrue("a comfortable touch target", ((View) field.getParent().getParent()).getHeight() >= 48 * app.getResources().getDisplayMetrics().density - 1);
        }
        View in = a.findViewById(R.id.tilLandingCheckIn), out = a.findViewById(R.id.tilLandingCheckOut);
        assertTrue("side by side", in.getRight() <= out.getLeft());
    }
}
