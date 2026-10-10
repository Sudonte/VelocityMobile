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
 * The real wizard Activity hosting the changed steps - Step 1 (dates), Step 5 (guests) and Step 6 (ID
 * verification / discounts) of the Booking flow, plus Step 5/6 of the 8-step Reservation flow. Each screen must
 * inflate and bind without a layout crash, and behave per spec: check-in window, capacity-limited steppers with an
 * "N of M guests" counter, and a discount list loaded from the API with loading / error+Retry / empty states.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WizardStepsScreenTest {

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
    public void step1_opensWithTheTwoDayWindowHint_forBothFlows() {
        for (BookingWizardState.Mode mode : BookingWizardState.Mode.values()) {
            BookingWizardActivity activity = launch(mode).get();
            View checkIn = find(activity, R.id.etCheckIn);
            assertTrue(checkIn.isShown() || checkIn.getVisibility() == View.VISIBLE);
            com.google.android.material.textfield.TextInputLayout til = find(activity, R.id.tilCheckIn);
            assertEquals(app.getString(R.string.helper_checkin_window), String.valueOf(til.getHelperText()));
            assertTrue(app.getString(R.string.helper_checkin_window).contains("2 days from today"));
        }
    }

    private static Calendar daysFromHotelToday(int days) {
        return CheckInWindow.toDeviceMidnight(CheckInWindow.today().plusDays(days));
    }

    private boolean step1Accepts(BookingWizardActivity activity, int checkInDays, int checkOutDays) {
        BookingWizardState state = activity.getState();
        state.checkIn = daysFromHotelToday(checkInDays);
        state.checkOut = daysFromHotelToday(checkOutDays);
        return ((WizardStepFragment) fragment(activity)).validateBeforeNext();
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
        activity.goToStep(3);
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

        assertEquals("still on the same step", "wizard_step_3", fragment(controller.get()).getTag());
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
        assertEquals("Room Selection is open", "wizard_step_2", fragment(controller.get()).getTag());
        assertNotNull("the dates were kept", state.checkIn);
        assertEquals("other entered data was kept", 2, state.adults);
    }

    @Test
    public void comingBack_whenTheCheckFails_saysNothingAndChangesNothing() {
        ActivityController<BookingWizardActivity> controller = wizardAtAmenitiesWithRoom1();
        leaveAndComeBack(controller);

        api.<com.example.velocitysuites.network.dto.RoomsResponse>last("getRoomsFresh").offline();
        idle();

        assertEquals("wizard_step_3", fragment(controller.get()).getTag());
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
        controller.get().goToStep(7); // Booking mode: the review step
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

        assertEquals("Room Selection is open", "wizard_step_2", fragment(controller.get()).getTag());
        assertTrue("only the gone room was dropped", state.selectedRooms.isEmpty());
        assertEquals("dates and guests kept", 2, state.adults);
        assertNotNull(state.checkIn);
        assertTrue("the server's own sentence is shown",
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast().contains("Room 1 is fully booked for these dates."));
    }

    // ---- Step 5 (guests) ----

    private BookingWizardActivity openStep5(BookingWizardState.Mode mode, int roomCapacity, int roomCount) {
        BookingWizardActivity activity = launch(mode).get();
        for (int i = 0; i < roomCount; i++) activity.getState().selectedRooms.add(room("R" + i, roomCapacity));
        activity.goToStep(5);
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
        activity.goToStep(5);
        idle();
        assertEquals(2, activity.getState().adults + activity.getState().children);
        assertEquals("2 of 2 guests", text(find(activity, R.id.tvGuestCounter)));
        assertTrue(((WizardStepFragment) fragment(activity)).validateBeforeNext());
    }

    // ---- Step 6 (ID verification / discounts) ----

    private BookingWizardActivity openStep6(BookingWizardState.Mode mode) {
        BookingWizardActivity activity = launch(mode).get();
        activity.goToStep(6);
        idle();
        return activity;
    }

    private void answerDiscounts(List<DiscountDto> items) {
        FakeCall<List<DiscountDto>> call = api.last("getDiscounts");
        call.succeed(items);
        idle();
    }

    @Test
    public void step6_showsLoadingThenOnlyActiveDiscountsWithNameValueAndDescription_bothFlows() {
        for (BookingWizardState.Mode mode : BookingWizardState.Mode.values()) {
            api = new FakeApi();
            ScreenTestSupport.freshRepository(app, api);
            BookingWizardActivity activity = openStep6(mode);
            assertEquals("loaded from the API every visit", 1, api.count("getDiscounts"));
            assertEquals(View.VISIBLE, find(activity, R.id.layoutDiscountLoading).getVisibility());

            List<DiscountDto> items = new ArrayList<>();
            DiscountDto sc = dto(3, "Senior Citizen", "percentage", "20.00", "60 years old or above with a valid ID.", "active");
            DiscountDto vip = dto(4, "VIP", "fixed", "500.00", "Frequent guests.", "active");
            vip.end_date = "2099-12-31";
            items.add(sc);
            items.add(vip);
            items.add(dto(5, "Retired Promo", "percentage", "5.00", "Old.", "inactive"));
            answerDiscounts(items);

            assertEquals(View.GONE, find(activity, R.id.layoutDiscountLoading).getVisibility());
            LinearLayout list = find(activity, R.id.layoutDiscountList);
            assertEquals("inactive discount is never shown", 2, list.getChildCount());
            View first = list.getChildAt(0);
            assertEquals("Senior Citizen", text(first.findViewById(R.id.tvDiscountName)));
            assertEquals("20%", text(first.findViewById(R.id.tvDiscountValue)));
            assertEquals("60 years old or above with a valid ID.", text(first.findViewById(R.id.tvDiscountShortDesc)));
            assertEquals("₱500.00", text(list.getChildAt(1).findViewById(R.id.tvDiscountValue)));
            assertEquals("no dates = no expiry", "No expiry", text(first.findViewById(R.id.tvDiscountValidity)));
            assertEquals("Valid until Dec 31, 2099", text(list.getChildAt(1).findViewById(R.id.tvDiscountValidity)));
            assertEquals(View.GONE, find(activity, R.id.tvDiscountEmpty).getVisibility());
        }
    }

    @Test
    public void step6_errorStateOffersRetry_andRetryReloads() {
        BookingWizardActivity activity = openStep6(BookingWizardState.Mode.BOOKING);
        api.<List<DiscountDto>>last("getDiscounts").offline();
        idle();
        assertEquals(View.VISIBLE, find(activity, R.id.layoutDiscountError).getVisibility());
        // the guest can still continue without a discount
        assertTrue(((WizardStepFragment) fragment(activity)).validateBeforeNext());

        find(activity, R.id.btnDiscountRetry).performClick();
        idle();
        assertEquals(2, api.count("getDiscounts"));
        assertEquals(View.VISIBLE, find(activity, R.id.layoutDiscountLoading).getVisibility());
        answerDiscounts(Collections.singletonList(dto(3, "PWD", "percentage", "10.00", "d", "active")));
        assertEquals(View.GONE, find(activity, R.id.layoutDiscountError).getVisibility());
        assertEquals(1, ((LinearLayout) find(activity, R.id.layoutDiscountList)).getChildCount());
    }

    @Test
    public void step6_emptyStateStillLetsTheGuestContinue() {
        BookingWizardActivity activity = openStep6(BookingWizardState.Mode.RESERVATION);
        answerDiscounts(Collections.<DiscountDto>emptyList());
        TextView empty = find(activity, R.id.tvDiscountEmpty);
        assertEquals(View.VISIBLE, empty.getVisibility());
        assertEquals("No discounts available right now", text(empty));
        assertTrue(((WizardStepFragment) fragment(activity)).validateBeforeNext());
    }

    @Test
    public void step6_tappingADiscountShowsFullDetails_andSelectingItRequiresAnId() {
        BookingWizardActivity activity = openStep6(BookingWizardState.Mode.BOOKING);
        answerDiscounts(Collections.singletonList(dto(3, "Senior Citizen", "percentage", "20.00", "Full description text.", "active")));

        LinearLayout list = find(activity, R.id.layoutDiscountList);
        list.getChildAt(0).performClick();
        idle();
        Dialog sheet = ShadowDialog.getLatestDialog();
        assertNotNull(sheet);
        assertTrue(sheet.isShowing());
        assertEquals("Senior Citizen", text(sheet.findViewById(R.id.tvDetailTitle)));
        LinearLayout rows = sheet.findViewById(R.id.layoutDetailRows);
        StringBuilder all = new StringBuilder();
        collect(rows, all);
        String detailText = all.toString();
        assertTrue(detailText, detailText.contains("Percentage off"));
        assertTrue(detailText, detailText.contains("20%"));
        assertTrue(detailText, detailText.contains("Full description text."));
        assertTrue(detailText, detailText.contains("Active"));
        assertTrue(detailText, detailText.contains("No expiry"));
        assertNotNull(sheet.findViewById(R.id.btnDetailBack));
        assertNotNull(sheet.findViewById(R.id.btnDetailClose));

        sheet.findViewById(R.id.btnDetailSelect).performClick();
        idle();
        BookingWizardState state = activity.getState();
        assertNotNull(state.discount);
        assertEquals("Senior Citizen", state.idCardType);
        assertEquals(Long.valueOf(3), state.discountIdOrNull());
        assertEquals(View.VISIBLE, find(activity, R.id.layoutIdUpload).getVisibility());

        // a chosen discount needs an ID before Next; the error shows next to the upload field
        assertFalse(((WizardStepFragment) fragment(activity)).validateBeforeNext());
        assertEquals(app.getString(R.string.error_id_required), text(find(activity, R.id.tvIdUploadStatus)));

        // "No discount" clears it again
        find(activity, R.id.rowNoDiscount).performClick();
        idle();
        assertEquals("None", state.idCardType);
        assertEquals(View.GONE, find(activity, R.id.layoutIdUpload).getVisibility());
        assertTrue(((WizardStepFragment) fragment(activity)).validateBeforeNext());
    }

    // ---- back navigation never loses what was typed ----

    @Test
    public void goingBackFromStep4KeepsTheTypedNames_andStep5KeepsTheTypedChildAges() {
        for (BookingWizardState.Mode mode : BookingWizardState.Mode.values()) {
            BookingWizardActivity activity = launch(mode).get();
            activity.getState().selectedRooms.add(room("R1", 6));

            activity.goToStep(4);
            idle();
            ((TextView) find(activity, R.id.etPrimaryGuestFirstName)).setText("Ana");
            ((TextView) find(activity, R.id.etPrimaryGuestLastName)).setText("Cruz");
            activity.goToStep(3); // Back
            idle();
            activity.goToStep(4);
            idle();
            assertEquals("Ana", text(find(activity, R.id.etPrimaryGuestFirstName)));
            assertEquals("Cruz", text(find(activity, R.id.etPrimaryGuestLastName)));

            activity.goToStep(5);
            idle();
            find(activity, R.id.btnChildrenPlus).performClick();
            LinearLayout ages = find(activity, R.id.layoutChildAgeFields);
            android.widget.EditText age = firstEditText(ages);
            age.setText("5");
            activity.goToStep(4); // Back
            idle();
            activity.goToStep(5);
            idle();
            assertEquals(1, activity.getState().children);
            assertEquals("5", firstEditText(find(activity, R.id.layoutChildAgeFields)).getText().toString());
        }
    }

    @Test
    public void reviewStepShowsTheClaimedDiscountsEstimatedValueFromTheModule() {
        BookingWizardActivity activity = launch(BookingWizardState.Mode.BOOKING).get();
        BookingWizardState s = activity.getState();
        s.selectedRooms.add(room("R1", 4));
        Calendar in = Calendar.getInstance();
        Calendar out = (Calendar) in.clone();
        out.add(Calendar.DAY_OF_YEAR, 2);
        s.checkIn = in;
        s.checkOut = out; // 2 nights x 2,500 = 5,000 rooms + 1,000 add-ons = 6,000 whole bill
        com.example.velocitysuites.AddOnAmenity breakfast = new com.example.velocitysuites.AddOnAmenity("9", "Breakfast", "", "", 500.0, 2);
        breakfast.setQuantity(2); // 1,000 of add-ons
        s.selectedAmenities.add(breakfast);
        s.setDiscount(new com.example.velocitysuites.Discount("3", "Senior Citizen", "percentage", 20, "d", "active", null, null));
        activity.goToStep(7);
        idle();
        TextView note = find(activity, R.id.tvSummaryDiscountNote);
        assertEquals(View.VISIBLE, note.getVisibility());
        assertTrue(text(note), text(note).contains("Senior Citizen"));
        assertTrue(text(note), text(note).contains("20%"));
        assertTrue("20% of the WHOLE bill (6,000), not just the rooms: " + text(note), text(note).contains("-₱1,200.00"));
        s.setDiscount(null);
        activity.goToStep(7);
        idle();
        assertEquals(View.GONE, ((TextView) find(activity, R.id.tvSummaryDiscountNote)).getVisibility());
    }

    @Test
    public void aDoubleTapOnNextMovesExactlyOneStep() {
        BookingWizardActivity activity = launch(BookingWizardState.Mode.BOOKING).get();
        activity.getState().selectedRooms.add(room("R1", 4));
        activity.goToStep(3); // amenities: nothing to validate, so a second tap would sail straight through
        idle();
        View next = activity.findViewById(R.id.btnWizardNext);
        next.performClick();
        next.performClick();
        idle();
        assertEquals("4 of 7 - Guest and Identification", "Step 4 of 7", text(activity.findViewById(R.id.tvWizardStepLabel)).substring(0, 11));
        assertTrue(text(activity.findViewById(R.id.tvWizardStepLabel)).startsWith("Step 4 of 7"));
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
}
