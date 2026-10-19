package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.PaymentTransaction;
import com.example.velocitysuites.R;
import com.example.velocitysuites.NotificationActivity;
import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.TransactionDetailsActivity;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.NotificationDto;
import com.example.velocitysuites.network.dto.PaginatedResponse;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.example.velocitysuites.ui.ScreenTestSupport.FakeApi;
import com.example.velocitysuites.ui.ScreenTestSupport.FakeCall;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.time.Duration;
import java.util.Collections;

/**
 * The real Notifications screen against a fake API: the "You're all caught up" rule, the badge and summary
 * following every read/unread change in the same beat, the toggle label, and "View Transaction" opening the
 * EXACT record (or saying it is gone) without ever crashing.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NotificationScreenTest {

    private Context app;
    private FakeApi api;
    private RoomRepository repository;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new FakeApi();
        repository = ScreenTestSupport.freshRepository(app, api);
    }

    // ---- helpers ----

    private final java.util.List<ActivityController<NotificationActivity>> launched = new java.util.ArrayList<>();

    /** Leave no live window behind for the next test class (a leaked one breaks later theme-recreate tests). */
    @org.junit.After
    public void destroyLaunchedScreens() {
        for (ActivityController<NotificationActivity> c : launched) {
            try {
                c.pause().stop().destroy();
            } catch (RuntimeException ignored) {
                // already finished by the test
            }
        }
        launched.clear();
    }

    private ActivityController<NotificationActivity> launch() {
        ActivityController<NotificationActivity> controller = Robolectric.buildActivity(NotificationActivity.class, new Intent(app, NotificationActivity.class));
        launched.add(controller);
        controller.setup();
        idle();
        return controller;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void idleFor(long millis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    private void answerNotifications(int unread, NotificationDto... items) {
        FakeCall<PaginatedResponse<NotificationDto>> call = api.last("getNotifications");
        call.succeed(ScreenTestSupport.notificationPage(unread, items));
        idle();
    }

    private static RecyclerView list(NotificationActivity activity) {
        RecyclerView rv = activity.findViewById(R.id.rvNotifications);
        rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(3000, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, 1080, 3000);
        return rv;
    }

    private static View row(RecyclerView rv, int position) {
        RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(position);
        assertNotNull("row " + position + " must be bound", holder);
        return holder.itemView;
    }

    private static boolean isUnread(View row) {
        return row.findViewById(R.id.viewNotifUnreadDot).getVisibility() == View.VISIBLE;
    }

    /** Opens the card's overflow menu and picks its one item ("Mark as read" / "Mark as unread"); returns that item's title. */
    private static String pickOverflow(View row) {
        row.findViewById(R.id.btnNotifOverflow).performClick();
        android.widget.PopupMenu menu = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull("the overflow menu opened", menu);
        android.view.MenuItem item = menu.getMenu().getItem(0);
        String title = item.getTitle().toString();
        org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu();
        shadowOf(menu).getOnMenuItemClickListener().onMenuItemClick(item);
        return title;
    }

    /** Taps a card and returns the detail sheet it opens (the full message, the details, the action button). */
    private Dialog openSheet(NotificationActivity activity, int position) {
        row(list(activity), position).findViewById(R.id.cardNotification).performClick();
        idle();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("tapping a card opens its detail sheet", dialog);
        assertNotNull(dialog.findViewById(R.id.notifDetailSheet));
        return dialog;
    }

    private static void tapAction(Dialog sheet) {
        sheet.findViewById(R.id.btnNotifDetailAction).performClick();
        idle();
    }

    private static String summary(NotificationActivity activity) {
        return ((TextView) activity.findViewById(R.id.tvUnreadSummary)).getText().toString();
    }

    private static String badgeText(NotificationActivity activity) {
        View badge = activity.findViewById(R.id.headerNotificationBadge);
        return badge.getVisibility() == View.VISIBLE ? ((TextView) badge).getText().toString() : "(hidden)";
    }

    private static void confirmLatestDialog() {
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("a confirmation dialog", dialog);
        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
    }

    // ---- "You're all caught up" only when the count is known to be zero ----

    @Test
    public void beforeTheFirstLoad_theSummaryDoesNotClaimAllCaughtUp() {
        NotificationActivity activity = launch().get();
        assertEquals("Checking for new alerts…", summary(activity));
    }

    @Test
    public void afterALoadWithNothingUnread_itSaysAllCaughtUp() {
        NotificationActivity activity = launch().get();
        answerNotifications(0, ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, true));
        assertEquals("You're all caught up", summary(activity));
        assertEquals("(hidden)", badgeText(activity));
    }

    @Test
    public void withUnread_theSummaryAndBellBadgeShowTheSameCount() {
        NotificationActivity activity = launch().get();
        answerNotifications(2,
                ScreenTestSupport.notification(2, "Payment Pending Validation", "payment", 588L, false),
                ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, false));
        assertEquals("2 unread", summary(activity));
        assertEquals("2", badgeText(activity));
    }

    @Test
    public void offline_onFirstLoad_showsTheErrorState_andNeverAllCaughtUp() {
        NotificationActivity activity = launch().get();
        FakeCall<PaginatedResponse<NotificationDto>> call = api.last("getNotifications");
        call.offline();
        idle();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.scrollEmptyState).getVisibility());
        assertEquals("Unable to Load Notifications", ((TextView) activity.findViewById(R.id.emptyTitle)).getText().toString());
        assertEquals("Couldn't check for new alerts", summary(activity));
    }

    // ---- read / unread ----

    @Test
    public void markAllAsRead_clearsTheBadgeAndSummaryImmediately_thenStaysConsistent() {
        ActivityController<NotificationActivity> controller = launch();
        NotificationActivity activity = controller.get();
        answerNotifications(2,
                ScreenTestSupport.notification(2, "Payment Pending Validation", "payment", 588L, false),
                ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, false));

        activity.findViewById(R.id.btnMarkAllRead).performClick();
        confirmLatestDialog();

        // Before the server has even answered:
        assertEquals("You're all caught up", summary(activity));
        assertEquals("(hidden)", badgeText(activity));
        RecyclerView rv = list(activity);
        assertFalse("every row flipped to read", isUnread(row(rv, 0)));
        assertFalse(isUnread(row(rv, 1)));
        assertEquals("Mark all as read has nothing left to do", View.GONE, activity.findViewById(R.id.btnMarkAllRead).getVisibility());

        FakeCall<Object> request = api.last("markAllNotificationsRead");
        request.succeed(new Object());
        idle();
        assertEquals("You're all caught up", summary(activity));
    }

    @Test
    public void markAllAsRead_thatFails_putsTheUnreadStateBack() {
        NotificationActivity activity = launch().get();
        answerNotifications(1, ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, false));

        activity.findViewById(R.id.btnMarkAllRead).performClick();
        confirmLatestDialog();
        assertEquals("You're all caught up", summary(activity));

        FakeCall<Object> request = api.last("markAllNotificationsRead");
        request.offline();
        idle();
        assertEquals("1 unread", summary(activity));
        assertEquals("1", badgeText(activity));
    }

    @Test
    public void theOverflowMenuSwitchesBetweenMarkAsReadAndMarkAsUnread_andTheBadgeFollows() {
        NotificationActivity activity = launch().get();
        answerNotifications(1, ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, false));
        RecyclerView rv = list(activity);
        assertTrue(isUnread(row(rv, 0)));
        assertEquals("1", badgeText(activity));

        assertEquals("an unread card offers Mark as read", "Mark as read", pickOverflow(row(rv, 0)));
        confirmLatestDialog();
        rv = list(activity);
        assertFalse(isUnread(row(rv, 0)));
        assertEquals("(hidden)", badgeText(activity));
        assertEquals("You're all caught up", summary(activity));

        idleFor(800); // past the double-tap guard window, so this is a NEW tap, not a repeat of the last one
        assertEquals("a read card offers Mark as unread", "Mark as unread", pickOverflow(row(rv, 0)));
        confirmLatestDialog();
        rv = list(activity);
        assertTrue(isUnread(row(rv, 0)));
        assertEquals("1", badgeText(activity));
        assertEquals("1 unread", summary(activity));
    }

    @Test
    public void tappingAnUnreadCard_marksItRead_andOpensTheDetailSheet_notTheTransaction() {
        NotificationActivity activity = launch().get();
        answerNotifications(2,
                ScreenTestSupport.notification(1, "New Promotion", "promotion", null, false),
                ScreenTestSupport.notification(2, "Payment Received", "payment", 6L, false));
        assertEquals("2", badgeText(activity));

        Dialog sheet = openSheet(activity, 1); // the payment: it has a linked transaction, which must NOT open by itself

        assertEquals("the badge dropped at once", "1", badgeText(activity));
        assertTrue("the other row is untouched", isUnread(row(list(activity), 0)));
        assertFalse(isUnread(row(list(activity), 1)));
        assertEquals("Payment Received", ((TextView) sheet.findViewById(R.id.tvNotifDetailTitle)).getText().toString());
        assertEquals("View payment", ((TextView) sheet.findViewById(R.id.btnNotifDetailAction)).getText().toString());
        assertNull("no screen opened from the card tap", shadowOf(activity).getNextStartedActivity());
    }

    // ---- View Transaction ----

    @Test
    public void aCardWithNoTransaction_opensItsOwnDetails_andOneWithATransactionShowsItsLinkedId() {
        NotificationActivity activity = launch().get();
        answerNotifications(1,
                ScreenTestSupport.notification(2, "New Promotion", "promotion", null, false),
                ScreenTestSupport.notification(1, "Payment Pending Validation", "payment", 588L, false));
        RecyclerView rv = list(activity);
        assertEquals(View.GONE, row(rv, 0).findViewById(R.id.tvNotifLinked).getVisibility());
        assertEquals(View.VISIBLE, row(rv, 1).findViewById(R.id.tvNotifLinked).getVisibility());
        assertEquals("Booking #588", ((TextView) row(rv, 1).findViewById(R.id.tvNotifLinked)).getText().toString());
    }

    @Test
    public void viewTransaction_opensTheExactLoadedRecord_onlyFromTheSheetButton_onceEvenOnADoubleTap() {
        NotificationActivity activity = launch().get();
        // Two different transactions share id 5 (a reservation and a direct booking).
        repositoryHasBookings(ScreenTestSupport.reservation(5, 1800), ScreenTestSupport.directBooking(5, 3000));
        answerNotifications(1, ScreenTestSupport.notification(1, "Reservation Confirmed", "reservation", 5L, true));

        Dialog sheet = openSheet(activity, 0);
        assertNull("the card tap alone opens nothing", shadowOf(activity).getNextStartedActivity());
        assertEquals("View Reservation #5", ((TextView) sheet.findViewById(R.id.btnNotifDetailAction)).getText().toString());
        sheet.findViewById(R.id.btnNotifDetailAction).performClick();
        sheet.findViewById(R.id.btnNotifDetailAction).performClick();

        Intent started = shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(TransactionDetailsActivity.class.getName(), started.getComponent().getClassName());
        PaymentTransaction opened = (PaymentTransaction) started.getSerializableExtra(TransactionDetailsActivity.EXTRA_TRANSACTION);
        assertTrue("a Reservation notification opens the reservation, not the booking sharing its id", !opened.parentBooking.isDirectBooking());
        assertEquals(1800, opened.parentBooking.getTotalAmount(), 0.01);
        assertNull("no second screen from the second tap", shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void aReceiptNotification_opensThatReceiptFromTheSheetsButton() {
        NotificationActivity activity = launch().get();
        com.example.velocitysuites.network.dto.NotificationDto withReceipt = ScreenTestSupport.notification(1, "Checked In - Receipt 2 Ready", "booking", 5L, false);
        withReceipt.receipt_number = "SR-000123";
        withReceipt.receipt_type = "CHECKIN_SETTLEMENT_RECEIPT";
        answerNotifications(1, withReceipt);

        Dialog sheet = openSheet(activity, 0);
        assertEquals("View receipt", ((TextView) sheet.findViewById(R.id.btnNotifDetailAction)).getText().toString());
        tapAction(sheet);

        Intent started = shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.example.velocitysuites.PaymentReceiptActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("SR-000123", started.getStringExtra(com.example.velocitysuites.PaymentReceiptActivity.EXTRA_RECEIPT_NUMBER));
        assertNull(shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void viewTransaction_forARecordNotLoaded_fetchesItByIdThenOpensIt() {
        NotificationActivity activity = launch().get();
        answerNotifications(1, ScreenTestSupport.notification(1, "Booking Pending", "booking", 77L, true));

        tapAction(openSheet(activity, 0));
        assertEquals("a Booking notification asks the booking table first", "getDirectBooking(77)", api.log.get(api.log.size() - 1));

        FakeCall<DirectBookingResponseDto> call = api.last("getDirectBooking");
        call.succeed(ScreenTestSupport.directBooking(77, 3000));
        idle();

        Intent started = shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(TransactionDetailsActivity.class.getName(), started.getComponent().getClassName());
    }

    @Test
    public void viewTransaction_forARecordThatNoLongerExists_saysSoInTheSheet_insteadOfCrashing() {
        NotificationActivity activity = launch().get();
        answerNotifications(1, ScreenTestSupport.notification(1, "Payment Pending Validation", "payment", 9999L, true));

        Dialog sheet = openSheet(activity, 0);
        tapAction(sheet);
        FakeCall<ReservationDto> reservation = api.last("getReservation");
        reservation.http(404);
        idle();
        FakeCall<DirectBookingResponseDto> direct = api.last("getDirectBooking");
        direct.http(404);
        idle();

        TextView error = sheet.findViewById(R.id.tvNotifDetailError);
        assertEquals(View.VISIBLE, error.getVisibility());
        assertEquals("This item is no longer available", error.getText().toString());
        assertTrue("the sheet stays open", sheet.isShowing());
        assertNull("nothing was opened", shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void viewTransaction_whenOffline_saysSo_andCanBeTappedAgain() {
        NotificationActivity activity = launch().get();
        answerNotifications(1, ScreenTestSupport.notification(1, "Payment Pending Validation", "payment", 9999L, true));

        Dialog sheet = openSheet(activity, 0);
        tapAction(sheet);
        FakeCall<ReservationDto> reservation = api.last("getReservation");
        reservation.offline();
        idle();
        TextView error = sheet.findViewById(R.id.tvNotifDetailError);
        assertEquals(View.VISIBLE, error.getVisibility());
        assertTrue(error.getText().toString().startsWith("Couldn't open it"));

        tapAction(sheet);
        assertEquals("a retry asks again", 2, api.count("getReservation"));
    }

    // ---- search ----

    @Test
    public void searchIsDebounced() {
        NotificationActivity activity = launch().get();
        answerNotifications(2,
                ScreenTestSupport.notification(2, "Payment Pending Validation", "payment", 588L, false),
                ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, false));
        assertEquals(2, activity.<RecyclerView>findViewById(R.id.rvNotifications).getAdapter().getItemCount());

        ((android.widget.EditText) activity.findViewById(R.id.etSearchNotifications)).setText("confirmed");
        idleFor(100);
        assertEquals("still debouncing", 2, activity.<RecyclerView>findViewById(R.id.rvNotifications).getAdapter().getItemCount());
        idleFor(300);
        assertEquals(1, activity.<RecyclerView>findViewById(R.id.rvNotifications).getAdapter().getItemCount());
    }

    // ---- lifecycle ----

    @Test
    public void anAnswerLandingAfterTheScreenIsGone_isDroppedQuietly() {
        ActivityController<NotificationActivity> controller = launch();
        controller.pause().stop().destroy();
        answerNotifications(1, ScreenTestSupport.notification(1, "Booking Confirmed", "booking", 5L, false));
    }

    // ---- fixture plumbing ----

    /** Puts these transactions into the shared cache the way a booking refresh would, via the fake API. */
    private void repositoryHasBookings(ReservationDto reservation, DirectBookingResponseDto direct) {
        repository.refreshBookings(new RoomRepository.RepositoryCallback<java.util.List<com.example.velocitysuites.Booking>>() {
            @Override public void onSuccess(java.util.List<com.example.velocitysuites.Booking> result) { }
            @Override public void onError(String message) { }
        });
        FakeCall<PaginatedResponse<ReservationDto>> r = api.last("getReservations");
        r.succeed(ScreenTestSupport.page(Collections.singletonList(reservation)));
        FakeCall<PaginatedResponse<DirectBookingResponseDto>> d = api.last("getDirectBookings");
        d.succeed(ScreenTestSupport.page(Collections.singletonList(direct)));
        idle();
    }
}
