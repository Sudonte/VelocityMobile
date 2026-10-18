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

    // ---- Recent Upcoming Stay

    @Test
    public void recentUpcomingStay_sitsBelowTheSummary_showsFive_expandsToAll_andOpensFilteredUpcoming() {
        DashboardActivity activity = launch();
        View content = (View) activity.findViewById(R.id.welcomeCard).getParent();
        assertTrue("Recent Upcoming Stay now comes before the summary",
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
        assertEquals("Deluxe", text(activity, R.id.tvNextStayRooms));
        assertEquals("2 nights · 2 Guests", text(activity, R.id.tvNextStayNights));
        assertTrue(text(activity, R.id.tvNextStayDates).contains(" → "));
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
        assertEquals(View.VISIBLE, money.getVisibility());
        List<String> rows = new ArrayList<>();
        collectText(money, rows);
        String all = String.join(" | ", rows);
        int last = -1;
        for (String needle : new String[]{"Check-in Total", "₱1,600.00", "Approved amenities", "Breakfast (×1)", "₱200.00", "Broken lamp", "₱300.00",
                "Amenities total", "Remaining Balance – payable at check-out", "₱500.00", "Final Total", "₱2,100.00", "1 amenity request awaiting approval"}) {
            int at = all.indexOf(needle, Math.max(0, last));
            assertTrue(needle + " missing or out of order in: " + all, at >= 0);
            last = at;
        }
        assertFalse("a pending request is never part of the numbers", all.contains("₱100.00"));
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
    public void recentBookings_highlightTheBookingNumber_withTheRoomNameSmallerBelow() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(booking(338, 2)));

        for (int listId : new int[]{R.id.bookingListContainer}) {
            ViewGroup list = activity.findViewById(listId);
            TextView id = first(list, R.id.tvNextItemBookingId);
            TextView room = first(list, R.id.tvNextItemRoomName);
            assertEquals("Booking #338", id.getText().toString());
            assertTrue("the ID is the larger line", id.getTextSize() > room.getTextSize());
            assertEquals("in the brand colour", androidx.core.content.ContextCompat.getColor(app, R.color.velocity_red_primary), id.getCurrentTextColor());
            assertTrue("the room name is secondary text", room.getCurrentTextColor() != id.getCurrentTextColor());
            assertTrue("12sp minimum", room.getTextSize() >= 12 * app.getResources().getDisplayMetrics().scaledDensity - 0.5f);
        }
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
        TextView paymentRoom = (TextView) payments.getChildAt(0).findViewById(R.id.tvPaymentRoomName);
        assertTrue("the room name is the smaller line", paymentId.getTextSize() > paymentRoom.getTextSize());
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
        assertTrue(text(activity, R.id.nextStayDayInfo), text(activity, R.id.nextStayDayInfo).endsWith("2 stays"));
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
        assertTrue(text(activity, R.id.nextStayDayInfo), text(activity, R.id.nextStayDayInfo).contains("Your next stay · Tomorrow"));
        selectDay(activity, 0);
        assertTrue("Today is selected", strip.getChildAt(14).isSelected());
        assertEquals("tomorrow has two transactions", "2", ((TextView) strip.getChildAt(15).findViewById(R.id.tvDayCount)).getText().toString());
        assertEquals("nothing today: friendly empty state", View.VISIBLE, activity.findViewById(R.id.nextStayDayEmpty).getVisibility());
        assertEquals("a quiet day has no badge", View.INVISIBLE, strip.getChildAt(16 + 5).findViewById(R.id.tvDayCount).getVisibility());

        selectDay(activity, -1);
        assertEquals("a stay that ended yesterday still shows on its check-out day", 1, activity.<ViewGroup>findViewById(R.id.nextStayDayCards).getChildCount());
        selectDay(activity, 2);
        assertTrue("a stay also covers its check-out day: " + text(activity, R.id.nextStayDayInfo),
                text(activity, R.id.nextStayDayInfo).endsWith("2 stays"));
    }

    @Test
    public void nextStay_opensOnTheNearestDayWithAStay_andTheTodayButtonJumpsBack() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 3, 1)), ScreenTestSupport.directBookings());

        ViewGroup strip = activity.findViewById(R.id.nextStayDayStrip);
        assertTrue(strip.getChildAt(14 + 3).isSelected());
        assertTrue(text(activity, R.id.nextStayDayInfo), text(activity, R.id.nextStayDayInfo).contains("Your next stay · In 3 days"));
        assertEquals("the Today button shows while another day is selected", View.VISIBLE, activity.findViewById(R.id.btnNextStayToday).getVisibility());

        tap(activity.findViewById(R.id.btnNextStayToday));
        idle();
        assertTrue(strip.getChildAt(14).isSelected());
        assertEquals("no Today button once Today is selected", View.GONE, activity.findViewById(R.id.btnNextStayToday).getVisibility());
        assertTrue(text(activity, R.id.nextStayDayInfo), text(activity, R.id.nextStayDayInfo).contains("Today"));
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
        assertTrue(text(activity, R.id.nextStayDayInfo), text(activity, R.id.nextStayDayInfo).contains("Your next stay · In 2 days · 1 stay"));

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

        int[] toggles = {R.id.nextStayToggle, R.id.moreStaysToggle, R.id.stayCalToggle, R.id.recentUpcomingToggle, R.id.bookingsToggle,
                R.id.notificationsToggle, R.id.paymentsToggle, R.id.supportToggle}; // the Summary opens expanded and is tested on its own
        for (int id : toggles) {
            View toggle = activity.findViewById(id);
            String name = activity.getResources().getResourceEntryName(id);
            assertEquals(name + " starts collapsed", app.getString(R.string.section_expand), toggle.getContentDescription().toString());
            assertEquals(name + " chevron points down", 0f, toggle.getRotation(), 0.01f);

            toggle.performClick(); // the chevron
            idle();
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
            assertEquals(name + " expanded", app.getString(R.string.section_collapse), toggle.getContentDescription().toString());
            assertEquals(name + " chevron turned over", 180f, toggle.getRotation(), 0.01f);

            ((View) toggle.getParent()).performClick(); // the header row
            idle();
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
            assertEquals(name + " collapsed again from the header row", app.getString(R.string.section_expand), toggle.getContentDescription().toString());
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
}
