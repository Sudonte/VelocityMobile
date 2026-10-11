package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.DashboardActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.RoomBrowsingActivity;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.ReservationDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The Dashboard for a first-time guest: the sections read in a sensible order, the date and time sit above the Next Stay
 * card (in hotel time), the Next Stay card has loading / error (Retry) / empty (one action) / content states and opens the
 * stay's details, and the quick actions each have an icon, a text label and a 48dp+ touch target.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DashboardScreenTest {

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() throws Exception {
        // NavUtils' click debounce keeps its last click time in a static; the clock restarts for every test, so clear it.
        java.lang.reflect.Field last = com.example.velocitysuites.NavUtils.class.getDeclaredField("lastClickAtMs");
        last.setAccessible(true);
        last.setLong(null, Long.MIN_VALUE / 2);
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com",
                "09171234567", "Female", "Jan 01, 1990", "");
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private DashboardActivity launch() {
        ActivityController<DashboardActivity> controller = Robolectric.buildActivity(DashboardActivity.class);
        controller.setup();
        idle();
        return controller.get();
    }

    private static LocalDate manilaToday() {
        return LocalDate.now(ZoneId.of("Asia/Manila"));
    }

    /** A reservation checking in {@code daysAway} days from today (hotel time) for {@code nights} nights, total ₱1,800, unpaid. */
    private static ReservationDto stay(long id, int daysAway, int nights) {
        ReservationDto r = ScreenTestSupport.reservation(id, 1800);
        r.check_in = manilaToday().plusDays(daysAway).toString();
        r.check_out = manilaToday().plusDays(daysAway + nights).toString();
        return r;
    }

    private void answer(ReservationDto... reservations) {
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(reservations), ScreenTestSupport.directBookings());
        idle();
    }

    private static String text(DashboardActivity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    /** The screen's click debounce (600 ms) is measured on the real clock, so let time pass before each tap. */
    private static void tap(View v) {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        v.performClick();
    }

    /** The next screen the Dashboard started - the notification-permission prompt it raises on first launch is not one. */
    private static Intent nextScreen(DashboardActivity a) {
        Intent next;
        while ((next = shadowOf(a).getNextStartedActivity()) != null) {
            if (!"android.content.pm.action.REQUEST_PERMISSIONS".equals(next.getAction())) return next;
        }
        return null;
    }

    private static void assertOnly(DashboardActivity a, int visibleId) {
        for (int id : new int[]{R.id.nextStayLoading, R.id.nextStayError, R.id.nextStayEmpty, R.id.nextStayContent}) {
            assertEquals("state " + a.getResources().getResourceEntryName(id),
                    id == visibleId ? View.VISIBLE : View.GONE, a.findViewById(id).getVisibility());
        }
    }

    // ---- structure

    @Test
    public void sectionsReadInOrder_greeting_dateAndNextStay_quickActions_summary_recentActivity() {
        DashboardActivity activity = launch();
        View content = (View) activity.findViewById(R.id.welcomeCard).getParent();

        int[] order = {R.id.welcomeCard, R.id.dateTimeCard, R.id.nextStayCard, R.id.actionBrowseRooms,
                R.id.bookingReservationSummaryCard, R.id.bookingDetailsCard, R.id.notificationsCard,
                R.id.upcomingTransactionsCard, R.id.paymentStatusCard, R.id.accountSummaryCard};
        int previous = -1;
        for (int id : order) {
            int index = topLevelIndex(content, activity.findViewById(id));
            assertTrue(activity.getResources().getResourceEntryName(id) + " must come after the section before it", index > previous);
            previous = index;
        }
        assertTrue("the date and time sit directly above the Next Stay card",
                topLevelIndex(content, activity.findViewById(R.id.nextStayCard)) - topLevelIndex(content, activity.findViewById(R.id.dateTimeCard)) == 1);
    }

    private static int topLevelIndex(View content, View view) {
        View child = view;
        while (child.getParent() != content) child = (View) child.getParent();
        return ((ViewGroup) content).indexOfChild(child);
    }

    @Test
    public void everySectionHasATitle_andTheNewOnesALineSayingWhatTheyAreFor() {
        DashboardActivity activity = launch();
        assertEquals(app.getString(R.string.next_stay_section_title), findText(activity, R.string.next_stay_section_title));
        assertNotNull(findText(activity, R.string.next_stay_section_desc));
        assertNotNull(findText(activity, R.string.quick_actions));
        assertNotNull(findText(activity, R.string.quick_actions_desc));
        assertNotNull(findText(activity, R.string.recent_bookings_title));
        assertNotNull(findText(activity, R.string.recent_bookings_desc));
    }

    private static String findText(DashboardActivity a, int stringRes) {
        String wanted = a.getString(stringRes);
        return findText(a.findViewById(android.R.id.content), wanted);
    }

    private static String findText(View v, String wanted) {
        if (v instanceof TextView && wanted.contentEquals(((TextView) v).getText())) return wanted;
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                String hit = findText(((ViewGroup) v).getChildAt(i), wanted);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    @Test
    public void quickActionsEachHaveAnIconAndAWordLabel_andAreAtLeast48dpTall() {
        DashboardActivity activity = launch();
        View decor = activity.getWindow().getDecorView();
        int w = app.getResources().getDisplayMetrics().widthPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(app.getResources().getDisplayMetrics().heightPixels, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, app.getResources().getDisplayMetrics().heightPixels);

        float min = 48 * app.getResources().getDisplayMetrics().density;
        for (int id : new int[]{R.id.actionBrowseRooms, R.id.actionBooking, R.id.actionReservation, R.id.actionHistory}) {
            View action = activity.findViewById(id);
            assertTrue(action.getHeight() >= min);
            assertNotNull("icon", firstOf(action, ImageView.class));
            TextView label = firstOf(action, TextView.class);
            assertNotNull("label", label);
            assertTrue("a real word label", label.getText().length() > 3);
            assertTrue("12sp minimum", label.getTextSize() >= 12 * app.getResources().getDisplayMetrics().scaledDensity - 0.5f);
        }
    }

    private static <T extends View> T firstOf(View v, Class<T> type) {
        if (type.isInstance(v)) return type.cast(v);
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                T hit = firstOf(((ViewGroup) v).getChildAt(i), type);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    // ---- date and time

    @Test
    public void theDateAndTimeAreOneClearLineInHotelTime() {
        DashboardActivity activity = launch();
        SimpleDateFormat format = new SimpleDateFormat(app.getString(R.string.dashboard_date_time_format), Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
        String expected = format.format(new java.util.Date());
        String shown = text(activity, R.id.dateText);
        // e.g. "Sunday, October 11, 2026 · 8:31 AM" - compare the date part (the minute may roll over during the test)
        assertTrue(shown, shown.contains(" · "));
        assertEquals(expected.substring(0, expected.indexOf(" · ")), shown.substring(0, shown.indexOf(" · ")));
    }

    // ---- Next Stay card

    @Test
    public void nextStay_showsLoading_thenTheStay_withHintStatusDatesNightsAndBalance() {
        DashboardActivity activity = launch();
        assertOnly(activity, R.id.nextStayLoading);

        answer(stay(588, 1, 2));

        assertOnly(activity, R.id.nextStayContent);
        assertEquals("Check-in tomorrow", text(activity, R.id.tvNextStayHint));
        assertEquals("Deluxe", text(activity, R.id.tvNextStayRooms));
        assertEquals("2 nights", text(activity, R.id.tvNextStayNights));
        assertTrue(text(activity, R.id.tvNextStayDates).contains(" → "));
        assertEquals("Waiting for payment", text(activity, R.id.tvNextStayStatus));
        assertEquals(app.getString(R.string.next_stay_balance_due), text(activity, R.id.tvNextStayBalanceLabel));
        assertEquals("₱1,800.00", text(activity, R.id.tvNextStayBalance));
    }

    @Test
    public void tappingTheStayOpensItsDetails() {
        DashboardActivity activity = launch();
        answer(stay(588, 3, 1));

        tap(activity.findViewById(R.id.nextStayCard));
        Intent opened = nextScreen(activity);
        assertEquals(new ComponentName(activity, BookingDetailsActivity.class), opened.getComponent());
        assertNotNull(opened.getSerializableExtra(BookingDetailsActivity.EXTRA_BOOKING));
    }

    @Test
    public void nextStay_withNoStay_offersOneClearActionToBook() {
        DashboardActivity activity = launch();
        answer();

        assertOnly(activity, R.id.nextStayEmpty);
        assertEquals(app.getString(R.string.next_stay_empty_title), findText(activity, R.string.next_stay_empty_title));
        tap(activity.findViewById(R.id.btnNextStayBook));
        assertEquals(new ComponentName(activity, RoomBrowsingActivity.class), nextScreen(activity).getComponent());
    }

    @Test
    public void aTapOnTheEmptyCardItselfOpensNothing() {
        DashboardActivity activity = launch();
        answer();
        tap(activity.findViewById(R.id.nextStayCard));
        assertEquals(null, nextScreen(activity));
    }

    @Test
    public void nextStay_whenNothingCouldBeLoaded_showsAnErrorWithRetry_thatAsksAgain() {
        DashboardActivity activity = launch();
        for (int round = 0; round < 3; round++) {
            for (String method : new String[]{"getReservations", "getDirectBookings"}) {
                for (int i = 0; i < api.count(method); i++) {
                    ScreenTestSupport.FakeCall<Object> call = api.call(method, i);
                    if (call.pending() && !call.isCanceled()) {
                        try {
                            call.offline();
                        } catch (RuntimeException alreadyAnswered) {
                            // answered in an earlier round
                        }
                    }
                }
            }
            idle();
        }
        assertOnly(activity, R.id.nextStayError);

        int before = api.count("getReservations");
        tap(activity.findViewById(R.id.btnNextStayRetry));
        idle();
        assertTrue("Retry asks the server again", api.count("getReservations") > before);
        assertOnly(activity, R.id.nextStayLoading);
    }

    @Test
    public void everyStatusChipHasWordsAndAnIcon_notJustColour() {
        DashboardActivity activity = launch();
        answer(stay(588, 1, 1));
        TextView chip = activity.findViewById(R.id.tvNextStayStatus);
        assertTrue(chip.getText().length() > 0);
        assertNotNull("an icon beside the words", chip.getCompoundDrawables()[0]);
        assertTrue("12sp minimum", chip.getTextSize() >= 12 * app.getResources().getDisplayMetrics().scaledDensity - 0.5f);
    }
}
