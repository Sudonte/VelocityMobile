package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

import com.example.velocitysuites.Notification;
import com.example.velocitysuites.R;
import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.TransactionDetailsActivity;
import com.example.velocitysuites.TransactionHistoryActivity;
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
import org.robolectric.shadows.ShadowToast;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * The real Transaction History screen, launched against a fake API: every state it can be in (loading, cards,
 * empty, error with retry), the status flipping from PENDING to PAID when a refresh sees the receptionist's
 * verification, a notification arrival triggering that refresh, search debounce, and the deep-link that must
 * open the EXACT record. These are runtime behaviours lint and unit tests can't see - the kind of thing that
 * used to only show up on a phone.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TransactionHistoryScreenTest {

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

    private ActivityController<TransactionHistoryActivity> launch(Intent intent) {
        ActivityController<TransactionHistoryActivity> controller = Robolectric.buildActivity(TransactionHistoryActivity.class, intent);
        controller.setup();
        idle();
        return controller;
    }

    private ActivityController<TransactionHistoryActivity> launch() {
        return launch(new Intent(app, TransactionHistoryActivity.class));
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void idleFor(long millis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    /** Lays the list out at the screen size so its rows are actually bound and readable. */
    private static RecyclerView list(TransactionHistoryActivity activity) {
        RecyclerView rv = activity.findViewById(R.id.rvTransactions);
        rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, 1080, 2000);
        return rv;
    }

    private static String textAt(RecyclerView rv, int position, int viewId) {
        RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(position);
        assertNotNull("row " + position + " must be bound", holder);
        return ((TextView) holder.itemView.findViewById(viewId)).getText().toString();
    }

    private void answerInitialLoad(List<ReservationDto> reservations, List<DirectBookingResponseDto> direct) {
        ScreenTestSupport.answerBookingLoad(api, 0, reservations, direct);
        idle();
    }

    // ---- states ----

    @Test
    public void showsTheLoader_thenTheCards() {
        ActivityController<TransactionHistoryActivity> controller = launch();
        TransactionHistoryActivity activity = controller.get();

        assertEquals("loader while nothing has loaded", View.VISIBLE, activity.findViewById(R.id.layoutInitialLoading).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.rvTransactions).getVisibility());
        assertEquals("both transaction families are requested", 1, api.count("getReservations"));
        assertEquals(1, api.count("getDirectBookings"));

        answerInitialLoad(ScreenTestSupport.reservations(
                        ScreenTestSupport.reservation(588, 1800, ScreenTestSupport.payment(1, "1800.00", "pending", "gcash"))),
                ScreenTestSupport.directBookings());

        assertEquals(View.GONE, activity.findViewById(R.id.layoutInitialLoading).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.rvTransactions).getVisibility());
        RecyclerView rv = list(activity);
        assertEquals(1, rv.getAdapter().getItemCount());
        // The bug report: a P1,800 payment was submitted, the card said "P0.00 / Not yet paid".
        assertEquals("Reservation #588", textAt(rv, 0, R.id.tvTxnRef));
        assertEquals("₱1,800.00", textAt(rv, 0, R.id.tvTxnTotal));
        assertEquals("Pending", textAt(rv, 0, R.id.tvTxnStatusBadge));
        assertEquals("₱1,800.00 submitted • awaiting verification", textAt(rv, 0, R.id.tvTxnPayment));
        assertEquals("Deluxe", textAt(rv, 0, R.id.tvTxnRoom));
        assertEquals("Oct 01, 2026 – Oct 02, 2026", textAt(rv, 0, R.id.tvTxnStay));
    }

    @Test
    public void emptyAccount_saysNoTransactionsYet() {
        ActivityController<TransactionHistoryActivity> controller = launch();
        TransactionHistoryActivity activity = controller.get();
        answerInitialLoad(Collections.emptyList(), Collections.emptyList());

        assertEquals(View.VISIBLE, activity.findViewById(R.id.scrollEmptyState).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.rvTransactions).getVisibility());
        assertEquals("No transactions yet", ((TextView) activity.findViewById(R.id.emptyTitle)).getText().toString());
    }

    @Test
    public void offline_showsTheErrorWithARetryThatReallyRetries() {
        ActivityController<TransactionHistoryActivity> controller = launch();
        TransactionHistoryActivity activity = controller.get();

        // Both families fail; the repository retries the pair once before giving up.
        FakeCall<PaginatedResponse<ReservationDto>> r0 = api.call("getReservations", 0);
        FakeCall<PaginatedResponse<DirectBookingResponseDto>> d0 = api.call("getDirectBookings", 0);
        r0.offline();
        d0.offline();
        idle();
        assertEquals("one automatic retry", 2, api.count("getReservations"));
        FakeCall<PaginatedResponse<ReservationDto>> r1 = api.call("getReservations", 1);
        FakeCall<PaginatedResponse<DirectBookingResponseDto>> d1 = api.call("getDirectBookings", 1);
        r1.offline();
        d1.offline();
        idle();

        assertEquals(View.VISIBLE, activity.findViewById(R.id.scrollEmptyState).getVisibility());
        assertEquals("Unable to Load Transactions", ((TextView) activity.findViewById(R.id.emptyTitle)).getText().toString());
        View retry = activity.findViewById(R.id.btnEmptyAction);
        assertEquals(View.VISIBLE, retry.getVisibility());
        assertEquals("Retry", ((TextView) retry).getText().toString());

        retry.performClick();
        idle();
        assertEquals("Retry asks again", 3, api.count("getReservations"));
        assertEquals("...and shows the loader while it does", View.VISIBLE, activity.findViewById(R.id.layoutInitialLoading).getVisibility());
    }

    // ---- fresh statuses ----

    @Test
    public void theStatusTurnsFromPendingToPaid_whenARefreshSeesTheVerification() {
        ActivityController<TransactionHistoryActivity> controller = launch();
        TransactionHistoryActivity activity = controller.get();
        answerInitialLoad(ScreenTestSupport.reservations(
                        ScreenTestSupport.reservation(588, 1800, ScreenTestSupport.payment(1, "1800.00", "pending", "gcash"))),
                ScreenTestSupport.directBookings());
        assertEquals("Pending", textAt(list(activity), 0, R.id.tvTxnStatusBadge));

        // The receptionist verifies it on the web system; the guest returns to the screen.
        controller.pause();
        controller.resume();
        idle();
        assertEquals("a status check went out", 2, api.count("getReservations"));
        ScreenTestSupport.answerBookingLoad(api, 1, ScreenTestSupport.reservations(
                        ScreenTestSupport.reservation(588, 1800, ScreenTestSupport.payment(1, "1800.00", "completed", "gcash"))),
                ScreenTestSupport.directBookings());
        idle();

        RecyclerView rv = list(activity);
        assertEquals("Paid", textAt(rv, 0, R.id.tvTxnStatusBadge));
        assertEquals("₱1,800.00 paid • ₱0.00 balance", textAt(rv, 0, R.id.tvTxnPayment));
    }

    @Test
    public void aPartialVerification_showsPartiallyPaidWithTheRightBalance() {
        ActivityController<TransactionHistoryActivity> controller = launch();
        TransactionHistoryActivity activity = controller.get();
        answerInitialLoad(ScreenTestSupport.reservations(
                        ScreenTestSupport.reservation(588, 1800, ScreenTestSupport.payment(1, "900.00", "completed", "gcash"))),
                ScreenTestSupport.directBookings());

        RecyclerView rv = list(activity);
        assertEquals("Partially Paid", textAt(rv, 0, R.id.tvTxnStatusBadge));
        assertEquals("₱900.00 paid • ₱900.00 balance", textAt(rv, 0, R.id.tvTxnPayment));
    }

    @Test
    public void aRelatedNotificationArriving_refreshesTheStatusesImmediately() {
        ActivityController<TransactionHistoryActivity> controller = launch();
        TransactionHistoryActivity activity = controller.get();
        answerInitialLoad(ScreenTestSupport.reservations(
                        ScreenTestSupport.reservation(588, 1800, ScreenTestSupport.payment(1, "1800.00", "pending", "gcash"))),
                ScreenTestSupport.directBookings());

        // Notifications load once (nothing is "new" on a first load)...
        repository.refreshNotifications(new Ignore());
        FakeCall<PaginatedResponse<NotificationDto>> first = api.last("getNotifications");
        first.succeed(ScreenTestSupport.notificationPage(1,
                ScreenTestSupport.notification(10, "Payment Pending Validation", "payment", 588L, false)));
        idle();
        assertEquals("no refresh for a first load", 1, api.count("getReservations"));

        // ...then the receptionist verifies the payment and a newer notification appears on the next poll.
        repository.pollNotifications(new Ignore());
        FakeCall<PaginatedResponse<NotificationDto>> second = api.last("getNotifications");
        second.succeed(ScreenTestSupport.notificationPage(2,
                ScreenTestSupport.notification(11, "Payment Verified", "payment", 588L, false),
                ScreenTestSupport.notification(10, "Payment Pending Validation", "payment", 588L, false)));
        idle();

        assertEquals("the arrival triggered a status refresh without waiting for the 30s timer", 2, api.count("getReservations"));
    }

    @Test
    public void anUnrelatedNotification_doesNotTriggerARefresh() {
        ActivityController<TransactionHistoryActivity> controller = launch();
        TransactionHistoryActivity activity = controller.get();
        answerInitialLoad(Collections.emptyList(), Collections.emptyList());

        repository.refreshNotifications(new Ignore());
        FakeCall<PaginatedResponse<NotificationDto>> first = api.last("getNotifications");
        first.succeed(ScreenTestSupport.notificationPage(0, ScreenTestSupport.notification(10, "Welcome", "system", null, true)));
        repository.pollNotifications(new Ignore());
        FakeCall<PaginatedResponse<NotificationDto>> second = api.last("getNotifications");
        second.succeed(ScreenTestSupport.notificationPage(1,
                ScreenTestSupport.notification(11, "New Promo", "promotion", null, false),
                ScreenTestSupport.notification(10, "Welcome", "system", null, true)));
        idle();

        assertEquals(1, api.count("getReservations"));
    }

    @Test
    public void onlyOneStatusCheckRunsAtATime_aSecondOneQueuesInsteadOfDuplicating() {
        ActivityController<TransactionHistoryActivity> controller = launch();
        TransactionHistoryActivity activity = controller.get();
        answerInitialLoad(Collections.emptyList(), Collections.emptyList());

        controller.pause();
        controller.resume();   // poll #1 in flight
        idle();
        controller.pause();
        controller.resume();   // would be poll #2 - must wait
        idle();
        assertEquals("still only the first poll went out", 2, api.count("getReservations"));

        ScreenTestSupport.answerBookingLoad(api, 1, Collections.emptyList(), Collections.emptyList());
        idle();
        assertEquals("the queued one runs once the first lands", 3, api.count("getReservations"));
    }

    // ---- search / filters ----

    @Test
    public void searchIsDebounced_thenFilters() {
        ActivityController<TransactionHistoryActivity> controller = launch();
        TransactionHistoryActivity activity = controller.get();
        answerInitialLoad(ScreenTestSupport.reservations(
                        ScreenTestSupport.reservation(588, 1800, ScreenTestSupport.payment(1, "1800.00", "pending", "gcash")),
                        ScreenTestSupport.reservation(577, 900, ScreenTestSupport.payment(2, "900.00", "completed", "gcash"))),
                ScreenTestSupport.directBookings());
        assertEquals(2, activity.<RecyclerView>findViewById(R.id.rvTransactions).getAdapter().getItemCount());

        android.widget.EditText search = activity.findViewById(R.id.etSearch);
        search.setText("588");
        idleFor(100);
        assertEquals("not filtered yet - still debouncing", 2, activity.<RecyclerView>findViewById(R.id.rvTransactions).getAdapter().getItemCount());
        idleFor(300);
        assertEquals("filtered after the pause in typing", 1, activity.<RecyclerView>findViewById(R.id.rvTransactions).getAdapter().getItemCount());
    }

    @Test
    public void aSearchWithNoMatch_showsTheFilteredEmptyState_withClearFilters() {
        ActivityController<TransactionHistoryActivity> controller = launch();
        TransactionHistoryActivity activity = controller.get();
        answerInitialLoad(ScreenTestSupport.reservations(
                        ScreenTestSupport.reservation(588, 1800, ScreenTestSupport.payment(1, "1800.00", "pending", "gcash"))),
                ScreenTestSupport.directBookings());

        ((android.widget.EditText) activity.findViewById(R.id.etSearch)).setText("zzz");
        idleFor(400);

        assertEquals(View.VISIBLE, activity.findViewById(R.id.scrollEmptyState).getVisibility());
        assertEquals("No matching transactions", ((TextView) activity.findViewById(R.id.emptyTitle)).getText().toString());
        View clear = activity.findViewById(R.id.btnEmptyAction);
        assertEquals("Clear All Filters", ((TextView) clear).getText().toString());
        clear.performClick();
        idle();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.rvTransactions).getVisibility());
        assertEquals(1, activity.<RecyclerView>findViewById(R.id.rvTransactions).getAdapter().getItemCount());
    }

    // ---- the filter chip row ----

    @Test
    public void theFilterChipRow_scrollsHorizontally_withEndPaddingSoTheLastChipIsNeverCutOff() {
        ActivityController<TransactionHistoryActivity> controller = launch();
        TransactionHistoryActivity activity = controller.get();
        android.widget.HorizontalScrollView scroll = activity.findViewById(R.id.scrollFilterChips);
        assertNotNull(scroll);
        assertFalse("content may draw into the end padding", scroll.getClipToPadding());
        View row = scroll.getChildAt(0);
        float density = app.getResources().getDisplayMetrics().density;
        assertTrue("end padding clears the last chip from the card edge (" + row.getPaddingEnd() / density + "dp)",
                row.getPaddingEnd() >= 16 * density);
        // all three chips live in the scrolling row
        assertNotNull(row.findViewById(R.id.chipRoomType));
        assertNotNull(row.findViewById(R.id.chipDateFilter));
        assertNotNull(row.findViewById(R.id.chipBookingStatus));
    }

    // ---- pull to refresh ----

    @Test
    public void pullToRefresh_reloadsTheList_once() throws Exception {
        ActivityController<TransactionHistoryActivity> controller = launch();
        TransactionHistoryActivity activity = controller.get();
        answerInitialLoad(Collections.emptyList(), Collections.emptyList());
        assertEquals(1, api.count("getReservations"));

        androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipe = activity.findViewById(R.id.swipeRefresh);
        java.lang.reflect.Field field = androidx.swiperefreshlayout.widget.SwipeRefreshLayout.class.getDeclaredField("mListener");
        field.setAccessible(true);
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener listener =
                (androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener) field.get(swipe);
        listener.onRefresh();
        listener.onRefresh(); // a second pull while the first is still loading must not duplicate the request
        idle();

        assertEquals("one reload, not two", 2, api.count("getReservations"));
        ScreenTestSupport.answerBookingLoad(api, 1, ScreenTestSupport.reservations(ScreenTestSupport.reservation(588, 1800)), ScreenTestSupport.directBookings());
        idle();
        assertFalse("the spinner stops when the reload lands", swipe.isRefreshing());
        assertEquals(1, activity.<RecyclerView>findViewById(R.id.rvTransactions).getAdapter().getItemCount());
    }

    // ---- export ----

    @Test
    public void exportWithNothingToExport_saysSo_insteadOfOpeningAPicker() {
        ActivityController<TransactionHistoryActivity> controller = launch();
        TransactionHistoryActivity activity = controller.get();
        answerInitialLoad(Collections.emptyList(), Collections.emptyList());

        activity.findViewById(R.id.btnExport).performClick();
        idle();

        assertEquals("There are no transactions to export.", ShadowToast.getTextOfLatestToast());
    }

    // ---- opening a record ----

    @Test
    public void tappingACard_opensThatTransactionsDetails_once_evenOnADoubleTap() {
        ActivityController<TransactionHistoryActivity> controller = launch();
        TransactionHistoryActivity activity = controller.get();
        answerInitialLoad(ScreenTestSupport.reservations(
                        ScreenTestSupport.reservation(588, 1800, ScreenTestSupport.payment(1, "1800.00", "pending", "gcash"))),
                ScreenTestSupport.directBookings());
        RecyclerView rv = list(activity);
        View card = rv.findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.cardTransaction);

        card.performClick();
        card.performClick();

        Intent started = shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(TransactionDetailsActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("the second tap of a double tap is swallowed", null, shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void aDeepLinkThatKnowsItsFamily_opensTheExactRecord_whenAReservationAndABookingShareAnId() {
        Intent intent = new Intent(app, TransactionHistoryActivity.class);
        intent.putExtra(TransactionHistoryActivity.EXTRA_SELECTED_BOOKING_ID, "5");
        intent.putExtra(TransactionHistoryActivity.EXTRA_SELECTED_DIRECT, true);
        ActivityController<TransactionHistoryActivity> controller = launch(intent);
        TransactionHistoryActivity activity = controller.get();
        answerInitialLoad(ScreenTestSupport.reservations(ScreenTestSupport.reservation(5, 1800)),
                ScreenTestSupport.directBookings(ScreenTestSupport.directBooking(5, 3000)));

        Intent started = shadowOf(activity).getNextStartedActivity();
        assertNotNull("the deep link auto-opens the record", started);
        com.example.velocitysuites.PaymentTransaction opened = (com.example.velocitysuites.PaymentTransaction)
                started.getSerializableExtra(TransactionDetailsActivity.EXTRA_TRANSACTION);
        assertNotNull(opened);
        assertTrue("the DIRECT booking (Suite, P3,000), not the reservation that shares id 5", opened.parentBooking.isDirectBooking());
        assertEquals(3000, opened.parentBooking.getTotalAmount(), 0.01);
    }

    @Test
    public void aDeepLinkToARecordThatNoLongerExists_showsAFriendlyMessage_notACrash() {
        Intent intent = new Intent(app, TransactionHistoryActivity.class);
        intent.putExtra(TransactionHistoryActivity.EXTRA_SELECTED_BOOKING_ID, "9999");
        intent.putExtra(TransactionHistoryActivity.EXTRA_SELECTED_TYPE_HINT, Notification.TYPE_PAYMENT);
        ActivityController<TransactionHistoryActivity> controller = launch(intent);
        answerInitialLoad(Collections.emptyList(), Collections.emptyList());

        // Not in the loaded window -> asked for by id, in the category's order; both say 404.
        assertEquals(1, api.count("getReservation"));
        FakeCall<ReservationDto> r = api.last("getReservation");
        r.http(404);
        idle();
        assertEquals(1, api.count("getDirectBooking"));
        FakeCall<DirectBookingResponseDto> d = api.last("getDirectBooking");
        d.http(404);
        idle();

        android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull("a friendly dialog, not a crash", dialog);
        assertTrue(dialog.isShowing());
    }

    @Test
    public void leavingTheScreen_thenAnAnswerLanding_doesNothingAndDoesNotCrash() {
        ActivityController<TransactionHistoryActivity> controller = launch();
        controller.pause().stop().destroy();

        // Answers arriving after the screen is gone must be dropped, not applied to dead views.
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(ScreenTestSupport.reservation(588, 1800)), ScreenTestSupport.directBookings());
        idle();
    }

    /** A do-nothing repository callback. */
    private static final class Ignore implements RoomRepository.RepositoryCallback<List<Notification>> {
        @Override public void onSuccess(List<Notification> result) { }
        @Override public void onError(String message) { }
    }

}
