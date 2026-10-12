package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingAndReservationActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The status chips are the only filter on Bookings & Reservations: they filter each tab on its own, and the checked
 * chip always matches the tab that is open.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BookingStatusChipsTest {

    private BookingAndReservationActivity activity;

    @Before
    public void setUp() {
        Context app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.FakeApi api = new ScreenTestSupport.FakeApi();
        RoomRepository repository = ScreenTestSupport.freshRepository(app, api);
        activity = Robolectric.buildActivity(BookingAndReservationActivity.class,
                new Intent(app, BookingAndReservationActivity.class)).setup().get();
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
        if (rv.getVisibility() != android.view.View.VISIBLE || rv.getAdapter() == null) return 0;
        return rv.getAdapter().getItemCount();
    }

    private void tap(int chipId) {
        activity.findViewById(chipId).performClick();
        idle();
    }

    private int checkedChip() {
        return ((ChipGroup) activity.findViewById(R.id.cgStatusFilter)).getCheckedChipId();
    }

    private void openTab(int position) {
        TabLayout tabs = activity.findViewById(R.id.tabLayout);
        tabs.getTabAt(position).select();
        idle();
    }

    @Test
    public void bookingTab_chipsFilterTheList() {
        assertEquals("All is the default", R.id.chipStatusAll, checkedChip());
        assertEquals(2, shown());

        tap(R.id.chipStatusCancelled);
        assertEquals(1, shown());
        tap(R.id.chipStatusActive);
        assertEquals(1, shown());
        tap(R.id.chipStatusCompleted);
        assertEquals(0, shown());
        tap(R.id.chipStatusAll);
        assertEquals(2, shown());
    }

    @Test
    public void reservationTab_chipsFilterTheList_onItsOwn() {
        openTab(1);
        assertEquals(R.id.chipStatusAll, checkedChip());
        assertEquals(2, shown());

        tap(R.id.chipStatusCancelled);
        assertEquals(1, shown());
        tap(R.id.chipStatusActive);
        assertEquals(1, shown());
        tap(R.id.chipStatusCompleted);
        assertEquals(0, shown());
    }

    @Test
    public void eachTabKeepsItsOwnChip_whenSwitchingBackAndForth() {
        tap(R.id.chipStatusCancelled);          // Bookings: Cancelled
        openTab(1);
        assertEquals("Reservations still show All", R.id.chipStatusAll, checkedChip());
        tap(R.id.chipStatusCompleted);          // Reservations: Completed

        openTab(0);
        assertEquals("Bookings remember Cancelled", R.id.chipStatusCancelled, checkedChip());
        assertEquals(1, shown());
        openTab(1);
        assertEquals("Reservations remember Completed", R.id.chipStatusCompleted, checkedChip());
        assertEquals(0, shown());
    }
}
