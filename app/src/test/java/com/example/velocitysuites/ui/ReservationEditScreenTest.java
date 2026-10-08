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
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.AddOnAmenity;
import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingAmenity;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.BookingRoom;
import com.example.velocitysuites.BookingWizardActivity;
import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.EditTotals;
import com.example.velocitysuites.R;
import com.example.velocitysuites.ReservationEditPolicy;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomAmenity;
import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.network.dto.DiscountDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.example.velocitysuites.network.dto.TimelineStepDto;
import com.example.velocitysuites.ui.ScreenTestSupport.FakeApi;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Booking Details / Reservation Details and the Edit Reservation flow, rendered by the real Activities: the Edit
 * action appears only while a reservation can still change, the timeline shows the server's steps with their
 * Verified/Pending status and time (and updates on pull-to-refresh), and an edit opens pre-filled with everything
 * the guest had selected - dates, rooms, guests, add-ons, discount and the ID already on file - keeping all of it
 * as they move through the steps.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReservationEditScreenTest {

    private Context app;
    private FakeApi api;
    private RoomRepository repository;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new FakeApi();
        repository = ScreenTestSupport.freshRepository(app, api);
    }

    // ---- fixtures ----

    private static Room room(String id, String name, int capacity, double price) {
        return new Room(id, name, name, capacity, price, "desc", 0, true, Collections.<RoomAmenity>emptyList(), "Queen", "24 sqm", "No smoking");
    }

    private static String day(int offset) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, offset);
        return new java.text.SimpleDateFormat("MMM dd, yyyy", Locale.US).format(c.getTime());
    }

    /** A pending 2-night reservation: 1 Deluxe (1,000/night) + 1 Suite (2,000/night), 2 adults + 1 child, a VIP discount with its ID on file. */
    private Booking pendingReservation() {
        Booking b = new Booking("77", "1", "Deluxe", "Deluxe", day(1), day(3), 3, 6000.0, "Pending", day(0));
        b.setTotalIncludesAmenities(true);
        b.setAmountPaid(2500.0);
        b.setRoomTypeId("1");
        b.setRoomsRequested(2);
        b.setAdults(2);
        b.setChildren(1);
        b.setGuestFirstName("Ana");
        b.setGuestLastName("Cruz");
        b.setPaymentMethod("CASH");
        b.setIdCardType("VIP");
        b.setDiscountId("7");
        b.setHasIdCard(true);
        b.setRooms(Arrays.asList(
                new BookingRoom("1", "Deluxe", 1, 1000.0, 2, 2000.0, null),
                new BookingRoom("2", "Suite", 1, 2000.0, 2, 4000.0, null)));
        b.setAmenities(Collections.singletonList(new BookingAmenity("9", "Breakfast", 2, 150.0, 300.0)));
        b.setAdditionalGuests(Collections.singletonList(new Booking.AdditionalGuest("Child 1", 5, null, "Child")));
        return b;
    }

    private void cacheRooms() {
        try {
            java.lang.reflect.Method m = RoomRepository.class.getDeclaredMethod("setRoomsForTesting", List.class);
            m.setAccessible(true);
            m.invoke(repository, Arrays.asList(room("1", "Deluxe", 2, 1000.0), room("2", "Suite", 4, 2000.0)));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private BookingDetailsActivity openDetails(Booking b) {
        Intent intent = new Intent(app, BookingDetailsActivity.class).putExtra(BookingDetailsActivity.EXTRA_BOOKING, b);
        ActivityController<BookingDetailsActivity> controller = Robolectric.buildActivity(BookingDetailsActivity.class, intent);
        controller.setup();
        idle();
        return controller.get();
    }

    private static String allText(View v) {
        StringBuilder sb = new StringBuilder();
        collect(v, sb);
        return sb.toString();
    }

    private static void collect(View v, StringBuilder out) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof TextView) out.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    private static TimelineStepDto step(String key, String label, String status, String at) {
        TimelineStepDto s = new TimelineStepDto();
        s.key = key;
        s.label = label;
        s.status = status;
        s.at = at;
        return s;
    }

    // ---- Edit Reservation visibility ----

    @Test
    public void editReservation_isOfferedOnlyWhileTheReservationCanStillChange() {
        assertTrue(ReservationEditPolicy.canEdit(pendingReservation(), false));

        Booking edited = pendingReservation();
        edited.setEditedOnce(true);
        Booking converted = pendingReservation();
        converted.setHasBooking(true);
        Booking checkedIn = pendingReservation();
        checkedIn.setStatus("Checked-In");
        Booking completed = pendingReservation();
        completed.setStatus("Completed");
        Booking cancelled = pendingReservation();
        cancelled.setStatus("Cancelled");
        Booking direct = pendingReservation();
        direct.setDirectBooking(true);

        for (Booking b : Arrays.asList(edited, converted, checkedIn, completed, cancelled, direct)) {
            assertFalse(b.getStatus() + " edited=" + b.isEditedOnce(), ReservationEditPolicy.canEdit(b, false));
        }
        assertFalse("just edited on this device", ReservationEditPolicy.canEdit(pendingReservation(), true));

        // ...and the real Reservation Details screen follows the same rule
        for (Booking b : Arrays.asList(edited, converted, checkedIn, completed, cancelled, direct)) {
            assertEquals(View.GONE, openDetails(b).findViewById(R.id.btnEditReservation).getVisibility());
        }
        assertEquals(View.VISIBLE, openDetails(pendingReservation()).findViewById(R.id.btnEditReservation).getVisibility());
    }

    // ---- timeline ----

    @Test
    public void timeline_showsServerStepsWithStatusAndDateTime() {
        Booking b = pendingReservation();
        b.setTimeline(Arrays.asList(
                new Booking.TimelineEntry("created", "Reservation created", "Recorded", "Oct 8, 2026 • 9:00 AM"),
                new Booking.TimelineEntry("reservation_modified", "Reservation modified by guest", "Recorded", "Oct 8, 2026 • 10:30 AM"),
                new Booking.TimelineEntry("payment_1", "Payment verified", "Verified", "Oct 8, 2026 • 11:00 AM"),
                new Booking.TimelineEntry("confirmed", "Reservation confirmation", "Pending", "")));
        BookingDetailsActivity activity = openDetails(b);
        String timeline = allText(activity.findViewById(R.id.sectionTimelineContent));
        assertTrue(timeline, timeline.contains("Reservation modified by guest"));
        assertTrue(timeline, timeline.contains("Oct 8, 2026 • 10:30 AM"));
        assertTrue(timeline, timeline.contains("Verified • Oct 8, 2026 • 11:00 AM"));
        assertTrue(timeline, timeline.contains("Reservation confirmation"));
        assertTrue(timeline, timeline.contains("Pending"));
    }

    @Test
    public void timeline_ofACompletedStayShowsEveryStepVerified() {
        Booking b = pendingReservation();
        b.setStatus("Completed");
        b.setHasBooking(true);
        b.setTimeline(Arrays.asList(
                new Booking.TimelineEntry("created", "Reservation created", "Verified", "Oct 1, 2026 • 9:00 AM"),
                new Booking.TimelineEntry("payment_1", "Payment verified", "Verified", "Oct 1, 2026 • 11:00 AM"),
                new Booking.TimelineEntry("checked_in", "Checked in", "Verified", "Oct 2, 2026 • 2:00 PM"),
                new Booking.TimelineEntry("checked_out", "Checked out", "Verified", "Oct 3, 2026 • 11:00 AM"),
                new Booking.TimelineEntry("completed", "Completed", "Verified", "Oct 3, 2026 • 11:30 AM")));
        String timeline = allText(openDetails(b).findViewById(R.id.sectionTimelineContent));
        for (String label : Arrays.asList("Reservation created", "Payment verified", "Checked in", "Checked out", "Completed")) {
            assertTrue(label, timeline.contains(label));
        }
        assertFalse(timeline, timeline.contains("Pending"));
        assertEquals(5, timeline.split("Verified • ").length - 1);
    }

    @Test
    public void pullToRefresh_reloadsTheTransactionAndShowsWhatTheReceptionistVerified() {
        Booking b = pendingReservation();
        b.setTimeline(Collections.singletonList(new Booking.TimelineEntry("payment_1", "Payment awaiting verification", "Pending", "")));
        BookingDetailsActivity activity = openDetails(b);
        assertEquals("opens with one silent refresh", 1, api.count("getReservation"));
        assertTrue(allText(activity.findViewById(R.id.sectionTimelineContent)).contains("Payment awaiting verification"));

        SwipeRefreshLayout swipe = activity.findViewById(R.id.swipeRefreshDetails);
        assertNotNull(swipe);
        shadowOf(Looper.getMainLooper()).idle();
        // the silent refresh on open answers first
        answer(activity, "Payment awaiting verification", "Pending", null);
        assertEquals(1, api.count("getReservation"));

        // pull-to-refresh: a second request, answered with the receptionist's verification
        activity.runOnUiThread(() -> swipe.setRefreshing(true));
        triggerRefresh(swipe);
        idle();
        assertEquals(2, api.count("getReservation"));
        answer(activity, "Payment verified", "Verified", "2026-10-08T03:00:00Z");
        String timeline = allText(activity.findViewById(R.id.sectionTimelineContent));
        assertTrue(timeline, timeline.contains("Payment verified"));
        assertTrue(timeline, timeline.contains("Verified"));
        assertFalse(timeline, timeline.contains("awaiting verification"));
        assertFalse("spinner stops", swipe.isRefreshing());
    }

    private void triggerRefresh(SwipeRefreshLayout swipe) {
        try {
            java.lang.reflect.Field f = SwipeRefreshLayout.class.getDeclaredField("mListener");
            f.setAccessible(true);
            ((SwipeRefreshLayout.OnRefreshListener) f.get(swipe)).onRefresh();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void answer(BookingDetailsActivity activity, String label, String status, String at) {
        ReservationDto dto = ScreenTestSupport.reservation(77, 6000.0);
        dto.total_amount_due = 6000.0;
        dto.timeline = new ArrayList<>(Collections.singletonList(step("payment_1", label, status, at)));
        api.<ReservationDto>last("getReservation").succeed(dto);
        idle();
    }

    // ---- the edit wizard ----

    private BookingWizardActivity openEdit(Booking b) {
        ActivityController<BookingWizardActivity> controller =
                Robolectric.buildActivity(BookingWizardActivity.class, BookingWizardActivity.newEditIntent(app, b));
        controller.setup();
        idle();
        return controller.get();
    }

    private static Fragment fragment(BookingWizardActivity activity) {
        return activity.getSupportFragmentManager().findFragmentById(R.id.wizardFragmentContainer);
    }

    @Test
    public void edit_opensPrefilledWithEverythingTheGuestHadSelected() {
        cacheRooms();
        BookingWizardActivity activity = openEdit(pendingReservation());
        BookingWizardState s = activity.getState();

        assertTrue(activity.isEditMode());
        assertEquals("77", activity.getEditingReservationId());
        assertEquals(day(1), new java.text.SimpleDateFormat("MMM dd, yyyy", Locale.US).format(s.checkIn.getTime()));
        assertEquals(day(3), new java.text.SimpleDateFormat("MMM dd, yyyy", Locale.US).format(s.checkOut.getTime()));
        assertEquals(2, s.selectedRooms.size());
        assertEquals(2, s.adults);
        assertEquals(1, s.children);
        assertEquals(1, s.additionalGuests.size());
        assertEquals(5, s.additionalGuests.get(0).age);
        assertEquals(1, s.selectedAmenities.size());
        assertEquals("9", s.selectedAmenities.get(0).getId());
        assertEquals(2, s.selectedAmenities.get(0).getQuantity());
        assertNotNull(s.discount);
        assertEquals("VIP", s.discount.getName());
        assertEquals(Long.valueOf(7), s.discountIdOrNull());
        assertTrue("the uploaded ID is on file", s.idCardOnFile);
        assertFalse(s.removeIdCard);
        assertEquals("cash", s.paymentMethod);
        assertEquals("Ana", s.guestFirstName);
        assertEquals(6000.0, s.editOldTotal, 0.001);
        assertEquals(2500.0, s.editAmountPaid, 0.001);
    }

    @Test
    public void edit_keepsEveryUnchangedFieldWhileMovingThroughAllSteps() {
        cacheRooms();
        BookingWizardActivity activity = openEdit(pendingReservation());
        BookingWizardState s = activity.getState();
        String before = snapshot(s);

        for (int step = 1; step <= 7; step++) {
            activity.goToStep(step);
            idle();
            if (step == 6) {
                // the discount list loads; the guest's own discount is bound to it and the ID stays on file
                api.<List<DiscountDto>>last("getDiscounts").succeed(Arrays.asList(
                        discount(3, "Senior Citizen"), discount(7, "VIP")));
                idle();
                assertTrue(allText(fragment(activity).getView()).contains("ID is on file"));
            }
        }
        for (int step = 6; step >= 1; step--) {
            activity.goToStep(step);
            idle();
        }
        assertEquals("nothing was cleared, reset or re-defaulted", before, snapshot(s));
    }

    private static DiscountDto discount(long id, String name) {
        DiscountDto d = new DiscountDto();
        d.id = id;
        d.name = name;
        d.discount_type = "percentage";
        d.value = "10.00";
        d.description = name + " discount";
        d.status = "active";
        return d;
    }

    private static String snapshot(BookingWizardState s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.checkIn.getTimeInMillis()).append('|').append(s.checkOut.getTimeInMillis()).append('|');
        for (Room r : s.selectedRooms) sb.append(r.getId()).append(',');
        sb.append('|').append(s.adults).append('/').append(s.children).append('|');
        for (AddOnAmenity a : s.selectedAmenities) sb.append(a.getId()).append('x').append(a.getQuantity()).append(',');
        sb.append('|').append(s.discountIdOrNull()).append('|').append(s.idCardType).append('|').append(s.idCardOnFile).append(s.removeIdCard);
        sb.append('|').append(s.guestFirstName).append(s.guestLastName).append('|').append(s.paymentMethod);
        for (com.example.velocitysuites.BookingAndReservationActivity.AdditionalGuest g : s.additionalGuests) sb.append('|').append(g.name).append(g.age);
        return sb.toString();
    }

    @Test
    public void edit_reviewStepShowsOldNewPaidAndBalanceOrExcess() {
        cacheRooms();
        BookingWizardActivity activity = openEdit(pendingReservation());
        BookingWizardState s = activity.getState();
        activity.goToStep(7);
        idle();
        LinearLayout panel = fragment(activity).getView().findViewById(R.id.layoutEditTotals);
        assertEquals(View.VISIBLE, panel.getVisibility());
        // unchanged selection: 2 nights x (1,000 + 2,000) + 2 x 150 amenities = 6,300? -> the wizard prices rooms + amenities
        EditTotals totals = s.editTotals();
        String text = allText(panel);
        assertTrue(text, text.contains("Previous total"));
        assertTrue(text, text.contains("New total"));
        assertTrue(text, text.contains("Already paid"));
        assertTrue(text, text.contains(String.format(Locale.US, "₱%,.2f", 2500.0)));
        assertTrue(text, text.contains(totals.balanceDue > 0 ? "Balance due" : "Excess paid"));

        // dropping the Suite lowers the new total below what was paid -> an excess is shown
        s.selectedRooms.remove(1);
        s.selectedAmenities.clear();
        activity.goToStep(7);
        idle();
        EditTotals lower = s.editTotals();
        assertEquals(2000.0, lower.newTotal, 0.001);
        assertEquals(500.0, lower.excess, 0.001);
        String lowerText = allText(fragment(activity).getView().findViewById(R.id.layoutEditTotals));
        assertTrue(lowerText, lowerText.contains("Excess paid"));
        assertTrue(lowerText, lowerText.contains(String.format(Locale.US, "₱%,.2f", 500.0)));
    }

    @Test
    public void edit_withAColdRoomCache_loadsRoomsFirstInsteadOfDroppingThem() {
        // no cacheRooms(): the catalog isn't in memory yet
        ActivityController<BookingWizardActivity> controller =
                Robolectric.buildActivity(BookingWizardActivity.class, BookingWizardActivity.newEditIntent(app, pendingReservation()));
        controller.setup();
        idle();
        BookingWizardActivity activity = controller.get();
        assertEquals("the wizard waits for the catalog", View.VISIBLE, activity.findViewById(R.id.wizardLoading).getVisibility());
        assertTrue(activity.getState().selectedRooms.isEmpty());
        assertEquals(1, api.count("getRooms"));
    }
}
