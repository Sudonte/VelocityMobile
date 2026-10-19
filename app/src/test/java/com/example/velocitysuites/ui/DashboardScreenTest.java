package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        // Which Next Stay cards are expanded lives for the whole process; every test starts like a fresh app start.
        java.lang.reflect.Method reset = Class.forName("com.example.velocitysuites.NextStayDetails").getDeclaredMethod("resetForTests");
        reset.setAccessible(true);
        reset.invoke(null);
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

    // ---- Pending & Confirmed Stays

    @Test
    public void recentUpcomingStay_sitsBelowTheSummary_showsFive_expandsToAll_andOpensFilteredUpcoming() {
        DashboardActivity activity = launch();
        View content = (View) activity.findViewById(R.id.welcomeCard).getParent();
        assertTrue("Pending & Confirmed Stays now comes before the summary",
                topLevelIndex(content, activity.findViewById(R.id.recentUpcomingCard))
                < topLevelIndex(content, activity.findViewById(R.id.bookingReservationSummaryCard)));
        assertTrue(topLevelIndex(content, activity.findViewById(R.id.bookingReservationSummaryCard))
                < topLevelIndex(content, activity.findViewById(R.id.bookingDetailsCard)));

        ReservationDto[] seven = new ReservationDto[7];
        for (int i = 0; i < seven.length; i++) {
            seven[i] = stay(100 + i, 3 + i, 1);
            seven[i].status = "AWAITING_GCASH_PAYMENT"; // Pending
        }
        answer(seven);

        LinearLayout list = activity.findViewById(R.id.recentUpcomingList);
        assertEquals("collapsed: the first three cards and the Show all button", 4, list.getChildCount());
        assertEquals("7", text(activity, R.id.recentUpcomingCount));
        TextView toggle = list.findViewById(R.id.btnPreviewToggle);
        assertEquals("Show all (7)", toggle.getText().toString());

        tap(toggle);
        assertEquals("expanded: all seven and Show less", 8, list.getChildCount());
        toggle = list.findViewById(R.id.btnPreviewToggle);
        assertEquals("Show less", toggle.getText().toString());
        tap(activity.findViewById(R.id.recentUpcomingToggle)); // the header chevron collapses it again
        assertEquals(4, list.getChildCount());

        tap(list.getChildAt(0));
        Intent next = nextScreen(activity);
        assertNotNull(next);
        assertEquals("com.example.velocitysuites.UpcomingTransactionsActivity", next.getComponent().getClassName());
        assertEquals("RESERVATIONS", next.getStringExtra("EXTRA_TYPE_FILTER"));
        assertEquals("PENDING", next.getStringExtra("EXTRA_STATUS_FILTER"));
        assertEquals("100", next.getStringExtra("EXTRA_SELECTED_BOOKING_ID"));
        assertTrue("it asks Upcoming to open the details", next.getBooleanExtra("EXTRA_OPEN_DETAILS", false));
    }

    @Test
    public void recentUpcomingStay_showsAnEmptyState_whenNothingIsPendingOrConfirmed() {
        DashboardActivity activity = launch();
        ReservationDto cancelled = stay(200, 3, 1);
        cancelled.status = "REJECTED_RESERVATION";
        answer(cancelled);

        assertEquals(0, ((LinearLayout) activity.findViewById(R.id.recentUpcomingList)).getChildCount());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.recentUpcomingEmpty).getVisibility());
        assertNull("no Show all button for an empty list", activity.findViewById(R.id.btnPreviewToggle));
    }

    @Test
    public void listSections_withThreeOrFewerItems_showThemAll_andNoShowAllButton() {
        DashboardActivity activity = launch();
        ReservationDto[] three = new ReservationDto[3];
        for (int i = 0; i < three.length; i++) {
            three[i] = stay(300 + i, 3 + i, 1);
            three[i].status = "AWAITING_GCASH_PAYMENT";
        }
        answer(three);
        LinearLayout list = activity.findViewById(R.id.recentUpcomingList);
        assertEquals(3, list.getChildCount());
        assertNull(list.findViewById(R.id.btnPreviewToggle));
    }

    @Test
    public void otherStays_showThreeCollapsed_allExpanded_withACount() {
        DashboardActivity activity = launch();
        ReservationDto[] six = new ReservationDto[6];
        for (int i = 0; i < six.length; i++) six[i] = stay(400 + i, 2 + i, 1);
        answer(six);
        LinearLayout list = activity.findViewById(R.id.moreStaysList);
        // 6 stays: the selected day shows one, the other five are in Other Stays - three of them while collapsed
        assertEquals(3 + 1, list.getChildCount());
        assertEquals("Show all (5)", ((TextView) list.findViewById(R.id.btnPreviewToggle)).getText().toString());
        assertEquals("5", text(activity, R.id.moreStaysCount));

        tap(activity.findViewById(R.id.moreStaysToggle));
        assertEquals(5 + 1, list.getChildCount());
        assertEquals("Show less", ((TextView) list.findViewById(R.id.btnPreviewToggle)).getText().toString());
    }

    @Test
    public void quickActions_haveNoCollapseControl_andShowAllFour_whileTheSummaryOpensExpanded() {
        DashboardActivity activity = launch();
        answer(stay(500, 2, 1));
        for (int id : new int[]{R.id.actionBrowseRooms, R.id.actionBooking, R.id.actionReservation, R.id.actionHistory}) {
            assertEquals(View.VISIBLE, activity.findViewById(id).getVisibility());
            assertEquals(View.VISIBLE, ((View) activity.findViewById(id).getParent()).getVisibility());
        }
        assertNull("Quick Actions can no longer be collapsed", activity.findViewById(
                activity.getResources().getIdentifier("quickActionsToggle", "id", activity.getPackageName())));

        for (int id : new int[]{R.id.tileActiveBooking, R.id.tileActiveReservation, R.id.tilePastBooking, R.id.tilePastReservation,
                R.id.tileCancelBooking, R.id.tileCancelReservation}) {
            assertEquals("Summary starts expanded: " + activity.getResources().getResourceEntryName(id), View.VISIBLE,
                    activity.findViewById(id).getVisibility());
        }
        tap(activity.findViewById(R.id.summaryToggle)); // still collapsible: Active Bookings and Active Reservations only
        assertEquals(View.VISIBLE, activity.findViewById(R.id.tileActiveBooking).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.tilePastBooking).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.tileCancelReservation).getVisibility());
        tap(activity.findViewById(R.id.summaryToggle));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.tileCancelReservation).getVisibility());
    }

    // ---- structure

    @Test
    public void sectionsReadInOrder_greeting_dateAndNextStay_quickActions_summary_recentActivity() {
        DashboardActivity activity = launch();
        View content = (View) activity.findViewById(R.id.welcomeCard).getParent();

        int[] order = {R.id.welcomeCard, R.id.dateTimeCard, R.id.nextStayCard, R.id.actionBrowseRooms,
                R.id.bookingReservationSummaryCard, R.id.bookingDetailsCard, R.id.notificationsCard,
                R.id.paymentStatusCard, R.id.accountSummaryCard};
        int previous = -1;
        for (int id : order) {
            int index = topLevelIndex(content, activity.findViewById(id));
            assertTrue(activity.getResources().getResourceEntryName(id) + " must come after the section before it", index > previous);
            previous = index;
        }
        assertTrue("the date and time sit directly under the greeting",
                topLevelIndex(content, activity.findViewById(R.id.dateTimeCard)) - topLevelIndex(content, activity.findViewById(R.id.welcomeCard)) == 1);
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
            assertTrue(app.getResources().getResourceEntryName(id) + " height " + action.getHeight() + " vis " + action.getVisibility(), action.getHeight() >= min);
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
    public void theDateIsOnTop_theTimeBelowIt_largerAndInHotelTime() {
        DashboardActivity activity = launch();
        SimpleDateFormat dateFormat = new SimpleDateFormat(app.getString(R.string.dashboard_date_format), Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
        SimpleDateFormat timeFormat = new SimpleDateFormat(app.getString(R.string.dashboard_time_format), Locale.US);
        timeFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));

        // e.g. "Sunday, October 11, 2026" over "09:19:05 AM" (hours, minutes and seconds)
        assertEquals(dateFormat.format(new java.util.Date()), text(activity, R.id.dateText));
        assertTrue(text(activity, R.id.timeText).matches("\\d{2}:\\d{2}:\\d{2} (AM|PM)"));
        TextView date = activity.findViewById(R.id.dateText);
        TextView time = activity.findViewById(R.id.timeText);
        assertTrue("the time is the larger of the two", time.getTextSize() > date.getTextSize());
        assertTrue("the time sits directly below the date", ((View) date.getParent()) == ((View) time.getParent())
                && ((ViewGroup) date.getParent()).indexOfChild(time) == ((ViewGroup) date.getParent()).indexOfChild(date) + 1);
    }

    // ---- Next Stay card

    @Test
    public void nextStay_showsLoading_thenTheStay_withHintStatusDatesNightsAndBalance() {
        DashboardActivity activity = launch();
        assertOnly(activity, R.id.nextStayLoading);

        answer(stay(588, 1, 2));

        assertOnly(activity, R.id.nextStayContent);
        selectDay(activity, 1);
        assertEquals("Check-in day", text(activity, R.id.tvNextStayHint));
        assertEquals("room type(s) and guests on one row", "Deluxe · 2 guests", text(activity, R.id.tvNextStayRooms));
        assertTrue(text(activity, R.id.tvNextStayDates).contains(" → "));
        assertTrue("the length of the stay is on the dates row: " + text(activity, R.id.tvNextStayDates),
                text(activity, R.id.tvNextStayDates).endsWith(" · 2 nights"));
        assertEquals("Waiting for payment", text(activity, R.id.tvNextStayStatus));
        assertEquals("Balance due", text(activity, R.id.tvNextStayBalanceLabel));
        assertEquals("₱1,800.00", text(activity, R.id.tvNextStayBalance));
        assertEquals("the amount is bold red", app.getColor(R.color.velocity_red_primary),
                ((TextView) activity.findViewById(R.id.tvNextStayBalance)).getCurrentTextColor());
    }

    @Test
    public void nextStay_inHouse_showsCheckInTotal_approvedAmenities_remainingBalanceAndFinalTotal() {
        DashboardActivity activity = launch();
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(StayMoneyScreensTest.inHouseWithMoney()));
        idle();

        assertOnly(activity, R.id.nextStayContent);
        LinearLayout money = activity.findViewById(R.id.nextStayMoney);
        assertEquals("the full details are hidden by default", View.GONE, money.getVisibility());
        TextView toggle = activity.findViewById(R.id.btnNextStayToggleDetails);
        assertEquals("Show full details", toggle.getText().toString());
        tap(toggle);
        assertEquals(View.VISIBLE, money.getVisibility());
        assertEquals("Hide full details", toggle.getText().toString());
        List<String> rows = new ArrayList<>();
        collectText(money, rows);
        String all = String.join(" | ", rows);
        int last = -1;
        for (String needle : new String[]{"Room charges", "₱2,000.00", "Approved amenities", "Breakfast × 1", "₱200.00", "Amenities total", "Additional charges", "Broken lamp", "₱300.00",
                "Total", "₱2,100.00", "Paid", "₱1,600.00", "Balance due", "₱500.00", "Payable at check-out", "1 amenity request awaiting approval"}) {
            int at = all.indexOf(needle, Math.max(0, last));
            assertTrue(needle + " missing or out of order in: " + all, at >= 0);
            last = at;
        }
        assertFalse("a pending request is never part of the numbers", all.contains("₱100.00"));
        assertFalse("one balance line only", all.contains("Remaining Balance"));
        assertEquals(1, all.split("Balance due", -1).length - 1);
        tap(toggle);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        assertEquals("hides again", View.GONE, money.getVisibility());
        assertEquals("Show full details", toggle.getText().toString());
    }

    @Test
    public void aBookingNotification_refreshesYourNextStayAtOnce_andOtherNotificationsDoNot() {
        launch();
        answer(stay(588, 1, 2));
        com.example.velocitysuites.RoomRepository repository = com.example.velocitysuites.RoomRepository.getInstance(app);
        repository.pollNotifications(null);
        api.<com.example.velocitysuites.network.dto.PaginatedResponse<com.example.velocitysuites.network.dto.NotificationDto>>last("getNotifications")
                .succeed(ScreenTestSupport.notificationPage(1, ScreenTestSupport.notification(1, "Older", "booking", 588L, true))); // first load: nothing is new
        idle();
        int before = api.count("getReservations");

        repository.pollNotifications(null);
        api.<com.example.velocitysuites.network.dto.PaginatedResponse<com.example.velocitysuites.network.dto.NotificationDto>>last("getNotifications")
                .succeed(ScreenTestSupport.notificationPage(2, ScreenTestSupport.notification(2, "Promo", "promotion", 588L, false),
                        ScreenTestSupport.notification(1, "Older", "booking", 588L, true)));
        idle();
        assertEquals("a promotion says nothing about the stay", before, api.count("getReservations"));

        repository.pollNotifications(null);
        api.<com.example.velocitysuites.network.dto.PaginatedResponse<com.example.velocitysuites.network.dto.NotificationDto>>last("getNotifications")
                .succeed(ScreenTestSupport.notificationPage(3, ScreenTestSupport.notification(3, "Amenity Request Approved", "booking", 588L, false),
                        ScreenTestSupport.notification(2, "Promo", "promotion", 588L, false), ScreenTestSupport.notification(1, "Older", "booking", 588L, true)));
        idle();
        assertEquals("the approval re-reads the bookings straight away", before + 1, api.count("getReservations"));
    }

    @Test
    public void nextStay_beforeCheckIn_hasNoMoneySummary() {
        DashboardActivity activity = launch();
        answer(stay(588, 1, 2));
        selectDay(activity, 1);
        assertEquals(View.GONE, activity.findViewById(R.id.nextStayMoney).getVisibility());
    }

    private static void collectText(View v, List<String> out) {
        if (v instanceof TextView && ((TextView) v).getText().length() > 0) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collectText(((ViewGroup) v).getChildAt(i), out);
    }

    @Test
    public void tappingTheStayOpensItsDetails() {
        DashboardActivity activity = launch();
        answer(stay(588, 3, 1));
        selectDay(activity, 3);

        tap(activity.findViewById(R.id.tvNextStayViewDetails));
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
        selectDay(activity, 1);
        TextView chip = activity.findViewById(R.id.tvNextStayStatus);
        assertTrue(chip.getText().length() > 0);
        assertNotNull("an icon beside the words", chip.getCompoundDrawables()[0]);
        assertTrue("12sp minimum", chip.getTextSize() >= 12 * app.getResources().getDisplayMetrics().scaledDensity - 0.5f);
    }

    // ---- ID leads each item (Recent Bookings, Upcoming Stays, Payment Status)

    private static com.example.velocitysuites.network.dto.DirectBookingResponseDto booking(long id, int daysAway) {
        com.example.velocitysuites.network.dto.DirectBookingResponseDto d = ScreenTestSupport.directBooking(id, 1800);
        d.check_in = manilaToday().plusDays(daysAway).toString();
        d.check_out = manilaToday().plusDays(daysAway + 1).toString();
        return d;
    }

    private void answerWith(java.util.List<ReservationDto> reservations, java.util.List<com.example.velocitysuites.network.dto.DirectBookingResponseDto> direct) {
        ScreenTestSupport.answerBookingLoad(api, 0, reservations, direct);
        idle();
    }

    private static TextView first(ViewGroup container, int id) {
        assertTrue("the section lists an item", container.getChildCount() > 0);
        return (TextView) container.getChildAt(0).findViewById(id);
    }

    @Test
    public void recentBookings_leadWithTheBookingNumber_boldInPrimaryText_withTheRoomTypeSmallerBelow() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(booking(338, 2)));

        ViewGroup list = activity.findViewById(R.id.bookingListContainer);
        TextView id = first(list, R.id.tvRecentBookingRef);
        TextView room = first(list, R.id.tvRecentBookingRoom);
        assertEquals("Booking #338", id.getText().toString());
        assertTrue("the ID is the larger line", id.getTextSize() > room.getTextSize());
        assertEquals("primary text colour (it was red)", androidx.core.content.ContextCompat.getColor(app, R.color.velocity_text_primary), id.getCurrentTextColor());
        assertTrue("12sp minimum", room.getTextSize() >= 12 * app.getResources().getDisplayMetrics().scaledDensity - 0.5f);
    }

    @Test
    public void upcomingAndPaymentStatus_labelAReservationAsAReservation() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 2, 1)), ScreenTestSupport.directBookings(booking(338, 3)));
        selectDay(activity, 2);

        java.util.List<String> labels = new java.util.ArrayList<>();
        ViewGroup nextStays = activity.findViewById(R.id.nextStayDayCards);
        for (int i = 0; i < nextStays.getChildCount(); i++) {
            TextView ref = nextStays.getChildAt(i).findViewById(R.id.tvNextStayRef);
            if (ref != null) labels.add(ref.getText().toString());
        }
        ViewGroup more = activity.findViewById(R.id.moreStaysList);
        for (int i = 0; i < more.getChildCount(); i++) {
            labels.add(((TextView) more.getChildAt(i).findViewById(R.id.tvRecentStayRef)).getText().toString());
        }
        assertTrue(labels.toString(), labels.contains("Reservation #588") && labels.contains("Booking #338"));

        ViewGroup payments = activity.findViewById(R.id.paymentListContainer);
        assertTrue(payments.getChildCount() > 0);
        TextView paymentId = (TextView) payments.getChildAt(0).findViewById(R.id.tvPaymentBookingId);
        String shown = paymentId.getText().toString();
        assertTrue(shown, shown.startsWith("Booking #") || shown.startsWith("Reservation #"));
        TextView paymentTotal = (TextView) payments.getChildAt(0).findViewById(R.id.tvTotalAmount);
        assertTrue("the ID is the leading line", paymentId.getTextSize() > paymentTotal.getTextSize());
    }

    // ---- loading / error states: never "you have none" when the load failed

    private void failEverythingPending() {
        for (int round = 0; round < 3; round++) {
            for (String method : new String[]{"getReservations", "getDirectBookings", "getNotifications"}) {
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
    }

    @Test
    public void twoTransactionsOnTheSameDay_areBothShownInNextStay() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 2, 1)), ScreenTestSupport.directBookings(booking(338, 2)));
        selectDay(activity, 2);

        ViewGroup content = activity.findViewById(R.id.nextStayDayCards);
        java.util.List<String> refs = new java.util.ArrayList<>();
        for (int i = 0; i < content.getChildCount(); i++) {
            TextView ref = content.getChildAt(i).findViewById(R.id.tvNextStayRef);
            if (ref != null) refs.add(ref.getText().toString());
        }
        assertEquals(refs.toString(), 2, refs.size());
        assertTrue(refs.toString(), refs.contains("Reservation #588") && refs.contains("Booking #338"));
        assertEquals("the chip badge carries the count", "2",
                ((TextView) activity.<ViewGroup>findViewById(R.id.nextStayDayStrip).getChildAt(14 + 2).findViewById(R.id.tvDayCount)).getText().toString());
    }

    @Test
    public void nextStay_opensOnToday_showsPastAndFutureDays_withCountBadgesAndFriendlyNames() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 1, 1), stay(589, 1, 2), stay(590, -2, 1)), ScreenTestSupport.directBookings());

        ViewGroup strip = activity.findViewById(R.id.nextStayDayStrip);
        assertEquals("two weeks back through a month ahead", 14 + 1 + 30, strip.getChildCount());
        assertEquals("Yesterday", ((TextView) strip.getChildAt(13).findViewById(R.id.tvDayName)).getText().toString());
        assertEquals("Today", ((TextView) strip.getChildAt(14).findViewById(R.id.tvDayName)).getText().toString());
        assertEquals("Tomorrow", ((TextView) strip.getChildAt(15).findViewById(R.id.tvDayName)).getText().toString());
        assertTrue("nothing today, so the nearest day with a stay is selected", strip.getChildAt(15).isSelected());
        assertTrue(text(activity, R.id.nextStayDayInfo), text(activity, R.id.nextStayDayInfo).equals("Your next stay · tomorrow"));
        selectDay(activity, 0);
        assertTrue("Today is selected", strip.getChildAt(14).isSelected());
        assertEquals("tomorrow has two transactions", "2", ((TextView) strip.getChildAt(15).findViewById(R.id.tvDayCount)).getText().toString());
        assertEquals("nothing today: friendly empty state", View.VISIBLE, activity.findViewById(R.id.nextStayDayEmpty).getVisibility());
        assertEquals("a quiet day has no badge", View.INVISIBLE, strip.getChildAt(16 + 5).findViewById(R.id.tvDayCount).getVisibility());

        selectDay(activity, -1);
        assertEquals("a stay that ended yesterday still shows on its check-out day", 1, activity.<ViewGroup>findViewById(R.id.nextStayDayCards).getChildCount());
        selectDay(activity, 2);
        assertEquals("a stay also covers its check-out day", "In 2 days", text(activity, R.id.nextStayDayInfo));
    }

    @Test
    public void nextStay_opensOnTheNearestDayWithAStay_andTheTodayButtonJumpsBack() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 3, 1)), ScreenTestSupport.directBookings());

        ViewGroup strip = activity.findViewById(R.id.nextStayDayStrip);
        assertTrue(strip.getChildAt(14 + 3).isSelected());
        assertTrue(text(activity, R.id.nextStayDayInfo), text(activity, R.id.nextStayDayInfo).equals("Your next stay · in 3 days"));
        assertEquals("the Today button shows while another day is selected", View.VISIBLE, activity.findViewById(R.id.btnNextStayToday).getVisibility());

        tap(activity.findViewById(R.id.btnNextStayToday));
        idle();
        assertTrue(strip.getChildAt(14).isSelected());
        assertEquals("no Today button once Today is selected", View.GONE, activity.findViewById(R.id.btnNextStayToday).getVisibility());
        assertTrue(text(activity, R.id.nextStayDayInfo), text(activity, R.id.nextStayDayInfo).equals("Today"));
        assertEquals("empty state on a quiet Today", View.VISIBLE, activity.findViewById(R.id.nextStayDayEmpty).getVisibility());
    }

    @Test
    public void nextStay_theGuestsCase_opensOnTheCheckInDay_withTheSelectedChipFullyVisible() {
        DashboardActivity activity = launch();
        // Viewed 2 days before a 1-night stay: the check-in day is selected, not the check-out day.
        answer(stay(567, 2, 1));
        ViewGroup strip = activity.findViewById(R.id.nextStayDayStrip);
        assertTrue("check-in day", strip.getChildAt(14 + 2).isSelected());
        assertFalse("not the check-out day", strip.getChildAt(14 + 3).isSelected());
        assertTrue(text(activity, R.id.nextStayDayInfo), text(activity, R.id.nextStayDayInfo).equals("Your next stay · in 2 days"));

        // lay the screen out as on a phone, then the selected chip must sit inside the strip's visible window
        View decor = activity.getWindow().getDecorView();
        int w = app.getResources().getDisplayMetrics().widthPixels;
        int h = app.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
        idle();
        android.widget.HorizontalScrollView scroll = activity.findViewById(R.id.nextStayDayScroll);
        View chip = strip.getChildAt(14 + 2);
        assertTrue("selected chip starts inside the window", chip.getLeft() >= scroll.getScrollX());
        assertTrue("selected chip ends inside the window", chip.getRight() <= scroll.getScrollX() + scroll.getWidth());
    }

    @Test
    public void nextStay_dayChips_areEqualWidth_withTheSameRoundBadge_andTodayOutlinedWhenNotSelected() {
        DashboardActivity activity = launch();
        answer(stay(567, 2, 1));
        ViewGroup strip = activity.findViewById(R.id.nextStayDayStrip);
        View selected = strip.getChildAt(14 + 2);
        View today = strip.getChildAt(14);
        View quiet = strip.getChildAt(14 + 6);

        assertEquals("equal-width chips", selected.getLayoutParams().width, today.getLayoutParams().width);
        assertEquals(selected.getLayoutParams().width, quiet.getLayoutParams().width);
        assertTrue("Today is outlined while another day is selected", today.isActivated() && !today.isSelected());
        assertFalse(selected.isActivated());

        TextView selectedBadge = selected.findViewById(R.id.tvDayCount);
        assertEquals("1", selectedBadge.getText().toString());
        assertEquals("red on white on the selected chip", app.getColor(R.color.velocity_red_primary), selectedBadge.getCurrentTextColor());
        assertEquals(app.getColor(R.color.white), ((TextView) selected.findViewById(R.id.tvDayName)).getCurrentTextColor());
        assertEquals(View.INVISIBLE, quiet.findViewById(R.id.tvDayCount).getVisibility());
        assertTrue("a quiet day is lighter", quiet.findViewById(R.id.tvDayName).getAlpha() < 1f);

        selected.findViewById(R.id.tvDayCount); // another stay on an unselected day: white on red
        answerWith(ScreenTestSupport.reservations(stay(567, 2, 1), stay(568, 4, 1)), ScreenTestSupport.directBookings());
        TextView other = ((ViewGroup) activity.findViewById(R.id.nextStayDayStrip)).getChildAt(14 + 4).findViewById(R.id.tvDayCount);
        assertEquals("1", other.getText().toString());
        assertEquals("white on red on an unselected chip", app.getColor(R.color.white), other.getCurrentTextColor());
    }

    @Test
    public void nextStayCard_idIsPlainBoldText_neverTruncated_andThereIsNoCardAroundTheCards() {
        DashboardActivity activity = launch();
        answer(stay(567, 2, 1));
        TextView ref = activity.findViewById(R.id.tvNextStayRef);
        assertEquals("Reservation #567", ref.getText().toString());
        assertNull("no pink pill behind the ID", ref.getBackground());
        assertEquals("primary text colour, not red", app.getColor(R.color.velocity_text_primary), ref.getCurrentTextColor());
        assertNull("no chevron inside the ID", ref.getCompoundDrawables()[2]);
        assertNull("never ellipsized", ref.getEllipsize());
        assertEquals(Integer.MAX_VALUE, ref.getMaxLines());
        assertFalse("no card around the day strip and the cards",
                activity.findViewById(R.id.nextStayCard) instanceof com.google.android.material.card.MaterialCardView);
    }

    @Test
    @org.robolectric.annotation.Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xxhdpi", application = TestApplication.class)
    public void nextStay_inDarkMode_idChipsBadgesAndSelectedDayStayReadable() {
        DashboardActivity activity = launch();
        answer(stay(567, 2, 1));
        int surface = app.getColor(R.color.velocity_card_surface);
        TextView ref = activity.findViewById(R.id.tvNextStayRef);
        assertTrue("the ID reads on the card surface",
                androidx.core.graphics.ColorUtils.calculateContrast(ref.getCurrentTextColor(), surface) >= 4.5);

        ViewGroup strip = activity.findViewById(R.id.nextStayDayStrip);
        View selected = strip.getChildAt(14 + 2);
        int red = app.getColor(R.color.velocity_red_primary);
        assertTrue("white on the red selected chip",
                androidx.core.graphics.ColorUtils.calculateContrast(((TextView) selected.findViewById(R.id.tvDayName)).getCurrentTextColor(), red) >= 3.0);
        View unselected = strip.getChildAt(14 + 1);
        assertTrue("an unselected day's text reads on the chip surface",
                androidx.core.graphics.ColorUtils.calculateContrast(((TextView) unselected.findViewById(R.id.tvDayDate)).getCurrentTextColor(), surface) >= 3.0);
        TextView balance = activity.findViewById(R.id.tvNextStayBalance);
        assertTrue("the balance reads on the card surface",
                androidx.core.graphics.ColorUtils.calculateContrast(balance.getCurrentTextColor(), surface) >= 3.0);
    }

    @Test
    public void nextStayCard_typeChipIsNeutral_dayRoleChipKeepsTheRedAccent_andTheHeaderBadgeIsSolidRed() {
        DashboardActivity activity = launch();
        answer(stay(567, 2, 1));
        TextView type = activity.findViewById(R.id.tvNextStayType);
        TextView role = activity.findViewById(R.id.tvNextStayHint);
        assertEquals("neutral gray text on the type chip", app.getColor(R.color.status_cancelled_fg), type.getCurrentTextColor());
        assertEquals("red accent on the day-role chip", app.getColor(R.color.velocity_red_primary), role.getCurrentTextColor());
        assertFalse("the two chips differ", type.getCurrentTextColor() == role.getCurrentTextColor());

        TextView badge = activity.findViewById(R.id.nextStayCount);
        assertEquals("white number", app.getColor(R.color.white), badge.getCurrentTextColor());
        assertTrue("solid red, readable in dark mode too", androidx.core.graphics.ColorUtils.calculateContrast(
                badge.getCurrentTextColor(), app.getColor(R.color.velocity_red_primary)) >= 3.0);
    }

    @Test
    public void everySectionHeaderCountBadge_isTheSameRoundSolidRedBadgeWithAWhiteNumber() {
        DashboardActivity activity = launch();
        int red = app.getColor(R.color.velocity_red_primary);
        int[] badges = {R.id.nextStayCount, R.id.moreStaysCount, R.id.recentUpcomingCount,
                R.id.bookingCountBadge, R.id.notificationCountBadge, R.id.paymentCountBadge};
        for (int id : badges) {
            TextView badge = activity.findViewById(id);
            String name = activity.getResources().getResourceEntryName(id);
            assertEquals(name + " white number", app.getColor(R.color.white), badge.getCurrentTextColor());
            assertEquals(name + " is 24dp minimum", Math.round(24 * app.getResources().getDisplayMetrics().density), badge.getMinHeight());
            android.graphics.drawable.GradientDrawable bg = (android.graphics.drawable.GradientDrawable) badge.getBackground();
            assertEquals(name + " solid red", red, bg.getColor().getDefaultColor());
            assertEquals(name + " rounded", Math.round(12 * app.getResources().getDisplayMetrics().density), Math.round(bg.getCornerRadius()));
            assertNull(name + " no tint left over", badge.getBackgroundTintList());
        }
    }

    // ---- Your Next Stay: full collapse / full expand

    private void settle() {
        idle();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1)); // the 180 ms height animation
    }

    private static void layOut(DashboardActivity activity, Context ctx) {
        View decor = activity.getWindow().getDecorView();
        int w = ctx.getResources().getDisplayMetrics().widthPixels;
        int h = ctx.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
    }

    @Test
    public void nextStay_opensExpanded_withTheChevronUp_andTheNamedDescription() {
        DashboardActivity activity = launch();
        answer(stay(567, 2, 1));
        View toggle = activity.findViewById(R.id.nextStayToggle);
        assertTrue(activity.findViewById(R.id.nextStayDayStrip).isShown());
        assertTrue(activity.findViewById(R.id.nextStayDayInfo).isShown());
        assertTrue(activity.findViewById(R.id.tvNextStayRef).isShown());
        assertEquals("chevron up while open", 180f, toggle.getRotation(), 0.01f);
        assertEquals("Collapse Your Next Stay", toggle.getContentDescription().toString());
        assertEquals("the header row says the same", "Collapse Your Next Stay", ((View) toggle.getParent()).getContentDescription().toString());
        assertTrue("the header is at least 48dp tall",
                ((View) toggle.getParent()).getMinimumHeight() >= Math.round(48 * app.getResources().getDisplayMetrics().density));
    }

    @Test
    public void nextStay_collapsed_showsOnlyTheHeader_andTheCountBadgeStays_andItTogglesRepeatedlyViaChevronAndHeader() {
        DashboardActivity activity = launch();
        answer(stay(567, 2, 1));
        View chevron = activity.findViewById(R.id.nextStayToggle);
        View header = (View) chevron.getParent();

        for (int round = 0; round < 3; round++) {
            chevron.performClick(); // collapse via the chevron
            settle();
            assertFalse("strip hidden", activity.findViewById(R.id.nextStayDayStrip).isShown());
            assertFalse("context line hidden", activity.findViewById(R.id.nextStayDayInfo).isShown());
            assertFalse("Today button hidden", activity.findViewById(R.id.btnNextStayToday).isShown());
            assertFalse("cards hidden", activity.findViewById(R.id.tvNextStayRef).isShown());
            assertNull("no Show all control", activity.findViewById(R.id.nextStayDayCards).findViewById(R.id.btnPreviewToggle));
            assertTrue("the header count badge stays", activity.findViewById(R.id.nextStayCount).isShown());
            assertEquals("chevron down", 0f, chevron.getRotation(), 0.01f);
            assertEquals("Expand Your Next Stay", chevron.getContentDescription().toString());

            header.performClick(); // expand via the header row
            settle();
            assertTrue(activity.findViewById(R.id.nextStayDayStrip).isShown());
            assertTrue(activity.findViewById(R.id.tvNextStayRef).isShown());
            assertEquals(180f, chevron.getRotation(), 0.01f);
            assertEquals("Collapse Your Next Stay", chevron.getContentDescription().toString());
        }
    }

    @Test
    public void nextStay_expanded_showsEveryStayOfTheDay_withNoPreviewLimitAndNoShowAllButton() {
        DashboardActivity activity = launch();
        ReservationDto[] five = new ReservationDto[5];
        for (int i = 0; i < five.length; i++) five[i] = stay(700 + i, 2, 1);
        answer(five);
        ViewGroup cards = activity.findViewById(R.id.nextStayDayCards);
        int stays = 0;
        for (int i = 0; i < cards.getChildCount(); i++) if (cards.getChildAt(i).findViewById(R.id.tvNextStayRef) != null) stays++;
        assertEquals("all five, not a 3-item preview", 5, stays);
        assertNull("no Show all / Show less in this section", cards.findViewById(R.id.btnPreviewToggle));
    }

    @Test
    public void nextStay_remembersItsState_whenTheScreenIsOpenedAgain() {
        DashboardActivity first = launch();
        answer(stay(567, 2, 1));
        first.findViewById(R.id.nextStayToggle).performClick();
        settle();
        assertFalse(first.findViewById(R.id.nextStayDayStrip).isShown());

        DashboardActivity again = launch();
        assertEquals("opens collapsed, as it was left", View.GONE, again.findViewById(R.id.nextStayCard).getVisibility());
        assertEquals(0f, again.findViewById(R.id.nextStayToggle).getRotation(), 0.01f);
        assertEquals("Expand Your Next Stay", again.findViewById(R.id.nextStayToggle).getContentDescription().toString());
    }

    @Test
    public void nextStay_afterExpanding_keepsTheChosenDay_andCentresItsChip() {
        DashboardActivity activity = launch();
        answer(stay(567, 2, 1), stay(568, 6, 1));
        selectDay(activity, 6); // the guest picks another day
        layOut(activity, app);
        idle();

        activity.findViewById(R.id.nextStayToggle).performClick(); // collapse
        settle();
        activity.findViewById(R.id.nextStayToggle).performClick(); // expand
        settle();
        layOut(activity, app); // the strip can only be measured now that it is shown again
        idle();

        ViewGroup strip = activity.findViewById(R.id.nextStayDayStrip);
        assertTrue("the day chosen before collapsing is still selected", strip.getChildAt(14 + 6).isSelected());
        android.widget.HorizontalScrollView scroll = activity.findViewById(R.id.nextStayDayScroll);
        View chip = strip.getChildAt(14 + 6);
        assertTrue("selected chip fully inside the window (left)", chip.getLeft() >= scroll.getScrollX());
        assertTrue("selected chip fully inside the window (right)", chip.getRight() <= scroll.getScrollX() + scroll.getWidth());
        int chipCentre = chip.getLeft() + chip.getWidth() / 2 - scroll.getScrollX();
        assertTrue("and centred (to within a chip)", Math.abs(chipCentre - scroll.getWidth() / 2) <= chip.getWidth());
    }

    @Test
    public void nextStay_cardTapsStillDeepLink_afterACollapseAndExpand() {
        DashboardActivity activity = launch();
        answer(stay(567, 2, 1));
        activity.findViewById(R.id.nextStayToggle).performClick();
        settle();
        activity.findViewById(R.id.nextStayToggle).performClick();
        settle();
        tap(activity.findViewById(R.id.nextStayItem));
        Intent next = nextScreen(activity);
        assertEquals(new ComponentName(activity, com.example.velocitysuites.UpcomingTransactionsActivity.class), next.getComponent());
        assertEquals("567", next.getStringExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID));
    }

    // ---- Your Next Stay card layout: every state, no overlap, nothing clipped

    private com.example.velocitysuites.network.dto.DirectBookingResponseDto checkedIn(long id, boolean paid) {
        com.example.velocitysuites.network.dto.DirectBookingResponseDto d = booking(id, 2); // booked 2 days ahead: an EARLY check-in
        d.booking_status = "CHECKED_IN";
        if (paid) d.payments = new java.util.ArrayList<>(java.util.Arrays.asList(ScreenTestSupport.payment(9, "1800.00", "completed", "cash")));
        return d;
    }

    private ReservationDto reservationPaid(long id) {
        ReservationDto r = ScreenTestSupport.reservation(id, 1800, ScreenTestSupport.payment(9, "1800.00", "completed", "gcash"));
        r.check_in = manilaToday().plusDays(2).toString();
        r.check_out = manilaToday().plusDays(3).toString();
        return r;
    }

    /** Shows the state, lays the screen out like a phone, and checks the first Next Stay card. Returns the activity. */
    private DashboardActivity showCard(java.util.List<ReservationDto> reservations,
                                       java.util.List<com.example.velocitysuites.network.dto.DirectBookingResponseDto> direct) {
        DashboardActivity activity = launch();
        answerWith(reservations, direct);
        layOut(activity, app);
        idle();
        layOut(activity, app);
        View card = activity.findViewById(R.id.nextStayItem);
        assertNotNull("a card is shown", card);
        assertTrue("laid out", card.getHeight() > 0);

        View root = activity.getWindow().getDecorView();
        int[] ids = {R.id.tvNextStayRef, R.id.tvNextStayStatus, R.id.tvNextStayType, R.id.tvNextStayHint, R.id.tvNextStayRooms,
                R.id.tvNextStayDates, R.id.nextStayPaymentRow, R.id.btnNextStayRequestAmenities,
                R.id.btnNextStayToggleDetails, R.id.tvNextStayViewDetails};
        android.graphics.Rect bounds = LayoutHarness.boundsIn(root, card);
        java.util.List<View> shown = new java.util.ArrayList<>();
        for (int id : ids) {
            View v = card.findViewById(id);
            if (v == null || !v.isShown()) continue;
            shown.add(v);
            android.graphics.Rect r = LayoutHarness.boundsIn(root, v);
            String name = activity.getResources().getResourceEntryName(id);
            assertTrue(name + " inside the card (top)", r.top >= bounds.top);
            assertTrue(name + " inside the card (bottom): " + r + " vs " + bounds, r.bottom <= bounds.bottom);
            assertTrue(name + " inside the card (right)", r.right <= bounds.right);
        }
        // the payment row's own two texts, and every pair of stacked blocks, must not overlap
        shown.add(card.findViewById(R.id.tvNextStayBalanceLabel));
        shown.add(card.findViewById(R.id.tvNextStayBalance));
        for (int a = 0; a < shown.size(); a++) {
            for (int b = a + 1; b < shown.size(); b++) {
                View x = shown.get(a);
                View y = shown.get(b);
                if (isAncestor(x, y) || isAncestor(y, x)) continue; // the payment row holds its two texts
                if (x == y) continue;
                assertFalse(activity.getResources().getResourceEntryName(x.getId()) + " overlaps "
                        + activity.getResources().getResourceEntryName(y.getId()), LayoutHarness.overlaps(root, x, y));
            }
        }
        // the bottom padding matches the top (about 12dp), so nothing hugs or is cut by the card's edge
        View last = card.findViewById(R.id.nextStayActions);
        int lastBottom = LayoutHarness.boundsIn(root, last).bottom;
        assertTrue("bottom padding is positive", bounds.bottom - lastBottom >= LayoutHarness.dp(app, 8));
        return activity;
    }

    private static boolean isAncestor(View maybeParent, View child) {
        for (android.view.ViewParent p = child.getParent(); p != null; p = p.getParent()) if (p == maybeParent) return true;
        return false;
    }

    private static final java.util.List<ReservationDto> NO_RESERVATIONS = new java.util.ArrayList<>();
    private static final java.util.List<com.example.velocitysuites.network.dto.DirectBookingResponseDto> NO_DIRECT = new java.util.ArrayList<>();

    @Test
    public void card_notCheckedIn_balanceDue_hasOnlyViewDetails_andNothingOverlaps() {
        DashboardActivity a = showCard(ScreenTestSupport.reservations(stay(567, 2, 1)), NO_DIRECT);
        assertFalse("no amenities button before check-in", a.findViewById(R.id.btnNextStayRequestAmenities).isShown());
        assertEquals("Balance due", text(a, R.id.tvNextStayBalanceLabel));
        assertEquals("Your next stay · in 2 days", text(a, R.id.nextStayDayInfo));
    }

    @Test
    public void card_notCheckedIn_paid_hasOnlyViewDetails_andNothingOverlaps() {
        DashboardActivity a = showCard(java.util.Arrays.asList(reservationPaid(567)), NO_DIRECT);
        assertFalse(a.findViewById(R.id.btnNextStayRequestAmenities).isShown());
        assertEquals("Paid", text(a, R.id.tvNextStayBalance));
    }

    @Test
    public void card_checkedIn_paid_requestAmenitiesIsACompactButtonOnItsOwnRow_belowTheActionRow() {
        DashboardActivity a = showCard(NO_RESERVATIONS, java.util.Arrays.asList(checkedIn(567, true)));
        TextView button = a.findViewById(R.id.btnNextStayRequestAmenities);
        assertTrue("the amenities button is shown while checked in", button.isShown());
        assertEquals("Request amenities", button.getText().toString());
        assertEquals("one line", 1, button.getLineCount());
        assertEquals("no letter-spacing", 0f, button.getLetterSpacing(), 0.0001f);
        assertTrue("compact: about 40dp to look at", button.getHeight() <= LayoutHarness.dp(app, 48));

        View root = a.getWindow().getDecorView();
        View actions = a.findViewById(R.id.nextStayActions);
        assertTrue("on its own row, never overlapping the actions",
                LayoutHarness.boundsIn(root, actions).bottom <= LayoutHarness.boundsIn(root, button).top);
        View toggle = a.findViewById(R.id.btnNextStayToggleDetails);
        View open = a.findViewById(R.id.tvNextStayViewDetails);
        assertEquals("Show full details", ((TextView) toggle).getText().toString());
        assertEquals("Open booking ›", ((TextView) open).getText().toString());
        assertTrue("Show full details left, Open booking right",
                LayoutHarness.boundsIn(root, toggle).right <= LayoutHarness.boundsIn(root, open).left);

        // the check is beside "Paid" - not beside the "Payment" label
        TextView paid = a.findViewById(R.id.tvNextStayBalance);
        TextView label = a.findViewById(R.id.tvNextStayBalanceLabel);
        assertEquals("Paid", paid.getText().toString());
        assertEquals("Payment", label.getText().toString());
        assertNotNull("check icon directly before Paid", paid.getCompoundDrawables()[0]);
        assertNull("no icon on the label", label.getCompoundDrawables()[0]);
        assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, paid.getLayoutParams().width);
        assertTrue("Paid is right-aligned", LayoutHarness.boundsIn(root, paid).right >= LayoutHarness.boundsIn(root, a.findViewById(R.id.nextStayPaymentRow)).right - LayoutHarness.dp(app, 2));
        assertTrue("the label ends before the icon begins", LayoutHarness.boundsIn(root, label).right <= LayoutHarness.boundsIn(root, paid).left);

        assertEquals("You're staying with us", text(a, R.id.nextStayDayInfo));
    }

    @Test
    public void card_checkedIn_balanceDue_hasBothActions_andNothingOverlaps() {
        DashboardActivity a = showCard(NO_RESERVATIONS, java.util.Arrays.asList(checkedIn(567, false)));
        assertTrue(a.findViewById(R.id.btnNextStayRequestAmenities).isShown());
        assertEquals("Balance due", text(a, R.id.tvNextStayBalanceLabel));
        assertEquals("You're staying with us", text(a, R.id.nextStayDayInfo));
    }

    @Test
    public void card_checkedIn_atALargeFontScale_stacksTheActions_withoutOverlapOrWrappedButtonText() {
        android.content.res.Configuration config = new android.content.res.Configuration(app.getResources().getConfiguration());
        config.fontScale = 1.6f;
        app.getResources().updateConfiguration(config, app.getResources().getDisplayMetrics());
        try {
            DashboardActivity a = showCard(NO_RESERVATIONS, java.util.Arrays.asList(checkedIn(567, true)));
            TextView button = a.findViewById(R.id.btnNextStayRequestAmenities);
            assertEquals("the button label never wraps", 1, button.getLineCount());
            View root = a.getWindow().getDecorView();
            assertTrue("the button sits below the action row, no overlap",
                    LayoutHarness.boundsIn(root, a.findViewById(R.id.nextStayActions)).bottom <= LayoutHarness.boundsIn(root, button).top);
        } finally {
            config.fontScale = 1f;
            app.getResources().updateConfiguration(config, app.getResources().getDisplayMetrics());
        }
    }

    // ---- Payment Status: one card per payment, balance colours, currency ----

    private static ReservationDto reservationPartlyPaid(long id) {
        ReservationDto r = ScreenTestSupport.reservation(id, 1800, ScreenTestSupport.payment(8, "900.00", "completed", "gcash"));
        r.check_in = manilaToday().plusDays(2).toString();
        r.check_out = manilaToday().plusDays(3).toString();
        return r;
    }

    private TextView paymentValue(DashboardActivity a, String reference, int viewId) {
        ViewGroup list = a.findViewById(R.id.paymentListContainer);
        for (int i = 0; i < list.getChildCount(); i++) {
            TextView id = list.getChildAt(i).findViewById(R.id.tvPaymentBookingId);
            if (id != null && id.getText().toString().equals(reference)) return list.getChildAt(i).findViewById(viewId);
        }
        throw new AssertionError("no payment card for " + reference);
    }

    /** Any view of the payment card for this reference (paymentValue is for its text views). */
    private View paymentView(DashboardActivity a, String reference, int viewId) {
        ViewGroup list = a.findViewById(R.id.paymentListContainer);
        for (int k = 0; k < list.getChildCount(); k++) {
            TextView id = list.getChildAt(k).findViewById(R.id.tvPaymentBookingId);
            if (id != null && id.getText().toString().equals(reference)) return list.getChildAt(k).findViewById(viewId);
        }
        throw new AssertionError("no payment card for " + reference);
    }

    @Test
    public void paymentCards_balanceIsGreenWhenNothingIsOwed_amberWhenPartlyPaid_redOnlyWhenOwed_andAmountsUseTheCurrencyFormat() {
        DashboardActivity activity = launch();
        ReservationDto paidToday = reservationPaid(601);
        paidToday.check_in = manilaToday().toString(); // a fully paid one is listed only on the day it matters
        paidToday.check_out = manilaToday().plusDays(1).toString();
        answerWith(java.util.Arrays.asList(paidToday, reservationPartlyPaid(602), stay(603, 2, 1)), NO_DIRECT);

        TextView paid = paymentValue(activity, "Reservation #601", R.id.tvPaymentRemainingBalance);
        assertEquals("₱0.00", paid.getText().toString());
        assertEquals("nothing owed is green, never red", app.getColor(R.color.status_paid_fg), paid.getCurrentTextColor());
        assertEquals(View.VISIBLE, paymentValue(activity, "Reservation #601", R.id.tvPaymentBalanceNote).getVisibility());
        assertEquals("Fully paid", ((TextView) paymentValue(activity, "Reservation #601", R.id.tvPaymentBalanceNote)).getText().toString());

        TextView partial = paymentValue(activity, "Reservation #602", R.id.tvPaymentRemainingBalance);
        assertEquals("₱900.00", partial.getText().toString());
        assertEquals("partly paid is amber", app.getColor(R.color.status_pending_fg), partial.getCurrentTextColor());

        TextView owed = paymentValue(activity, "Reservation #603", R.id.tvPaymentRemainingBalance);
        assertEquals("₱1,800.00", owed.getText().toString());
        assertEquals("owing is red", app.getColor(R.color.velocity_red_primary), owed.getCurrentTextColor());

        assertEquals("₱1,800.00", paymentValue(activity, "Reservation #601", R.id.tvTotalAmount).getText().toString());
        assertEquals("₱1,800.00 of ₱1,800.00 paid", paymentValue(activity, "Reservation #601", R.id.tvPaymentPaidOf).getText().toString());
        assertEquals("labels sit above the values", "Total", paymentValue(activity, "Reservation #601", R.id.tvPaymentTotalLabel).getText().toString());
        assertEquals("Balance", paymentValue(activity, "Reservation #601", R.id.tvPaymentBalanceLabel).getText().toString());
    }

    @Test
    public void paymentStatusHeader_hasAPlainViewAllTextButton_andTheSectionIsNotACardAroundCards() {
        DashboardActivity activity = launch();
        answerWith(java.util.Arrays.asList(reservationPartlyPaid(601)), NO_DIRECT);
        TextView viewAll = activity.findViewById(R.id.btnViewAllPayments);
        assertEquals("View all", viewAll.getText().toString());
        assertEquals("no filled pink block", null, viewAll.getBackgroundTintList());
        assertTrue("48dp tap target", viewAll.getMinHeight() >= LayoutHarness.dp(app, 48));
        com.google.android.material.card.MaterialCardView section = activity.findViewById(R.id.paymentStatusCard);
        com.google.android.material.card.MaterialCardView account = activity.findViewById(R.id.accountSummaryCard);
        assertEquals("the same border width as Account Summary", account.getStrokeWidth(), section.getStrokeWidth());
        assertTrue(section.getStrokeWidth() > 0);
        assertEquals("the same corner radius", account.getRadius(), section.getRadius(), 0.01f);
        assertEquals("the same elevation", account.getCardElevation(), section.getCardElevation(), 0.01f);
        TextView desc = findTextView((ViewGroup) activity.findViewById(R.id.paymentsBody), R.string.payment_status_dashboard_desc);
        assertNotNull("the one-line description", desc);
        assertEquals("Paid and still due", desc.getText().toString());
    }

    private static TextView findTextView(ViewGroup root, int stringRes) {
        String wanted = root.getContext().getString(stringRes);
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof TextView && ((TextView) child).getText().toString().equals(wanted)) return (TextView) child;
        }
        return null;
    }

    // ---- Account Summary: the donut ----

    private ActivityController<DashboardActivity> launchController() {
        ActivityController<DashboardActivity> controller = Robolectric.buildActivity(DashboardActivity.class);
        controller.setup();
        idle();
        return controller;
    }

    @Test
    public void accountSummary_donutShowsTheProfilePercentage_whatIsMissing_andOpensEditProfile() {
        DashboardActivity activity = launchController().get(); // setUp saved a profile with everything except a photo
        com.example.velocitysuites.ProfileDonutView donut = activity.findViewById(R.id.profileDonut);
        assertEquals(83, donut.getPercent());
        assertEquals("Profile 83% complete", donut.getContentDescription().toString());
        TextView missing = activity.findViewById(R.id.profileMissingText);
        assertEquals(View.VISIBLE, missing.getVisibility());
        assertEquals("Add a profile photo to complete your profile", missing.getText().toString());
        assertEquals("Guest", text(activity, R.id.membershipStatusText));
        assertEquals("Active", text(activity, R.id.accountStatusText));
        assertEquals("Edit Profile", text(activity, R.id.btnEditProfile));

        tap(missing);
        Intent next = nextScreen(activity);
        assertEquals("com.example.velocitysuites.ProfileManagementActivity", next.getComponent().getClassName());
        tap(activity.findViewById(R.id.profileDonutArea));
        assertEquals("com.example.velocitysuites.ProfileManagementActivity", nextScreen(activity).getComponent().getClassName());
    }

    @Test
    public void accountSummary_donutUpdatesWhenTheGuestReturnsFromEditProfile_withoutRestarting() {
        ActivityController<DashboardActivity> controller = launchController();
        DashboardActivity activity = controller.get();
        com.example.velocitysuites.ProfileDonutView donut = activity.findViewById(R.id.profileDonut);
        assertEquals(83, donut.getPercent());

        controller.pause(); // Edit Profile opens ... and saves a profile photo
        SessionManager.setProfilePictureUrl(app, "https://example.com/me.jpg");
        controller.resume();
        idle();

        assertEquals("100% as soon as the screen is back", 100, donut.getPercent());
        assertEquals("Profile 100% complete", donut.getContentDescription().toString());
        assertEquals("nothing is missing any more", View.GONE, activity.findViewById(R.id.profileMissingText).getVisibility());
    }

    private void assertAccountAndPaymentAreaFits(DashboardActivity activity) {
        View root = activity.getWindow().getDecorView();
        int w = app.getResources().getDisplayMetrics().widthPixels;
        int h = app.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);

        // Payment Status header: the title, count badge, View all and chevron share one row without overlap.
        View[] header = {activity.findViewById(R.id.paymentStatusTitle), activity.findViewById(R.id.paymentCountBadge),
                activity.findViewById(R.id.btnViewAllPayments), activity.findViewById(R.id.paymentsToggle)};
        for (int a = 0; a < header.length; a++) {
            if (!header[a].isShown() && header[a].getId() == R.id.paymentCountBadge) continue;
            assertTrue("fully on screen", LayoutHarness.boundsIn(root, header[a]).right <= w);
            for (int b = a + 1; b < header.length; b++) {
                if (!header[b].isShown()) continue;
                assertFalse("payment header views overlap", LayoutHarness.overlaps(root, header[a], header[b]));
            }
        }
        assertEquals("the title is one line", 1, ((TextView) activity.findViewById(R.id.paymentStatusTitle)).getLineCount());

        // Account Summary: donut area and the account type / status column side by side, nothing clipped.
        View donutArea = activity.findViewById(R.id.profileDonutArea);
        View donut = activity.findViewById(R.id.profileDonut);
        View caption = activity.findViewById(R.id.profileDonutCaption);
        View status = activity.findViewById(R.id.accountStatusText);
        assertFalse(LayoutHarness.overlaps(root, donutArea, status));
        assertFalse("the caption sits under the donut", LayoutHarness.overlaps(root, donut, caption));
        assertTrue(LayoutHarness.boundsIn(root, status).right <= w);
        assertTrue("Edit Profile is on screen and 48dp tall", LayoutHarness.boundsIn(root, activity.findViewById(R.id.btnEditProfile)).right <= w
                && activity.findViewById(R.id.btnEditProfile).getHeight() >= LayoutHarness.dp(app, 48));
        assertEquals("the caption is one line", 1, ((TextView) caption).getLineCount());
    }

    @Test
    public void paymentHeaderAndAccountSummary_fitAt360dp_inLightMode() {
        DashboardActivity activity = launch();
        answerWith(java.util.Arrays.asList(reservationPartlyPaid(601)), NO_DIRECT);
        assertAccountAndPaymentAreaFits(activity);
    }

    @Test
    @org.robolectric.annotation.Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xxhdpi", application = TestApplication.class)
    public void paymentHeaderAndAccountSummary_fitAt360dp_inDarkMode_andTheDonutTrackStaysVisible() {
        DashboardActivity activity = launch();
        answerWith(java.util.Arrays.asList(reservationPartlyPaid(601)), NO_DIRECT);
        assertAccountAndPaymentAreaFits(activity);
        // the unfilled donut track must contrast with the card in dark mode
        int track = app.getColor(R.color.status_cancelled_bg);
        int surface = app.getColor(R.color.velocity_card_surface);
        assertTrue("track vs card surface contrast " + androidx.core.graphics.ColorUtils.calculateContrast(track, surface),
                androidx.core.graphics.ColorUtils.calculateContrast(track, surface) >= 1.2);
        assertTrue("the percentage text reads on the card", androidx.core.graphics.ColorUtils.calculateContrast(
                app.getColor(R.color.velocity_text_primary), surface) >= 4.5);
    }

    // ---- Payment Status: which items, their order, one colour scheme, Pay Now on every card with a balance ----

    private ReservationDto unpaidReservation(long id, String method, int daysAway) {
        ReservationDto r = stay(id, daysAway, 1);
        r.payment_method = method;
        return r;
    }

    private static java.util.List<String> paymentRefs(DashboardActivity a) {
        ViewGroup list = a.findViewById(R.id.paymentListContainer);
        java.util.List<String> refs = new java.util.ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) {
            TextView id = list.getChildAt(i).findViewById(R.id.tvPaymentBookingId);
            if (id != null) refs.add(id.getText().toString());
        }
        return refs;
    }

    @Test
    public void paymentStatus_listsEveryActiveTransaction_andDropsCheckedOutCancelledAndRejected() {
        DashboardActivity activity = launch();
        activity.findViewById(R.id.paymentsToggle).performClick(); // expanded: every item, not the 3-item preview
        ReservationDto cancelled = stay(701, 3, 1);
        cancelled.status = "CANCELLED_RESERVATION";
        ReservationDto rejected = stay(702, 3, 1);
        rejected.status = "REJECTED_RESERVATION";
        com.example.velocitysuites.network.dto.DirectBookingResponseDto checkedOut = booking(703, -3);
        checkedOut.booking_status = "COMPLETED_BOOKING";
        ReservationDto paidToday = reservationPaid(704);
        paidToday.check_in = manilaToday().plusDays(5).toString(); // fully paid and not today: still listed
        paidToday.check_out = manilaToday().plusDays(6).toString();
        answerWith(java.util.Arrays.asList(cancelled, rejected, paidToday, reservationPartlyPaid(705), unpaidReservation(706, "gcash", 4)),
                java.util.Arrays.asList(checkedOut));

        assertEquals("unpaid first, then partly paid, then fully paid",
                java.util.Arrays.asList("Reservation #706", "Reservation #705", "Reservation #704"), paymentRefs(activity));
        assertEquals("the badge counts what is listed", "3", text(activity, R.id.paymentCountBadge));
    }

    @Test
    public void paymentStatus_withinEachGroup_theNearestCheckInComesFirst() {
        DashboardActivity activity = launch();
        activity.findViewById(R.id.paymentsToggle).performClick();
        answerWith(java.util.Arrays.asList(unpaidReservation(711, "gcash", 9), unpaidReservation(712, "gcash", 2), unpaidReservation(713, "gcash", 5)), NO_DIRECT);
        assertEquals(java.util.Arrays.asList("Reservation #712", "Reservation #713", "Reservation #711"), paymentRefs(activity));
    }

    @Test
    public void paymentStatus_chipBalanceAndProgressShareOneColour_andTheCardNamesItsStay() {
        DashboardActivity activity = launch();
        activity.findViewById(R.id.paymentsToggle).performClick();
        ReservationDto paidToday = reservationPaid(721);
        paidToday.check_in = manilaToday().toString();
        paidToday.check_out = manilaToday().plusDays(1).toString();
        answerWith(java.util.Arrays.asList(paidToday, reservationPartlyPaid(722), unpaidReservation(723, "gcash", 2)), NO_DIRECT);

        int[][] expected = {
                {R.string.payment_chip_paid, R.color.status_paid_fg}, {R.string.payment_chip_partial, R.color.status_pending_fg},
                {R.string.payment_chip_unpaid, R.color.status_unpaid_fg}};
        String[] refs = {"Reservation #721", "Reservation #722", "Reservation #723"};
        for (int i = 0; i < refs.length; i++) {
            TextView chip = paymentValue(activity, refs[i], R.id.tvPaymentStatus);
            int colour = app.getColor(expected[i][1]);
            assertEquals(refs[i] + " chip words", app.getString(expected[i][0]), chip.getText().toString());
            assertEquals(refs[i] + " chip colour", colour, chip.getCurrentTextColor());
            assertEquals(refs[i] + " balance colour", colour, paymentValue(activity, refs[i], R.id.tvPaymentRemainingBalance).getCurrentTextColor());
            com.google.android.material.progressindicator.LinearProgressIndicator bar =
                    (com.google.android.material.progressindicator.LinearProgressIndicator) paymentView(activity, refs[i], R.id.progressPayment);
            assertEquals(refs[i] + " progress colour", colour, bar.getIndicatorColor()[0]);
        }
        TextView meta = paymentValue(activity, "Reservation #723", R.id.tvPaymentStayMeta);
        assertTrue("dates and the booking status: " + meta.getText(), meta.getText().toString().contains("\u2192") && meta.getText().toString().endsWith("Pending approval"));
        assertEquals("the chip never just says Pending", false, ((TextView) paymentValue(activity, "Reservation #723", R.id.tvPaymentStatus)).getText().toString().equals("Pending"));
    }

    @Test
    public void payNow_isOnGcashReservationsOnly_cashPaysAtTheFrontDesk_orPaymentUnderReview_orNothingWhenPaid() {
        DashboardActivity activity = launch();
        activity.findViewById(R.id.paymentsToggle).performClick();
        ReservationDto review = ScreenTestSupport.reservation(733, 1800, ScreenTestSupport.payment(3, "1800.00", "pending", "gcash"));
        review.check_in = manilaToday().plusDays(3).toString();
        review.check_out = manilaToday().plusDays(4).toString();
        ReservationDto paidToday = reservationPaid(734);
        paidToday.check_in = manilaToday().toString();
        paidToday.check_out = manilaToday().plusDays(1).toString();
        answerWith(java.util.Arrays.asList(unpaidReservation(731, "gcash", 2), unpaidReservation(732, "cash", 2), review, paidToday), NO_DIRECT);

        assertEquals("a GCash reservation gets Pay Now", View.VISIBLE, paymentValue(activity, "Reservation #731", R.id.btnQuickPay).getVisibility());
        assertEquals("a cash reservation does not", View.GONE, paymentValue(activity, "Reservation #732", R.id.btnQuickPay).getVisibility());
        TextView cashNote = paymentValue(activity, "Reservation #732", R.id.tvPaymentNote);
        assertEquals(View.VISIBLE, cashNote.getVisibility());
        assertEquals("Pay at the front desk", cashNote.getText().toString());
        assertEquals(View.GONE, paymentValue(activity, "Reservation #733", R.id.btnQuickPay).getVisibility());
        TextView note = paymentValue(activity, "Reservation #733", R.id.tvPaymentNote);
        assertEquals(View.VISIBLE, note.getVisibility());
        assertEquals("Payment under review", note.getText().toString());
        assertEquals("fully paid: only View details", View.GONE, paymentView(activity, "Reservation #734", R.id.paymentLeftAction).getVisibility());
    }

    @Test
    public void paymentCards_theActionRowHasNoEmptyGap_andViewDetailsIsRightAligned() {
        DashboardActivity activity = launch();
        activity.findViewById(R.id.paymentsToggle).performClick();
        ReservationDto paidToday = reservationPaid(741);
        paidToday.check_in = manilaToday().toString();
        paidToday.check_out = manilaToday().plusDays(1).toString();
        answerWith(java.util.Arrays.asList(paidToday, unpaidReservation(742, "gcash", 2)), NO_DIRECT);
        View root = activity.getWindow().getDecorView();
        int w = app.getResources().getDisplayMetrics().widthPixels;
        int h = app.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);

        for (String ref : new String[]{"Reservation #741", "Reservation #742"}) {
            View actions = paymentView(activity, ref, R.id.paymentActions);
            View details = paymentValue(activity, ref, R.id.tvPaymentViewDetails);
            View paidOf = paymentValue(activity, ref, R.id.tvPaymentPaidOf);
            int gap = LayoutHarness.boundsIn(root, actions).top - LayoutHarness.boundsIn(root, paidOf).bottom;
            assertTrue(ref + ": the action row starts right under the progress text (" + gap + "px)", gap <= LayoutHarness.dp(app, 8));
            assertTrue(ref + ": View details is on the right",
                    LayoutHarness.boundsIn(root, details).right >= LayoutHarness.boundsIn(root, actions).right - LayoutHarness.dp(app, 2));
        }
        View detailsOnly = paymentView(activity, "Reservation #741", R.id.paymentActions);
        assertTrue("a card with no button has a compact action row (" + detailsOnly.getHeight() + "px)", detailsOnly.getHeight() <= LayoutHarness.dp(app, 44));
    }

    private void assertPaymentCardsFit(DashboardActivity activity) {
        View root = activity.getWindow().getDecorView();
        int w = app.getResources().getDisplayMetrics().widthPixels;
        int h = app.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        for (String ref : new String[]{"Reservation #751", "Reservation #752"}) {
            View id = paymentValue(activity, ref, R.id.tvPaymentBookingId);
            View chip = paymentValue(activity, ref, R.id.tvPaymentStatus);
            View meta = paymentValue(activity, ref, R.id.tvPaymentStayMeta);
            View pay = paymentView(activity, ref, R.id.paymentLeftAction);
            View details = paymentValue(activity, ref, R.id.tvPaymentViewDetails);
            assertFalse(ref + " id/chip overlap", LayoutHarness.overlaps(root, id, chip));
            assertFalse(ref + " chip/meta overlap", LayoutHarness.overlaps(root, chip, meta));
            if (pay.getVisibility() == View.VISIBLE) assertFalse(ref + " pay/details overlap", LayoutHarness.overlaps(root, pay, details));
            for (View v : new View[]{id, chip, meta, details}) {
                assertTrue(ref + " inside the screen", LayoutHarness.boundsIn(root, v).right <= w);
            }
            assertEquals("the ID wraps instead of being cut", 0, ((TextView) id).getLayout().getEllipsisCount(0));
            assertEquals(ref + " no letter-spacing on Pay Now", 0f, ((TextView) paymentValue(activity, ref, R.id.btnQuickPay)).getLetterSpacing(), 0.0001f);
        }
    }

    @Test
    public void paymentCards_fitAt360dp_inLightMode() {
        DashboardActivity activity = launch();
        activity.findViewById(R.id.paymentsToggle).performClick();
        answerWith(java.util.Arrays.asList(unpaidReservation(751, "gcash", 2), reservationPartlyPaid(752)), NO_DIRECT);
        assertPaymentCardsFit(activity);
    }

    @Test
    @org.robolectric.annotation.Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xxhdpi", application = TestApplication.class)
    public void paymentCards_fitAt360dp_inDarkMode_withReadableChipAndBalance() {
        DashboardActivity activity = launch();
        activity.findViewById(R.id.paymentsToggle).performClick();
        answerWith(java.util.Arrays.asList(unpaidReservation(751, "gcash", 2), reservationPartlyPaid(752)), NO_DIRECT);
        assertPaymentCardsFit(activity);
        int surface = app.getColor(R.color.velocity_card_surface);
        for (String ref : new String[]{"Reservation #751", "Reservation #752"}) {
            TextView balance = paymentValue(activity, ref, R.id.tvPaymentRemainingBalance);
            assertTrue(ref + " balance readable on the dark card",
                    androidx.core.graphics.ColorUtils.calculateContrast(balance.getCurrentTextColor(), surface) >= 3.0);
        }
    }

    @Test
    public void nextStayCard_paymentRow_showsBalanceDueInRed_orPaidWithACheck() {
        DashboardActivity activity = launch();
        com.example.velocitysuites.network.dto.ReservationDto paid = ScreenTestSupport.reservation(570, 1800,
                ScreenTestSupport.payment(1, "1800.00", "completed", "gcash"));
        paid.check_in = manilaToday().plusDays(2).toString();
        paid.check_out = manilaToday().plusDays(3).toString();
        answerWith(ScreenTestSupport.reservations(stay(569, 2, 1), paid), ScreenTestSupport.directBookings());
        ViewGroup cards = activity.findViewById(R.id.nextStayDayCards);
        boolean sawBalance = false;
        boolean sawPaid = false;
        for (int i = 0; i < cards.getChildCount(); i++) {
            TextView value = cards.getChildAt(i).findViewById(R.id.tvNextStayBalance);
            if (value == null) continue;
            String shown = value.getText().toString();
            if (shown.equals("₱1,800.00")) {
                sawBalance = true;
                assertEquals("Balance due", ((TextView) cards.getChildAt(i).findViewById(R.id.tvNextStayBalanceLabel)).getText().toString());
            } else if (shown.equals("Paid")) {
                sawPaid = true;
                assertNotNull("a green check beside Paid", value.getCompoundDrawables()[0]);
            }
        }
        assertTrue("one card owes the balance", sawBalance);
        assertTrue("one card is paid", sawPaid);
    }

    @Test
    public void nextStay_withNoUpcomingStay_opensOnTodayWithTheEmptyState() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(590, -5, 1)), ScreenTestSupport.directBookings());

        ViewGroup strip = activity.findViewById(R.id.nextStayDayStrip);
        assertTrue(strip.getChildAt(14).isSelected());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.nextStayDayEmpty).getVisibility());
    }

    @Test
    public void otherStays_followTheSelectedDay_soNothingIsHiddenFromBothSections() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 2, 1)), ScreenTestSupport.directBookings(booking(338, 5)));
        ViewGroup more = activity.findViewById(R.id.moreStaysList);

        assertEquals("on 588's day, Other Stays lists only 338", 1, more.getChildCount());
        assertEquals("Booking #338", ((TextView) more.getChildAt(0).findViewById(R.id.tvRecentStayRef)).getText().toString());

        selectDay(activity, 5);
        assertEquals("on 338's day, Other Stays lists only 588", 1, more.getChildCount());
        assertEquals("Reservation #588", ((TextView) more.getChildAt(0).findViewById(R.id.tvRecentStayRef)).getText().toString());

        selectDay(activity, 0);
        assertEquals("on a quiet day both are listed", 2, more.getChildCount());
    }

    @Test
    public void stayCalendar_showCancelledToggle_listsThem_andTheyOpenUpcomingWithTheCancelledFilter() {
        DashboardActivity activity = launch();
        ReservationDto cancelled = stay(591, 0, 1);
        cancelled.status = "CANCELLED";
        answerWith(ScreenTestSupport.reservations(cancelled), ScreenTestSupport.directBookings());
        ViewGroup entries = activity.findViewById(R.id.stayCalSummary);
        assertEquals("hidden by default", 0, entries.getChildCount());

        activity.<android.widget.CompoundButton>findViewById(R.id.stayCalShowCancelled).setChecked(true);
        idle();
        assertEquals(1, entries.getChildCount());

        tap(entries.getChildAt(0));
        Intent next = nextScreen(activity);
        assertEquals(new ComponentName(activity, com.example.velocitysuites.UpcomingTransactionsActivity.class), next.getComponent());
        assertEquals("CANCELLED", next.getStringExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_STATUS_FILTER));
    }

    /** Taps the day {@code daysAway} from today in the Your Next Stay selector. */
    private static void selectDay(DashboardActivity activity, int daysAway) {
        ViewGroup strip = activity.findViewById(R.id.nextStayDayStrip);
        strip.getChildAt(14 + daysAway).performClick();
        idle();
    }

    @Test
    public void aStayIsShownOnlyOnceAcrossNextStayAndTheOtherStays() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 2, 1)), ScreenTestSupport.directBookings(booking(338, 5)));

        ViewGroup more = activity.findViewById(R.id.moreStaysList);
        assertEquals(1, more.getChildCount());
        assertEquals("Booking #338", ((TextView) more.getChildAt(0).findViewById(R.id.tvRecentStayRef)).getText().toString());
    }

    @Test
    public void otherStays_openThatTransactionInUpcoming() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 2, 1)), ScreenTestSupport.directBookings(booking(338, 5)));

        tap(activity.<ViewGroup>findViewById(R.id.moreStaysList).getChildAt(0));
        Intent opened = nextScreen(activity);
        assertEquals(new ComponentName(activity, com.example.velocitysuites.UpcomingTransactionsActivity.class), opened.getComponent());
        assertEquals("338", opened.getStringExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID));
        assertTrue(opened.getBooleanExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_DIRECT, false));
    }

    @Test
    public void everySection_startsCollapsed_hasAChevronThatTurns_andTheHeaderRowTogglesIt() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 2, 1)), ScreenTestSupport.directBookings(booking(338, 5)));

        for (int body : new int[]{R.id.nextStayCard, R.id.moreStaysBody, R.id.quickActionsBody, R.id.summaryBody, R.id.bookingsBody,
                R.id.notificationsBody, R.id.paymentsBody, R.id.recentUpcomingList, R.id.stayCalBody}) {
            assertEquals(activity.getResources().getResourceEntryName(body) + ": collapsed never means hidden",
                    View.VISIBLE, activity.findViewById(body).getVisibility());
        }

        int[] toggles = {R.id.moreStaysToggle, R.id.stayCalToggle, R.id.recentUpcomingToggle, R.id.bookingsToggle,
                R.id.notificationsToggle, R.id.paymentsToggle, R.id.supportToggle}; // the Summary opens expanded and is tested on its own
        for (int id : toggles) {
            View toggle = activity.findViewById(id);
            String name = activity.getResources().getResourceEntryName(id);
            // The Pending & Confirmed Stays toggle is named ("Expand Pending & Confirmed Stays"); the rest read "Expand section".
            String expand = id == R.id.recentUpcomingToggle ? app.getString(R.string.section_expand_named, app.getString(R.string.recent_upcoming_title)) : app.getString(R.string.section_expand);
            String collapse = id == R.id.recentUpcomingToggle ? app.getString(R.string.section_collapse_named, app.getString(R.string.recent_upcoming_title)) : app.getString(R.string.section_collapse);
            assertEquals(name + " starts collapsed", expand, toggle.getContentDescription().toString());
            assertEquals(name + " chevron points down", 0f, toggle.getRotation(), 0.01f);

            toggle.performClick(); // the chevron
            idle();
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
            assertEquals(name + " expanded", collapse, toggle.getContentDescription().toString());
            assertEquals(name + " chevron turned over", 180f, toggle.getRotation(), 0.01f);

            ((View) toggle.getParent()).performClick(); // the header row
            idle();
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
            assertEquals(name + " collapsed again from the header row", expand, toggle.getContentDescription().toString());
        }
    }

    @Test
    public void needHelp_collapsedShowsOneLine_expandedShowsTheCard() {
        DashboardActivity activity = launch();
        assertEquals(View.GONE, activity.findViewById(R.id.supportDesc).getVisibility());
        assertTrue(text(activity, R.id.supportSummary).length() > 0);
        activity.findViewById(R.id.supportToggle).performClick();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.supportDesc).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.supportSummary).getVisibility());
    }

    @Test
    public void stayCalendar_listsOnlyTheEssentials_andLinksIntoUpcoming() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 0, 1)), ScreenTestSupport.directBookings());

        ViewGroup entries = activity.findViewById(R.id.stayCalSummary);
        assertTrue("today's check-in is listed", entries.getChildCount() >= 1);
        View entry = entries.getChildAt(0);
        assertEquals("Reservation #588", ((TextView) entry.findViewById(R.id.tvRecentStayRef)).getText().toString());
        assertTrue(((TextView) entry.findViewById(R.id.tvRecentStayMeta)).getText().toString().startsWith("1 night"));
        assertTrue(((TextView) entry.findViewById(R.id.tvRecentStayStatus)).getText().length() > 0);

        tap(entry);
        Intent focused = nextScreen(activity);
        assertEquals(new ComponentName(activity, com.example.velocitysuites.UpcomingTransactionsActivity.class), focused.getComponent());
        assertEquals("588", focused.getStringExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID));
    }

    @Test
    public void tappingAnIdOnTheDashboard_opensUpcomingFocusedOnThatTransaction() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 2, 1)), ScreenTestSupport.directBookings(booking(338, 5)));

        // Your Next Stay: the reference of the reservation.
        selectDay(activity, 2);
        tap(activity.findViewById(R.id.tvNextStayRef));
        Intent next = nextScreen(activity);
        assertEquals(new ComponentName(activity, com.example.velocitysuites.UpcomingTransactionsActivity.class), next.getComponent());
        assertEquals("588", next.getStringExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID));
        assertEquals(false, next.getBooleanExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_DIRECT, true));
    }

    @Test
    public void noDashboardControlIsAnUpcomingButton() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 2, 1)), ScreenTestSupport.directBookings(booking(338, 3)));

        assertNoUpcomingControl(activity.findViewById(android.R.id.content));
    }

    /** Titles may say "Upcoming"; a tappable control (button, link, pill) must not. */
    private static void assertNoUpcomingControl(View view) {
        if (view.getVisibility() == View.GONE) return;
        if (view instanceof TextView && view.isClickable()) {
            assertFalse("Upcoming button: " + ((TextView) view).getText(),
                    ((TextView) view).getText().toString().toLowerCase(Locale.ENGLISH).contains("upcoming"));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) assertNoUpcomingControl(group.getChildAt(i));
        }
    }

    @Test
    public void otherStays_collapsedShowsThree_expandedShowsEveryStay() {
        DashboardActivity activity = launch();
        // 8 upcoming stays: the selected (nearest) day shows one, the other 7 are Other Stays
        ReservationDto[] reservations = new ReservationDto[8];
        for (int i = 0; i < 8; i++) reservations[i] = stay(600 + i, 2 + i, 1);
        answerWith(ScreenTestSupport.reservations(reservations), ScreenTestSupport.directBookings());

        ViewGroup more = activity.findViewById(R.id.moreStaysList);
        assertEquals("collapsed: three cards and the Show all button", 3 + 1, more.getChildCount());
        assertEquals("Show all (7)", ((TextView) more.findViewById(R.id.btnPreviewToggle)).getText().toString());

        activity.findViewById(R.id.moreStaysToggle).performClick();
        assertEquals("expanded: every stay and Show less", 7 + 1, more.getChildCount());
        activity.findViewById(R.id.btnPreviewToggle).performClick();
        assertEquals("the button collapses it again", 3 + 1, more.getChildCount());
    }

    @Test
    public void stayCalendar_listsEachTransactionOnce_acrossAPeriod_sortedByCheckIn_withACount() {
        DashboardActivity activity = launch();
        // 590 arrives in 2 days for 3 nights, 588 arrives today for 2 nights: both overlap this week; each must appear once.
        answerWith(ScreenTestSupport.reservations(stay(590, 2, 3), stay(588, 0, 2)), ScreenTestSupport.directBookings());
        ViewGroup entries = activity.findViewById(R.id.stayCalSummary);

        activity.findViewById(R.id.stayCalModeMonthly).performClick();
        idle();
        java.util.List<String> refs = new java.util.ArrayList<>();
        for (int i = 0; i < entries.getChildCount(); i++) {
            refs.add(((TextView) entries.getChildAt(i).findViewById(R.id.tvRecentStayRef)).getText().toString());
        }
        assertEquals("a multi-day stay is listed once, nearest check-in first", java.util.Arrays.asList("Reservation #588", "Reservation #590"), refs);
        assertEquals("2 transactions", text(activity, R.id.stayCalCount));

        activity.findViewById(R.id.stayCalModeDaily).performClick();
        idle();
        assertEquals("today overlaps only 588", 1, entries.getChildCount());
    }

    private static final int[] STATE_VIEWS = {R.id.stateSummary, R.id.stateRecent, R.id.statePayments, R.id.stateNotifications};
    private static final int[] EMPTY_MESSAGES = {R.id.noBookingsText, R.id.noPaymentsText, R.id.noNotificationsText};

    @Test
    public void everySectionSaysLoadingFirst_notThatYouHaveNone() {
        DashboardActivity activity = launch();
        for (int id : STATE_VIEWS) {
            assertEquals(View.VISIBLE, activity.findViewById(id).getVisibility());
            assertEquals(app.getString(R.string.dashboard_section_loading), ((TextView) activity.findViewById(id).findViewById(R.id.sectionStateText)).getText().toString());
        }
        for (int emptyId : EMPTY_MESSAGES) {
            assertEquals("no empty message while loading", View.GONE, activity.findViewById(emptyId).getVisibility());
        }
    }

    @Test
    public void whenLoadingFails_everySectionShowsAnErrorWithRetry_andNeverAnEmptyMessage() {
        DashboardActivity activity = launch();
        failEverythingPending();

        for (int id : STATE_VIEWS) {
            View state = activity.findViewById(id);
            assertEquals(View.VISIBLE, state.getVisibility());
            assertEquals(app.getString(R.string.dashboard_section_error), ((TextView) state.findViewById(R.id.sectionStateText)).getText().toString());
            View retry = state.findViewById(R.id.sectionStateRetry);
            assertEquals(View.VISIBLE, retry.getVisibility());
            assertTrue("a 48dp touch target", retry.getLayoutParams().height >= (int) (48 * app.getResources().getDisplayMetrics().density));
        }
        for (int emptyId : EMPTY_MESSAGES) {
            assertEquals("a failed load is never worded as none", View.GONE, activity.findViewById(emptyId).getVisibility());
        }
        assertEquals("the summary counts are hidden too", View.GONE, ((View) activity.findViewById(R.id.tileActiveBooking).getParent()).getVisibility());
    }

    @Test
    public void retryInAnySection_asksAgain_andShowsLoadingWhileItDoes() {
        DashboardActivity activity = launch();
        failEverythingPending();
        int before = api.count("getReservations");

        tap(activity.findViewById(R.id.stateRecent).findViewById(R.id.sectionStateRetry));
        idle();

        assertTrue("asked the server again", api.count("getReservations") > before);
        assertEquals(app.getString(R.string.dashboard_section_loading),
                ((TextView) activity.findViewById(R.id.stateRecent).findViewById(R.id.sectionStateText)).getText().toString());
    }

    @Test
    public void aLoadedButTrulyEmptyDashboardSaysSo_withoutErrors() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(), ScreenTestSupport.directBookings());
        api.<Object>call("getNotifications", 0).succeed(ScreenTestSupport.notificationPage(0));
        idle();

        for (int id : STATE_VIEWS) assertEquals(View.GONE, activity.findViewById(id).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.noBookingsText).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.noNotificationsText).getVisibility());
    }

    @Test
    public void recentBookingsIsTheSectionName() {
        DashboardActivity activity = launch();
        assertEquals("Recent Bookings", app.getString(R.string.recent_bookings_title));
        assertNotNull(findText(activity, R.string.recent_bookings_title));
    }

    @Test
    public void allSixSummaryCounters_shareOneSize_andEveryCounterHasALabelUnderIt() {
        DashboardActivity a = launch();
        answer(stay(1, 3, 2));
        int[] counts = {R.id.activeBookingsCountText, R.id.activeReservationsCountText, R.id.pastBookingsCountText,
                R.id.pastReservationsCountText, R.id.cancelBookingsCountText, R.id.cancelReservationsCountText};
        float size = ((TextView) a.findViewById(counts[0])).getTextSize();
        for (int id : counts) {
            TextView count = a.findViewById(id);
            assertEquals(a.getResources().getResourceEntryName(id), size, count.getTextSize(), 0.01f);
            ViewGroup column = (ViewGroup) count.getParent();
            TextView label = (TextView) column.getChildAt(column.indexOfChild(count) + 1);
            assertTrue("label under " + a.getResources().getResourceEntryName(id), label.getText().length() > 0);
            assertTrue("the value is the larger of the two", count.getTextSize() > label.getTextSize());
        }
    }

    @Test
    public void theClockStopsWhileTheScreenIsInTheBackground_andCatchesUpOnReturn() {
        ActivityController<DashboardActivity> controller = Robolectric.buildActivity(DashboardActivity.class).setup();
        idle();
        DashboardActivity a = controller.get();
        String before = text(a, R.id.timeText);
        controller.pause();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMinutes(3));
        assertEquals("no tick while paused", before, text(a, R.id.timeText));
        ((TextView) a.findViewById(R.id.timeText)).setText("stale");
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5));
        assertEquals("still stale while paused", "stale", text(a, R.id.timeText));
        controller.resume();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
        assertTrue("back in front it shows the time again", !text(a, R.id.timeText).equals("stale"));
    }

    // ---- Your Next Stay: Daily / Weekly / Monthly ----

    @Test
    public void nextStay_hasADailyWeeklyMonthlyControl_defaultingToDaily_andTheChoiceIsSaved() {
        DashboardActivity activity = launch();
        answer(stay(7001, 1, 2));
        com.google.android.material.button.MaterialButtonToggleGroup group = activity.findViewById(R.id.nextStayMode);
        assertEquals(R.id.nextStayModeDaily, group.getCheckedButtonId());
        tap(activity.findViewById(R.id.nextStayModeWeekly));
        assertEquals(R.id.nextStayModeWeekly, group.getCheckedButtonId());
        assertEquals("WEEKLY", app.getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE).getString("next_stay_mode_v1", ""));
        tap(activity.findViewById(R.id.nextStayModeMonthly));
        assertEquals("MONTHLY", app.getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE).getString("next_stay_mode_v1", ""));
    }

    @Test
    public void nextStay_monthly_showsMonthChips_andTheMonthContextLine() {
        DashboardActivity activity = launch();
        answer(stay(7003, 1, 2));
        tap(activity.findViewById(R.id.nextStayModeMonthly));
        String info = text(activity, R.id.nextStayDayInfo);
        assertTrue(info, info.matches("[A-Z][a-z]+ \\d{4} · \\d+ stays?"));
        TextView firstTitle = ((ViewGroup) activity.findViewById(R.id.nextStayDayStrip)).getChildAt(0).findViewById(R.id.tvDayName);
        assertTrue(firstTitle.getText().toString(), firstTitle.getText().toString().matches("[A-Z][a-z]{2} \\d{4}"));
    }

    /** Every TextView-bearing badge count of the strip, as the chip shows it ("" when the badge is invisible). */
    private static String badge(View chip) {
        View b = chip.findViewById(R.id.tvDayCount);
        return b.getVisibility() == View.VISIBLE ? ((TextView) b).getText().toString() : "";
    }

    private static String chipTitle(View chip) {
        return ((TextView) chip.findViewById(R.id.tvDayName)).getText().toString();
    }

    private static LocalDate sundayOf(LocalDate d) {
        return d.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY));
    }

    /** True when the stay [in, out] touches [start, end] at all (an independent re-statement of "overlaps"). */
    private static boolean overlaps(LocalDate in, LocalDate out, LocalDate start, LocalDate end) {
        return !out.isBefore(start) && !in.isAfter(end);
    }

    @Test
    public void nextStay_weekly_hasTheRightWeekChipsAndCounts_andListsEachOverlappingTransactionOnce_sortedByCheckIn() {
        DashboardActivity activity = launch();
        // A: a 3-night stay starting tomorrow (may cross into next week); B: starts in 8 days; C: starts in 15 days, 1 night.
        // Booked out of order on purpose.
        ReservationDto c = stay(7013, 15, 1);
        ReservationDto a = stay(7011, 1, 3);
        ReservationDto b = stay(7012, 8, 2);
        answer(c, a, b);
        tap(activity.findViewById(R.id.nextStayModeWeekly));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));

        LocalDate today = manilaToday();
        LocalDate thisWeek = sundayOf(today);
        LocalDate[][] stays = {
                {today.plusDays(1), today.plusDays(4)},
                {today.plusDays(8), today.plusDays(10)},
                {today.plusDays(15), today.plusDays(16)}};
        long[] ids = {7011, 7012, 7013};

        ViewGroup strip = activity.findViewById(R.id.nextStayDayStrip);
        int thisWeekIndex = -1;
        for (int i = 0; i < strip.getChildCount(); i++) if (chipTitle(strip.getChildAt(i)).equals("This week")) thisWeekIndex = i;
        assertTrue("a This week chip exists with past weeks to its left", thisWeekIndex >= 4);
        assertEquals("Next week", chipTitle(strip.getChildAt(thisWeekIndex + 1)));
        assertEquals("Last week", chipTitle(strip.getChildAt(thisWeekIndex - 1)));
        for (int i = 0; i < strip.getChildCount(); i++) {
            LocalDate start = thisWeek.plusWeeks(i - thisWeekIndex);
            LocalDate end = start.plusDays(6);
            int expected = 0;
            for (LocalDate[] s : stays) if (overlaps(s[0], s[1], start, end)) expected++;
            assertEquals("badge of the week starting " + start, expected == 0 ? "" : String.valueOf(expected), badge(strip.getChildAt(i)));
            if (i > thisWeekIndex + 1) {
                String range = ((TextView) strip.getChildAt(i).findViewById(R.id.tvDayName)).getText().toString();
                assertTrue("a date range title: " + range, range.matches("[A-Z][a-z]{2} \\d{1,2}–(\\d{1,2}|[A-Z][a-z]{2} \\d{1,2})"));
            }
        }

        // For every chip with something in it: the cards are exactly the overlapping stays, each once, nearest check-in first.
        for (int i = 0; i < strip.getChildCount(); i++) {
            LocalDate start = thisWeek.plusWeeks(i - thisWeekIndex);
            LocalDate end = start.plusDays(6);
            java.util.List<String> expected = new java.util.ArrayList<>();
            for (int k = 0; k < stays.length; k++) if (overlaps(stays[k][0], stays[k][1], start, end)) expected.add("#" + ids[k]);
            if (expected.isEmpty()) continue;
            tap(strip.getChildAt(i));
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
            ViewGroup cards = activity.findViewById(R.id.nextStayDayCards);
            java.util.List<String> shown = new java.util.ArrayList<>();
            for (int k = 0; k < cards.getChildCount(); k++) {
                String ref = ((TextView) cards.getChildAt(k).findViewById(R.id.tvNextStayRef)).getText().toString();
                shown.add(ref.substring(ref.indexOf('#')));
            }
            assertEquals("cards of the week starting " + start, expected, shown);
            assertEquals("the header badge counts the cards", String.valueOf(expected.size()), text(activity, R.id.nextStayCount));
            String info = text(activity, R.id.nextStayDayInfo);
            assertTrue(info, info.endsWith(" · " + expected.size() + (expected.size() == 1 ? " stay" : " stays")));
            strip = activity.findViewById(R.id.nextStayDayStrip);
        }
    }

    @Test
    public void nextStay_monthly_hasMonthChipsWithCounts_andListsEachOverlappingTransactionOnce() {
        DashboardActivity activity = launch();
        ReservationDto a = stay(7021, 1, 2);
        ReservationDto b = stay(7022, 40, 2);
        answer(b, a);
        tap(activity.findViewById(R.id.nextStayModeMonthly));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        LocalDate today = manilaToday();
        LocalDate[][] stays = {{today.plusDays(1), today.plusDays(3)}, {today.plusDays(40), today.plusDays(42)}};
        ViewGroup strip = activity.findViewById(R.id.nextStayDayStrip);
        int thisMonthIndex = -1;
        for (int i = 0; i < strip.getChildCount(); i++) {
            String sub = ((TextView) strip.getChildAt(i).findViewById(R.id.tvDayDate)).getText().toString();
            if (sub.equals("This month")) thisMonthIndex = i;
        }
        assertTrue("a This month chip with past months to its left", thisMonthIndex >= 3);
        for (int i = 0; i < strip.getChildCount(); i++) {
            LocalDate start = today.withDayOfMonth(1).plusMonths(i - thisMonthIndex);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            int expected = 0;
            for (LocalDate[] s : stays) if (overlaps(s[0], s[1], start, end)) expected++;
            assertEquals("badge of " + start.getMonth(), expected == 0 ? "" : String.valueOf(expected), badge(strip.getChildAt(i)));
            assertEquals(start.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)), chipTitle(strip.getChildAt(i)));
        }
    }

    @Test
    public void dayRoleChip_isShownInDaily_butHiddenInWeeklyAndMonthly() {
        DashboardActivity activity = launch();
        answer(stay(7031, 0, 2));
        assertEquals("Daily keeps it", View.VISIBLE, activity.findViewById(R.id.tvNextStayHint).getVisibility());
        tap(activity.findViewById(R.id.nextStayModeWeekly));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        assertTrue(((ViewGroup) activity.findViewById(R.id.nextStayDayCards)).getChildCount() > 0);
        assertEquals("Weekly hides it", View.GONE, activity.findViewById(R.id.tvNextStayHint).getVisibility());
        assertEquals("the type chip stays", View.VISIBLE, activity.findViewById(R.id.tvNextStayType).getVisibility());
        tap(activity.findViewById(R.id.nextStayModeMonthly));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        assertEquals("Monthly hides it", View.GONE, activity.findViewById(R.id.tvNextStayHint).getVisibility());
        tap(activity.findViewById(R.id.nextStayModeDaily));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        assertEquals("back in Daily", View.VISIBLE, activity.findViewById(R.id.tvNextStayHint).getVisibility());
    }

    // ---- Your Next Stay card: 393dp and dark mode, collapsed and expanded; recreation and coming back ----

    /** Expands the first card and checks the full details stay inside it, on one line each, with nothing overlapping. */
    private void assertExpandedFits(DashboardActivity a) {
        View toggle = a.findViewById(R.id.btnNextStayToggleDetails);
        tap(toggle);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        layOut(a, app);
        idle();
        layOut(a, app);
        View root = a.getWindow().getDecorView();
        View card = a.findViewById(R.id.nextStayItem);
        LinearLayout money = a.findViewById(R.id.nextStayMoney);
        assertEquals(View.VISIBLE, money.getVisibility());
        assertTrue(money.getChildCount() >= 3);
        android.graphics.Rect cardBounds = LayoutHarness.boundsIn(root, card);
        android.graphics.Rect moneyBounds = LayoutHarness.boundsIn(root, money);
        assertTrue("details inside the card: " + moneyBounds + " vs " + cardBounds,
                moneyBounds.left >= cardBounds.left && moneyBounds.right <= cardBounds.right && moneyBounds.bottom <= cardBounds.bottom);
        android.graphics.Rect actions = LayoutHarness.boundsIn(root, a.findViewById(R.id.nextStayActions));
        assertTrue("the details end above the actions", moneyBounds.bottom <= actions.top);
        android.graphics.Rect previous = null;
        for (int i = 0; i < money.getChildCount(); i++) {
            View row = money.getChildAt(i);
            android.graphics.Rect r = LayoutHarness.boundsIn(root, row);
            assertTrue("row " + i + " inside the card width", r.left >= cardBounds.left && r.right <= cardBounds.right);
            if (previous != null) assertTrue("rows " + (i - 1) + "/" + i + " do not overlap", r.top >= previous.bottom - 1);
            previous = r;
            if (row instanceof LinearLayout) {
                LinearLayout line = (LinearLayout) row;
                if (line.getChildCount() == 2) {
                    TextView label = (TextView) line.getChildAt(0);
                    TextView value = (TextView) line.getChildAt(1);
                    assertEquals("amount on one line: " + value.getText(), 1, value.getLineCount());
                    assertFalse("label and amount do not overlap", LayoutHarness.overlaps(root, label, value));
                    assertTrue("the amount is right-aligned", LayoutHarness.boundsIn(root, value).right >= r.right - LayoutHarness.dp(app, 2));
                }
            }
        }
        assertEquals("Hide full details", text(a, R.id.btnNextStayToggleDetails));
    }

    @Test
    public void nextStayCard_at393dp_fitsCollapsedAndExpanded() {
        org.robolectric.RuntimeEnvironment.setQualifiers("w393dp-h851dp-xxhdpi"); // set explicitly so an earlier test cannot leave another mode behind
        app = ApplicationProvider.getApplicationContext();
        DashboardActivity a = showCard(NO_RESERVATIONS, java.util.Arrays.asList(StayMoneyScreensTest.inHouseWithMoney()));
        assertExpandedFits(a);
    }

    @Test
    public void nextStayCard_at393dpInDarkMode_fitsCollapsedAndExpanded() {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); // an earlier test may have forced a mode (the theme toggle)
        try {
            org.robolectric.RuntimeEnvironment.setQualifiers("w393dp-h851dp-night-xxhdpi"); // set explicitly so an earlier test cannot leave another mode behind
            app = ApplicationProvider.getApplicationContext();
            DashboardActivity a = showCard(NO_RESERVATIONS, java.util.Arrays.asList(StayMoneyScreensTest.inHouseWithMoney()));
            assertEquals("night mode is really on", android.content.res.Configuration.UI_MODE_NIGHT_YES,
                    a.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK);
            assertExpandedFits(a);
        } finally {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    @Test
    public void nextStayCard_at360dpInDarkMode_fitsCollapsedAndExpanded_withReadableColours() {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); // an earlier test may have forced a mode (the theme toggle)
        try {
            org.robolectric.RuntimeEnvironment.setQualifiers("w360dp-h800dp-night-xxhdpi"); // set explicitly so an earlier test cannot leave another mode behind
            app = ApplicationProvider.getApplicationContext();
            DashboardActivity a = showCard(NO_RESERVATIONS, java.util.Arrays.asList(StayMoneyScreensTest.inHouseWithMoney()));
            assertEquals("night mode is really on", android.content.res.Configuration.UI_MODE_NIGHT_YES,
                    a.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK);
            assertExpandedFits(a);
            // balance is the red, not the dark-on-dark primary text
            TextView balance = a.findViewById(R.id.tvNextStayBalance);
            assertEquals(app.getColor(R.color.velocity_red_primary), balance.getCurrentTextColor());
        } finally {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    @Test
    public void nextStayCard_expandedState_survivesARealRecreation_andComingBackFromAnotherScreen() {
        ActivityController<DashboardActivity> controller = launchController();
        DashboardActivity a = controller.get();
        answerWith(NO_RESERVATIONS, java.util.Arrays.asList(StayMoneyScreensTest.inHouseWithMoney()));
        assertEquals(View.GONE, a.findViewById(R.id.nextStayMoney).getVisibility());
        tap(a.findViewById(R.id.btnNextStayToggleDetails));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        assertEquals(View.VISIBLE, a.findViewById(R.id.nextStayMoney).getVisibility());

        // rotation: the activity is destroyed and a new one built
        controller.recreate();
        idle();
        DashboardActivity b = controller.get();
        if (b.findViewById(R.id.nextStayItem) == null && api.count("getReservations") > 1) {
            ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1, NO_RESERVATIONS,
                    java.util.Arrays.asList(StayMoneyScreensTest.inHouseWithMoney()));
            idle();
        }
        assertTrue("a different activity instance", a != b);
        assertNotNull("the card is back", b.findViewById(R.id.nextStayItem));
        assertEquals("still expanded after rotation", View.VISIBLE, b.findViewById(R.id.nextStayMoney).getVisibility());
        assertEquals("Hide full details", text(b, R.id.btnNextStayToggleDetails));

        // another screen opens on top, then the guest comes back
        controller.pause().stop();
        controller.restart().start().resume();
        idle();
        assertEquals("still expanded after returning", View.VISIBLE, b.findViewById(R.id.nextStayMoney).getVisibility());
        tap(b.findViewById(R.id.btnNextStayToggleDetails));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        assertEquals("it can still be hidden again", View.GONE, b.findViewById(R.id.nextStayMoney).getVisibility());
    }

    @Test
    public void nextStayCard_collapsedByDefault_expandsOnShowFullDetails_andKeepsItOnAFreshScreen() {
        DashboardActivity a = launch();
        answer(stay(7004, 0, 2));
        assertEquals(View.GONE, a.findViewById(R.id.nextStayMoney).getVisibility());
        assertEquals("Open reservation ›", text(a, R.id.tvNextStayViewDetails));
        tap(a.findViewById(R.id.btnNextStayToggleDetails));
        assertEquals(View.VISIBLE, a.findViewById(R.id.nextStayMoney).getVisibility());
        // Switching period and back rebuilds the cards: the open state is kept.
        tap(a.findViewById(R.id.nextStayModeWeekly));
        tap(a.findViewById(R.id.nextStayModeDaily));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        assertEquals("still open after the cards were rebuilt", View.VISIBLE, a.findViewById(R.id.nextStayMoney).getVisibility());
        assertEquals("Hide full details", text(a, R.id.btnNextStayToggleDetails));
    }

    @Test
    public void bordered_sectionContainers_areIdentical_andNoAllCapsWelcome() {
        DashboardActivity a = launch();
        TextView welcome = a.findViewById(R.id.welcomeText);
        assertFalse(welcome.isAllCaps());
        assertEquals(0f, welcome.getLetterSpacing(), 0f);
        com.google.android.material.card.MaterialCardView acc = a.findViewById(R.id.accountSummaryCard);
        int[] ids = {R.id.paymentStatusCard, R.id.bookingDetailsCard, R.id.notificationsCard};
        for (int id : ids) {
            com.google.android.material.card.MaterialCardView c = a.findViewById(id);
            assertEquals(acc.getStrokeWidth(), c.getStrokeWidth());
            assertEquals(acc.getStrokeColor(), c.getStrokeColor());
            assertEquals(acc.getRadius(), c.getRadius(), 0.01f);
            assertEquals(acc.getCardElevation(), c.getCardElevation(), 0.01f);
        }
    }
}
