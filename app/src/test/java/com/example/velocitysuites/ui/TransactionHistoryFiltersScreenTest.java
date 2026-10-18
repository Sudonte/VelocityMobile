package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.R;
import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.TransactionHistoryActivity;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.example.velocitysuites.network.dto.RoomTypeDto;
import com.example.velocitysuites.ui.ScreenTestSupport.FakeApi;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Transaction History's search and filters, end to end on the real screen: the three dropdowns (Transaction, Booking
 * status, Room type) that always open with their full list, the two date fields, the Show / Hide toggle, Clear filters,
 * the result count, the empty state, Export using the filtered rows, filters surviving a trip to a transaction's details
 * and a screen rotation, and filtering across every page.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TransactionHistoryFiltersScreenTest {

    private Context app;
    private FakeApi api;
    private RoomRepository repository;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new FakeApi();
        repository = ScreenTestSupport.freshRepository(app, api);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    // ---- data: one of each kind -------------------------------------------------------------------------

    private static DirectBookingResponseDto direct(long id, double total, String status, String room, String in, String out, String paid) {
        DirectBookingResponseDto d = ScreenTestSupport.directBooking(id, total);
        d.booking_status = status;
        d.check_in = in;
        d.check_out = out;
        RoomTypeDto type = new RoomTypeDto();
        type.id = 1;
        type.name = room;
        type.rate = "1000.00";
        d.room_type = type;
        if (paid != null) d.payments = new ArrayList<>(Collections.singletonList(ScreenTestSupport.payment(id, paid, "completed", "cash")));
        return d;
    }

    /**
     * #1 pending reservation (Deluxe, Oct 1-2) - #2 paid booking (Suite, Oct 10-11, Confirmed) - #3 checked-out booking
     * (Deluxe, Sep 1-2, paid) - #4 partially paid booking (Standard, Oct 20-21) - #5 cancelled reservation (Standard, Nov 5-6).
     */
    private ActivityController<TransactionHistoryActivity> launchWithEverything() {
        ActivityController<TransactionHistoryActivity> controller = Robolectric.buildActivity(TransactionHistoryActivity.class,
                new Intent(app, TransactionHistoryActivity.class));
        controller.setup();
        idle();
        ReservationDto pending = ScreenTestSupport.reservation(1, 1800);
        ReservationDto cancelled = ScreenTestSupport.reservation(5, 1500);
        cancelled.status = "CANCELLED_RESERVATION";
        cancelled.check_in = "2026-11-05";
        cancelled.check_out = "2026-11-06";
        RoomTypeDto standard = new RoomTypeDto();
        standard.id = 3;
        standard.name = "Standard";
        standard.rate = "1500.00";
        cancelled.room_type = standard;
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(pending, cancelled), ScreenTestSupport.directBookings(
                direct(2, 3000, "ACTIVE_BOOKING", "Suite", "2026-10-10", "2026-10-11", "3000.00"),
                direct(3, 2000, "COMPLETED_BOOKING", "Deluxe", "2026-09-01", "2026-09-02", "2000.00"),
                direct(4, 2000, "ACTIVE_BOOKING", "Standard", "2026-10-20", "2026-10-21", "1000.00")));
        idle();
        return controller;
    }

    private static List<String> shownRefs(TransactionHistoryActivity activity) {
        RecyclerView rv = activity.findViewById(R.id.rvTransactions);
        rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, 1080, 4000);
        List<String> refs = new ArrayList<>();
        for (int i = 0; i < rv.getAdapter().getItemCount(); i++) {
            RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(i);
            assertNotNull("row " + i + " bound", holder);
            refs.add(((TextView) holder.itemView.findViewById(R.id.tvTxnRef)).getText().toString());
        }
        Collections.sort(refs);
        return refs;
    }

    private static List<String> refs(String... r) {
        List<String> list = new ArrayList<>(Arrays.asList(r));
        Collections.sort(list);
        return list;
    }

    private static String text(TransactionHistoryActivity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    // ---- dropdown helpers ----

    private static AutoCompleteTextView dropdown(TransactionHistoryActivity a, int id) {
        return a.findViewById(id);
    }

    /** Picks entry {@code position} the way a tap on the open list does. */
    private static void pick(TransactionHistoryActivity a, int dropdownId, int position) {
        dropdown(a, dropdownId).getOnItemClickListener().onItemClick(null, null, position, position);
        idle();
    }

    private static List<String> optionsOf(TransactionHistoryActivity a, int dropdownId) {
        android.widget.ListAdapter adapter = dropdown(a, dropdownId).getAdapter();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < adapter.getCount(); i++) out.add(adapter.getItem(i).toString());
        return out;
    }

    private static void choose(TransactionHistoryActivity a, int dropdownId, String label) {
        List<String> options = optionsOf(a, dropdownId);
        int index = options.indexOf(label);
        if (index < 0) throw new AssertionError("no option '" + label + "' in " + options);
        pick(a, dropdownId, index);
    }

    private static void pickTransaction(TransactionHistoryActivity a, String label) {
        choose(a, R.id.dropdownTransaction, label);
    }

    private static void pickBookingStatus(TransactionHistoryActivity a, String label) {
        choose(a, R.id.dropdownBookingStatus, label);
    }

    private static void pickRoomType(TransactionHistoryActivity a, String label) {
        choose(a, R.id.dropdownRoomType, label);
    }

    /** What the adapter's own filter would keep for this text - synchronously, as a typed or pre-filled text would filter. */
    private static int countAfterFiltering(AutoCompleteTextView dd, String text) throws Exception {
        android.widget.Filter filter = ((android.widget.Filterable) dd.getAdapter()).getFilter();
        java.lang.reflect.Method m = android.widget.Filter.class.getDeclaredMethod("performFiltering", CharSequence.class);
        m.setAccessible(true);
        Object results = m.invoke(filter, text);
        return results.getClass().getField("count").getInt(results);
    }

    // ---- every dropdown opens with its FULL list ----------------------------------------------------------

    @Test
    public void everyDropdown_opensWithItsFullList_alsoAfterASelectionAndAfterComingBack() throws Exception {
        ActivityController<TransactionHistoryActivity> controller = launchWithEverything();
        TransactionHistoryActivity a = controller.get();
        assertEquals("Transaction: 11 options", 11, optionsOf(a, R.id.dropdownTransaction).size());
        assertEquals("Booking status: All + 6", 7, optionsOf(a, R.id.dropdownBookingStatus).size());
        int rooms = optionsOf(a, R.id.dropdownRoomType).size();
        assertTrue("Room type: All room types + the room types: " + optionsOf(a, R.id.dropdownRoomType), rooms >= 4);

        pickTransaction(a, "Bookings");
        pickBookingStatus(a, "Confirmed");
        pickRoomType(a, "Suite");
        // the text now says what was picked ...
        assertEquals("Bookings", dropdown(a, R.id.dropdownTransaction).getText().toString());
        // ... and that text does not narrow the list: every dropdown still has everything
        assertEquals(11, countAfterFiltering(dropdown(a, R.id.dropdownTransaction), "Bookings"));
        assertEquals(7, countAfterFiltering(dropdown(a, R.id.dropdownBookingStatus), "Confirmed"));
        assertEquals(rooms, countAfterFiltering(dropdown(a, R.id.dropdownRoomType), "Suite"));
        assertEquals(11, dropdown(a, R.id.dropdownTransaction).getAdapter().getCount());

        controller.pause().stop();
        controller.restart().start().resume(); // back from a transaction's details
        idle();
        assertEquals(11, dropdown(a, R.id.dropdownTransaction).getAdapter().getCount());
        assertEquals(7, dropdown(a, R.id.dropdownBookingStatus).getAdapter().getCount());
        assertEquals(rooms, dropdown(a, R.id.dropdownRoomType).getAdapter().getCount());
    }

    // ---- the Transaction dropdown, option by option -----------------------------------------------------------

    @Test
    public void transactionDropdown_eachOption() {
        TransactionHistoryActivity a = launchWithEverything().get();
        pickTransaction(a, "Bookings");
        assertEquals(refs("Booking #2", "Booking #3", "Booking #4"), shownRefs(a));
        pickTransaction(a, "Reservations");
        assertEquals("a cancelled reservation is closed, so it is not under Reservations", refs("Reservation #1"), shownRefs(a));
        pickTransaction(a, "Payments");
        assertEquals(refs("Booking #2", "Booking #3", "Booking #4"), shownRefs(a));
        pickTransaction(a, "Pending");
        assertEquals(refs("Reservation #1"), shownRefs(a));
        pickTransaction(a, "Paid");
        assertEquals(refs("Booking #2", "Booking #3"), shownRefs(a));
        pickTransaction(a, "Partially paid");
        assertEquals(refs("Booking #4"), shownRefs(a));
        pickTransaction(a, "Cancelled");
        assertEquals(refs("Reservation #5"), shownRefs(a));
        pickTransaction(a, "Rejected");
        assertEquals(Collections.emptyList(), shownRefs(a));
        pickTransaction(a, "Checked in");
        assertEquals(Collections.emptyList(), shownRefs(a));
        pickTransaction(a, "Checked out");
        assertEquals(refs("Booking #3"), shownRefs(a));
        pickTransaction(a, "All transactions");
        assertEquals(5, shownRefs(a).size());
    }

    @Test
    public void bookingStatusDropdown_eachOption() {
        TransactionHistoryActivity a = launchWithEverything().get();
        pickBookingStatus(a, "Pending");
        assertEquals(refs("Reservation #1"), shownRefs(a));
        pickBookingStatus(a, "Confirmed");
        assertEquals(refs("Booking #2", "Booking #4"), shownRefs(a));
        pickBookingStatus(a, "Checked out");
        assertEquals(refs("Booking #3"), shownRefs(a));
        pickBookingStatus(a, "Cancelled");
        assertEquals(refs("Reservation #5"), shownRefs(a));
        pickBookingStatus(a, "Checked in");
        assertEquals(Collections.emptyList(), shownRefs(a));
        pickBookingStatus(a, "All");
        assertEquals(5, shownRefs(a).size());
    }

    @Test
    public void roomTypeDropdown_listsTheCatalogOrTheGuestsOwnRoomTypes_andFilters() {
        TransactionHistoryActivity a = launchWithEverything().get();
        List<String> offered = optionsOf(a, R.id.dropdownRoomType);
        assertEquals("All room types", offered.get(0));
        assertTrue("fallback to the guest's own room types: " + offered, offered.containsAll(Arrays.asList("Deluxe", "Standard", "Suite")));
        pickRoomType(a, "Standard");
        assertEquals(refs("Booking #4", "Reservation #5"), shownRefs(a));
        pickRoomType(a, "All room types");
        assertEquals(5, shownRefs(a).size());
    }

    // ---- the date fields ----------------------------------------------------------------------------------------

    private void setDates(TransactionHistoryActivity a, LocalDate checkIn, LocalDate checkOut) throws Exception {
        Object criteria = field(a, "criteria");
        field(criteria, "checkInFrom", checkIn);
        field(criteria, "checkOutTo", checkOut);
        invoke(a, "onFiltersChanged");
        idle();
    }

    @Test
    public void checkInDateOnly_checkOutDateOnly_andBoth() throws Exception {
        TransactionHistoryActivity a = launchWithEverything().get();
        setDates(a, LocalDate.of(2026, 10, 10), null);
        assertEquals("checking in on or after Oct 10", refs("Booking #2", "Booking #4", "Reservation #5"), shownRefs(a));
        assertEquals("Oct 10, 2026", text(a, R.id.etCheckIn));

        setDates(a, null, LocalDate.of(2026, 10, 11));
        assertEquals("checking out on or before Oct 11", refs("Reservation #1", "Booking #2", "Booking #3"), shownRefs(a));
        assertEquals("Oct 11, 2026", text(a, R.id.etCheckOut));

        setDates(a, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 21));
        assertEquals("inside the window", refs("Reservation #1", "Booking #2", "Booking #4"), shownRefs(a));
    }

    // ---- combining, counting, clearing ----------------------------------------------------------------------

    @Test
    public void dropdownsDatesAndSearchCombine_theToggleCounts_andClearFiltersResetsEverything() throws Exception {
        TransactionHistoryActivity a = launchWithEverything().get();
        assertEquals("Clear filters is hidden while nothing filters", View.GONE, a.findViewById(R.id.btnClearFilters).getVisibility());
        assertEquals("5 results", text(a, R.id.tvResultCount));

        pickTransaction(a, "Paid");
        pickRoomType(a, "Suite");
        assertEquals(refs("Booking #2"), shownRefs(a));
        assertEquals("1 result", text(a, R.id.tvResultCount));
        assertEquals(View.VISIBLE, a.findViewById(R.id.btnClearFilters).getVisibility());

        a.findViewById(R.id.btnToggleFilters).performClick(); // fold the panel
        assertEquals(View.GONE, a.findViewById(R.id.layoutFilterPanel).getVisibility());
        assertEquals("Show filters (2 active)", text(a, R.id.btnToggleFilters));
        a.findViewById(R.id.btnToggleFilters).performClick();
        assertEquals(View.VISIBLE, a.findViewById(R.id.layoutFilterPanel).getVisibility());
        assertEquals("Hide filters", text(a, R.id.btnToggleFilters));

        ((TextView) a.findViewById(R.id.etSearch)).setText("deluxe"); // search narrows further (AND)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(Collections.emptyList(), shownRefs(a));
        assertEquals("No transactions match your filters", text(a, R.id.emptyTitle));
        assertEquals("Clear filters", text(a, R.id.btnEmptyAction));

        a.findViewById(R.id.btnEmptyAction).performClick();
        idle();
        assertEquals(5, shownRefs(a).size());
        assertEquals("All", dropdown(a, R.id.dropdownTransaction).getText().toString());
        assertEquals("All", dropdown(a, R.id.dropdownRoomType).getText().toString());
        assertEquals("", text(a, R.id.etSearch));
        assertEquals(View.GONE, a.findViewById(R.id.btnClearFilters).getVisibility());

        setDates(a, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 21));
        pickBookingStatus(a, "Confirmed");
        a.findViewById(R.id.btnClearFilters).performClick();
        idle();
        assertEquals("the Clear filters button clears the dates too", 5, shownRefs(a).size());
        assertEquals("", text(a, R.id.etCheckIn));
    }

    // ---- Export, details round trip, rotation, pagination -----------------------------------------------------

    @Test
    public void export_usesExactlyTheFilteredAndSearchedRows() throws Exception {
        TransactionHistoryActivity a = launchWithEverything().get();
        pickTransaction(a, "Paid");
        ((TextView) a.findViewById(R.id.etSearch)).setText("suite");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        List<?> exported = (List<?>) invoke(a, "rowsForExport");
        assertEquals("exactly the one row on screen", 1, exported.size());
        assertEquals("2", field(exported.get(0), "id"));
        assertEquals(1, shownRefs(a).size());
    }

    @Test
    public void filtersAndSearchSurviveAVisitToATransactionsDetails() {
        ActivityController<TransactionHistoryActivity> controller = launchWithEverything();
        TransactionHistoryActivity a = controller.get();
        pickTransaction(a, "Paid");
        pickRoomType(a, "Suite");
        assertEquals(refs("Booking #2"), shownRefs(a));

        RecyclerView rv = a.findViewById(R.id.rvTransactions);
        rv.findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.cardTransaction).performClick();
        assertNotNull("the details screen was opened", shadowOf(a).getNextStartedActivity());
        controller.pause().stop();
        controller.restart().start().resume(); // coming back
        idle();

        assertEquals("the same filters, the same rows", refs("Booking #2"), shownRefs(a));
        assertEquals("Paid", dropdown(a, R.id.dropdownTransaction).getText().toString());
        assertEquals("Suite", dropdown(a, R.id.dropdownRoomType).getText().toString());
    }

    @Test
    public void filtersSurviveAScreenRotation() throws Exception {
        ActivityController<TransactionHistoryActivity> controller = launchWithEverything();
        TransactionHistoryActivity a = controller.get();
        pickTransaction(a, "Bookings");
        pickBookingStatus(a, "Confirmed");
        setDates(a, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        a.findViewById(R.id.btnToggleFilters).performClick(); // folded
        assertEquals(refs("Booking #2", "Booking #4"), shownRefs(a));

        controller.recreate(); // rotation
        idle();
        TransactionHistoryActivity again = controller.get();
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1,
                ScreenTestSupport.reservations(ScreenTestSupport.reservation(1, 1800)), ScreenTestSupport.directBookings(
                        direct(2, 3000, "ACTIVE_BOOKING", "Suite", "2026-10-10", "2026-10-11", "3000.00"),
                        direct(4, 2000, "ACTIVE_BOOKING", "Standard", "2026-10-20", "2026-10-21", "1000.00")));
        idle();
        assertEquals("Bookings", dropdown(again, R.id.dropdownTransaction).getText().toString());
        assertEquals("Confirmed", dropdown(again, R.id.dropdownBookingStatus).getText().toString());
        assertEquals("Oct 1, 2026", text(again, R.id.etCheckIn));
        assertEquals("Oct 31, 2026", text(again, R.id.etCheckOut));
        assertEquals("the panel stays folded", View.GONE, again.findViewById(R.id.layoutFilterPanel).getVisibility());
        assertEquals(refs("Booking #2", "Booking #4"), shownRefs(again));
        assertEquals("every dropdown still has its full list", 11, dropdown(again, R.id.dropdownTransaction).getAdapter().getCount());
    }

    @Test
    public void aCard_neverShowsTheSameStatusWordTwice_andTheIdIsPlainBold() {
        TransactionHistoryActivity a = launchWithEverything().get();
        RecyclerView rv = a.findViewById(R.id.rvTransactions);
        shownRefs(a);
        for (int i = 0; i < rv.getAdapter().getItemCount(); i++) {
            View card = rv.findViewHolderForAdapterPosition(i).itemView;
            TextView payment = card.findViewById(R.id.tvTxnStatusBadge);
            TextView booking = card.findViewById(R.id.tvTxnBookingStatus);
            if (payment.getVisibility() == View.VISIBLE && booking.getVisibility() == View.VISIBLE) {
                assertFalse("\"" + payment.getText() + "\" twice", payment.getText().toString().equalsIgnoreCase(booking.getText().toString()));
            }
            TextView ref = card.findViewById(R.id.tvTxnRef);
            assertFalse("Title Case, not ALL CAPS", ref.isAllCaps());
            assertEquals("no letter-spacing", 0f, ref.getLetterSpacing(), 0.0001f);
        }
    }

    @Test
    public void whileAFilterIsActive_everyPageIsLoaded_notJustTheFirst() {
        ActivityController<TransactionHistoryActivity> controller = Robolectric.buildActivity(TransactionHistoryActivity.class,
                new Intent(app, TransactionHistoryActivity.class));
        controller.setup();
        idle();
        TransactionHistoryActivity a = controller.get();
        com.example.velocitysuites.network.dto.PaginatedResponse<ReservationDto> first = ScreenTestSupport.page(ScreenTestSupport.reservations(ScreenTestSupport.reservation(1, 1800)));
        first.total = 450;
        api.call("getReservations", 0).succeed(first);
        api.call("getDirectBookings", 0).succeed(ScreenTestSupport.page(new ArrayList<DirectBookingResponseDto>()));
        idle();
        assertEquals("no filter: nothing extra is loaded", 1, api.count("getReservations"));

        pickTransaction(a, "Reservations");
        assertEquals("a filter asks for the next page", 2, api.count("getReservations"));

        com.example.velocitysuites.network.dto.PaginatedResponse<ReservationDto> second = ScreenTestSupport.page(ScreenTestSupport.reservations(
                ScreenTestSupport.reservation(1, 1800), ScreenTestSupport.reservation(2, 1800)));
        second.total = 450;
        api.call("getReservations", 1).succeed(second);
        api.call("getDirectBookings", 1).succeed(ScreenTestSupport.page(new ArrayList<DirectBookingResponseDto>()));
        idle();
        assertEquals("...and keeps going while the server reports more", 3, api.count("getReservations"));

        com.example.velocitysuites.network.dto.PaginatedResponse<ReservationDto> third = ScreenTestSupport.page(ScreenTestSupport.reservations(
                ScreenTestSupport.reservation(1, 1800), ScreenTestSupport.reservation(2, 1800), ScreenTestSupport.reservation(3, 1800)));
        third.total = 450;
        api.call("getReservations", 2).succeed(third);
        api.call("getDirectBookings", 2).succeed(ScreenTestSupport.page(new ArrayList<DirectBookingResponseDto>()));
        idle();
        assertEquals("then it stops", 3, api.count("getReservations"));
        assertEquals("every loaded page is filtered", 3, shownRefs(a).size());
        assertEquals("3 results", text(a, R.id.tvResultCount));
    }

    // ---- layout at 360dp, light and dark ---------------------------------------------------------------------

    private void assertFilterPanelFits(TransactionHistoryActivity a) {
        View root = a.getWindow().getDecorView();
        int w = app.getResources().getDisplayMetrics().widthPixels;
        int h = app.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        int[] ids = {R.id.layoutTransactionFilter, R.id.layoutBookingStatusFilter, R.id.layoutRoomTypeFilter, R.id.layoutCheckIn, R.id.layoutCheckOut};
        for (int i = 0; i < ids.length; i++) {
            View view = a.findViewById(ids[i]);
            Rect r = LayoutHarness.boundsIn(root, view);
            assertTrue("on screen: " + a.getResources().getResourceEntryName(ids[i]), r.left >= 0 && r.right <= w);
            assertTrue("a usable width", r.width() >= LayoutHarness.dp(app, 120));
            assertTrue("48dp tall at least", r.height() >= LayoutHarness.dp(app, 48));
            for (int j = i + 1; j < ids.length; j++) {
                assertFalse(a.getResources().getResourceEntryName(ids[i]) + " overlaps " + a.getResources().getResourceEntryName(ids[j]),
                        LayoutHarness.overlaps(root, view, a.findViewById(ids[j])));
            }
        }
        Rect transaction = LayoutHarness.boundsIn(root, a.findViewById(R.id.layoutTransactionFilter));
        Rect booking = LayoutHarness.boundsIn(root, a.findViewById(R.id.layoutBookingStatusFilter));
        assertEquals("two columns on the first row", transaction.top, booking.top);
        Rect checkIn = LayoutHarness.boundsIn(root, a.findViewById(R.id.layoutCheckIn));
        Rect checkOut = LayoutHarness.boundsIn(root, a.findViewById(R.id.layoutCheckOut));
        assertTrue("the date fields are stacked, each full width", checkOut.top >= checkIn.bottom && checkIn.width() == checkOut.width());
        assertTrue(LayoutHarness.boundsIn(root, a.findViewById(R.id.btnToggleFilters)).height() >= LayoutHarness.dp(app, 48));

        View count = a.findViewById(R.id.tvResultCount);
        View export = a.findViewById(R.id.btnExport);
        assertFalse("the count does not overlap Export", Rect.intersects(LayoutHarness.boundsIn(root, count), LayoutHarness.boundsIn(root, export)));
        assertTrue("Export is fully on screen", LayoutHarness.boundsIn(root, export).right <= w);
        assertEquals("the description is one line", 1, ((TextView) a.findViewById(R.id.screenDescription)).getLineCount());
    }

    private static final int[] FIELD_LAYOUTS = {R.id.layoutTransactionFilter, R.id.layoutBookingStatusFilter, R.id.layoutRoomTypeFilter,
            R.id.layoutCheckIn, R.id.layoutCheckOut};
    private static final int[] FIELD_VALUES = {R.id.dropdownTransaction, R.id.dropdownBookingStatus, R.id.dropdownRoomType,
            R.id.etCheckIn, R.id.etCheckOut};

    /**
     * What the real device showed and the old tests missed: each field's VALUE on ONE line, not ellipsized, and its text
     * actually inside the field - for the defaults and for a real date. (The old tests only compared the fields' outer
     * boxes with each other, so a box that was wide enough but too short for its wrapped text passed.)
     */
    private void assertFieldsShowTheirValuesInFull(TransactionHistoryActivity a) throws Exception {
        View root = a.getWindow().getDecorView();
        int w = app.getResources().getDisplayMetrics().widthPixels;
        int h = app.getResources().getDisplayMetrics().heightPixels;
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) { // a real date in both date fields
                Object criteria = field(a, "criteria");
                field(criteria, "checkInFrom", LocalDate.of(2026, 10, 19));
                field(criteria, "checkOutTo", LocalDate.of(2026, 10, 20));
                invoke(a, "onFiltersChanged");
                idle();
            }
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, w, h);
            int borderColor = Integer.MIN_VALUE;
            for (int i = 0; i < FIELD_LAYOUTS.length; i++) {
                com.google.android.material.textfield.TextInputLayout layout = a.findViewById(FIELD_LAYOUTS[i]);
                TextView value = a.findViewById(FIELD_VALUES[i]);
                String name = a.getResources().getResourceEntryName(FIELD_LAYOUTS[i]) + (pass == 1 ? " (with a date)" : "");

                assertTrue(name + ": it has a label (hint=" + layout.getHint() + ", enabled=" + layout.isHintEnabled() + ", edit=" + (layout.getEditText() == null ? "null" : layout.getEditText().getHint()) + ")", layout.getHint() != null && layout.getHint().toString().trim().length() > 0);
                assertEquals(name + ": one line", 1, value.getLineCount());
                assertEquals(name + ": not ellipsized", 0, value.getLayout().getEllipsisCount(0));
                assertEquals(name + ": no second line hiding below", 1, value.getLayout().getLineCount());

                // the text is inside the field: its width fits the value view, and the value view sits inside the field's box
                float textWidth = value.getPaint().measureText(value.getText().toString());
                int room = value.getWidth() - value.getTotalPaddingLeft() - value.getTotalPaddingRight();
                assertTrue(name + ": text " + textWidth + "px fits " + room + "px", textWidth <= room);
                Rect box = LayoutHarness.boundsIn(root, layout);
                Rect inside = LayoutHarness.boundsIn(root, value);
                assertTrue(name + ": the text view is inside the field's box", box.contains(inside));
                assertTrue(name + ": the box itself is on screen", box.left >= 0 && box.right <= w);

                java.lang.reflect.Field strokeField = com.google.android.material.textfield.TextInputLayout.class.getDeclaredField("defaultStrokeColor");
                strokeField.setAccessible(true);
                int color = strokeField.getInt(layout); // the unfocused border
                assertFalse(name + ": not red while unfocused", color == app.getColor(R.color.velocity_red_primary));
                if (borderColor == Integer.MIN_VALUE) borderColor = color;
                assertEquals(name + ": every field has the same unfocused border", borderColor, color);
            }
            if (pass == 0) {
                assertEquals("All", text(a, R.id.dropdownTransaction));
                assertEquals("", text(a, R.id.etCheckIn));
                assertEquals("", text(a, R.id.etCheckOut));
            } else {
                assertEquals("Oct 19, 2026", text(a, R.id.etCheckIn));
                assertEquals("Oct 20, 2026", text(a, R.id.etCheckOut));
            }
        }
    }

    @Test
    public void everyFilterField_isLabelledSingleLineAndUnclipped_at360dp_inLightMode() throws Exception {
        TransactionHistoryActivity a = launchWithEverything().get();
        assertFilterPanelFits(a);
        assertFieldsShowTheirValuesInFull(a);
    }

    @Test
    @Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xxhdpi", application = TestApplication.class)
    public void everyFilterField_isLabelledSingleLineAndUnclipped_at360dp_inDarkMode() throws Exception {
        TransactionHistoryActivity a = launchWithEverything().get();
        assertFilterPanelFits(a);
        assertFieldsShowTheirValuesInFull(a);
    }

    @Test
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-xxhdpi", application = TestApplication.class)
    public void everyFilterField_isLabelledSingleLineAndUnclipped_at393dp_inLightMode() throws Exception {
        TransactionHistoryActivity a = launchWithEverything().get();
        assertFilterPanelFits(a);
        assertFieldsShowTheirValuesInFull(a);
    }

    @Test
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-night-xxhdpi", application = TestApplication.class)
    public void everyFilterField_isLabelledSingleLineAndUnclipped_at393dp_inDarkMode() throws Exception {
        TransactionHistoryActivity a = launchWithEverything().get();
        assertFilterPanelFits(a);
        assertFieldsShowTheirValuesInFull(a);
    }

    // ---- the date filter through the REAL pickers (the bug seen on a device) ----------------------------------

    /** Four one-night stays: Oct 18-19, Oct 19-20, Oct 20-21 and Oct 21-22, 2026. */
    private ActivityController<TransactionHistoryActivity> launchWithFourStays() {
        ActivityController<TransactionHistoryActivity> controller = Robolectric.buildActivity(TransactionHistoryActivity.class,
                new Intent(app, TransactionHistoryActivity.class));
        controller.setup();
        idle();
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(
                direct(18, 1000, "ACTIVE_BOOKING", "Deluxe", "2026-10-18", "2026-10-19", null),
                direct(19, 1000, "ACTIVE_BOOKING", "Deluxe", "2026-10-19", "2026-10-20", null),
                direct(20, 1000, "ACTIVE_BOOKING", "Deluxe", "2026-10-20", "2026-10-21", null),
                direct(21, 1000, "ACTIVE_BOOKING", "Deluxe", "2026-10-21", "2026-10-22", null)));
        idle();
        return controller;
    }

    /** Opens the check-in (or check-out) picker from its field, chooses {@code date} in it and confirms - exactly what a guest does. */
    @SuppressWarnings("unchecked")
    private void pickDateInPicker(TransactionHistoryActivity a, boolean checkIn, LocalDate date) throws Exception {
        a.findViewById(checkIn ? R.id.etCheckIn : R.id.etCheckOut).performClick();
        idle();
        androidx.fragment.app.Fragment fragment = a.getSupportFragmentManager().findFragmentByTag(checkIn ? "PICK_CHECK_IN" : "PICK_CHECK_OUT");
        assertNotNull("the date picker opened", fragment);
        com.google.android.material.datepicker.MaterialDatePicker<Long> picker = (com.google.android.material.datepicker.MaterialDatePicker<Long>) fragment;
        java.lang.reflect.Field selectorField = picker.getClass().getDeclaredField("dateSelector");
        selectorField.setAccessible(true);
        com.google.android.material.datepicker.DateSelector<Long> selector = (com.google.android.material.datepicker.DateSelector<Long>) selectorField.get(picker);
        selector.select(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli());
        picker.getDialog().findViewById(com.google.android.material.R.id.confirm_button).performClick();
        idle();
    }

    @Test
    public void theGuestsExactCase_checkInOct18_checkOutOct20_keepsOnlyTheTwoStaysInsideTheWindow() throws Exception {
        TransactionHistoryActivity a = launchWithFourStays().get();
        assertEquals("4 results", text(a, R.id.tvResultCount));

        pickDateInPicker(a, true, LocalDate.of(2026, 10, 18));
        assertEquals("check-in only: stays checking in on or after Oct 18", "4 results", text(a, R.id.tvResultCount));
        pickDateInPicker(a, false, LocalDate.of(2026, 10, 20));
        assertEquals("the count updates the moment the date is picked", "2 results", text(a, R.id.tvResultCount));
        assertEquals(refs("Booking #18", "Booking #19"), shownRefs(a));
        assertEquals("Oct 18, 2026", text(a, R.id.etCheckIn));
        assertEquals("Oct 20, 2026", text(a, R.id.etCheckOut));
    }

    @Test
    public void theSameCase_inTheManilaTimeZone_doesNotShiftADay() throws Exception {
        java.util.TimeZone before = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Manila"));
        try {
            TransactionHistoryActivity a = launchWithFourStays().get();
            pickDateInPicker(a, true, LocalDate.of(2026, 10, 18));
            pickDateInPicker(a, false, LocalDate.of(2026, 10, 20));
            assertEquals(refs("Booking #18", "Booking #19"), shownRefs(a));
            assertEquals("Oct 18, 2026", text(a, R.id.etCheckIn));
            assertEquals("Oct 20, 2026", text(a, R.id.etCheckOut));
        } finally {
            java.util.TimeZone.setDefault(before);
        }
    }

    @Test
    public void openedFromADeepLink_theFiltersStillFilter_theLinkedTransactionIsNotForcedBackIn() throws Exception {
        // How the guest got here: a Dashboard Payment Status card for Booking #21 (Oct 21-22) opened this screen on it.
        Intent link = new Intent(app, TransactionHistoryActivity.class)
                .putExtra(TransactionHistoryActivity.EXTRA_SELECTED_BOOKING_ID, "21")
                .putExtra(TransactionHistoryActivity.EXTRA_SELECTED_DIRECT, true);
        ActivityController<TransactionHistoryActivity> controller = Robolectric.buildActivity(TransactionHistoryActivity.class, link);
        controller.setup();
        idle();
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(
                direct(18, 1000, "ACTIVE_BOOKING", "Deluxe", "2026-10-18", "2026-10-19", null),
                direct(19, 1000, "ACTIVE_BOOKING", "Deluxe", "2026-10-19", "2026-10-20", null),
                direct(20, 1000, "ACTIVE_BOOKING", "Deluxe", "2026-10-20", "2026-10-21", null),
                direct(21, 1000, "ACTIVE_BOOKING", "Deluxe", "2026-10-21", "2026-10-22", null)));
        idle();
        TransactionHistoryActivity a = controller.get();

        pickDateInPicker(a, true, LocalDate.of(2026, 10, 18));
        pickDateInPicker(a, false, LocalDate.of(2026, 10, 20));
        assertEquals("the stay outside the window stays out, although the screen was opened on it", refs("Booking #18", "Booking #19"), shownRefs(a));
        assertEquals("2 results", text(a, R.id.tvResultCount));
        assertEquals("the dates the guest picked are still there", "Oct 20, 2026", text(a, R.id.etCheckOut));
    }

    @Test
    public void checkInOnly_andCheckOutOnly_throughThePickers() throws Exception {
        TransactionHistoryActivity a = launchWithFourStays().get();
        pickDateInPicker(a, true, LocalDate.of(2026, 10, 20));
        assertEquals(refs("Booking #20", "Booking #21"), shownRefs(a));
        assertEquals("2 results", text(a, R.id.tvResultCount));
        a.findViewById(R.id.btnClearFilters).performClick();
        idle();
        pickDateInPicker(a, false, LocalDate.of(2026, 10, 20));
        assertEquals(refs("Booking #18", "Booking #19"), shownRefs(a));
        assertEquals("", text(a, R.id.etCheckIn));
    }

    @Test
    public void datesAreEmptyByDefault_andAfterClearFilters() throws Exception {
        TransactionHistoryActivity a = launchWithFourStays().get();
        assertEquals("", text(a, R.id.etCheckIn));
        assertEquals("", text(a, R.id.etCheckOut));
        pickDateInPicker(a, true, LocalDate.of(2026, 10, 19));
        pickDateInPicker(a, false, LocalDate.of(2026, 10, 21));
        assertEquals("Oct 19, 2026", text(a, R.id.etCheckIn));
        a.findViewById(R.id.btnClearFilters).performClick();
        idle();
        assertEquals("", text(a, R.id.etCheckIn));
        assertEquals("", text(a, R.id.etCheckOut));
        assertEquals("All", dropdown(a, R.id.dropdownTransaction).getText().toString());
        assertEquals("All", dropdown(a, R.id.dropdownBookingStatus).getText().toString());
        assertEquals("All", dropdown(a, R.id.dropdownRoomType).getText().toString());
        assertEquals("4 results", text(a, R.id.tvResultCount));
    }

    // ---- reflection helpers ----------------------------------------------------------------------------------

    private static Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object invoke(Object target, String name) throws Exception {
        java.lang.reflect.Method m = target.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(target);
    }
}
