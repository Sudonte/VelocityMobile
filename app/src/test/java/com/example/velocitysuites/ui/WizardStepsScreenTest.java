package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingWizardActivity;
import com.example.velocitysuites.CheckInWindow;
import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.R;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomAmenity;
import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.WizardStepFragment;
import com.example.velocitysuites.network.dto.DiscountDto;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * The real wizard Activity hosting the steps - the dates step and the representative and guests step (3 of a booking, which has Amenities second; 2 of a reservation) of the 4-step Booking and Reservation flows. Each screen must
 * inflate and bind without a layout crash, and behave per spec: check-in window, capacity-limited steppers with an
 * "N of M guests" counter, and a review with the one front-desk discount note.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WizardStepsScreenTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;
    private FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new FakeApi();
        ScreenTestSupport.freshRepository(app, api);
    }

    // ---- helpers ----

    private ActivityController<BookingWizardActivity> launch(BookingWizardState.Mode mode) {
        ActivityController<BookingWizardActivity> controller =
                Robolectric.buildActivity(BookingWizardActivity.class, BookingWizardActivity.newIntent(app, mode));
        controller.setup();
        idle();
        return controller;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static Room room(String id, int capacity) {
        return new Room(id, "Room " + id, "Deluxe", capacity, 2500.0, "desc",
                0, true, Collections.<RoomAmenity>emptyList(), "Queen", "24 sqm", "No smoking");
    }

    private static Fragment fragment(BookingWizardActivity activity) {
        return activity.getSupportFragmentManager().findFragmentById(R.id.wizardFragmentContainer);
    }

    private static <T extends View> T find(BookingWizardActivity activity, int id) {
        View root = fragment(activity).getView();
        assertNotNull("step view", root);
        T v = root.findViewById(id);
        assertNotNull("view " + activity.getResources().getResourceName(id), v);
        return v;
    }

    private static DiscountDto dto(long id, String name, String type, String value, String desc, String status) {
        DiscountDto d = new DiscountDto();
        d.id = id;
        d.name = name;
        d.discount_type = type;
        d.value = value;
        d.description = desc;
        d.status = status;
        d.created_at = "2026-09-26T03:16:05.000000Z";
        return d;
    }

    private static String text(View v) {
        return ((TextView) v).getText().toString();
    }

    // ---- Step 1 (dates) ----

    @Test
    public void step1_opensWithTheEarliestCheckInHelperLine_forBothFlows() {
        for (BookingWizardState.Mode mode : BookingWizardState.Mode.values()) {
            BookingWizardActivity activity = launch(mode).get();
            View checkIn = find(activity, R.id.etCheckIn);
            assertTrue(checkIn.isShown() || checkIn.getVisibility() == View.VISIBLE);
            com.google.android.material.textfield.TextInputLayout til = find(activity, R.id.tilCheckIn);
            assertEquals("Earliest: " + CheckInWindow.earliestShort(), String.valueOf(til.getHelperText()));
        }
    }

    private static Calendar daysFromHotelToday(int days) {
        return CheckInWindow.toDeviceMidnight(CheckInWindow.today().plusDays(days));
    }

    private boolean step1Accepts(BookingWizardActivity activity, int checkInDays, int checkOutDays) {
        BookingWizardState state = activity.getState();
        state.checkIn = daysFromHotelToday(checkInDays);
        state.checkOut = daysFromHotelToday(checkOutDays);
        // the dates part of the merged Dates and rooms step (its own child fragment)
        return ((WizardStepFragment) fragment(activity).getChildFragmentManager().findFragmentByTag("stay_dates")).validateBeforeNext();
    }

    @Test
    public void step1_checkInRule_todayAndTomorrowRefused_todayPlusTwoAndMonthsAheadAccepted_bothFlows() {
        for (BookingWizardState.Mode mode : BookingWizardState.Mode.values()) {
            BookingWizardActivity activity = launch(mode).get();
            assertFalse("today", step1Accepts(activity, 0, 2));
            assertFalse("tomorrow", step1Accepts(activity, 1, 3));
            assertTrue("today+2", step1Accepts(activity, 2, 3));
            assertTrue("months ahead", step1Accepts(activity, 120, 123));
        }
    }

    @Test
    public void step1_checkOutRule_sameDayRefused_checkInPlusOneAccepted() {
        BookingWizardActivity activity = launch(BookingWizardState.Mode.BOOKING).get();
        assertFalse("same day", step1Accepts(activity, 2, 2));
        assertFalse("before check-in", step1Accepts(activity, 5, 4));
        assertTrue("check-in + 1", step1Accepts(activity, 2, 3));
        assertTrue("late check-in, +1", step1Accepts(activity, 7, 8));
        assertFalse("late check-in, same day", step1Accepts(activity, 7, 7));
    }

    // ---- availability is re-read when the guest comes back ----

    private static com.example.velocitysuites.network.dto.RoomsResponse roomsResponse(long id, int available) {
        com.example.velocitysuites.network.dto.RoomTypeDto type = new com.example.velocitysuites.network.dto.RoomTypeDto();
        type.id = id;
        type.name = "Room " + id;
        type.capacity = 2;
        type.rate = "2500.00";
        type.available_count = available;
        com.example.velocitysuites.network.dto.RoomsResponse response = new com.example.velocitysuites.network.dto.RoomsResponse();
        response.room_types = ScreenTestSupport.page(Collections.singletonList(type));
        return response;
    }

    private ActivityController<BookingWizardActivity> wizardAtAmenitiesWithRoom1() {
        ActivityController<BookingWizardActivity> controller = launch(BookingWizardState.Mode.BOOKING);
        BookingWizardActivity activity = controller.get();
        BookingWizardState state = activity.getState();
        state.checkIn = daysFromHotelToday(5);
        state.checkOut = daysFromHotelToday(7);
        state.selectedRooms.add(room("1", 2));
        activity.goToStep(2);
        idle();
        return controller;
    }

    private static void leaveAndComeBack(ActivityController<BookingWizardActivity> controller) {
        controller.pause().stop();
        idle();
        controller.start().resume();
        idle();
    }

    @Test
    public void comingBack_readsAvailabilityFresh_andKeepsAWorkingSelection() {
        ActivityController<BookingWizardActivity> controller = wizardAtAmenitiesWithRoom1();
        leaveAndComeBack(controller);

        assertEquals("uses the no-cache path", 1, api.count("getRoomsFresh"));
        assertEquals("never the cacheable one", 0, api.count("getRooms"));
        api.<com.example.velocitysuites.network.dto.RoomsResponse>last("getRoomsFresh").succeed(roomsResponse(1, 3));
        idle();

        assertEquals("still on the same step", "wizard_step_2", fragment(controller.get()).getTag());
        assertEquals(1, controller.get().getState().selectedRooms.size());
    }

    @Test
    public void comingBack_whenTheRoomIsGone_dropsOnlyThatRoom_andOpensRoomSelection() {
        ActivityController<BookingWizardActivity> controller = wizardAtAmenitiesWithRoom1();
        BookingWizardState state = controller.get().getState();
        state.adults = 2;
        leaveAndComeBack(controller);

        api.<com.example.velocitysuites.network.dto.RoomsResponse>last("getRoomsFresh").succeed(roomsResponse(1, 0));
        idle();

        assertTrue("the gone room is out of the selection", state.selectedRooms.isEmpty());
        assertEquals("Room Selection is open", "wizard_step_1", fragment(controller.get()).getTag());
        assertNotNull("the dates were kept", state.checkIn);
        assertEquals("other entered data was kept", 2, state.adults);
    }

    @Test
    public void comingBack_whenTheCheckFails_saysNothingAndChangesNothing() {
        ActivityController<BookingWizardActivity> controller = wizardAtAmenitiesWithRoom1();
        leaveAndComeBack(controller);

        api.<com.example.velocitysuites.network.dto.RoomsResponse>last("getRoomsFresh").offline();
        idle();

        assertEquals("wizard_step_2", fragment(controller.get()).getTag());
        assertEquals(1, controller.get().getState().selectedRooms.size());
    }

    @Test
    public void afterTheServerRefusedTheBooking_reviewStepSendsTheGuestBackToRooms_keepingTheirData() throws Exception {
        ActivityController<BookingWizardActivity> controller = launch(BookingWizardState.Mode.BOOKING);
        BookingWizardState state = controller.get().getState();
        state.checkIn = daysFromHotelToday(5);
        state.checkOut = daysFromHotelToday(7);
        state.adults = 2;
        state.selectedRooms.add(room("1", 2));
        controller.get().goToStep(4); // Booking mode: the review step
        idle();

        // PaymentActivity records the refusal, finishes, and the review step comes back to the front
        Class<?> payload = Class.forName("com.example.velocitysuites.PendingBookingPayload");
        java.lang.reflect.Method mark = payload.getDeclaredMethod("markRoomsUnavailable", String.class);
        mark.setAccessible(true);
        mark.invoke(null, "Room 1 is fully booked for these dates.");
        controller.pause();
        controller.resume();
        idle();

        assertEquals("the fresh, no-cache read", 1, api.count("getRoomsFresh"));
        api.<com.example.velocitysuites.network.dto.RoomsResponse>last("getRoomsFresh").succeed(roomsResponse(1, 0));
        idle();

        assertEquals("Room Selection is open", "wizard_step_1", fragment(controller.get()).getTag());
        assertTrue("only the gone room was dropped", state.selectedRooms.isEmpty());
        assertEquals("dates and guests kept", 2, state.adults);
        assertNotNull(state.checkIn);
        assertTrue("the server's own sentence is shown",
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast().contains("Room 1 is fully booked for these dates."));
    }

    // ---- Step 2 (the guests, below the representative) ----

    private BookingWizardActivity openStep5(BookingWizardState.Mode mode, int roomCapacity, int roomCount) {
        BookingWizardActivity activity = launch(mode).get();
        for (int i = 0; i < roomCount; i++) activity.getState().selectedRooms.add(room("R" + i, roomCapacity));
        activity.goToStep(activity.repGuestsStep());
        idle();
        return activity;
    }

    @Test
    public void step5_countsGuestsAgainstTotalCapacityAndDisablesPlusAtTheLimit_bothFlows() {
        for (BookingWizardState.Mode mode : BookingWizardState.Mode.values()) {
            BookingWizardActivity activity = openStep5(mode, 3, 2); // 2 rooms x 3 = 6 guests
            View adultsPlus = find(activity, R.id.btnAdultsPlus);
            View childrenPlus = find(activity, R.id.btnChildrenPlus);
            TextView counter = find(activity, R.id.tvGuestCounter);
            assertEquals("1 of 6 guests", text(counter));
            assertTrue(adultsPlus.isEnabled() && childrenPlus.isEnabled());

            // 1 adult + 5 children = 6: no 3-child cap
            for (int i = 0; i < 5; i++) childrenPlus.performClick();
            assertEquals(5, activity.getState().children);
            assertEquals("6 of 6 guests", text(counter));
            assertFalse("adult + disabled at capacity", adultsPlus.isEnabled());
            assertFalse("child + disabled at capacity", childrenPlus.isEnabled());

            // the minus buttons keep working, and re-enable the plus buttons
            find(activity, R.id.btnChildrenMinus).performClick();
            assertEquals("5 of 6 guests", text(counter));
            assertTrue(adultsPlus.isEnabled() && childrenPlus.isEnabled());
        }
    }

    @Test
    public void step5_requiresAtLeastOneAdult() {
        BookingWizardActivity activity = openStep5(BookingWizardState.Mode.BOOKING, 4, 1);
        View adultsMinus = find(activity, R.id.btnAdultsMinus);
        assertFalse("minus disabled at 1 adult", adultsMinus.isEnabled());
        adultsMinus.performClick();
        assertEquals(1, activity.getState().adults);
    }

    @Test
    public void step5_lowersTheCountWhenRoomsShrinkBelowIt() {
        BookingWizardActivity activity = launch(BookingWizardState.Mode.RESERVATION).get();
        activity.getState().selectedRooms.add(room("R0", 2));
        activity.getState().adults = 3;
        activity.getState().children = 2;
        activity.goToStep(2);
        idle();
        assertEquals(2, activity.getState().adults + activity.getState().children);
        assertEquals("2 of 2 guests", text(find(activity, R.id.tvGuestCounter)));
        assertTrue(((WizardStepFragment) fragment(activity).getChildFragmentManager().findFragmentByTag("guests")).validateBeforeNext());
    }

    // ---- the old ID / discount step is gone ----

    @Test
    public void thereIsNoIdOrDiscountStep_andNoDiscountListIsLoaded() {
        for (BookingWizardState.Mode mode : BookingWizardState.Mode.values()) {
            api = new FakeApi();
            ScreenTestSupport.freshRepository(app, api);
            BookingWizardActivity activity = launch(mode).get();
            int last = 4;
            for (int step = 1; step <= last; step++) {
                activity.goToStep(step);
                idle();
            }
            assertEquals("no discounts are loaded when booking", 0, api.count("getDiscounts"));
        }
    }

    // ---- back navigation never loses what was typed ----

    @Test
    public void goingBackFromStep2KeepsTheTypedNamesAndTheTypedChildAge() {
        for (BookingWizardState.Mode mode : BookingWizardState.Mode.values()) {
            BookingWizardActivity activity = launch(mode).get();
            activity.getState().selectedRooms.add(room("R1", 6));

            activity.getState().who = BookingWizardState.Who.OTHER;
            activity.goToStep(activity.repGuestsStep());
            idle();
            View other = find(activity, R.id.otherFields);
            ((TextView) other.findViewById(R.id.etRepLast)).setText("Cruz");
            ((TextView) other.findViewById(R.id.etRepFirst)).setText("Ana");
            ((TextView) other.findViewById(R.id.etRepMiddle)).setText("Reyes");
            find(activity, R.id.btnChildrenPlus).performClick();
            android.widget.EditText age = firstEditText(find(activity, R.id.layoutChildAgeFields));
            age.setText("5");
            activity.goToStep(1); // Back
            idle();
            activity.goToStep(activity.repGuestsStep());
            idle();
            other = find(activity, R.id.otherFields);
            assertEquals("Cruz", text(other.findViewById(R.id.etRepLast)));
            assertEquals("Ana", text(other.findViewById(R.id.etRepFirst)));
            assertEquals("Reyes", text(other.findViewById(R.id.etRepMiddle)));
            assertEquals(1, activity.getState().children);
            assertEquals("5", firstEditText(find(activity, R.id.layoutChildAgeFields)).getText().toString());
        }
    }

    @Test
    public void reviewStepShowsTheFrontDeskDiscountNote_andNeverAnEstimate_evenForAReservationThatHoldsADiscount() {
        BookingWizardActivity activity = launch(BookingWizardState.Mode.BOOKING).get();
        BookingWizardState s = activity.getState();
        s.selectedRooms.add(room("R1", 4));
        Calendar in = Calendar.getInstance();
        Calendar out = (Calendar) in.clone();
        out.add(Calendar.DAY_OF_YEAR, 2);
        s.checkIn = in;
        s.checkOut = out; // 2 nights x 2,500 = 5,000
        activity.goToStep(4);
        idle();
        TextView note = find(activity, R.id.tvSummaryDiscountNote);
        assertEquals(View.VISIBLE, note.getVisibility());
        assertEquals("PWD and Senior Citizen discounts are verified and applied by the front desk at check-in.", text(note));

        // an older reservation that already carries a discount: still no estimate, the same note
        s.setDiscount(new com.example.velocitysuites.Discount("3", "Senior Citizen", "percentage", 20, "d", "active", null, null));
        activity.goToStep(2);
        idle();
        activity.goToStep(4);
        idle();
        assertEquals("PWD and Senior Citizen discounts are verified and applied by the front desk at check-in.", text(find(activity, R.id.tvSummaryDiscountNote)));
        assertFalse(text(find(activity, R.id.tvSummaryDiscountNote)).contains("-₱"));
    }

    @Test
    public void aDoubleTapOnNextMovesExactlyOneStep() {
        app.getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE).edit()
                .putString("userFirstName", "Maria").putString("userLastName", "Santos").apply();
        BookingWizardActivity activity = launch(BookingWizardState.Mode.BOOKING).get();
        activity.getState().selectedRooms.add(room("R1", 4));
        activity.getState().who = BookingWizardState.Who.OWNER;
        activity.goToStep(3); // representative and guests: valid as it stands, so a second tap would sail straight through
        idle();
        View next = activity.findViewById(R.id.btnWizardNext);
        next.performClick();
        next.performClick();
        idle();
        assertTrue("one question, not two", TapTestSupport.alertsShowing() <= 1);
        assertTrue(text(activity.findViewById(R.id.tvWizardStepLabel)).startsWith("Step 3 of 4"));
        ((androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog()).getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        idle();
        assertTrue("one answer moves exactly one step", text(activity.findViewById(R.id.tvWizardStepLabel)).startsWith("Step 4 of 4"));
    }

    private static android.widget.EditText firstEditText(View v) {
        if (v instanceof android.widget.EditText) return (android.widget.EditText) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.widget.EditText e = firstEditText(g.getChildAt(i));
                if (e != null) return e;
            }
        }
        return null;
    }

    private static void collect(View v, StringBuilder out) {
        if (v instanceof TextView) out.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    // ---- repeat taps on the final confirm ----

    private ActivityController<BookingWizardActivity> readyToConfirm(BookingWizardState.Mode mode, int reviewStep) {
        ActivityController<BookingWizardActivity> controller = launch(mode);
        BookingWizardState state = controller.get().getState();
        state.checkIn = daysFromHotelToday(5);
        state.checkOut = daysFromHotelToday(7);
        state.adults = 2;
        state.selectedRooms.add(room("1", 2));
        state.termsViewed = true;
        state.termsAccepted = true;
        controller.get().goToStep(reviewStep);
        idle();
        return controller;
    }

    @Test
    public void bookingWizard_finalConfirm_tripleTap_asksOnce_checksOnce_andOpensPaymentOnce() {
        ActivityController<BookingWizardActivity> controller = readyToConfirm(BookingWizardState.Mode.BOOKING, 4);
        BookingWizardActivity a = controller.get();
        TapTestSupport.layOut(a);
        View confirm = find(a, R.id.btnConfirmTransaction);
        assertTrue("ready", confirm.isEnabled());
        TapTestSupport.tapThreeTimes(a, confirm);
        assertTrue("at most one confirmation dialog", TapTestSupport.alertsShowing() <= 1);
        TapTestSupport.confirmAllThreeTimes(a);
        assertEquals("one availability check", 1, api.count("getRoomsFresh"));
        assertFalse("disabled while it works", confirm.isEnabled());
        api.<com.example.velocitysuites.network.dto.RoomsResponse>last("getRoomsFresh").succeed(roomsResponse(1, 3));
        idle();
        int payments = 0;
        Intent next;
        while ((next = shadowOf(a).getNextStartedActivity()) != null) {
            if (next.getComponent() != null && next.getComponent().getClassName().endsWith("PaymentActivity")) payments++;
        }
        assertEquals("one Payment screen", 1, payments);
    }

    @Test
    public void reservationWizard_finalConfirm_tripleTap_asksOnce_andSendsOneRequest() {
        ActivityController<BookingWizardActivity> controller = readyToConfirm(BookingWizardState.Mode.RESERVATION, 4);
        BookingWizardActivity a = controller.get();
        TapTestSupport.layOut(a);
        View confirm = find(a, R.id.btnConfirmTransaction);
        assertTrue("ready", confirm.isEnabled());
        TapTestSupport.tapThreeTimes(a, confirm);
        assertTrue("at most one confirmation dialog", TapTestSupport.alertsShowing() <= 1);
        TapTestSupport.confirmAllThreeTimes(a);
        assertEquals("one availability check", 1, api.count("getRoomsFresh"));
        assertFalse("disabled while it works", confirm.isEnabled());
        api.<com.example.velocitysuites.network.dto.RoomsResponse>last("getRoomsFresh").succeed(roomsResponse(1, 3));
        idle();
        assertEquals("exactly one reservation request", 1, api.count("createReservation"));
    }

    // ---- inline messages instead of toasts ----

    @Test
    public void step1_withNoRoom_showsSelectAtLeastOneRoom_inline_notAToast() {
        BookingWizardActivity activity = launch(BookingWizardState.Mode.BOOKING).get();
        activity.getState().checkIn = daysFromHotelToday(5);
        activity.getState().checkOut = daysFromHotelToday(7);
        activity.goToStep(1);
        idle();
        org.robolectric.shadows.ShadowToast.reset();
        TextView error = find(activity, R.id.tvRoomsError);
        assertEquals(View.GONE, error.getVisibility());
        assertFalse(((WizardStepFragment) fragment(activity)).validateBeforeNext());
        assertEquals(View.VISIBLE, error.getVisibility());
        assertEquals("Select at least one room", text(error));
        assertEquals("no toast", 0, org.robolectric.shadows.ShadowToast.shownToastCount());
    }

    @Test
    public void step8_confirmWithTermsMissing_listsWhatIsMissing_scrollsThere_andShowsNoToast() {
        ActivityController<BookingWizardActivity> controller = launch(BookingWizardState.Mode.BOOKING);
        BookingWizardState state = controller.get().getState();
        state.checkIn = daysFromHotelToday(5);
        state.checkOut = daysFromHotelToday(7);
        state.selectedRooms.add(room("1", 2));
        controller.get().goToStep(4);
        idle();
        BookingWizardActivity a = controller.get();
        org.robolectric.shadows.ShadowToast.reset();
        TextView hint = find(a, R.id.tvConfirmHint);
        assertEquals(View.VISIBLE, hint.getVisibility());
        String list = text(hint);
        assertTrue(list, list.startsWith("Still needed:"));
        assertTrue(list, list.contains("• Open the Terms and Policy and scroll to the end"));
        assertTrue(list, list.contains("• Check the box to confirm you agree"));
        hint.performClick(); // jumps to the Terms block
        assertEquals("no toast", 0, org.robolectric.shadows.ShadowToast.shownToastCount());
        assertEquals(a.getColor(R.color.velocity_action_text), hint.getCurrentTextColor());
        assertFalse("Confirm stays disabled", find(a, R.id.btnConfirmTransaction).isEnabled());

        state.termsViewed = true;
        controller.get().goToStep(4);
        idle();
        String afterViewing = text(find(a, R.id.tvConfirmHint));
        assertTrue(afterViewing, afterViewing.contains("• Check the box"));
        assertFalse(afterViewing, afterViewing.contains("Open the Terms"));
    }
}
