package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

/**
 * Opens every screen the Booking / Reservation work changed, on a real device or emulator (the JVM Robolectric
 * twins live in src/test/.../ui): Steps 1, 5 and 6 of both wizards, Booking Details, Reservation Details and the
 * Edit Reservation wizard. Plain unit tests never inflate these layouts, which is how a layout crash can ship with
 * a green suite - launching the Activity re-throws anything its lifecycle raises, so a crash fails the test.
 *
 * Uses test data only - no account is signed in, so any network call simply fails into the screens' own
 * error / empty states (which is itself part of what is being exercised).
 */
@RunWith(AndroidJUnit4.class)
public class ChangedScreensInstrumentedTest {

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static Room testRoom(String id, int capacity) {
        return new Room(id, "Test Room " + id, "Deluxe", capacity, 1500.0, "Test data", 0, true,
                Collections.<RoomAmenity>emptyList(), "Queen", "24 sqm", "No smoking");
    }

    private static void openStep(BookingWizardState.Mode mode, int step) {
        try (ActivityScenario<BookingWizardActivity> scenario = ActivityScenario.launch(BookingWizardActivity.newIntent(context(), mode))) {
            scenario.onActivity(activity -> {
                activity.getState().selectedRooms.add(testRoom("1", 4));
                activity.goToStep(step);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                Fragment shown = activity.getSupportFragmentManager().findFragmentById(R.id.wizardFragmentContainer);
                assertNotNull(shown);
                assertNotNull(shown.getView());
                assertEquals(View.VISIBLE, shown.getView().getVisibility());
            });
        }
    }

    @Test
    public void booking_step1Dates_opens() {
        openStep(BookingWizardState.Mode.BOOKING, 1);
    }

    @Test
    public void booking_step5Guests_opens() {
        openStep(BookingWizardState.Mode.BOOKING, 5);
    }

    @Test
    public void booking_step6Discounts_opens() {
        openStep(BookingWizardState.Mode.BOOKING, 6);
    }

    @Test
    public void reservation_step1Dates_opens() {
        openStep(BookingWizardState.Mode.RESERVATION, 1);
    }

    @Test
    public void reservation_step5Guests_opens() {
        openStep(BookingWizardState.Mode.RESERVATION, 5);
    }

    @Test
    public void reservation_step6Discounts_opens() {
        openStep(BookingWizardState.Mode.RESERVATION, 6);
    }

    private static Booking testReservation(String status, boolean hasBooking) {
        Booking b = new Booking("9001", "1", "Test Room 1", "Deluxe", "Oct 09, 2026", "Oct 11, 2026", 2, 3000.0, status, "Oct 08, 2026");
        b.setTotalIncludesAmenities(true);
        b.setHasBooking(hasBooking);
        b.setRoomTypeId("1");
        b.setRoomsRequested(1);
        b.setAdults(2);
        b.setGuestFirstName("Test");
        b.setGuestLastName("Guest");
        b.setRooms(Collections.singletonList(new BookingRoom("1", "Deluxe", 1, 1500.0, 2, 3000.0, null)));
        b.setTimeline(Arrays.asList(
                new Booking.TimelineEntry("created", "Reservation created", "Recorded", "Oct 8, 2026 • 9:00 AM"),
                new Booking.TimelineEntry("confirmed", "Reservation confirmation", "Pending", "")));
        return b;
    }

    @Test
    public void bookingDetails_opens() {
        Intent intent = new Intent(context(), BookingDetailsActivity.class)
                .putExtra(BookingDetailsActivity.EXTRA_BOOKING, testReservation("Confirmed", true));
        try (ActivityScenario<BookingDetailsActivity> scenario = ActivityScenario.launch(intent)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> assertEquals(View.GONE, activity.findViewById(R.id.btnEditReservation).getVisibility()));
        }
    }

    @Test
    public void reservationDetails_opensWithEditReservationOffered() {
        Intent intent = new Intent(context(), BookingDetailsActivity.class)
                .putExtra(BookingDetailsActivity.EXTRA_BOOKING, testReservation("Pending", false));
        try (ActivityScenario<BookingDetailsActivity> scenario = ActivityScenario.launch(intent)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> assertEquals(View.VISIBLE, activity.findViewById(R.id.btnEditReservation).getVisibility()));
        }
    }

    @Test
    public void editReservation_opensPrefilled() {
        Intent intent = BookingWizardActivity.newEditIntent(context(), testReservation("Pending", false));
        try (ActivityScenario<BookingWizardActivity> scenario = ActivityScenario.launch(intent)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertEquals(true, activity.isEditMode());
                assertEquals("9001", activity.getEditingReservationId());
                assertEquals(2, activity.getState().adults);
            });
        }
    }
}
