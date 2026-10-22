package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingWizardActivity;
import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.CheckInWindow;
import com.example.velocitysuites.PaymentRules;
import com.example.velocitysuites.R;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomAmenity;
import com.example.velocitysuites.WizardStepFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Calendar;
import java.util.Collections;

/**
 * Dates and rooms are one wizard step, and "Add another stay" turns a booking into a group of stays (each its own booking): the stays
 * list, edit, remove, the group total, the payment limits on the GROUP total, and the layout at 360dp and 393dp in light and dark.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StaysWizardTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static Room room(String id, double price) {
        return new Room(id, "Room " + id, "Deluxe", 2, price, "desc", 0, true, Collections.<RoomAmenity>emptyList(), "Queen", "24 sqm", "No smoking");
    }

    private static Calendar day(int days) {
        return CheckInWindow.toDeviceMidnight(CheckInWindow.today().plusDays(days));
    }

    private BookingWizardActivity wizardOnStay(int checkInDays, int nights, Room room, int qty) {
        BookingWizardActivity activity = Robolectric.buildActivity(BookingWizardActivity.class,
                BookingWizardActivity.newIntent(app, BookingWizardState.Mode.BOOKING)).setup().get();
        idle();
        fill(activity.getState(), checkInDays, nights, room, qty);
        activity.goToStep(1);
        idle();
        return activity;
    }

    private static void fill(BookingWizardState state, int checkInDays, int nights, Room room, int qty) {
        state.checkIn = day(checkInDays);
        state.checkOut = day(checkInDays + nights);
        state.selectedRooms.clear();
        for (int i = 0; i < qty; i++) state.selectedRooms.add(room);
    }

    private static Fragment step(BookingWizardActivity a) {
        return a.getSupportFragmentManager().findFragmentById(R.id.wizardFragmentContainer);
    }

    private static <T extends View> T find(BookingWizardActivity a, int id) {
        T v = step(a).getView().findViewById(id);
        assertNotNull("view " + a.getResources().getResourceName(id), v);
        return v;
    }

    private static String text(View v) {
        return ((TextView) v).getText().toString();
    }

    private static boolean valid(BookingWizardActivity a) {
        return ((WizardStepFragment) step(a)).validateBeforeNext();
    }

    @Test
    public void datesAndRoomsAreOneStep_andABookingHasSixSteps() {
        BookingWizardActivity activity = wizardOnStay(5, 2, room("1", 1000), 1);

        assertEquals("Step 1 of 6", text(activity.findViewById(R.id.tvWizardStepLabel)));
        assertNotNull("the dates are on this step", find(activity, R.id.etCheckIn));
        assertNotNull("and so are the rooms", find(activity, R.id.layoutSelectedRooms));
        assertEquals(View.VISIBLE, find(activity, R.id.btnAddAnotherStay).getVisibility());
    }

    @Test
    public void oneStay_works_asBefore_noSummary_noGroup() {
        BookingWizardActivity activity = wizardOnStay(5, 2, room("1", 1000), 2);
        BookingWizardState state = activity.getState();

        assertEquals(View.GONE, find(activity, R.id.layoutStaysSummary).getVisibility());
        assertTrue(valid(activity));
        assertEquals(1, state.stayCount());
        assertFalse(state.isGroup());
        assertEquals(4000.0, state.groupTotal(), 0.001);
    }

    @Test
    public void addAnotherStay_opensAnEmptyStay2_andTheSummaryListsBothWithTheGroupTotal() {
        BookingWizardActivity activity = wizardOnStay(5, 2, room("1", 1000), 2); // 4,000
        find(activity, R.id.btnAddAnotherStay).performClick();
        idle();
        BookingWizardState state = activity.getState();

        assertEquals(2, state.stayCount());
        assertEquals(1, state.activeStayIndex());
        assertEquals("Stay 2 of 2", text(find(activity, R.id.tvActiveStayHeading)));
        assertEquals("the new stay starts empty", 0, state.selectedRooms.size());
        assertEquals(View.VISIBLE, find(activity, R.id.layoutStaysSummary).getVisibility());

        // fill stay 2 on touching dates: Oct 7 -> check-out day of stay 1 is the check-in day of stay 2 (allowed)
        fill(state, 7, 3, room("2", 2000), 1); // 6,000
        activity.goToStep(1);
        idle();
        assertEquals(2, state.stayCount());
        android.widget.LinearLayout rows = find(activity, R.id.layoutStayRows);
        assertEquals("one row per stay", 2, rows.getChildCount());
        assertEquals("Group total: ₱10,000.00", text(find(activity, R.id.tvStaysGroupTotal)));
        assertTrue(text(find(activity, R.id.tvStayIncomplete)).isEmpty());

        assertTrue("every stay is complete: Next is allowed", valid(activity));
        assertEquals("the next steps start from the first stay", 0, state.activeStayIndex());
        assertEquals(10000.0, state.groupTotal(), 0.001);
    }

    @Test
    public void aSecondStayCannotBeAddedUntilTheFirstIsComplete() {
        BookingWizardActivity activity = wizardOnStay(5, 2, room("1", 1000), 1);
        activity.getState().selectedRooms.clear(); // no room yet
        activity.goToStep(1);
        idle();

        assertFalse("disabled until the stay has dates and a room", find(activity, R.id.btnAddAnotherStay).isEnabled());
        find(activity, R.id.btnAddAnotherStay).performClick();
        assertEquals(1, activity.getState().stayCount());
    }

    @Test
    public void anIncompleteLaterStayBlocksNext_andOpensThatStayWithTheReason() {
        BookingWizardActivity activity = wizardOnStay(5, 2, room("1", 1000), 1);
        find(activity, R.id.btnAddAnotherStay).performClick();
        idle();
        // stay 2 left empty
        assertFalse(valid(activity));
        assertEquals(View.VISIBLE, find(activity, R.id.tvStayIncomplete).getVisibility());
        assertFalse("says why", text(find(activity, R.id.tvStayIncomplete)).isEmpty());
    }

    @Test
    public void editingAnEarlierStay_andRemovingOne() {
        BookingWizardActivity activity = wizardOnStay(5, 2, room("1", 1000), 1);
        BookingWizardState state = activity.getState();
        find(activity, R.id.btnAddAnotherStay).performClick();
        idle();
        fill(state, 10, 1, room("2", 2000), 1);
        activity.goToStep(1);
        idle();

        // open stay 1 again
        state.activate(0);
        activity.goToStep(1);
        idle();
        assertEquals("Stay 1 of 2", text(find(activity, R.id.tvActiveStayHeading)));
        assertEquals(2L, state.nights());

        state.removeStay(1);
        activity.goToStep(1);
        idle();
        assertEquals(1, state.stayCount());
        assertEquals(View.GONE, find(activity, R.id.layoutStaysSummary).getVisibility());
        assertEquals(2000.0, state.groupTotal(), 0.001);
    }

    @Test
    public void threeStays_sumToOneGroupTotal_andThePaymentLimitsAreOnTheGroupTotal() {
        BookingWizardActivity activity = wizardOnStay(5, 1, room("1", 1000), 1); // 1,000
        BookingWizardState state = activity.getState();
        find(activity, R.id.btnAddAnotherStay).performClick();
        idle();
        fill(state, 10, 2, room("2", 1000), 2); // 4,000
        activity.goToStep(1);
        idle();
        find(activity, R.id.btnAddAnotherStay).performClick();
        idle();
        fill(state, 20, 1, room("3", 2000), 1); // 2,000
        activity.goToStep(1);
        idle();

        assertEquals(3, state.stayCount());
        assertEquals(7000.0, state.groupTotal(), 0.001);
        assertEquals("1,000 + 4,000 + 2,000", 7000.0, state.roomsTotal(), 0.001);

        // one combined payment: 20%-50% of the group total, never the whole of it
        PaymentRules.Range range = PaymentRules.of(state.groupTotal(), 0);
        assertEquals(1400.0, range.min, 0.001);
        assertEquals(3500.0, range.max, 0.001);
        assertEquals(PaymentRules.Verdict.OK, PaymentRules.check(range, false, 1400));
        assertEquals(PaymentRules.Verdict.OUT_OF_RANGE, PaymentRules.check(range, false, 1399.99));
        assertEquals(PaymentRules.Verdict.OUT_OF_RANGE, PaymentRules.check(range, false, 3500.01));
        assertEquals(PaymentRules.Verdict.PARTIAL_ONLY, PaymentRules.check(range, true, 7000));
    }

    @Test
    public void thereIsAMaximumNumberOfStays_andTheAddButtonStopsThere() {
        BookingWizardActivity activity = wizardOnStay(5, 1, room("1", 1000), 1);
        BookingWizardState state = activity.getState();
        for (int i = 1; i < BookingWizardState.MAX_STAYS; i++) {
            find(activity, R.id.btnAddAnotherStay).performClick();
            idle();
            fill(state, 5 + i * 5, 1, room("" + (i + 1), 1000), 1);
            activity.goToStep(1);
            idle();
        }
        assertEquals(BookingWizardState.MAX_STAYS, state.stayCount());
        assertFalse(find(activity, R.id.btnAddAnotherStay).isEnabled());
    }

    @Test
    public void theAmenitiesStepPicksTheStayFirst_whenThereAreSeveral() {
        BookingWizardActivity activity = wizardOnStay(5, 1, room("1", 1000), 1);
        BookingWizardState state = activity.getState();
        find(activity, R.id.btnAddAnotherStay).performClick();
        idle();
        fill(state, 10, 1, room("2", 1000), 1);
        activity.goToStep(1);
        idle();

        activity.goToStep(2);
        idle();
        android.view.ViewGroup root = (android.view.ViewGroup) step(activity).getView();
        com.google.android.material.chip.ChipGroup chips = findChipGroup(root);
        assertNotNull("a Stay 1 / Stay 2 picker", chips);
        assertEquals(2, chips.getChildCount());
    }

    private static com.google.android.material.chip.ChipGroup findChipGroup(View v) {
        if (v instanceof com.google.android.material.chip.ChipGroup) return (com.google.android.material.chip.ChipGroup) v;
        if (v instanceof android.view.ViewGroup) {
            for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) {
                com.google.android.material.chip.ChipGroup found = findChipGroup(((android.view.ViewGroup) v).getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    public void reviewListsEveryStay_andTheGroupTotal() {
        BookingWizardActivity activity = wizardOnStay(5, 2, room("1", 1000), 1); // 2,000
        BookingWizardState state = activity.getState();
        find(activity, R.id.btnAddAnotherStay).performClick();
        idle();
        fill(state, 12, 1, room("2", 3000), 1); // 3,000
        activity.goToStep(1);
        idle();
        state.termsViewed = true;
        state.termsAccepted = true;
        activity.goToStep(6);
        idle();

        String all = collect(step(activity).getView());
        assertTrue(all, all.contains("2 stays"));
        assertTrue(all, all.contains("Stay 1:"));
        assertTrue(all, all.contains("Stay 2:"));
        assertEquals("₱5,000.00", text(find(activity, R.id.tvSummaryTotal)));
    }

    private static String collect(View v) {
        StringBuilder sb = new StringBuilder();
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE) sb.append(((TextView) v).getText()).append('\n');
        if (v instanceof android.view.ViewGroup) {
            for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) sb.append(collect(((android.view.ViewGroup) v).getChildAt(i)));
        }
        return sb.toString();
    }

    @Test
    public void theStaysStepFitsAt360And393dp_inLightAndDark() {
        for (String qualifiers : new String[]{"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"}) {
            RuntimeEnvironment.setQualifiers("+" + qualifiers);
            BookingWizardActivity activity = wizardOnStay(5, 2, room("1", 1000), 1);
            BookingWizardState state = activity.getState();
            find(activity, R.id.btnAddAnotherStay).performClick();
            idle();
            fill(state, 12, 1, room("2", 2000), 1);
            activity.goToStep(1);
            idle();

            View root = step(activity).getView();
            int screen = activity.getResources().getDisplayMetrics().widthPixels;
            View add = find(activity, R.id.btnAddAnotherStay);
            View summary = find(activity, R.id.layoutStaysSummary);
            root.measure(View.MeasureSpec.makeMeasureSpec(screen, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            root.layout(0, 0, screen, root.getMeasuredHeight());
            assertTrue(qualifiers + ": the Add button fits", add.getRight() <= screen && add.getLeft() >= 0);
            assertTrue(qualifiers + ": the summary fits", summary.getRight() <= screen && summary.getLeft() >= 0);
            assertTrue(qualifiers + ": the Add button is at least 48dp tall",
                    add.getMeasuredHeight() >= Math.round(48 * activity.getResources().getDisplayMetrics().density));
            boolean night = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            assertEquals(qualifiers, qualifiers.endsWith("-night") && !qualifiers.endsWith("notnight"), night);
        }
        RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
    }
}
