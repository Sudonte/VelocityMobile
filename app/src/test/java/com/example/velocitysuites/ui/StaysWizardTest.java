package com.example.velocitysuites.ui;

import static com.example.velocitysuites.ui.WizardDriver.addConfirmedStay;
import static com.example.velocitysuites.ui.WizardDriver.advance;
import static com.example.velocitysuites.ui.WizardDriver.answer;
import static com.example.velocitysuites.ui.WizardDriver.collect;
import static com.example.velocitysuites.ui.WizardDriver.confirmStay;
import static com.example.velocitysuites.ui.WizardDriver.dialogText;
import static com.example.velocitysuites.ui.WizardDriver.fill;
import static com.example.velocitysuites.ui.WizardDriver.fillAndConfirm;
import static com.example.velocitysuites.ui.WizardDriver.find;
import static com.example.velocitysuites.ui.WizardDriver.idle;
import static com.example.velocitysuites.ui.WizardDriver.latestDialog;
import static com.example.velocitysuites.ui.WizardDriver.onConfirmedStay;
import static com.example.velocitysuites.ui.WizardDriver.open;
import static com.example.velocitysuites.ui.WizardDriver.plain;
import static com.example.velocitysuites.ui.WizardDriver.room;
import static com.example.velocitysuites.ui.WizardDriver.step;
import static com.example.velocitysuites.ui.WizardDriver.text;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.content.Context;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingWizardActivity;
import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.CheckInWindow;
import com.example.velocitysuites.PaymentRules;
import com.example.velocitysuites.R;
import com.example.velocitysuites.WizardStepFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Dates and rooms (Step 1): a stay is confirmed ("Confirm this stay", only once it has a room) before "Add another stay" appears or Next
 * works; a confirmed stay collapses into the "Your stays" card and reopens with Edit; identical dates are refused inline; Next shows the
 * "Review your stays" sheet and then asks "Proceed to amenities?". Also the layout at 360dp and 393dp in light and dark.
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

    private static boolean valid(BookingWizardActivity a) {
        return ((WizardStepFragment) step(a)).validateBeforeNext();
    }

    private static boolean nextEnabled(BookingWizardActivity a) {
        return a.findViewById(R.id.btnWizardNext).isEnabled();
    }

    @Test
    public void datesAndRoomsAreOneStep_andABookingHasSixSteps() {
        BookingWizardActivity activity = open(app, BookingWizardState.Mode.BOOKING);
        fill(activity, 5, 2, room("1", 1000), 1);

        assertEquals("Step 1 of 6 · Dates and rooms", text(activity.findViewById(R.id.tvWizardStepLabel)));
        assertNotNull("the dates are on this step", find(activity, R.id.etCheckIn));
        assertNotNull("and so are the rooms", find(activity, R.id.layoutSelectedRooms));
    }

    @Test
    public void confirmThisStay_appearsOnlyWithARoom_andAddAnotherStayOnlyAfterConfirming() {
        BookingWizardActivity activity = open(app, BookingWizardState.Mode.BOOKING);
        fill(activity, 5, 2, room("1", 1000), 1);
        activity.getState().selectedRooms.clear();
        activity.goToStep(1);
        idle();
        assertEquals("no room yet: nothing to confirm", View.GONE, find(activity, R.id.btnConfirmStay).getVisibility());
        assertEquals(View.GONE, find(activity, R.id.btnAddAnotherStay).getVisibility());

        fill(activity, 5, 2, room("1", 1000), 1);
        assertEquals("a room: the stay can be confirmed", View.VISIBLE, find(activity, R.id.btnConfirmStay).getVisibility());
        assertEquals("Confirm this stay", text(find(activity, R.id.btnConfirmStay)));
        assertEquals("but it cannot be added to yet", View.GONE, find(activity, R.id.btnAddAnotherStay).getVisibility());
        assertFalse("and Next waits for the confirmation", nextEnabled(activity));
        assertFalse(valid(activity));
        assertEquals("Confirm this stay to continue.", text(find(activity, R.id.tvStayIncomplete)));

        confirmStay(activity);
        assertEquals(View.VISIBLE, find(activity, R.id.btnAddAnotherStay).getVisibility());
        assertEquals(View.GONE, find(activity, R.id.btnConfirmStay).getVisibility());
        assertTrue(nextEnabled(activity));
    }

    @Test
    public void confirmDialog_showsTheStayInOneLine_andCancelKeepsEditing() {
        BookingWizardActivity activity = open(app, BookingWizardState.Mode.BOOKING);
        fill(activity, 5, 1, room("1", 4650), 2); // 9,300 for one night
        find(activity, R.id.btnConfirmStay).performClick();
        idle();
        Dialog d = latestDialog();
        String shown = dialogText(d);
        assertTrue(shown, shown.contains("Confirm this stay?"));
        assertTrue(shown, shown.contains("1 night") && shown.contains("2 rooms") && shown.contains("₱9,300.00") && shown.contains("→"));
        assertNotNull(((androidx.appcompat.app.AlertDialog) d).getButton(android.content.DialogInterface.BUTTON_POSITIVE));
        assertEquals("Yes, confirm", ((androidx.appcompat.app.AlertDialog) d).getButton(android.content.DialogInterface.BUTTON_POSITIVE).getText().toString());

        answer(false);
        assertFalse("Cancel: still editing", activity.getState().activeConfirmed);
        assertEquals(View.VISIBLE, find(activity, R.id.btnConfirmStay).getVisibility());
        assertEquals(View.GONE, find(activity, R.id.btnAddAnotherStay).getVisibility());
    }

    @Test
    public void aRepeatTapOnConfirm_opensOneQuestionOnly() {
        BookingWizardActivity activity = open(app, BookingWizardState.Mode.BOOKING);
        fill(activity, 5, 1, room("1", 1000), 1);
        View confirm = find(activity, R.id.btnConfirmStay);
        confirm.performClick();
        confirm.performClick();
        confirm.performClick();
        idle();
        assertEquals("one question", 1, TapTestSupport.alertsShowing());
    }

    @Test
    public void aConfirmedStay_collapsesIntoTheYourStaysCard_andEditReopensIt() {
        BookingWizardActivity activity = onConfirmedStay(app, 5, 2, room("1", 1000), 1);
        BookingWizardState state = activity.getState();

        assertEquals("the editor is gone", View.GONE, find(activity, R.id.layoutStayEditor).getVisibility());
        assertEquals("the stay is in the card", View.VISIBLE, find(activity, R.id.layoutStaysSummary).getVisibility());
        android.widget.LinearLayout rows = find(activity, R.id.layoutStayRows);
        assertEquals(1, rows.getChildCount());
        assertEquals("Edit", text(rows.getChildAt(0).findViewById(R.id.btnStayRowEdit)));
        assertEquals("Remove", text(rows.getChildAt(0).findViewById(R.id.btnStayRowRemove)));

        advance();
        rows.getChildAt(0).findViewById(R.id.btnStayRowEdit).performClick();
        idle();
        assertFalse("reopened", state.activeConfirmed);
        assertEquals(View.VISIBLE, find(activity, R.id.layoutStayEditor).getVisibility());
        assertEquals("must be confirmed again before adding", View.GONE, find(activity, R.id.btnAddAnotherStay).getVisibility());
        assertFalse("and before Next", nextEnabled(activity));
        assertFalse(valid(activity));

        confirmStay(activity);
        assertTrue(nextEnabled(activity));
        assertEquals(View.VISIBLE, find(activity, R.id.btnAddAnotherStay).getVisibility());
    }

    @Test
    public void addAnotherStay_opensAnEmptyStay2_andTheSummaryListsBothWithTheGroupTotal() {
        BookingWizardActivity activity = onConfirmedStay(app, 5, 2, room("1", 1000), 2); // 4,000
        BookingWizardState state = activity.getState();
        advance();
        find(activity, R.id.btnAddAnotherStay).performClick();
        idle();

        assertEquals(2, state.stayCount());
        assertEquals(1, state.activeStayIndex());
        assertEquals("Choose dates and rooms", text(find(activity, R.id.tvActiveStayHeading)));
        assertEquals("the new stay starts empty", 0, state.selectedRooms.size());
        assertEquals(View.GONE, find(activity, R.id.btnAddAnotherStay).getVisibility());
        assertFalse("stay 2 has no rooms: Next waits", nextEnabled(activity));

        fillAndConfirm(activity, 7, 3, room("2", 2000), 1); // touches stay 1's check-out day: allowed, 6,000
        assertEquals("one row per stay", 2, ((android.widget.LinearLayout) find(activity, R.id.layoutStayRows)).getChildCount());
        assertEquals("₱10,000.00", text(find(activity, R.id.tvStaysTotal)));
        assertTrue(nextEnabled(activity));
        assertTrue(valid(activity));
        assertEquals("the next steps start from the first stay", 0, state.activeStayIndex());
        assertEquals(10000.0, state.groupTotal(), 0.001);
    }

    @Test
    public void theYourStaysCard_listsEveryStay_withNightsRoomsPriceAndTheSameEditAndRemove() {
        BookingWizardActivity activity = onConfirmedStay(app, 5, 1, room("1", 9300), 1); // 9,300
        addConfirmedStay(activity, 7, 2, room("2", 2000), 2); // 8,000
        BookingWizardState state = activity.getState();

        android.widget.LinearLayout rows = find(activity, R.id.layoutStayRows);
        assertEquals(2, rows.getChildCount());
        View first = rows.getChildAt(0);
        View second = rows.getChildAt(1);
        String title1 = plain(text(first.findViewById(R.id.tvStayRowTitle)));
        assertTrue(title1, !title1.contains("Stay") && title1.contains("→") && title1.contains("1 night"));
        assertEquals("1 room · ₱9,300.00", text(first.findViewById(R.id.tvStayRowRooms)));
        assertEquals("2 rooms · ₱8,000.00", text(second.findViewById(R.id.tvStayRowRooms)));
        assertTrue(plain(text(second.findViewById(R.id.tvStayRowTitle))).contains("2 nights"));
        for (View row : new View[]{first, second}) {
            assertEquals(View.VISIBLE, row.findViewById(R.id.btnStayRowEdit).getVisibility());
            assertEquals(View.VISIBLE, row.findViewById(R.id.btnStayRowRemove).getVisibility());
        }
        assertEquals("both are confirmed: nobody is 'being edited'", View.GONE, first.findViewById(R.id.tvStayRowEditing).getVisibility());
        assertEquals(View.GONE, second.findViewById(R.id.tvStayRowEditing).getVisibility());
        assertEquals("₱17,300.00", text(find(activity, R.id.tvStaysTotal)));
        assertTrue(collect(find(activity, R.id.layoutStaysSummary)).contains("Total for all stays"));

        // Edit on the other stay opens it (and highlights it)
        advance();
        first.findViewById(R.id.btnStayRowEdit).performClick();
        idle();
        assertEquals(0, state.activeStayIndex());
        assertTrue(text(find(activity, R.id.tvActiveStayHeading)), text(find(activity, R.id.tvActiveStayHeading)).startsWith("Edit ") && text(find(activity, R.id.tvActiveStayHeading)).contains("→"));
        assertEquals(View.VISIBLE, ((android.widget.LinearLayout) find(activity, R.id.layoutStayRows)).getChildAt(0).findViewById(R.id.tvStayRowEditing).getVisibility());
        assertTrue("stay 1 reads 'not confirmed yet' while open",
                text(((android.widget.LinearLayout) find(activity, R.id.layoutStayRows)).getChildAt(0).findViewById(R.id.tvStayRowRooms)).contains("Not confirmed yet"));
    }

    @Test
    public void twoStaysWithTheSameCheckInAndCheckOut_areRefusedInline_andNextIsDisabled() {
        BookingWizardActivity activity = onConfirmedStay(app, 5, 1, room("1", 1000), 1);
        BookingWizardState state = activity.getState();
        advance();
        find(activity, R.id.btnAddAnotherStay).performClick();
        idle();
        fill(activity, 5, 1, room("2", 2000), 1); // exactly the same dates as stay 1

        android.view.View error = find(activity, R.id.tvStayDuplicate);
        assertEquals(View.VISIBLE, error.getVisibility());
        assertTrue(text(error), text(error).startsWith("You already added a stay for"));
        assertTrue(text(error), text(error).contains("Pick different dates or add rooms to that stay."));
        assertTrue("it names the dates", text(error).contains("–"));
        assertFalse("Next is disabled", nextEnabled(activity));
        assertEquals("a duplicate cannot be confirmed", View.GONE, find(activity, R.id.btnConfirmStay).getVisibility());
        assertFalse(valid(activity));
        assertEquals(1, state.activeStayIndex());

        fill(activity, 6, 1, room("2", 2000), 1); // different dates: fixed
        assertEquals(View.GONE, find(activity, R.id.tvStayDuplicate).getVisibility());
        assertEquals(View.VISIBLE, find(activity, R.id.btnConfirmStay).getVisibility());
        confirmStay(activity);
        assertTrue(nextEnabled(activity));
        assertTrue(valid(activity));
    }

    @Test
    public void overlappingAndTouchingStaysAreAllowed() {
        BookingWizardActivity activity = onConfirmedStay(app, 5, 3, room("1", 1000), 1); // nights 5, 6, 7
        BookingWizardState state = activity.getState();
        addConfirmedStay(activity, 6, 3, room("2", 2000), 1); // nights 6, 7, 8 - overlaps
        assertFalse(state.hasDuplicateStay());
        assertEquals(View.GONE, find(activity, R.id.tvStayDuplicate).getVisibility());
        addConfirmedStay(activity, 8, 2, room("3", 1000), 1); // checks in on stay 2's check-out day - touching
        assertFalse(state.hasDuplicateStay());
        assertEquals(3, state.stayCount());
        assertTrue(valid(activity));
    }

    @Test
    public void nextIsDisabledUntilEveryStayIsConfirmed() {
        BookingWizardActivity activity = onConfirmedStay(app, 5, 1, room("1", 1000), 1);
        assertTrue("one confirmed stay: Next is on", nextEnabled(activity));
        advance();
        find(activity, R.id.btnAddAnotherStay).performClick();
        idle();
        assertFalse("stay 2 has no rooms yet", nextEnabled(activity));
        fill(activity, 9, 1, room("2", 1000), 1);
        assertFalse("stay 2 has a room but is not confirmed", nextEnabled(activity));
        confirmStay(activity);
        assertTrue(nextEnabled(activity));
    }

    @Test
    public void theTwoDayRuleIsOneHelperLineUnderCheckIn_notASecondInfoBox() {
        BookingWizardActivity activity = open(app, BookingWizardState.Mode.BOOKING);
        fill(activity, 5, 1, room("1", 1000), 1);
        com.google.android.material.textfield.TextInputLayout til = find(activity, R.id.tilCheckIn);
        assertEquals("Earliest: " + CheckInWindow.earliestShort(), String.valueOf(til.getHelperText()));
        assertNull("no duplicate 48-hour info box", step(activity).getView().findViewById(app.getResources().getIdentifier("tvCheckInAdvanceNotice", "id", app.getPackageName())));
    }

    @Test
    public void theSummaryBarShowsStaysRoomsGuestsOnTheLeftAndTheTotalOnTheRight_andHowAmenitiesAddUp() {
        BookingWizardActivity activity = onConfirmedStay(app, 5, 2, room("1", 1000), 2); // 4,000
        addConfirmedStay(activity, 12, 1, room("2", 2000), 2); // 4,000
        BookingWizardState state = activity.getState();

        assertEquals("2 stays · 4 rooms · 1 guest", text(activity.findViewById(R.id.wizardSummaryLeft)));
        assertEquals("₱8,000.00", text(activity.findViewById(R.id.wizardSummaryTotal)));
        assertEquals(View.GONE, activity.findViewById(R.id.wizardSummaryBreakdown).getVisibility());

        state.selectedAmenities.add(new com.example.velocitysuites.AddOnAmenity("9", "Extra bed", "Room", "d", 300, 5));
        activity.refreshSummaryBar();
        assertEquals("₱8,300.00", text(activity.findViewById(R.id.wizardSummaryTotal)));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.wizardSummaryBreakdown).getVisibility());
        assertEquals("Rooms ₱8,000.00 + Amenities ₱300.00 = ₱8,300.00", text(activity.findViewById(R.id.wizardSummaryBreakdown)));
    }

    @Test
    public void anIncompleteLaterStayBlocksNext_andSaysWhy() {
        BookingWizardActivity activity = onConfirmedStay(app, 5, 2, room("1", 1000), 1);
        advance();
        find(activity, R.id.btnAddAnotherStay).performClick();
        idle();
        // stay 2 left empty
        assertFalse(valid(activity));
        assertEquals(View.VISIBLE, find(activity, R.id.tvStayIncomplete).getVisibility());
        assertFalse("says why", text(find(activity, R.id.tvStayIncomplete)).isEmpty());
    }

    @Test
    public void editingAnEarlierStay_andRemovingOne() {
        BookingWizardActivity activity = onConfirmedStay(app, 5, 2, room("1", 1000), 1);
        BookingWizardState state = activity.getState();
        addConfirmedStay(activity, 10, 1, room("2", 2000), 1);

        advance();
        ((android.widget.LinearLayout) find(activity, R.id.layoutStayRows)).getChildAt(0).findViewById(R.id.btnStayRowEdit).performClick();
        idle();
        assertTrue(text(find(activity, R.id.tvActiveStayHeading)), text(find(activity, R.id.tvActiveStayHeading)).startsWith("Edit ") && text(find(activity, R.id.tvActiveStayHeading)).contains("→"));
        assertEquals(2L, state.nights());

        advance();
        ((android.widget.LinearLayout) find(activity, R.id.layoutStayRows)).getChildAt(1).findViewById(R.id.btnStayRowRemove).performClick();
        idle();
        answer(true); // "Remove stay 2?" -> Yes
        assertEquals(1, state.stayCount());
        assertEquals("stay 1 (now open) is the only one left", 2000.0, state.groupTotal(), 0.001);
    }

    @Test
    public void threeStays_sumToOneGroupTotal_andThePaymentLimitsAreOnTheGroupTotal() {
        BookingWizardActivity activity = onConfirmedStay(app, 5, 1, room("1", 1000), 1); // 1,000
        BookingWizardState state = activity.getState();
        addConfirmedStay(activity, 10, 2, room("2", 1000), 2); // 4,000
        addConfirmedStay(activity, 20, 1, room("3", 2000), 1); // 2,000

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
        BookingWizardActivity activity = onConfirmedStay(app, 5, 1, room("1", 1000), 1);
        BookingWizardState state = activity.getState();
        for (int i = 1; i < BookingWizardState.MAX_STAYS; i++) {
            addConfirmedStay(activity, 5 + i * 5, 1, room("" + (i + 1), 1000), 1);
        }
        assertEquals(BookingWizardState.MAX_STAYS, state.stayCount());
        assertEquals(View.GONE, find(activity, R.id.btnAddAnotherStay).getVisibility());
    }

    // ---- Next: the review sheet, then "Proceed to amenities?" ----

    @Test
    public void next_opensTheReviewSheet_withEveryRoomLineTheSubtotalAndTheTotal_thenAsksBeforeAmenities() {
        BookingWizardActivity activity = onConfirmedStay(app, 5, 2, room("1", 1000), 2); // 4,000
        addConfirmedStay(activity, 12, 1, room("2", 3000), 1); // 3,000

        activity.findViewById(R.id.btnWizardNext).performClick();
        activity.findViewById(R.id.btnWizardNext).performClick(); // a repeat tap opens nothing new
        idle();
        Dialog sheet = latestDialog();
        assertTrue(sheet.isShowing());
        String shown = dialogText(sheet);
        assertTrue(shown, shown.contains("Review your stays"));
        assertTrue(shown, !shown.contains("Stay 1") && shown.contains("2 nights") && shown.contains("1 night"));
        assertTrue(shown, shown.contains("2 × Room 1 × ₱1,000.00 × 2 nights") && shown.contains("₱4,000.00"));
        assertTrue(shown, shown.contains("1 × Room 2 × ₱3,000.00 × 1 night") && shown.contains("₱3,000.00"));
        assertTrue(shown, shown.contains("Subtotal"));
        assertTrue(shown, shown.contains("Total for all stays") && shown.contains("₱7,000.00"));
        assertEquals("Edit stays", text(sheet.findViewById(R.id.btnReviewEdit)));
        assertEquals("Continue", text(sheet.findViewById(R.id.btnReviewContinue)));

        // Edit stays: back to the step, nothing moved
        sheet.findViewById(R.id.btnReviewEdit).performClick();
        idle();
        assertTrue(text(activity.findViewById(R.id.tvWizardStepLabel)).startsWith("Step 1 of 6"));

        // Continue -> "Proceed to amenities?"; Cancel stays, Yes moves on
        advance();
        activity.findViewById(R.id.btnWizardNext).performClick();
        idle();
        latestDialog().findViewById(R.id.btnReviewContinue).performClick();
        idle();
        String ask = dialogText(latestDialog());
        assertTrue(ask, ask.contains("Proceed to amenities?"));
        assertTrue(ask, ask.contains("2 stays") && ask.contains("3 rooms") && ask.contains("₱7,000.00"));
        answer(false);
        assertTrue(text(activity.findViewById(R.id.tvWizardStepLabel)).startsWith("Step 1 of 6"));
        advance();
        activity.findViewById(R.id.btnWizardNext).performClick();
        idle();
        latestDialog().findViewById(R.id.btnReviewContinue).performClick();
        idle();
        assertEquals("Yes, continue", ((androidx.appcompat.app.AlertDialog) latestDialog()).getButton(android.content.DialogInterface.BUTTON_POSITIVE).getText().toString());
        answer(true);
        assertTrue(text(activity.findViewById(R.id.tvWizardStepLabel)).startsWith("Step 2 of 6"));
    }

    // ---- layout ----

    @Test
    public void theStaysStepFitsAt360And393dp_inLightAndDark() {
        for (String qualifiers : new String[]{"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"}) {
            RuntimeEnvironment.setQualifiers("+" + qualifiers);
            BookingWizardActivity activity = onConfirmedStay(app, 5, 2, room("1", 1000), 1);
            addConfirmedStay(activity, 12, 1, room("2", 2000), 1);

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

    @Test
    public void stepOne_neverSaysStayN_theDatesHeadEachRange_andTheButtonsSayDate() {
        BookingWizardActivity activity = onConfirmedStay(app, 5, 2, room("1", 1000), 2);
        advance();
        String confirmed = collect(find(activity, R.id.layoutStaysSummary)) + " " + text(find(activity, R.id.tvActiveStayHeading));
        assertFalse(confirmed, java.util.regex.Pattern.compile("(^|[^A-Za-z])Stay [0-9]").matcher(confirmed).find());
        assertEquals("Add another date", text(find(activity, R.id.btnAddAnotherStay)));
        find(activity, R.id.btnAddAnotherStay).performClick();
        idle();
        assertEquals("Choose dates and rooms", text(find(activity, R.id.tvActiveStayHeading)));
        String second = collect(find(activity, R.id.layoutStaysSummary)) + " " + text(find(activity, R.id.tvActiveStayHeading));
        assertFalse(second, java.util.regex.Pattern.compile("(^|[^A-Za-z])Stay [0-9]").matcher(second).find());
        android.widget.LinearLayout rows = find(activity, R.id.layoutStayRows);
        String title = plain(text(rows.getChildAt(0).findViewById(R.id.tvStayRowTitle)));
        assertTrue(title, title.contains("→") && title.contains("2 nights"));
        assertEquals("the rooms line is its own text, not part of a Stay label", "2 rooms · ₱4,000.00", text(rows.getChildAt(0).findViewById(R.id.tvStayRowRooms)));
    }
}
