package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Transaction History's search and filters, end to end on the real screen: the Type chips, the Filters sheet (payment
 * status, booking status, room type, dates, Reset / Apply), the active-filter chips, the result count, the empty state,
 * Export using the filtered rows, filters surviving a trip to a transaction's details, and filtering across every page.
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

    private void chooseType(TransactionHistoryActivity a, int chipId) {
        ((Chip) a.findViewById(chipId)).performClick();
        idle();
    }

    /** Opens the Filters sheet, picks the chip tagged {@code value} in the group, and applies. */
    private void pickInSheet(TransactionHistoryActivity a, int groupId, String value) {
        a.findViewById(R.id.btnFilters).performClick();
        idle();
        Dialog sheet = ShadowDialog.getLatestDialog();
        assertNotNull("the Filters sheet opened", sheet);
        ChipGroup group = sheet.findViewById(groupId);
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip chip = (Chip) group.getChildAt(i);
            if (value.equals(chip.getTag())) chip.performClick();
        }
        sheet.findViewById(R.id.btnSheetApply).performClick();
        idle();
    }

    // ---- the Type chips -------------------------------------------------------------------------------------

    @Test
    public void typeChips_filterBookingsReservationsAndPayments_andAllRestoresEverything() {
        TransactionHistoryActivity a = launchWithEverything().get();
        assertEquals("all five at first", 5, shownRefs(a).size());
        assertEquals("5 results", text(a, R.id.tvResultCount));

        chooseType(a, R.id.chipTypeBookings);
        assertEquals(refs("Booking #2", "Booking #3", "Booking #4"), shownRefs(a));
        chooseType(a, R.id.chipTypeReservations);
        assertEquals("a cancelled reservation is closed, so it is not under Reservations", refs("Reservation #1"), shownRefs(a));
        chooseType(a, R.id.chipTypePayments);
        assertEquals("transactions with at least one payment", refs("Booking #2", "Booking #3", "Booking #4"), shownRefs(a));
        chooseType(a, R.id.chipTypeAll);
        assertEquals(5, shownRefs(a).size());
    }

    // ---- the Filters sheet, one filter at a time ------------------------------------------------------------

    @Test
    public void paymentStatus_eachOption() {
        TransactionHistoryActivity a = launchWithEverything().get();
        pickInSheet(a, R.id.sheetGroupPayment, "PAID");
        assertEquals(refs("Booking #2", "Booking #3"), shownRefs(a));
        pickInSheet(a, R.id.sheetGroupPayment, "PARTIALLY_PAID");
        assertEquals(refs("Booking #4"), shownRefs(a));
        pickInSheet(a, R.id.sheetGroupPayment, "PENDING");
        assertEquals(refs("Reservation #1"), shownRefs(a));
        pickInSheet(a, R.id.sheetGroupPayment, "");
        assertEquals("All", 5, shownRefs(a).size());
    }

    @Test
    public void bookingStatus_eachOption() {
        TransactionHistoryActivity a = launchWithEverything().get();
        pickInSheet(a, R.id.sheetGroupBooking, "Checked-Out");
        assertEquals(refs("Booking #3"), shownRefs(a));
        pickInSheet(a, R.id.sheetGroupBooking, "Cancelled");
        assertEquals("Cancelled finds reservations too", refs("Reservation #5"), shownRefs(a));
        pickInSheet(a, R.id.sheetGroupBooking, "Pending");
        assertEquals(refs("Reservation #1"), shownRefs(a));
        pickInSheet(a, R.id.sheetGroupBooking, "");
        assertEquals(5, shownRefs(a).size());
    }

    @Test
    public void roomType_isOfferedFromTheBrowseRoomsCatalog_andFilters() {
        TransactionHistoryActivity a = launchWithEverything().get();
        a.findViewById(R.id.btnFilters).performClick();
        idle();
        ChipGroup rooms = ShadowDialog.getLatestDialog().findViewById(R.id.sheetGroupRoom);
        List<String> offered = new ArrayList<>();
        for (int i = 0; i < rooms.getChildCount(); i++) offered.add(((Chip) rooms.getChildAt(i)).getText().toString());
        assertEquals("All room types", offered.get(0));
        assertTrue("fallback to the room types in the guest's own transactions: " + offered,
                offered.containsAll(Arrays.asList("Deluxe", "Standard", "Suite")));
        ShadowDialog.getLatestDialog().dismiss();

        pickInSheet(a, R.id.sheetGroupRoom, "Standard");
        assertEquals(refs("Booking #4", "Reservation #5"), shownRefs(a));
    }

    @Test
    public void dates_aStayThatOverlapsTheRangeMatches() throws Exception {
        TransactionHistoryActivity a = launchWithEverything().get();
        Object criteria = field(a, "criteria");
        field(criteria, "from", LocalDate.of(2026, 10, 11)); // starts on booking #2's check-OUT day
        field(criteria, "to", LocalDate.of(2026, 10, 20));   // ends on booking #4's check-IN day
        invoke(a, "updateFilterUi");
        invoke(a, "applyFilters");
        assertEquals(refs("Booking #2", "Booking #4"), shownRefs(a));
        assertEquals("the date shows as a removable chip", 1, ((ChipGroup) a.findViewById(R.id.chipGroupActive)).getChildCount());
        assertEquals("Oct 11 - Oct 20", ((Chip) ((ChipGroup) a.findViewById(R.id.chipGroupActive)).getChildAt(0)).getText().toString());
    }

    // ---- combining, counting, clearing ----------------------------------------------------------------------

    @Test
    public void filtersCombineWithEachOtherAndWithSearch_countOnTheButton_removableChips_resetAndClearAll() {
        TransactionHistoryActivity a = launchWithEverything().get();
        pickInSheet(a, R.id.sheetGroupPayment, "PAID");
        pickInSheet(a, R.id.sheetGroupRoom, "Suite");
        assertEquals("the room pick replaced nothing: payment status was reset by the staged copy? no - it is kept", refs("Booking #2"), shownRefs(a));
        assertEquals("Filters · 2", text(a, R.id.btnFilters));
        assertEquals(View.VISIBLE, a.findViewById(R.id.rowActiveFilters).getVisibility());
        ChipGroup active = a.findViewById(R.id.chipGroupActive);
        assertEquals(2, active.getChildCount());

        // search narrows it further (AND)
        ((TextView) a.findViewById(R.id.etSearch)).setText("deluxe");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(Collections.emptyList(), shownRefs(a));
        assertEquals("No transactions match your filters", text(a, R.id.emptyTitle));
        assertEquals("Clear filters", text(a, R.id.btnEmptyAction));

        a.findViewById(R.id.btnEmptyAction).performClick(); // Clear filters
        idle();
        assertEquals(5, shownRefs(a).size());
        assertEquals("Filters", text(a, R.id.btnFilters));
        assertEquals(View.GONE, a.findViewById(R.id.rowActiveFilters).getVisibility());
        assertEquals("", ((TextView) a.findViewById(R.id.etSearch)).getText().toString());
    }

    @Test
    public void removingAnActiveChip_dropsOnlyThatFilter_andResetInTheSheetClearsTheStagedChoices() {
        TransactionHistoryActivity a = launchWithEverything().get();
        pickInSheet(a, R.id.sheetGroupPayment, "PAID");
        pickInSheet(a, R.id.sheetGroupRoom, "Deluxe");
        assertEquals(refs("Booking #3"), shownRefs(a));

        ((Chip) ((ChipGroup) a.findViewById(R.id.chipGroupActive)).getChildAt(0)).performClick(); // remove "Paid"
        idle();
        assertEquals("only the room filter is left", refs("Booking #3", "Reservation #1"), shownRefs(a));
        assertEquals("Filters · 1", text(a, R.id.btnFilters));

        a.findViewById(R.id.btnFilters).performClick(); // Reset, then Apply
        idle();
        Dialog sheet = ShadowDialog.getLatestDialog();
        sheet.findViewById(R.id.btnSheetReset).performClick();
        sheet.findViewById(R.id.btnSheetApply).performClick();
        idle();
        assertEquals(5, shownRefs(a).size());
        assertEquals("Filters", text(a, R.id.btnFilters));

        pickInSheet(a, R.id.sheetGroupPayment, "PAID");
        a.findViewById(R.id.btnClearAllFilters).performClick();
        idle();
        assertEquals("Clear all", 5, shownRefs(a).size());
    }

    // ---- Export, details round trip, pagination --------------------------------------------------------------

    @Test
    public void export_usesExactlyTheFilteredAndSearchedRows() throws Exception {
        TransactionHistoryActivity a = launchWithEverything().get();
        pickInSheet(a, R.id.sheetGroupPayment, "PAID");
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
        pickInSheet(a, R.id.sheetGroupPayment, "PAID");
        chooseType(a, R.id.chipTypeBookings);
        assertEquals(refs("Booking #2", "Booking #3"), shownRefs(a));

        RecyclerView rv = a.findViewById(R.id.rvTransactions);
        rv.findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.cardTransaction).performClick();
        assertNotNull("the details screen was opened", shadowOf(a).getNextStartedActivity());
        controller.pause().stop();
        controller.restart().start().resume(); // coming back
        idle();

        assertEquals("the same filters, the same rows", refs("Booking #2", "Booking #3"), shownRefs(a));
        assertEquals("Filters · 1", text(a, R.id.btnFilters));
        assertEquals(R.id.chipTypeBookings, ((ChipGroup) a.findViewById(R.id.chipGroupType)).getCheckedChipId());
    }

    @Test
    public void whileAFilterIsActive_everyPageIsLoaded_notJustTheFirst() {
        ActivityController<TransactionHistoryActivity> controller = Robolectric.buildActivity(TransactionHistoryActivity.class,
                new Intent(app, TransactionHistoryActivity.class));
        controller.setup();
        idle();
        TransactionHistoryActivity a = controller.get();
        // the server says there are 450 reservations: more than the 200 per page asked for
        com.example.velocitysuites.network.dto.PaginatedResponse<ReservationDto> first = ScreenTestSupport.page(ScreenTestSupport.reservations(ScreenTestSupport.reservation(1, 1800)));
        first.total = 450;
        api.call("getReservations", 0).succeed(first);
        api.call("getDirectBookings", 0).succeed(ScreenTestSupport.page(new ArrayList<DirectBookingResponseDto>()));
        idle();
        assertEquals("no filter: nothing extra is loaded", 1, api.count("getReservations"));

        ((TextView) a.findViewById(R.id.etSearch)).setText("reservation");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals("searching asks for the next page", 2, api.count("getReservations"));

        com.example.velocitysuites.network.dto.PaginatedResponse<ReservationDto> second = ScreenTestSupport.page(ScreenTestSupport.reservations(
                ScreenTestSupport.reservation(1, 1800), ScreenTestSupport.reservation(2, 1800)));
        second.total = 450;
        api.call("getReservations", 1).succeed(second);
        api.call("getDirectBookings", 1).succeed(ScreenTestSupport.page(new ArrayList<DirectBookingResponseDto>()));
        idle();
        assertEquals("...and keeps going while the server reports more", 3, api.count("getReservations"));

        com.example.velocitysuites.network.dto.PaginatedResponse<ReservationDto> third = ScreenTestSupport.page(ScreenTestSupport.reservations(
                ScreenTestSupport.reservation(1, 1800), ScreenTestSupport.reservation(2, 1800), ScreenTestSupport.reservation(3, 1800)));
        third.total = 450; // 450 <= 600 now: nothing more to load
        api.call("getReservations", 2).succeed(third);
        api.call("getDirectBookings", 2).succeed(ScreenTestSupport.page(new ArrayList<DirectBookingResponseDto>()));
        idle();
        assertEquals("then it stops", 3, api.count("getReservations"));
        assertEquals("every loaded page is searched", 3, shownRefs(a).size());
        assertEquals("3 results", text(a, R.id.tvResultCount));
    }

    // ---- layout at 360dp, light and dark ---------------------------------------------------------------------

    private void assertFilterRowFits(TransactionHistoryActivity a) {
        View root = a.getWindow().getDecorView();
        int w = app.getResources().getDisplayMetrics().widthPixels;
        int h = app.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        View chips = (View) a.findViewById(R.id.chipGroupType).getParent();
        View filters = a.findViewById(R.id.btnFilters);
        View search = a.findViewById(R.id.searchLayout);
        Rect chipsBounds = LayoutHarness.boundsIn(root, chips);
        Rect filtersBounds = LayoutHarness.boundsIn(root, filters);
        assertFalse("the Filters button does not overlap the chips", Rect.intersects(chipsBounds, filtersBounds));
        assertTrue("the Filters button is fully on screen", filtersBounds.right <= w && filtersBounds.left >= 0);
        assertTrue("48dp tall", filters.getHeight() >= LayoutHarness.dp(app, 48));
        assertFalse("the search box does not overlap the chip row", Rect.intersects(LayoutHarness.boundsIn(root, search), chipsBounds));

        View count = a.findViewById(R.id.tvResultCount);
        View export = a.findViewById(R.id.btnExport);
        assertFalse("the count does not overlap Export", Rect.intersects(LayoutHarness.boundsIn(root, count), LayoutHarness.boundsIn(root, export)));
        assertTrue("Export is fully on screen", LayoutHarness.boundsIn(root, export).right <= w);
    }

    @Test
    public void filterRow_fitsAt360dp_inLightMode() {
        TransactionHistoryActivity a = launchWithEverything().get();
        assertFilterRowFits(a);
    }

    @Test
    @Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xxhdpi", application = TestApplication.class)
    public void filterRow_fitsAt360dp_inDarkMode() {
        TransactionHistoryActivity a = launchWithEverything().get();
        assertFilterRowFits(a);
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
