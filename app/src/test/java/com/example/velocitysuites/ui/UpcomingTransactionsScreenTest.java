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
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.R;
import com.example.velocitysuites.TransactionHistoryActivity;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.UpcomingTransactionsActivity;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.PaginatedResponse;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Upcoming Hotel Transactions: every transaction of every status is listed (nothing is dropped by the client), the type and
 * status filters narrow only on request, a deep link by id lands on - and highlights - the exact transaction (resetting a
 * filter that would hide it, and telling a booking from a same-numbered reservation), and each card opens its record in
 * Transaction History.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UpcomingTransactionsScreenTest {

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
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

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Manila"));
    }

    private static ReservationDto reservation(long id, String status, int daysAway) {
        ReservationDto r = ScreenTestSupport.reservation(id, 1800);
        r.status = status;
        r.check_in = today().plusDays(daysAway).toString();
        r.check_out = today().plusDays(daysAway + 1).toString();
        return r;
    }

    private static DirectBookingResponseDto booking(long id, String status, int daysAway) {
        DirectBookingResponseDto d = ScreenTestSupport.directBooking(id, 3000);
        d.booking_status = status;
        d.check_in = today().plusDays(daysAway).toString();
        d.check_out = today().plusDays(daysAway + 1).toString();
        return d;
    }

    /** One of each status, across both families. Booking #12 is already checked out, Booking #13 cancelled. */
    private void answerAllStatuses() {
        answer(new ArrayList<>(java.util.Arrays.asList(
                        reservation(1, "AWAITING_GCASH_PAYMENT", 5),
                        reservation(2, "REJECTED_RESERVATION", 6))),
                new ArrayList<>(java.util.Arrays.asList(
                        booking(10, "ACTIVE_BOOKING", 2),
                        booking(11, "CHECKED_IN", -1),
                        booking(12, "COMPLETED_BOOKING", -10),
                        booking(13, "CANCELLED_BOOKING", 8))));
    }

    private void answer(List<ReservationDto> reservations, List<DirectBookingResponseDto> direct) {
        for (int i = 0; i < api.count("getReservations"); i++) {
            ScreenTestSupport.FakeCall<PaginatedResponse<ReservationDto>> call = api.call("getReservations", i);
            call.succeed(ScreenTestSupport.page(reservations));
        }
        for (int i = 0; i < api.count("getDirectBookings"); i++) {
            ScreenTestSupport.FakeCall<PaginatedResponse<DirectBookingResponseDto>> call = api.call("getDirectBookings", i);
            call.succeed(ScreenTestSupport.page(direct));
        }
        idle();
    }

    private UpcomingTransactionsActivity launch(Intent intent) {
        org.robolectric.android.controller.ActivityController<UpcomingTransactionsActivity> controller =
                Robolectric.buildActivity(UpcomingTransactionsActivity.class, intent);
        controller.setup();
        idle();
        return controller.get();
    }

    private UpcomingTransactionsActivity launchWithEverything(Intent intent) {
        UpcomingTransactionsActivity activity = launch(intent);
        answerAllStatuses();
        return activity;
    }

    /** The references of the cards on screen, in order. */
    private static List<String> refs(UpcomingTransactionsActivity activity) {
        ViewGroup list = activity.findViewById(R.id.upcomingTransactionsListContainer);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child.getId() == R.id.cardUpcomingTransaction) {
                out.add(((TextView) child.findViewById(R.id.tvTransactionBookingId)).getText().toString());
            }
        }
        return out;
    }

    private static MaterialCardView cardOf(UpcomingTransactionsActivity activity, String ref) {
        ViewGroup list = activity.findViewById(R.id.upcomingTransactionsListContainer);
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child.getId() == R.id.cardUpcomingTransaction
                    && ref.equals(((TextView) child.findViewById(R.id.tvTransactionBookingId)).getText().toString())) {
                return (MaterialCardView) child;
            }
        }
        return null;
    }

    @Test
    public void everyStatusIsListed_includingCheckedOutAndCancelled() {
        UpcomingTransactionsActivity activity = launchWithEverything(new Intent(app, UpcomingTransactionsActivity.class));
        List<String> refs = refs(activity);
        assertEquals(refs.toString(), 6, refs.size());
        assertTrue(refs.toString(), refs.contains("Booking #12")); // checked out
        assertTrue(refs.toString(), refs.contains("Booking #13")); // cancelled
        assertTrue(refs.toString(), refs.contains("Reservation #2")); // rejected
        assertEquals("Showing 6 of 6", ((TextView) activity.findViewById(R.id.tvUpcomingResultCount)).getText().toString());
    }

    @Test
    public void theStatusFilterNarrowsToOneStatus_andAllStatusesBringsEverythingBack() {
        UpcomingTransactionsActivity activity = launchWithEverything(new Intent(app, UpcomingTransactionsActivity.class));

        activity.findViewById(R.id.chipStatusCheckedOut).performClick();
        idle();
        assertEquals(java.util.Collections.singletonList("Booking #12"), refs(activity));

        activity.findViewById(R.id.chipStatusAll).performClick();
        idle();
        assertEquals(6, refs(activity).size());
    }

    @Test
    public void theTypeFilterSplitsBookingsFromReservations() {
        UpcomingTransactionsActivity activity = launchWithEverything(new Intent(app, UpcomingTransactionsActivity.class));

        activity.findViewById(R.id.chipFilterReservations).performClick();
        idle();
        assertEquals(java.util.Arrays.asList("Reservation #1", "Reservation #2"), refs(activity));

        activity.findViewById(R.id.chipFilterBookings).performClick();
        idle();
        assertEquals(4, refs(activity).size());
    }

    @Test
    public void openingFromTheCalendarButton_startsOnAllStatuses() {
        UpcomingTransactionsActivity activity = launchWithEverything(UpcomingTransactionsActivity.intentAllStatuses(app));
        assertTrue(((com.google.android.material.chip.Chip) activity.findViewById(R.id.chipStatusAll)).isChecked());
        assertEquals(6, refs(activity).size());
    }

    @Test
    public void aDeepLink_highlightsTheTransaction_evenWhenTheOpeningFilterWouldHideIt() {
        // The link asks for Booking #12 (checked out) while the opening status filter is Confirmed - which hides it.
        Intent intent = new Intent(app, UpcomingTransactionsActivity.class)
                .putExtra(UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID, "12")
                .putExtra(UpcomingTransactionsActivity.EXTRA_SELECTED_DIRECT, true)
                .putExtra(UpcomingTransactionsActivity.EXTRA_STATUS_FILTER, "CONFIRMED");
        UpcomingTransactionsActivity activity = launchWithEverything(intent);

        assertTrue("the filter was reset so it can be seen",
                ((com.google.android.material.chip.Chip) activity.findViewById(R.id.chipStatusAll)).isChecked());
        MaterialCardView card = cardOf(activity, "Booking #12");
        assertNotNull(card);
        assertTrue("only the focused one is highlighted",
                card.getStrokeWidth() > cardOf(activity, "Booking #10").getStrokeWidth());
    }

    @Test
    public void aDeepLink_tellsABookingFromAReservationWithTheSameNumber() {
        UpcomingTransactionsActivity activity = launch(new Intent(app, UpcomingTransactionsActivity.class)
                .putExtra(UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID, "5")
                .putExtra(UpcomingTransactionsActivity.EXTRA_SELECTED_DIRECT, false));
        answer(new ArrayList<>(java.util.Collections.singletonList(reservation(5, "AWAITING_GCASH_PAYMENT", 3))),
                new ArrayList<>(java.util.Collections.singletonList(booking(5, "ACTIVE_BOOKING", 3))));

        assertTrue("the reservation is highlighted, the same-numbered booking is not",
                cardOf(activity, "Reservation #5").getStrokeWidth() > cardOf(activity, "Booking #5").getStrokeWidth());
    }

    @Test
    public void eachCardOpensItsRecordInTransactionHistory() {
        UpcomingTransactionsActivity activity = launchWithEverything(new Intent(app, UpcomingTransactionsActivity.class));
        cardOf(activity, "Booking #12").findViewById(R.id.btnViewFullDetails).performClick();

        Intent opened = shadowOf(activity).getNextStartedActivity();
        assertNotNull(opened);
        assertEquals(new ComponentName(activity, TransactionHistoryActivity.class), opened.getComponent());
        assertEquals("12", opened.getStringExtra(TransactionHistoryActivity.EXTRA_SELECTED_BOOKING_ID));
        assertTrue(opened.getBooleanExtra(TransactionHistoryActivity.EXTRA_SELECTED_DIRECT, false));
    }

    @Test
    public void aFilterThatMatchesNothing_showsAClearFiltersWayOut() {
        UpcomingTransactionsActivity activity = launch(new Intent(app, UpcomingTransactionsActivity.class));
        answer(new ArrayList<>(java.util.Collections.singletonList(reservation(1, "AWAITING_GCASH_PAYMENT", 3))),
                new ArrayList<>());
        activity.findViewById(R.id.chipStatusCheckedOut).performClick();
        idle();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.layoutUpcomingEmptyState).getVisibility());

        activity.findViewById(R.id.btnUpcomingClearFilters).performClick();
        idle();
        assertEquals(View.GONE, activity.findViewById(R.id.layoutUpcomingEmptyState).getVisibility());
        assertEquals(1, refs(activity).size());
        ChipGroup status = activity.findViewById(R.id.chipGroupUpcomingStatus);
        assertEquals(R.id.chipStatusAll, status.getCheckedChipId());
    }
}
