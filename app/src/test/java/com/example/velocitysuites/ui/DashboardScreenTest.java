package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        assertEquals("Check-in tomorrow", text(activity, R.id.tvNextStayHint));
        assertEquals("Deluxe", text(activity, R.id.tvNextStayRooms));
        assertEquals("2 nights", text(activity, R.id.tvNextStayNights));
        assertTrue(text(activity, R.id.tvNextStayDates).contains(" → "));
        assertEquals("Waiting for payment", text(activity, R.id.tvNextStayStatus));
        assertEquals(app.getString(R.string.next_stay_payment_label), text(activity, R.id.tvNextStayBalanceLabel));
        assertEquals("You still need to pay ₱1,800.00", text(activity, R.id.tvNextStayBalance));
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

        tap(activity.findViewById(R.id.nextStayItem));
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

        java.util.List<String> labels = new java.util.ArrayList<>();
        ViewGroup nextStays = activity.findViewById(R.id.nextStayContent);
        for (int i = 0; i < nextStays.getChildCount(); i++) {
            TextView ref = nextStays.getChildAt(i).findViewById(R.id.tvNextStayRef);
            if (ref != null) labels.add(ref.getText().toString());
        }
        ViewGroup more = activity.findViewById(R.id.moreStaysList);
        for (int i = 0; i < more.getChildCount(); i++) {
            labels.add(((TextView) more.getChildAt(i).findViewById(R.id.tvStayCompactRef)).getText().toString());
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

        ViewGroup content = activity.findViewById(R.id.nextStayContent);
        java.util.List<String> refs = new java.util.ArrayList<>();
        for (int i = 0; i < content.getChildCount(); i++) {
            TextView ref = content.getChildAt(i).findViewById(R.id.tvNextStayRef);
            if (ref != null) refs.add(ref.getText().toString());
        }
        assertEquals(refs.toString(), 2, refs.size());
        assertTrue(refs.toString(), refs.contains("Reservation #588") && refs.contains("Booking #338"));
        java.util.List<String> all = new java.util.ArrayList<>();
        collectText(content, all);
        assertTrue("a note says there are several: " + all, all.contains(app.getString(R.string.next_stay_same_day_note, 2)));
    }

    @Test
    public void aStayIsShownOnlyOnceAcrossNextStayAndTheOtherStays() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 2, 1)), ScreenTestSupport.directBookings(booking(338, 5)));

        ViewGroup more = activity.findViewById(R.id.moreStaysList);
        assertEquals(1, more.getChildCount());
        assertEquals("Booking #338", ((TextView) more.getChildAt(0).findViewById(R.id.tvStayCompactRef)).getText().toString());
    }

    @Test
    public void otherStays_openThatTransactionInUpcoming_andViewAllOpensTheWholeList() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 2, 1)), ScreenTestSupport.directBookings(booking(338, 5)));

        tap(activity.<ViewGroup>findViewById(R.id.moreStaysList).getChildAt(0));
        Intent opened = nextScreen(activity);
        assertEquals(new ComponentName(activity, com.example.velocitysuites.UpcomingTransactionsActivity.class), opened.getComponent());
        assertEquals("338", opened.getStringExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID));
        assertTrue(opened.getBooleanExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_DIRECT, false));

        tap(activity.findViewById(R.id.btnViewAllUpcoming));
        opened = nextScreen(activity);
        assertEquals(new ComponentName(activity, com.example.velocitysuites.UpcomingTransactionsActivity.class), opened.getComponent());
        assertEquals(null, opened.getStringExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID));
    }

    @Test
    public void everySectionCollapsesToAOneLineSummary_andExpandsAgain() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 2, 1)), ScreenTestSupport.directBookings(booking(338, 5)));

        int[][] sections = {
                {R.id.nextStayToggle, R.id.nextStaySummary, R.id.nextStayCard},
                {R.id.stayCalToggle, R.id.stayCalCollapsedSummary, R.id.stayCalBody},
                {R.id.bookingsToggle, R.id.bookingsSummary, R.id.bookingsBody},
                {R.id.notificationsToggle, R.id.notificationsSummary, R.id.notificationsBody},
                {R.id.paymentsToggle, R.id.paymentsSummary, R.id.paymentsBody},
                {R.id.quickActionsToggle, R.id.quickActionsSummary, R.id.quickActionsBody},
                {R.id.summaryToggle, R.id.summarySummary, R.id.summaryBody},
                {R.id.supportToggle, R.id.supportSummary, R.id.supportDesc}};
        for (int[] s : sections) {
            String name = activity.getResources().getResourceEntryName(s[0]);
            assertEquals(name + " starts expanded", View.VISIBLE, activity.findViewById(s[2]).getVisibility());
            assertEquals(name + " summary hidden while expanded", View.GONE, activity.findViewById(s[1]).getVisibility());

            activity.findViewById(s[0]).performClick();
            assertEquals(name + " body hidden", View.GONE, activity.findViewById(s[2]).getVisibility());
            assertEquals(name + " summary shown", View.VISIBLE, activity.findViewById(s[1]).getVisibility());
            assertTrue(name + " summary says something", text(activity, s[1]).length() > 0);

            activity.findViewById(s[0]).performClick();
            assertEquals(name + " body back", View.VISIBLE, activity.findViewById(s[2]).getVisibility());
        }
    }

    @Test
    public void stayCalendar_listsOnlyTheEssentials_andLinksIntoUpcoming() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 0, 1)), ScreenTestSupport.directBookings());

        ViewGroup entries = activity.findViewById(R.id.stayCalSummary);
        assertTrue("today's check-in is listed", entries.getChildCount() >= 1);
        View entry = entries.getChildAt(0);
        assertEquals("Reservation #588", ((TextView) entry.findViewById(R.id.tvEntryRef)).getText().toString());
        assertTrue(((TextView) entry.findViewById(R.id.tvEntryWhen)).getText().toString().startsWith("Check-in"));
        assertTrue(((TextView) entry.findViewById(R.id.tvEntryStatus)).getText().length() > 0);
        assertEquals("no balance, rooms or stay lines any more", null, entry.findViewById(R.id.btnNextStayBook));

        tap(entry);
        Intent focused = nextScreen(activity);
        assertEquals(new ComponentName(activity, com.example.velocitysuites.UpcomingTransactionsActivity.class), focused.getComponent());
        assertEquals("588", focused.getStringExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID));

        tap(activity.findViewById(R.id.stayCalOpenAll));
        Intent all = nextScreen(activity);
        assertEquals(new ComponentName(activity, com.example.velocitysuites.UpcomingTransactionsActivity.class), all.getComponent());
        assertEquals("ALL", all.getStringExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_STATUS_FILTER));
    }

    @Test
    public void tappingAnIdOnTheDashboard_opensUpcomingFocusedOnThatTransaction() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 2, 1)), ScreenTestSupport.directBookings(booking(338, 5)));

        // Your Next Stay: the reference of the reservation.
        tap(activity.findViewById(R.id.tvNextStayRef));
        Intent next = nextScreen(activity);
        assertEquals(new ComponentName(activity, com.example.velocitysuites.UpcomingTransactionsActivity.class), next.getComponent());
        assertEquals("588", next.getStringExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID));
        assertEquals(false, next.getBooleanExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_DIRECT, true));

        // Recent Bookings: the booking's reference (the rest of that row still opens Transaction History).
        ViewGroup recent = activity.findViewById(R.id.bookingListContainer);
        tap(recent.getChildAt(0).findViewById(R.id.btnNextItemOpenUpcoming));
        Intent booking = nextScreen(activity);
        assertEquals(new ComponentName(activity, com.example.velocitysuites.UpcomingTransactionsActivity.class), booking.getComponent());
        assertEquals("338", booking.getStringExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID));
        assertEquals(true, booking.getBooleanExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_DIRECT, false));
    }

    @Test
    public void paymentStatusId_hasItsOwnButton_thatOpensUpcomingFocusedOnThatTransaction() {
        DashboardActivity activity = launch();
        answerWith(ScreenTestSupport.reservations(stay(588, 2, 1)), ScreenTestSupport.directBookings(booking(338, 3)));

        ViewGroup payments = activity.findViewById(R.id.paymentListContainer);
        assertTrue(payments.getChildCount() > 0);
        View row = payments.getChildAt(0);
        String shown = ((TextView) row.findViewById(R.id.tvPaymentBookingId)).getText().toString();
        View button = row.findViewById(R.id.btnPaymentOpenUpcoming);
        assertEquals(app.getString(R.string.open_in_upcoming), ((TextView) button).getText().toString());
        assertTrue("a comfortable touch target", button.getMinimumHeight() >= (int) (48 * app.getResources().getDisplayMetrics().density) - 1);
        assertEquals("the ID text itself is plain text", false, row.findViewById(R.id.tvPaymentBookingId).hasOnClickListeners());

        tap(button);
        Intent opened = nextScreen(activity);
        assertEquals(new ComponentName(activity, com.example.velocitysuites.UpcomingTransactionsActivity.class), opened.getComponent());
        boolean direct = shown.startsWith("Booking");
        assertEquals(shown.substring(shown.indexOf('#') + 1), opened.getStringExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID));
        assertEquals(direct, opened.getBooleanExtra(com.example.velocitysuites.UpcomingTransactionsActivity.EXTRA_SELECTED_DIRECT, !direct));
    }

    @Test
    public void otherStays_collapsedKeepsThePreviewCap_expandedShowsEveryStay() {
        DashboardActivity activity = launch();
        // 1 next stay + 7 further upcoming stays (the preview cap is 4)
        ReservationDto[] reservations = new ReservationDto[8];
        for (int i = 0; i < 8; i++) reservations[i] = stay(600 + i, 2 + i, 1);
        answerWith(ScreenTestSupport.reservations(reservations), ScreenTestSupport.directBookings());

        ViewGroup more = activity.findViewById(R.id.moreStaysList);
        assertEquals("expanded: every stay", 7, more.getChildCount());
        activity.findViewById(R.id.moreStaysToggle).performClick();
        assertEquals("collapsed: the preview", 4, more.getChildCount());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.moreStaysSummary).getVisibility());
        activity.findViewById(R.id.moreStaysToggle).performClick();
        assertEquals(7, more.getChildCount());
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
