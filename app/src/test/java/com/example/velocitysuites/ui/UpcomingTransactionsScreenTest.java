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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

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

    /** Picks an option in one of the three combo boxes, the way a tap on its list does. */
    private static void pick(UpcomingTransactionsActivity activity, int dropdownId, String label) {
        android.widget.AutoCompleteTextView dropdown = activity.findViewById(dropdownId);
        android.widget.ListAdapter adapter = dropdown.getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (label.equals(adapter.getItem(i).toString())) {
                dropdown.getOnItemClickListener().onItemClick(null, null, i, i);
                idle();
                return;
            }
        }
        throw new AssertionError("no option '" + label + "' in " + dropdownId);
    }

    private static String shown(UpcomingTransactionsActivity activity, int dropdownId) {
        return ((android.widget.AutoCompleteTextView) activity.findViewById(dropdownId)).getText().toString();
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

        pick(activity, R.id.dropdownUpcomingStatus, "Checked Out");
        assertEquals(java.util.Collections.singletonList("Booking #12"), refs(activity));

        pick(activity, R.id.dropdownUpcomingStatus, "All");
        assertEquals(6, refs(activity).size());
    }

    @Test
    public void filtersAreTwoComboBoxes_typeAndStatus_eachDefaultingToAll_withNoDateFilter() {
        UpcomingTransactionsActivity activity = launchWithEverything(new Intent(app, UpcomingTransactionsActivity.class));
        for (int id : new int[]{R.id.dropdownUpcomingType, R.id.dropdownUpcomingStatus}) {
            android.widget.AutoCompleteTextView dropdown = activity.findViewById(id);
            assertEquals("All", dropdown.getText().toString());
            assertEquals("All", dropdown.getAdapter().getItem(0).toString());
        }
        assertEquals("the Date filter is gone", 0,
                activity.getResources().getIdentifier("dropdownUpcomingDate", "id", activity.getPackageName()));
    }

    @Test
    public void theScreenIsOrdered_filters_clock_calendar_list() {
        UpcomingTransactionsActivity activity = launchWithEverything(new Intent(app, UpcomingTransactionsActivity.class));
        ViewGroup page = (ViewGroup) ((ViewGroup) activity.findViewById(R.id.screenContent)).getChildAt(0);
        int filters = indexIn(page, activity.findViewById(R.id.dropdownUpcomingType));
        int clock = indexIn(page, activity.findViewById(R.id.tvUpcomingClock));
        int calendar = indexIn(page, activity.findViewById(R.id.stayCalendar));
        int list = indexIn(page, activity.findViewById(R.id.upcomingTransactionsListContainer));
        assertTrue(filters < clock && clock < calendar && calendar < list);
    }

    private static int indexIn(ViewGroup parent, View descendant) {
        View v = descendant;
        while (v.getParent() != parent) v = (View) v.getParent();
        return parent.indexOfChild(v);
    }

    @Test
    public void theClockShowsDateAndTime_andKeepsTicking() throws Exception {
        UpcomingTransactionsActivity activity = launchWithEverything(new Intent(app, UpcomingTransactionsActivity.class));
        TextView clock = activity.findViewById(R.id.tvUpcomingClock);
        String first = clock.getText().toString();
        assertTrue(first, first.matches("\\w+, \\w+ \\d{1,2}, \\d{4} • \\d{1,2}:\\d{2}:\\d{2} (AM|PM)"));

        Thread.sleep(1100); // the clock reads the real wall time; the looper only supplies the next tick
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
        assertTrue("the clock advanced", !first.equals(clock.getText().toString()));
    }

    @Test
    public void tappingACalendarDay_doesNotFilterTheList() {
        UpcomingTransactionsActivity activity = launchWithEverything(new Intent(app, UpcomingTransactionsActivity.class));
        assertEquals(6, refs(activity).size());

        tapCalendarDay(activity, today().plusDays(2));
        assertEquals("the calendar is a view, not a filter", 6, refs(activity).size());
    }

    @Test
    public void pagingTheCalendar_neverChangesTheList() {
        UpcomingTransactionsActivity activity = launchWithEverything(new Intent(app, UpcomingTransactionsActivity.class));

        activity.findViewById(R.id.stayCalNext).performClick();
        idle();
        assertEquals(6, refs(activity).size());
        activity.findViewById(R.id.stayCalPrev).performClick();
        idle();
        assertEquals(6, refs(activity).size());
    }

    @Test
    public void statusAll_shadesActiveStaysRed_andClosedStaysMuted_redWinningASharedDay() {
        UpcomingTransactionsActivity activity = launchWithEverything(new Intent(app, UpcomingTransactionsActivity.class));
        android.graphics.drawable.Drawable.ConstantState red = style(activity, R.drawable.bg_calendar_stay_day);
        android.graphics.drawable.Drawable.ConstantState muted = style(activity, R.drawable.bg_calendar_stay_day_muted);

        // Booking #10 (active) checks in at +2 and out at +3.
        assertEquals(red, dayCell(activity, today().plusDays(2)).getBackground().getConstantState());
        assertEquals(red, dayCell(activity, today().plusDays(3)).getBackground().getConstantState());
        assertEquals("no stay", null, dayCell(activity, today().plusDays(4)).getBackground());
        // Reservation #1 (pending, +5..+6) shares +6 with Reservation #2 (rejected, +6..+7): the active red wins.
        assertEquals(red, dayCell(activity, today().plusDays(6)).getBackground().getConstantState());
        assertEquals("rejected alone", muted, dayCell(activity, today().plusDays(7)).getBackground().getConstantState());
        assertEquals("cancelled alone", muted, dayCell(activity, today().plusDays(8)).getBackground().getConstantState());
    }

    private static android.graphics.drawable.Drawable.ConstantState style(UpcomingTransactionsActivity activity, int drawable) {
        return androidx.core.content.ContextCompat.getDrawable(activity, drawable).getConstantState();
    }

    @Test
    public void pickingCancelled_shadesOnlyCancelledStays_mutedAndDashed() {
        UpcomingTransactionsActivity activity = launchWithEverything(new Intent(app, UpcomingTransactionsActivity.class));
        pick(activity, R.id.dropdownUpcomingStatus, "Cancelled");

        android.graphics.drawable.Drawable muted = androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.bg_calendar_stay_day_muted);
        android.graphics.drawable.Drawable active = androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.bg_calendar_stay_day);
        View cancelledDay = dayCell(activity, today().plusDays(8));
        assertNotNull(cancelledDay.getBackground());
        assertEquals("muted, not the red active style", muted.getConstantState(), cancelledDay.getBackground().getConstantState());
        assertTrue(!active.getConstantState().equals(cancelledDay.getBackground().getConstantState()));
        assertEquals("the active stay is no longer shaded", null, dayCell(activity, today().plusDays(2)).getBackground());
        assertEquals("rejected is a different status", null, dayCell(activity, today().plusDays(7)).getBackground());

        pick(activity, R.id.dropdownUpcomingStatus, "Rejected");
        assertNotNull(dayCell(activity, today().plusDays(7)).getBackground());
        assertEquals(null, dayCell(activity, today().plusDays(8)).getBackground());

        pick(activity, R.id.dropdownUpcomingStatus, "All");
        assertNotNull("back to the active style", dayCell(activity, today().plusDays(2)).getBackground());
        assertEquals("and the cancelled stay is shaded again, muted", muted.getConstantState(),
                dayCell(activity, today().plusDays(8)).getBackground().getConstantState());
    }

    @Test
    public void dashboardLink_appliesTheTypeAndStatusFilters_andFocusesThatTransaction() {
        Intent intent = new Intent(app, UpcomingTransactionsActivity.class)
                .putExtra(UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID, "10")
                .putExtra(UpcomingTransactionsActivity.EXTRA_SELECTED_DIRECT, true)
                .putExtra(UpcomingTransactionsActivity.EXTRA_TYPE_FILTER, "BOOKINGS")
                .putExtra(UpcomingTransactionsActivity.EXTRA_STATUS_FILTER, "CONFIRMED");
        UpcomingTransactionsActivity activity = launchWithEverything(intent);

        assertEquals("Bookings", shown(activity, R.id.dropdownUpcomingType));
        assertEquals("Confirmed", shown(activity, R.id.dropdownUpcomingStatus));
        assertEquals(java.util.Collections.singletonList("Booking #10"), refs(activity));
        assertEquals("the focused card is outlined", (int) (3 * activity.getResources().getDisplayMetrics().density),
                cardOf(activity, "Booking #10").getStrokeWidth());
    }

    /** The details sheet on screen, or null. */
    private static android.app.Dialog sheet() {
        android.app.Dialog d = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        return d instanceof com.google.android.material.bottomsheet.BottomSheetDialog && d.isShowing() ? d : null;
    }

    private static String sheetText(android.app.Dialog sheet) {
        StringBuilder sb = new StringBuilder();
        collectText(sheet.findViewById(R.id.sheetRows).getRootView(), sb);
        return sb.toString();
    }

    private static void collectText(View v, StringBuilder sb) {
        if (v instanceof TextView) sb.append(((TextView) v).getText()).append('|');
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collectText(((ViewGroup) v).getChildAt(i), sb);
        }
    }

    @Test
    public void tappingACard_opensTheDetailsSheet_withTheKeyFacts() {
        UpcomingTransactionsActivity activity = launchWithEverything(new Intent(app, UpcomingTransactionsActivity.class));
        assertEquals(null, sheet());

        cardOf(activity, "Booking #10").performClick();
        idle();
        android.app.Dialog sheet = sheet();
        assertNotNull("the details sheet opened", sheet);
        String text = sheetText(sheet);
        for (String expected : new String[]{"Booking #10", "Suite", "Check-in", "Check-out", "Total Guests", "Stay Details", "Room Details",
                "Payment Details", "Total Amount", "Amount Paid", "Balance"}) {
            assertTrue(expected + " in: " + text, text.contains(expected));
        }
        assertTrue(text, !text.contains("null"));
    }

    @Test
    public void dashboardLinkWithOpenDetails_opensTheSheetOnThatTransaction() {
        Intent intent = new Intent(app, UpcomingTransactionsActivity.class)
                .putExtra(UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID, "10")
                .putExtra(UpcomingTransactionsActivity.EXTRA_SELECTED_DIRECT, true)
                .putExtra(UpcomingTransactionsActivity.EXTRA_TYPE_FILTER, "BOOKINGS")
                .putExtra(UpcomingTransactionsActivity.EXTRA_STATUS_FILTER, "CONFIRMED")
                .putExtra(UpcomingTransactionsActivity.EXTRA_OPEN_DETAILS, true);
        launchWithEverything(intent);

        android.app.Dialog sheet = sheet();
        assertNotNull(sheet);
        assertTrue(sheetText(sheet).contains("Booking #10"));
    }

    @Test
    public void statusAllListsEveryStay_includingCancelledAndRejected() {
        UpcomingTransactionsActivity activity = launchWithEverything(new Intent(app, UpcomingTransactionsActivity.class));
        java.util.List<String> all = refs(activity);
        assertTrue(all.contains("Booking #13")); // cancelled
        assertTrue(all.contains("Reservation #2")); // rejected

        pick(activity, R.id.dropdownUpcomingStatus, "Cancelled");
        assertEquals(java.util.Collections.singletonList("Booking #13"), refs(activity));
    }

    @Test
    public void cancelledAndRejectedCards_areMuted_activeOnesAreNot() {
        UpcomingTransactionsActivity activity = launchWithEverything(new Intent(app, UpcomingTransactionsActivity.class));
        for (String closed : new String[]{"Booking #13", "Reservation #2"}) {
            View card = cardOf(activity, closed);
            assertEquals(closed, UpcomingTransactionsActivity.MUTED_ALPHA, card.findViewById(R.id.tvTransactionTitle).getAlpha(), 0.001f);
            assertEquals("the status badge stays clear", 1f, card.findViewById(R.id.tvTransactionStatus).getAlpha(), 0.001f);
            assertEquals(1f, card.findViewById(R.id.tvTransactionBookingId).getAlpha(), 0.001f);
        }
        assertEquals(1f, cardOf(activity, "Booking #10").findViewById(R.id.tvTransactionTitle).getAlpha(), 0.001f);
    }

    private static View dayCell(UpcomingTransactionsActivity activity, LocalDate day) {
        String key = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH).format(day);
        int[] pages = {R.id.stayCalNext, R.id.stayCalPrev, R.id.stayCalPrev, R.id.stayCalPrev}; // this month, next, then back past the start
        for (int attempt = 0; attempt < pages.length; attempt++) {
            ViewGroup grid = activity.findViewById(R.id.stayCalGrid);
            for (int i = 0; i < grid.getChildCount(); i++) {
                CharSequence cd = grid.getChildAt(i).getContentDescription();
                if (cd != null && cd.toString().contains(key)) return grid.getChildAt(i);
            }
            activity.findViewById(pages[attempt]).performClick();
        }
        throw new AssertionError("day " + key + " not on the calendar");
    }

    private static void tapCalendarDay(UpcomingTransactionsActivity activity, LocalDate day) {
        String key = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH).format(day);
        if (!tapDayCell(activity, key)) {
            activity.findViewById(R.id.stayCalNext).performClick();
            assertTrue("day " + key + " is on the calendar", tapDayCell(activity, key));
        }
        idle();
    }

    private static boolean tapDayCell(UpcomingTransactionsActivity activity, String key) {
        ViewGroup grid = activity.findViewById(R.id.stayCalGrid);
        for (int i = 0; i < grid.getChildCount(); i++) {
            CharSequence cd = grid.getChildAt(i).getContentDescription();
            if (cd != null && cd.toString().contains(key)) {
                grid.getChildAt(i).performClick();
                return true;
            }
        }
        return false;
    }

    @Test
    public void theTypeFilterSplitsBookingsFromReservations() {
        UpcomingTransactionsActivity activity = launchWithEverything(new Intent(app, UpcomingTransactionsActivity.class));

        pick(activity, R.id.dropdownUpcomingType, "Reservations");
        assertEquals(java.util.Arrays.asList("Reservation #1", "Reservation #2"), refs(activity));

        pick(activity, R.id.dropdownUpcomingType, "Bookings");
        assertEquals(4, refs(activity).size());
    }

    @Test
    public void openingFromTheCalendarButton_startsOnAllStatuses() {
        UpcomingTransactionsActivity activity = launchWithEverything(UpcomingTransactionsActivity.intentAllStatuses(app));
        assertEquals("All", shown(activity, R.id.dropdownUpcomingStatus));
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

        assertEquals("the filter was reset so it can be seen", "All", shown(activity, R.id.dropdownUpcomingStatus));
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
        idle();
        android.app.Dialog sheet = sheet();
        assertNotNull("the details sheet opens first", sheet);
        sheet.findViewById(R.id.btnSheetViewFull).performClick(); // ...and leads on to the full record

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
        pick(activity, R.id.dropdownUpcomingStatus, "Checked Out");
        assertEquals(View.VISIBLE, activity.findViewById(R.id.layoutUpcomingEmptyState).getVisibility());

        activity.findViewById(R.id.btnUpcomingClearFilters).performClick();
        idle();
        assertEquals(View.GONE, activity.findViewById(R.id.layoutUpcomingEmptyState).getVisibility());
        assertEquals(1, refs(activity).size());
        assertEquals("All", shown(activity, R.id.dropdownUpcomingStatus));
    }
}
