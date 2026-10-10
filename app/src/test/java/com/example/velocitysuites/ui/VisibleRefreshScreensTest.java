package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BillingSummaryActivity;
import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingAndReservationActivity;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.CalendarActivity;
import com.example.velocitysuites.DashboardActivity;
import com.example.velocitysuites.NotificationActivity;
import com.example.velocitysuites.PaymentActivity;
import com.example.velocitysuites.PaymentTransaction;
import com.example.velocitysuites.ProfileManagementActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.RoomBrowsingActivity;
import com.example.velocitysuites.TransactionDetailsActivity;
import com.example.velocitysuites.TransactionHistoryActivity;
import com.example.velocitysuites.TransactionListActivity;
import com.example.velocitysuites.UpcomingTransactionsActivity;
import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.example.velocitysuites.ui.ScreenTestSupport.FakeApi;
import com.example.velocitysuites.ui.ScreenTestSupport.FakeCall;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Real-time data, screen by screen: each guest screen that shows server data refreshes itself every 30 seconds
 * WHILE IT IS ON SCREEN (and only then), silently, and only redraws when something really changed.
 * <p>
 * Part 1 is the same check for every screen: beats arrive 30 seconds apart (a second timer would show up as a
 * doubled request or a shorter gap), nothing is requested while the screen is covered, the timer restarts when the
 * guest comes back, and nothing happens once the screen is gone. Part 2 covers what matters per screen: a quiet
 * beat leaves what the guest has open alone, a changed server value reaches the screen without a pull, and the
 * payment form is never redrawn under the guest's hands.
 * <p>
 * Timing note: the virtual time that launching a screen consumes varies from screen to screen, and the timer starts
 * inside it. So these tests never assume "the first beat is N seconds after launch": they step the clock one second
 * at a time until a beat arrives and measure the gap to the next one.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class VisibleRefreshScreensTest {

    private static final int BEAT_SECONDS = 30;

    private Context app;
    private FakeApi api;
    /** Calls this test has already answered (FakeCall stays "pending" after it is answered). */
    private final Set<Object> settled = Collections.newSetFromMap(new IdentityHashMap<>());

    @Before
    public void setUp() throws Exception {
        app = ApplicationProvider.getApplicationContext();
        api = new FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        // The Profile screen calls ApiClient directly (not the repository), so the same fake answers it too.
        setApiClientService(api.proxy());
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com",
                "09171234567", "Female", "Jan 01, 1990", "");
    }

    @After
    public void tearDown() throws Exception {
        setApiClientService(null); // never leak the fake into another test
    }

    private static void setApiClientService(Object service) throws Exception {
        java.lang.reflect.Field field = com.example.velocitysuites.network.ApiClient.class.getDeclaredField("service");
        field.setAccessible(true);
        field.set(null, service);
    }

    // ---- helpers ----

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void advance(Duration d) {
        shadowOf(Looper.getMainLooper()).idleFor(d);
    }

    /** Answers every not-yet-answered call to {@code method} with {@code body} (null = the network is down). */
    private void answerPending(String method, Object body) {
        for (int i = 0; i < api.count(method); i++) {
            FakeCall<Object> call = api.call(method, i);
            if (!call.pending() || !settled.add(call)) continue;
            if (body == null) call.offline(); else call.succeed(body);
        }
        idle();
    }

    /** Settles everything outstanding as "offline" - including the retries that failing a call can trigger - so no screen is left waiting. */
    private void failAllPending() {
        boolean found;
        int guard = 0;
        do {
            found = false;
            for (String entry : new ArrayList<>(api.log)) {
                String method = entry.indexOf('(') < 0 ? entry : entry.substring(0, entry.indexOf('('));
                for (int i = 0; i < api.count(method); i++) {
                    FakeCall<Object> call = api.call(method, i);
                    if (call.pending() && settled.add(call)) {
                        call.offline();
                        found = true;
                    }
                }
            }
            idle();
        } while (found && ++guard < 10);
    }

    private void answerBookings(List<ReservationDto> reservations) {
        answerPending("getReservations", ScreenTestSupport.page(reservations));
        answerPending("getDirectBookings", ScreenTestSupport.page(ScreenTestSupport.directBookings()));
    }

    private static List<ReservationDto> oneReservation(double total) {
        return ScreenTestSupport.reservations(ScreenTestSupport.reservation(588, total));
    }

    private static List<ReservationDto> oneFutureReservation(double total) {
        ReservationDto reservation = ScreenTestSupport.reservation(588, total);
        // A stay next month, so it is in the default "active / upcoming" list whatever today's date is
        reservation.check_in = java.time.LocalDate.now().plusDays(30).toString();
        reservation.check_out = java.time.LocalDate.now().plusDays(32).toString();
        return ScreenTestSupport.reservations(reservation);
    }

    /** Launches the screen, with its first load still pending. */
    private <T extends Activity> ActivityController<T> launch(Class<T> type, Intent intent) {
        ActivityController<T> controller = Robolectric.buildActivity(type, intent);
        controller.setup();
        idle();
        return controller;
    }

    private Intent plain(Class<? extends Activity> type) {
        return new Intent(app, type);
    }

    /**
     * Steps the clock one second at a time until {@code marker} has been requested again, and returns how many
     * seconds that took. The beat must make exactly one such request (two would mean two timers).
     */
    private int secondsUntilBeat(String marker) {
        int before = api.count(marker);
        for (int seconds = 1; seconds <= BEAT_SECONDS + 5; seconds++) {
            advance(Duration.ofSeconds(1));
            if (api.count(marker) > before) {
                assertEquals("one beat makes one '" + marker + "' request (two would mean two timers)", before + 1, api.count(marker));
                return seconds;
            }
        }
        throw new AssertionError("no '" + marker + "' request within " + (BEAT_SECONDS + 5) + " seconds");
    }

    /**
     * Beats are 30 seconds apart. One second of slack: the clock is stepped in whole seconds and the harness can use a
     * few milliseconds inside idle() on a screen with an animated overlay, which can make a 30s gap read as 29.
     * A second timer would show as a doubled request (caught in secondsUntilBeat) or a gap of about 15.
     */
    private static void assertBeatGap(String message, int gapSeconds) {
        assertTrue(message + " (measured " + gapSeconds + "s)", gapSeconds >= BEAT_SECONDS - 1 && gapSeconds <= BEAT_SECONDS);
    }

    // ---- Part 1: the visible-only beat, for every screen ----

    /**
     * Beats 30 seconds apart; nothing while the screen is covered; a fresh 30-second timer when the guest comes
     * back (and still only one); nothing once the screen is destroyed. {@code marker} is the request the beat makes.
     */
    private <T extends Activity> void assertVisibleOnlyBeat(Class<T> type, Intent intent, String marker) {
        String name = type.getSimpleName();
        ActivityController<T> controller = launch(type, intent);
        failAllPending();

        secondsUntilBeat(marker); // whenever the first one is
        failAllPending();
        int gap = secondsUntilBeat(marker);
        assertBeatGap(name + ": beats are 30 seconds apart", gap);
        failAllPending();

        controller.pause();
        int whileCovered = api.log.size();
        advance(Duration.ofMinutes(5));
        assertEquals(name + ": nothing is requested while the screen is covered", whileCovered, api.log.size());

        long beforeResume = android.os.SystemClock.uptimeMillis();
        controller.resume();
        idle();
        // A screen with an animated loading overlay uses up virtual time INSIDE resume() (the harness keeps animating
        // it for up to 10s), and the timer started somewhere in there - so allow for what resume itself consumed.
        int consumed = (int) Math.ceil((android.os.SystemClock.uptimeMillis() - beforeResume) / 1000.0);
        failAllPending();
        int first = secondsUntilBeat(marker);
        assertTrue(name + ": after coming back the timer starts afresh - the first beat is 30 seconds after resuming "
                        + "(it came " + first + "s later, resuming itself used " + consumed + "s)",
                first <= BEAT_SECONDS && first >= BEAT_SECONDS - consumed - 1);
        failAllPending();
        assertBeatGap(name + ": and it stays one timer", secondsUntilBeat(marker));
        failAllPending();

        controller.pause().stop().destroy();
        int afterDestroy = api.log.size();
        advance(Duration.ofMinutes(2));
        assertEquals(name + ": nothing is requested once the screen is gone", afterDestroy, api.log.size());
    }

    @Test
    public void dashboard_beatsWhileVisibleOnly() {
        assertVisibleOnlyBeat(DashboardActivity.class, plain(DashboardActivity.class), "getNotifications");
    }

    @Test
    public void notifications_beatsWhileVisibleOnly() {
        assertVisibleOnlyBeat(NotificationActivity.class, plain(NotificationActivity.class), "getNotifications");
    }

    @Test
    public void transactionHistory_beatsWhileVisibleOnly() {
        assertVisibleOnlyBeat(TransactionHistoryActivity.class, plain(TransactionHistoryActivity.class), "getNotifications");
    }

    @Test
    public void bookingAndReservation_beatsWhileVisibleOnly() {
        assertVisibleOnlyBeat(BookingAndReservationActivity.class, plain(BookingAndReservationActivity.class), "getNotifications");
    }

    @Test
    public void rooms_beatsWhileVisibleOnly_throughTheNoCacheEndpoint() {
        assertVisibleOnlyBeat(RoomBrowsingActivity.class, plain(RoomBrowsingActivity.class), "getRoomsFresh");
    }

    @Test
    public void profile_beatsWhileVisibleOnly() {
        assertVisibleOnlyBeat(ProfileManagementActivity.class, plain(ProfileManagementActivity.class), "getProfile");
    }

    @Test
    public void calendar_beatsWhileVisibleOnly() {
        assertVisibleOnlyBeat(CalendarActivity.class, plain(CalendarActivity.class), "getNotifications");
    }

    @Test
    public void upcomingTransactions_beatsWhileVisibleOnly() {
        assertVisibleOnlyBeat(UpcomingTransactionsActivity.class, plain(UpcomingTransactionsActivity.class), "getNotifications");
    }

    @Test
    public void transactionList_beatsWhileVisibleOnly() {
        assertVisibleOnlyBeat(TransactionListActivity.class,
                plain(TransactionListActivity.class).putExtra(TransactionListActivity.EXTRA_LIST_TYPE, TransactionListActivity.TYPE_CANCELLED_BOOKINGS),
                "getNotifications");
    }

    @Test
    public void payment_beatsWhileVisibleOnly() {
        assertVisibleOnlyBeat(PaymentActivity.class, plain(PaymentActivity.class).putExtra("BOOKING_ID", "588"), "getNotifications");
    }

    @Test
    public void billing_beatsWhileVisibleOnly_andChecksTheReservation() {
        // Billing is not a BaseNavigationActivity (no header badge): its beat is the bookings check itself.
        // The screen closes itself when its reservation is missing, so give it the reservation first.
        ActivityController<BillingSummaryActivity> controller = launch(BillingSummaryActivity.class,
                plain(BillingSummaryActivity.class).putExtra(BillingSummaryActivity.EXTRA_RESERVATION_ID, "588"));
        answerBookings(oneReservation(1800));
        failAllPending();

        secondsUntilBeat("getReservations");
        answerBookings(oneReservation(1800));
        failAllPending();
        assertBeatGap("beats are 30 seconds apart", secondsUntilBeat("getReservations"));
        answerBookings(oneReservation(1800));
        failAllPending();

        controller.pause();
        int whileCovered = api.log.size();
        advance(Duration.ofMinutes(5));
        assertEquals("nothing while covered", whileCovered, api.log.size());

        long beforeResume = android.os.SystemClock.uptimeMillis();
        controller.resume();
        idle();
        int consumed = (int) Math.ceil((android.os.SystemClock.uptimeMillis() - beforeResume) / 1000.0);
        answerBookings(oneReservation(1800)); // the catch-up on coming back
        failAllPending();
        int first = secondsUntilBeat("getReservations");
        assertTrue("a fresh 30-second timer after coming back (first beat " + first + "s later, resuming used " + consumed + "s)",
                first <= BEAT_SECONDS && first >= BEAT_SECONDS - consumed - 1);
        answerBookings(oneReservation(1800));
        failAllPending();
        assertBeatGap("and still one timer", secondsUntilBeat("getReservations"));
    }

    @Test
    public void bookingDetails_beatsWhileVisibleOnly() {
        Booking booking = ApiMapper.toBooking(ScreenTestSupport.reservation(588, 1800));
        assertVisibleOnlyBeat(BookingDetailsActivity.class,
                plain(BookingDetailsActivity.class).putExtra(BookingDetailsActivity.EXTRA_BOOKING, booking), "getReservation");
    }

    @Test
    public void transactionDetails_stillBeatsWhileVisibleOnly() {
        Booking booking = ApiMapper.toBooking(ScreenTestSupport.reservation(588, 1800));
        assertVisibleOnlyBeat(TransactionDetailsActivity.class,
                plain(TransactionDetailsActivity.class).putExtra(TransactionDetailsActivity.EXTRA_TRANSACTION, new PaymentTransaction(booking, null)),
                "getReservations");
    }

    // ---- Part 2: what each beat does for the guest ----

    @Test
    public void dashboard_aQuietBeatLeavesTheCardsAlone_aChangeRedrawsThem() {
        ActivityController<DashboardActivity> controller = launch(DashboardActivity.class, plain(DashboardActivity.class));
        DashboardActivity activity = controller.get();
        answerBookings(oneReservation(1800));
        answerPending("getNotifications", ScreenTestSupport.notificationPage(1,
                ScreenTestSupport.notification(1, "Payment received", "payment", 588L, false)));
        ViewGroup notifications = activity.findViewById(R.id.notificationListContainer);
        assertEquals(1, notifications.getChildCount());
        View drawn = notifications.getChildAt(0);

        secondsUntilBeat("getNotifications"); // a beat: nothing has changed on the server
        answerBookings(oneReservation(1800));
        answerPending("getNotifications", ScreenTestSupport.notificationPage(1,
                ScreenTestSupport.notification(1, "Payment received", "payment", 588L, false)));
        assertSame("nothing changed, so the card the guest may have open is not rebuilt", drawn, notifications.getChildAt(0));

        secondsUntilBeat("getNotifications"); // next beat: a new notification arrived
        answerBookings(oneReservation(1800));
        answerPending("getNotifications", ScreenTestSupport.notificationPage(2,
                ScreenTestSupport.notification(2, "Booking confirmed", "booking", 588L, false),
                ScreenTestSupport.notification(1, "Payment received", "payment", 588L, false)));
        assertEquals("the new notification appeared without any pull", 2, notifications.getChildCount());
    }

    @Test
    public void bookingList_aQuietBeatDoesNotRebuildTheList_aChangeDoes() {
        ActivityController<BookingAndReservationActivity> controller = launch(BookingAndReservationActivity.class,
                plain(BookingAndReservationActivity.class));
        BookingAndReservationActivity activity = controller.get();
        // The Reservations tab, so the reservation in the fixture is the list being drawn.
        com.google.android.material.tabs.TabLayout tabs = activity.findViewById(R.id.tabLayout);
        tabs.getTabAt(1).select();
        idle();
        answerBookings(oneFutureReservation(1800));
        failAllPending(); // the header badge check
        RecyclerView list = activity.findViewById(R.id.rvItemList);
        RecyclerView.Adapter<?> drawn = list.getAdapter();
        assertNotEquals("the list has been drawn from the first load", null, drawn);

        secondsUntilBeat("getNotifications");
        answerBookings(oneFutureReservation(1800));
        failAllPending();
        assertSame("same data -> the list is not rebuilt under the guest", drawn, list.getAdapter());

        secondsUntilBeat("getNotifications");
        answerBookings(oneFutureReservation(2000));
        failAllPending();
        assertNotSame("a changed total -> the list is redrawn", drawn, list.getAdapter());
    }

    @Test
    public void rooms_aBeatShowsNewAvailability_withoutBlockingTheScreen_andKeepsTheGuestsFilter() {
        ActivityController<RoomBrowsingActivity> controller = launch(RoomBrowsingActivity.class, plain(RoomBrowsingActivity.class));
        RoomBrowsingActivity activity = controller.get();
        answerPending("getRoomsFresh", LandingFixtures.rooms(
                LandingFixtures.roomType(1, "Deluxe", "1800.00", 3),
                LandingFixtures.roomType(2, "Suite", "3000.00", 2),
                LandingFixtures.roomType(3, "Family", "4000.00", 5)));
        TextView count = activity.findViewById(R.id.tvResultsCount);
        assertEquals("3 Rooms Found", count.getText().toString());

        // The guest filters to one room type (their choice must survive every refresh from now on).
        AutoCompleteTextView types = activity.findViewById(R.id.dropdownRoomType);
        int deluxe = indexOf(types, "Deluxe");
        types.getOnItemClickListener().onItemClick(null, null, deluxe, 0);
        idle();
        answerPending("getRoomsFresh", LandingFixtures.rooms(
                LandingFixtures.roomType(1, "Deluxe", "1800.00", 3),
                LandingFixtures.roomType(2, "Suite", "3000.00", 2),
                LandingFixtures.roomType(3, "Family", "4000.00", 5)));
        assertEquals("1 Room Found", count.getText().toString());
        // every load of this screen - opening, searching, the beat - goes through the no-cache endpoint

        secondsUntilBeat("getRoomsFresh");
        assertEquals("the rooms screen never uses the cacheable endpoint", 0, api.count("getRooms"));
        assertEquals("a silent refresh never blocks the screen", View.GONE, activity.findViewById(R.id.loadingOverlay).getVisibility());
        answerPending("getRoomsFresh", LandingFixtures.rooms(
                LandingFixtures.roomType(1, "Deluxe", "2100.00", 1),   // price changed, one left
                LandingFixtures.roomType(2, "Suite", "3000.00", 2),
                LandingFixtures.roomType(3, "Family", "4000.00", 5),
                LandingFixtures.roomType(4, "Penthouse", "9000.00", 1)));
        assertEquals("the guest's room-type filter is still applied after the refresh", "1 Room Found", count.getText().toString());

        RecyclerView rooms = activity.findViewById(R.id.rvRooms);
        assertEquals("and the list shows just the Deluxe room", 1, rooms.getAdapter().getItemCount());
    }

    private static int indexOf(AutoCompleteTextView dropdown, String label) {
        for (int i = 0; i < dropdown.getAdapter().getCount(); i++) {
            if (label.equals(String.valueOf(dropdown.getAdapter().getItem(i)))) return i;
        }
        throw new AssertionError("no '" + label + "' in the dropdown");
    }

    @Test
    public void profile_isRefreshedWhenTheGuestComesBack_notOnTheFirstResume() {
        ActivityController<ProfileManagementActivity> controller = launch(ProfileManagementActivity.class, plain(ProfileManagementActivity.class));
        failAllPending();
        assertEquals("opening fetches the profile once (the first resume must not fetch again)", 1, api.count("getProfile"));

        controller.pause();
        controller.resume();
        idle();

        assertEquals("coming back fetches it again", 2, api.count("getProfile"));
    }

    @Test
    public void billing_aBeatShowsAChangedAmount() {
        ActivityController<BillingSummaryActivity> controller = launch(BillingSummaryActivity.class,
                plain(BillingSummaryActivity.class).putExtra(BillingSummaryActivity.EXTRA_RESERVATION_ID, "588"));
        BillingSummaryActivity activity = controller.get();
        answerBookings(oneReservation(1800));
        failAllPending();
        TextView balance = activity.findViewById(R.id.tvBillingBalanceDue);
        assertEquals("₱1,800.00", balance.getText().toString());

        secondsUntilBeat("getReservations");
        answerBookings(oneReservation(2000));
        failAllPending();

        assertEquals("the new amount reached the screen without leaving it", "₱2,000.00", balance.getText().toString());
    }

    @Test
    public void bookingDetails_aQuietBeatDoesNotRebuildThePage_aChangeDoes() {
        Booking booking = ApiMapper.toBooking(ScreenTestSupport.reservation(588, 1800));
        ActivityController<BookingDetailsActivity> controller = launch(BookingDetailsActivity.class,
                plain(BookingDetailsActivity.class).putExtra(BookingDetailsActivity.EXTRA_BOOKING, booking));
        BookingDetailsActivity activity = controller.get();
        answerPending("getReservation", ScreenTestSupport.reservation(588, 1800)); // the refresh on open
        failAllPending();
        LinearLayout section = activity.findViewById(R.id.sectionPaymentSummaryContent);
        View drawn = section.getChildAt(0);
        assertNotEquals(null, drawn);

        secondsUntilBeat("getReservation");
        answerPending("getReservation", ScreenTestSupport.reservation(588, 1800));
        failAllPending();
        assertSame("nothing changed -> the page is not rebuilt", drawn, section.getChildAt(0));

        secondsUntilBeat("getReservation");
        answerPending("getReservation", ScreenTestSupport.reservation(588, 2000));
        failAllPending();
        assertNotSame("a changed total -> the page is rebuilt", drawn, section.getChildAt(0));
    }

    @Test
    public void payment_whileReviewingTheBill_aBeatShowsAChangedTotal_butNeverOnceThePaymentFormIsOpen() {
        ActivityController<PaymentActivity> controller = launch(PaymentActivity.class, plain(PaymentActivity.class).putExtra("BOOKING_ID", "588"));
        PaymentActivity activity = controller.get();
        answerBookings(oneReservation(1800));
        failAllPending();
        TextView grandTotal = activity.findViewById(R.id.tvGrandTotal);
        assertEquals("₱1,800.00", grandTotal.getText().toString());

        secondsUntilBeat("getReservations"); // a beat while only reviewing
        answerBookings(oneReservation(2000));
        failAllPending();
        assertEquals("the changed total reached the review screen", "₱2,000.00", grandTotal.getText().toString());

        // Open the payment form: from now on nothing may be redrawn under the guest's hands.
        activity.findViewById(R.id.proceedToGcashButton).performClick();
        idle();
        Dialog dialog = ShadowDialog.getLatestDialog();
        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.gcashPortalSection).getVisibility());
        failAllPending();
        int bookingChecks = api.count("getReservations");
        int badgeChecks = api.count("getNotifications");

        advance(Duration.ofMinutes(3)); // several beats

        assertEquals("no bookings check while the payment form is open", bookingChecks, api.count("getReservations"));
        assertTrue("(the header badge still beats)", api.count("getNotifications") > badgeChecks);
    }
}
