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
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingAndReservationActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.google.android.material.tabs.TabLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The Status dropdown (All / Active / Completed / Cancelled-rejected) filters each tab on its own, and the field always shows
 * the choice of the tab that is open. Search, Sort and Room type are remembered per tab too.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BookingStatusChipsTest {

    private BookingAndReservationActivity activity;
    private org.robolectric.android.controller.ActivityController<BookingAndReservationActivity> controller;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        Context app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        RoomRepository repository = ScreenTestSupport.freshRepository(app, api);
        controller = Robolectric.buildActivity(BookingAndReservationActivity.class, new Intent(app, BookingAndReservationActivity.class)).setup();
        activity = controller.get();
        idle();

        ReservationDto awaiting = ScreenTestSupport.reservation(1, 1800);
        ReservationDto cancelled = ScreenTestSupport.reservation(2, 1800);
        cancelled.status = "CANCELLED_RESERVATION";
        DirectBookingResponseDto active = ScreenTestSupport.directBooking(11, 3000);
        DirectBookingResponseDto cancelledBooking = ScreenTestSupport.directBooking(12, 3000);
        cancelledBooking.booking_status = "CANCELLED_BOOKING";
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1,
                ScreenTestSupport.reservations(awaiting, cancelled),
                ScreenTestSupport.directBookings(active, cancelledBooking));
        idle();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private int shown() {
        RecyclerView rv = activity.findViewById(R.id.rvItemList);
        // An empty result hides the list and shows the empty state instead.
        if (rv.getVisibility() != View.VISIBLE || rv.getAdapter() == null) return 0;
        return rv.getAdapter().getItemCount();
    }

    private static final int ALL = 0, ACTIVE = 1, COMPLETED = 2, CANCELLED = 3;

    private void pick(int id, int position) {
        AutoCompleteTextView dd = activity.findViewById(id);
        dd.getOnItemClickListener().onItemClick(null, null, position, position);
        idle();
    }

    private void status(int position) {
        pick(R.id.dropdownTransactionStatus, position);
    }

    private String statusText() {
        return ((TextView) activity.findViewById(R.id.dropdownTransactionStatus)).getText().toString();
    }

    private void openTab(int position) {
        TabLayout tabs = activity.findViewById(R.id.tabLayout);
        tabs.getTabAt(position).select();
        idle();
    }

    private void search(String q) {
        ((TextView) activity.findViewById(R.id.etSearchTransactions)).setText(q);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1)); // the search waits for a pause in typing
    }

    @Test
    public void bookingTab_statusDropdownFiltersTheList_withShortValuesInTheField() {
        assertEquals("All is the default", "All", statusText());
        assertEquals(2, shown());

        status(CANCELLED);
        assertEquals("Cancelled / rejected", statusText());
        assertEquals(1, shown());
        status(ACTIVE);
        assertEquals("Active", statusText());
        assertEquals(1, shown());
        status(COMPLETED);
        assertEquals(0, shown());
        status(ALL);
        assertEquals(2, shown());
    }

    @Test
    public void reservationTab_statusDropdownFiltersTheList_onItsOwn() {
        openTab(1);
        assertEquals("All", statusText());
        assertEquals(2, shown());

        status(CANCELLED);
        assertEquals(1, shown());
        status(ACTIVE);
        assertEquals(1, shown());
        status(COMPLETED);
        assertEquals(0, shown());
    }

    @Test
    public void eachTabKeepsItsOwnStatus_whenSwitchingBackAndForth() {
        status(CANCELLED);          // Bookings: Cancelled
        openTab(1);
        assertEquals("Reservations still show All", "All", statusText());
        status(COMPLETED);          // Reservations: Completed

        openTab(0);
        assertEquals("Bookings remember Cancelled", "Cancelled / rejected", statusText());
        assertEquals(1, shown());
        openTab(1);
        assertEquals("Reservations remember Completed", "Completed", statusText());
        assertEquals(0, shown());
    }

    @Test
    public void theStatusOptionsNameTheTab() {
        AutoCompleteTextView dd = activity.findViewById(R.id.dropdownTransactionStatus);
        assertEquals("All bookings", dd.getAdapter().getItem(0));
        openTab(1);
        dd = activity.findViewById(R.id.dropdownTransactionStatus);
        assertEquals("All reservations", dd.getAdapter().getItem(0));
        assertEquals(4, dd.getAdapter().getCount());
    }

    @Test
    public void sortNewestAndOldestFirst_byTheDateItWasMade_andRoomTypeFromTheSharedSource() {
        RecyclerView rv = activity.findViewById(R.id.rvItemList);
        String newestFirst = idOfFirst(rv);
        pick(R.id.dropdownTransactionSort, 1);
        String oldestFirst = idOfFirst(rv);
        assertFalse("the order flips", newestFirst.equals(oldestFirst));
        pick(R.id.dropdownTransactionSort, 0);
        assertEquals(newestFirst, idOfFirst(rv));
        assertEquals("Newest first", ((TextView) activity.findViewById(R.id.dropdownTransactionSort)).getText().toString());

        AutoCompleteTextView room = activity.findViewById(R.id.dropdownTransactionRoomType);
        assertEquals("All", room.getText().toString());
        assertTrue("All plus a type", room.getAdapter().getCount() >= 2);
        int suiteIndex = 0;
        for (int i = 0; i < room.getAdapter().getCount(); i++) if ("Suite".equals(room.getAdapter().getItem(i))) suiteIndex = i;
        assertTrue("Suite is one of the room types", suiteIndex > 0);
        pick(R.id.dropdownTransactionRoomType, suiteIndex);
        assertEquals("Suite", room.getText().toString());
        assertEquals("the two Suite bookings", 2, shown());
    }

    private String idOfFirst(RecyclerView rv) {
        shadowOf(Looper.getMainLooper()).idle();
        RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(0);
        assertNotNull(h);
        return ((TextView) h.itemView.findViewById(R.id.tvBookingId)).getText().toString();
    }

    @Test
    public void searchCombinesWithTheStatus_clearFiltersResets_andTheEmptyStateOffersIt() {
        search("Suite");
        assertEquals(2, shown());
        status(CANCELLED);
        assertEquals(1, shown());
        search("zzzz");
        assertEquals(0, shown());
        assertEquals("No bookings match your filters", ((TextView) activity.findViewById(R.id.tvListEmptyTitle)).getText().toString());
        TextView clear = activity.findViewById(R.id.btnListRetry);
        assertEquals(View.VISIBLE, clear.getVisibility());
        assertEquals("Clear filters", clear.getText().toString());
        clear.performClick();
        idle();
        assertEquals(2, shown());
        assertEquals("All", statusText());
        assertEquals("", ((TextView) activity.findViewById(R.id.etSearchTransactions)).getText().toString());
        assertEquals(View.GONE, activity.findViewById(R.id.btnClearListFilters).getVisibility());
    }

    @Test
    public void searchAndSort_arePerTab_andTheCountsAreShown() {
        search("Suite");
        openTab(1);
        assertEquals("the Reservations tab has its own (empty) search", "", ((TextView) activity.findViewById(R.id.etSearchTransactions)).getText().toString());
        assertEquals("2 results", ((TextView) activity.findViewById(R.id.tvListCount)).getText().toString());
        assertEquals("Reservations", ((TextView) activity.findViewById(R.id.tvListTitle)).getText().toString());
        openTab(0);
        assertEquals("suite", ((TextView) activity.findViewById(R.id.etSearchTransactions)).getText().toString().toLowerCase());
        TabLayout tabs = activity.findViewById(R.id.tabLayout);
        assertEquals("Bookings (2)", tabs.getTabAt(0).getText().toString());
        assertEquals("Reservations (2)", tabs.getTabAt(1).getText().toString());
    }

    @Test
    public void filtersSurviveRotation() {
        status(CANCELLED);
        search("Suite");
        android.os.Bundle saved = new android.os.Bundle();
        controller.saveInstanceState(saved);
        BookingAndReservationActivity second = Robolectric.buildActivity(BookingAndReservationActivity.class,
                new Intent(ApplicationProvider.getApplicationContext(), BookingAndReservationActivity.class)).setup(saved).get();
        idle();
        assertEquals("Cancelled / rejected", ((TextView) second.findViewById(R.id.dropdownTransactionStatus)).getText().toString());
        assertEquals("suite", ((TextView) second.findViewById(R.id.etSearchTransactions)).getText().toString().toLowerCase());
    }
}
