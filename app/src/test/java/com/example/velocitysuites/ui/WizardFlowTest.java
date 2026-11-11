package com.example.velocitysuites.ui;

import static com.example.velocitysuites.ui.WizardDriver.addConfirmedStay;
import static com.example.velocitysuites.ui.WizardDriver.advance;
import static com.example.velocitysuites.ui.WizardDriver.answer;
import static com.example.velocitysuites.ui.WizardDriver.collect;
import static com.example.velocitysuites.ui.WizardDriver.dialogText;
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
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingAndReservationActivity;
import com.example.velocitysuites.BookingWizardActivity;
import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.Discount;
import com.example.velocitysuites.R;
import com.example.velocitysuites.WizardStepFragment;
import com.example.velocitysuites.network.dto.AmenityDto;
import com.example.velocitysuites.ui.ScreenTestSupport.FakeApi;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The guided steps after "Dates and rooms": the representative (Owner (me) / Someone else) and guests (Adults and Minors) on one screen, the "Proceed to ...?" questions between steps, the final review with its sections and Edit links,
 * the step counts (4 for a booking, with Amenities second; 4 for a reservation, with Payment method third), and leaving the wizard.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WizardFlowTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;
    private FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        app.getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE).edit()
                .putString("userFirstName", "Maria").putString("userMiddleName", "Reyes").putString("userLastName", "Santos").apply();
    }

    // ---- helpers ----

    private static List<View> all(View root, int id) {
        List<View> found = new ArrayList<>();
        if (root.getId() == id) found.add(root);
        if (root instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) found.addAll(all(((ViewGroup) root).getChildAt(i), id));
        }
        return found;
    }

    private static String hintOf(TextInputLayout til) {
        if (til.getHint() != null) return til.getHint().toString();
        return til.getEditText() != null && til.getEditText().getHint() != null ? til.getEditText().getHint().toString() : null;
    }

    private static String stepLabel(BookingWizardActivity a) {
        return text(a.findViewById(R.id.tvWizardStepLabel));
    }

    private static String stepName(BookingWizardActivity a) {
        return text(a.findViewById(R.id.tvWizardStepName));
    }

    private static void tapNext(BookingWizardActivity a) {
        advance();
        a.findViewById(R.id.btnWizardNext).performClick();
        idle();
    }

    /** Two stays: 2 x Room 1 for 2 nights at 1,000 (4,000) and 1 x Room 2 for one night at 3,000 - ready on the representative and guests step (3 of 4 for a booking). */
    private BookingWizardActivity atStepTwo() {
        BookingWizardActivity a = onConfirmedStay(app, 5, 2, room("1", 1000, 4), 2);
        addConfirmedStay(a, 12, 1, room("2", 3000, 4), 1);
        a.goToStep(3);
        idle();
        return a;
    }

    // ---- Step 2: the representative ----

    private BookingWizardActivity atRepresentative(BookingWizardState.Mode mode) {
        BookingWizardActivity a = open(app, mode);
        WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000, 4), 1);
        a.goToStep(mode == BookingWizardState.Mode.BOOKING ? 3 : 2); // a booking has Amenities before it
        idle();
        return a;
    }

    private static View ownerField(BookingWizardActivity a, int id) {
        return find(a, R.id.ownerFields).findViewById(id);
    }

    private static View otherField(BookingWizardActivity a, int id) {
        return find(a, R.id.otherFields).findViewById(id);
    }

    @Test
    public void representative_hasTwoLargeChoices_andNeitherIsSelectedByDefault_soNextIsBlockedInline() {
        BookingWizardActivity a = atRepresentative(BookingWizardState.Mode.BOOKING);
        MaterialButtonToggleGroup toggle = find(a, R.id.toggleWho);
        assertEquals(View.NO_ID, toggle.getCheckedButtonId());
        assertEquals("Account owner", text(find(a, R.id.btnWhoOwner)));
        assertEquals("Someone else", text(find(a, R.id.btnWhoOther)));
        assertTrue("large touch targets", find(a, R.id.btnWhoOwner).getLayoutParams().height >= 64 * a.getResources().getDisplayMetrics().density - 1);
        assertEquals(View.GONE, find(a, R.id.layoutWhoOwner).getVisibility());
        assertEquals(View.GONE, find(a, R.id.layoutWhoOther).getVisibility());
        assertTrue("the ID helper text stays", text(find(a, R.id.tvRepresentativeHelper)).contains("1 valid ID"));

        tapNext(a);
        assertEquals("no question: Next is blocked", 0, TapTestSupport.alertsShowing());
        assertTrue(stepLabel(a).startsWith("Step 3 of 4"));
        assertEquals(View.VISIBLE, find(a, R.id.tvWhoError).getVisibility());
        assertEquals("Choose the representative who will check in at the hotel", text(find(a, R.id.tvWhoError)));
    }

    @Test
    public void representative_ownerShowsTheProfileNamesReadOnly_andSendsTheOwnersNames() {
        BookingWizardActivity a = atRepresentative(BookingWizardState.Mode.BOOKING);
        find(a, R.id.btnWhoOwner).performClick();
        idle();
        assertEquals(View.VISIBLE, find(a, R.id.layoutWhoOwner).getVisibility());
        assertEquals("Santos", text(ownerField(a, R.id.etRepLast)));
        assertEquals("Maria", text(ownerField(a, R.id.etRepFirst)));
        assertEquals("Reyes", text(ownerField(a, R.id.etRepMiddle)));
        for (int id : new int[]{R.id.etRepLast, R.id.etRepFirst, R.id.etRepMiddle}) assertFalse("read-only", ownerField(a, id).isEnabled());
        assertEquals("Not you? Choose Someone else", text(find(a, R.id.tvOwnerNotYou)));

        tapNext(a);
        String ask = dialogText(latestDialog());
        assertTrue(ask, ask.contains("Continue with these details?") && ask.contains("Account owner") && ask.contains("Santos, Maria Reyes"));
        answer(true);
        assertTrue(stepLabel(a).startsWith("Step 4 of 4"));
        BookingWizardState state = a.getState();
        assertEquals("what the server expects for no separate representative", "Maria", state.guestFirstName);
        assertEquals("", state.guestMiddleName);
        assertEquals("Santos", state.guestLastName);
        assertTrue(state.representative().isBlank());
    }

    @Test
    public void representative_ownerWithoutAMiddleName_leavesItEmpty_andStillContinues() {
        app.getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE).edit().remove("userMiddleName").apply();
        BookingWizardActivity a = atRepresentative(BookingWizardState.Mode.BOOKING);
        find(a, R.id.btnWhoOwner).performClick();
        idle();
        assertEquals("", text(ownerField(a, R.id.etRepMiddle)));
        assertTrue(((WizardStepFragment) step(a)).validateBeforeNext());
    }

    @Test
    public void representative_ownerWhoseProfileHasNoName_isToldInline_andBlocked() {
        app.getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE).edit().clear().apply();
        BookingWizardActivity a = atRepresentative(BookingWizardState.Mode.BOOKING);
        find(a, R.id.btnWhoOwner).performClick();
        idle();
        assertEquals(View.VISIBLE, find(a, R.id.tvOwnerProfileMissing).getVisibility());
        assertFalse(((WizardStepFragment) step(a)).validateBeforeNext());
    }

    @Test
    public void representative_someoneElse_startsEmpty_requiresLastAndFirst_andSendsTheTypedNames() {
        BookingWizardActivity a = atRepresentative(BookingWizardState.Mode.RESERVATION);
        find(a, R.id.btnWhoOther).performClick();
        idle();
        assertEquals(View.VISIBLE, find(a, R.id.layoutWhoOther).getVisibility());
        assertEquals("", text(otherField(a, R.id.etRepLast)));
        assertEquals("", text(otherField(a, R.id.etRepFirst)));
        assertTrue("editable", otherField(a, R.id.etRepLast).isEnabled());
        assertEquals("Middle name (optional)", String.valueOf(((TextInputLayout) otherField(a, R.id.tilRepMiddle)).getHint()));

        WizardStepFragment step = (WizardStepFragment) step(a);
        assertFalse("nothing typed is not 'no representative' here", step.validateBeforeNext());
        assertEquals("Enter the representative’s last name", String.valueOf(((TextInputLayout) otherField(a, R.id.tilRepLast)).getError()));
        assertEquals("Enter the representative’s first name", String.valueOf(((TextInputLayout) otherField(a, R.id.tilRepFirst)).getError()));

        ((TextView) otherField(a, R.id.etRepLast)).setText("Peña 123 @");
        assertEquals("digits and symbols cannot be typed", "Peña  ", text(otherField(a, R.id.etRepLast)));
        ((TextView) otherField(a, R.id.etRepLast)).setText("Dela Cruz");
        ((TextView) otherField(a, R.id.etRepFirst)).setText("Juan");
        assertNull("errors clear as the fields become right", ((TextInputLayout) otherField(a, R.id.tilRepFirst)).getError());
        assertTrue(step.validateBeforeNext());

        tapNext(a);
        String ask = dialogText(latestDialog());
        assertTrue(ask, ask.contains("Continue with these details?") && ask.contains("Someone else") && ask.contains("Dela Cruz, Juan"));
        answer(true);
        BookingWizardState state = a.getState();
        assertEquals("Juan", state.guestFirstName);
        assertEquals("Dela Cruz", state.guestLastName);
        assertEquals("", state.guestMiddleName);
    }

    @Test
    public void representative_switchingChoices_keepsWhatWasTyped() {
        BookingWizardActivity a = atRepresentative(BookingWizardState.Mode.BOOKING);
        find(a, R.id.btnWhoOther).performClick();
        idle();
        ((TextView) otherField(a, R.id.etRepLast)).setText("Cruz");
        ((TextView) otherField(a, R.id.etRepFirst)).setText("Ana");
        advance();
        find(a, R.id.tvOwnerNotYou); // present in the owner block
        find(a, R.id.btnWhoOwner).performClick();
        idle();
        assertEquals(View.VISIBLE, find(a, R.id.layoutWhoOwner).getVisibility());
        assertEquals(View.GONE, find(a, R.id.layoutWhoOther).getVisibility());
        a.goToStep(1); // leave and come back
        idle();
        a.goToStep(3);
        idle();
        find(a, R.id.btnWhoOther).performClick();
        idle();
        assertEquals("Cruz", text(otherField(a, R.id.etRepLast)));
        assertEquals("Ana", text(otherField(a, R.id.etRepFirst)));
    }

    // ---- Step 2: the guests, directly below the representative ----

    private BookingWizardActivity atGuests(int capacity) {
        BookingWizardActivity a = open(app, BookingWizardState.Mode.BOOKING);
        WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000, capacity), 1);
        a.getState().who = BookingWizardState.Who.OWNER;
        a.goToStep(3);
        idle();
        return a;
    }

    @Test
    public void guests_areAdultsAndMinors_withMinorAgeLabels_andNoWordChildren() {
        BookingWizardActivity a = atGuests(4);
        String shown = plain(collect(step(a).getView()));
        assertTrue(shown, shown.contains("Adults") && shown.contains("Minors"));
        assertFalse(shown, shown.toLowerCase().contains("child"));
        find(a, R.id.btnChildrenPlus).performClick();
        find(a, R.id.btnChildrenPlus).performClick();
        LinearLayout ages = find(a, R.id.layoutChildAgeFields);
        assertEquals(2, ages.getChildCount());
        assertEquals("Minor 1 age", a.getString(R.string.child_age_field_format, 1));
        assertEquals("Minor 2 age", a.getString(R.string.child_age_field_format, 2));
        assertTrue("the age fields are TextInputLayouts, one per minor", ages.getChildAt(0) instanceof TextInputLayout && ages.getChildAt(1) instanceof TextInputLayout);
        String notice = text(find(a, R.id.tvChildAgeNotice));
        assertTrue(notice, notice.contains("minor") && !notice.toLowerCase().contains("child"));
        assertEquals("3 of 4 guests", text(find(a, R.id.tvGuestCounter)));
    }

    @Test
    public void guests_theCapacityCheckIsInline_notAToast() {
        BookingWizardActivity a = atGuests(2);
        ShadowToast.reset();
        find(a, R.id.btnAdultsPlus).performClick(); // 2 of 2
        assertEquals(View.GONE, find(a, R.id.tvGuestError).getVisibility());
        assertFalse("plus is disabled at capacity", find(a, R.id.btnAdultsPlus).isEnabled());
        find(a, R.id.btnAdultsPlus).performClick();
        assertEquals("no toast", 0, ShadowToast.shownToastCount());
        // the inline message appears when a guest is added past capacity (a disabled button cannot be tapped, so ask directly)
        BookingWizardState state = a.getState();
        state.adults = 2;
        state.children = 1;
        assertFalse(((WizardStepFragment) step(a)).validateBeforeNext());
        assertEquals(View.VISIBLE, find(a, R.id.tvGuestError).getVisibility());
        assertEquals("Adults + minors can't exceed the room capacity.", text(find(a, R.id.tvGuestError)));
    }

    @Test
    public void guests_aMinorsAgeIsRequired_andNextAsksBeforeIdVerification() {
        BookingWizardActivity a = atGuests(4);
        find(a, R.id.btnChildrenPlus).performClick();
        tapNext(a);
        assertEquals("blocked: no age yet", 0, TapTestSupport.alertsShowing());
        assertTrue(stepLabel(a).startsWith("Step 3 of 4"));
        TextInputLayout til = (TextInputLayout) ((LinearLayout) find(a, R.id.layoutChildAgeFields)).getChildAt(0);
        assertNotNull(til.getError());
        ((TextView) til.getEditText()).setText("5");
        tapNext(a);
        String ask = dialogText(latestDialog());
        assertTrue(ask, ask.contains("Continue with these details?") && ask.contains("1 Adult") && ask.contains("1 Minor"));
        answer(true);
        assertTrue(stepLabel(a).startsWith("Step 4 of 4"));
        List<BookingAndReservationActivity.AdditionalGuest> kept = a.getState().additionalGuests;
        assertEquals("Child 1", kept.get(0).name); // the stored name stays as the server knows it
        assertEquals(5, kept.get(0).age);
    }


    @Test
    public void step2_showsTheRepresentativeFirst_thenTheGuestsDirectlyBelow() {
        BookingWizardActivity a = atStepTwo();
        assertEquals("Representative and guests", stepName(a));
        assertTrue(stepLabel(a), stepLabel(a).startsWith("Step 3 of 4"));
        View who = find(a, R.id.toggleWho);
        View adults = find(a, R.id.btnAdultsPlus);
        int[] whoAt = new int[2];
        int[] adultsAt = new int[2];
        // the views are laid out in order inside one scrolling column: the representative section comes before the guests section
        assertTrue("the representative section is first", orderOf(a, who) < orderOf(a, adults));
        assertTrue("the helper about 1 valid ID is in the representative section", text(find(a, R.id.tvRepresentativeHelper)).contains("1 valid ID"));
        assertNotNull(find(a, R.id.btnChildrenPlus));
        assertNotNull(whoAt);
        assertNotNull(adultsAt);
    }

    /** The position of a view among all the views of the step, depth first - lower is earlier on the screen. */
    private static int orderOf(BookingWizardActivity a, View target) {
        List<View> flat = new ArrayList<>();
        flatten(step(a).getView(), flat);
        return flat.indexOf(target);
    }

    private static void flatten(View v, List<View> out) {
        out.add(v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) flatten(((ViewGroup) v).getChildAt(i), out);
    }

    @Test
    public void step2_nextChecksBothSectionsAtOnce_andAsksOneQuestion() {
        BookingWizardActivity a = atStepTwo();
        find(a, R.id.btnChildrenPlus).performClick();
        // the representative is not chosen and the minor has no age: both problems show together, with no question
        tapNext(a);
        assertEquals("no question while something is missing", 0, TapTestSupport.alertsShowing());
        assertEquals(View.VISIBLE, find(a, R.id.tvWhoError).getVisibility());
        TextInputLayout til = (TextInputLayout) ((LinearLayout) find(a, R.id.layoutChildAgeFields)).getChildAt(0);
        assertNotNull("the minor's age is asked too", til.getError());

        find(a, R.id.btnWhoOwner).performClick();
        idle();
        ((TextView) til.getEditText()).setText("5");
        tapNext(a);
        assertEquals("one question for the whole step", 1, TapTestSupport.alertsShowing());
        String ask = dialogText(latestDialog());
        assertTrue(ask, ask.contains("Continue with these details?") && ask.contains("Account owner") && ask.contains("1 Adult") && ask.contains("1 Minor"));
        answer(true);
        assertTrue(stepLabel(a), stepLabel(a).startsWith("Step 4 of 4"));
        assertEquals("Review and bill summary", stepName(a));
    }

    // ---- the step counts and the final steps ----

    @Test
    public void aBooking_hasFourSteps_andTheReviewIsTheLastOne() {
        BookingWizardActivity a = open(app, BookingWizardState.Mode.BOOKING);
        WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000, 4), 1);
        assertTrue(stepLabel(a), stepLabel(a).startsWith("Step 1 of 4"));
        assertEquals("Dates and rooms", stepName(a));
        a.getState().who = BookingWizardState.Who.OWNER;
        a.goToStep(3);
        idle();
        assertTrue(stepLabel(a), stepLabel(a).startsWith("Step 3 of 4"));
        tapNext(a);
        assertTrue(dialogText(latestDialog()), dialogText(latestDialog()).contains("Continue with these details?"));
        answer(true);
        assertEquals("Step 4 of 4", plain(stepLabel(a)).substring(0, 11));
        assertEquals("Review and bill summary", stepName(a));
        assertEquals("the last step has its own Confirm button", View.GONE, a.findViewById(R.id.btnWizardNext).getVisibility());
    }

    @Test
    public void aReservation_hasFourSteps_paymentMethodComesBeforeTheReview_andOneChoiceIsRequired() {
        BookingWizardActivity a = open(app, BookingWizardState.Mode.RESERVATION);
        WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000, 4), 1);
        assertTrue(stepLabel(a), stepLabel(a).startsWith("Step 1 of 4"));
        a.getState().who = BookingWizardState.Who.OWNER;
        a.goToStep(2);
        idle();
        assertTrue(stepLabel(a), stepLabel(a).startsWith("Step 2 of 4"));
        tapNext(a);
        assertTrue(dialogText(latestDialog()), dialogText(latestDialog()).contains("Continue with these details?"));
        answer(true);
        assertTrue(stepLabel(a).startsWith("Step 3 of 4"));
        assertEquals("Payment method", stepName(a));

        tapNext(a);
        assertEquals("no question without a choice", 0, TapTestSupport.alertsShowing());
        assertEquals(View.VISIBLE, find(a, R.id.tvPaymentMethodError).getVisibility());
        assertEquals(a.getString(R.string.error_payment_method_required), text(find(a, R.id.tvPaymentMethodError)));
        assertTrue(stepLabel(a).startsWith("Step 3 of 4"));

        find(a, R.id.cardPaymentCash).performClick();
        assertEquals(View.GONE, find(a, R.id.tvPaymentMethodError).getVisibility());
        tapNext(a);
        assertTrue(dialogText(latestDialog()), dialogText(latestDialog()).contains("Proceed to view your total amount to pay and review?"));
        answer(true);
        assertTrue(stepLabel(a).startsWith("Step 4 of 4"));
        assertEquals("Review and bill summary", stepName(a));
    }

    @Test
    public void onlyABookingHasTheAmenitiesStep_andThereIsNoIdStep() {
        for (BookingWizardState.Mode mode : BookingWizardState.Mode.values()) {
            BookingWizardActivity a = open(app, mode);
            WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000, 4), 1);
            a.getState().who = BookingWizardState.Who.OWNER;
            a.getState().paymentMethod = "cash";
            a.getState().paymentMethodChosen = true;
            List<String> names = new ArrayList<>();
            for (int step = 1; step <= 4; step++) {
                a.goToStep(step);
                idle();
                names.add(stepName(a));
                assertFalse(stepName(a), stepName(a).contains("ID verification"));
            }
            assertEquals(mode == BookingWizardState.Mode.BOOKING
                    ? java.util.Arrays.asList("Dates and rooms", "Amenities", "Representative and guests", "Review and bill summary")
                    : java.util.Arrays.asList("Dates and rooms", "Representative and guests", "Payment method", "Review and bill summary"), names);
        }
    }

    /** Two stays, a representative and a minor, no amenities and no ID: what a new transaction's review has to show. */
    private BookingWizardActivity readyToReview(BookingWizardState.Mode mode) {
        BookingWizardActivity a = open(app, mode);
        WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000, 3), 2); // 4,000
        addConfirmedStay(a, 12, 1, room("2", 3000, 3), 1); // 3,000
        BookingWizardState s = a.getState();
        s.who = BookingWizardState.Who.OTHER;
        s.repLast = "Dela Cruz";
        s.repFirst = "Juan";
        s.adults = 2;
        s.children = 1;
        s.additionalGuests.add(new BookingAndReservationActivity.AdditionalGuest("Child 1", 5, null, "Child"));
        s.paymentMethod = "gcash";
        s.paymentMethodChosen = true;
        a.goToStep(4);
        idle();
        return a;
    }

    @Test
    public void review_showsEverythingChosen_inSections_withTheBillAndThePartialPaymentForABooking() {
        BookingWizardActivity a = readyToReview(BookingWizardState.Mode.BOOKING);
        String sections = plain(collect(find(a, R.id.layoutReviewSections)));
        for (String needed : new String[]{"Stays", "2 × Room 1 × ₱1,000.00 × 2 nights", "1 × Room 2 × ₱3,000.00 × 1 night",
                "Representative", "Someone else", "Dela Cruz, Juan",
                "Guests", "Adults", "Minors", "Minor 1 age", "5"}) {
            assertTrue("missing '" + needed + "' in:\n" + sections, sections.contains(needed));
        }
        assertFalse("a booking has no payment-method section", sections.contains("Payment method"));
        assertTrue("a booking's amenities section says none were chosen", sections.contains("Amenities") && sections.contains("No additional amenities"));
        assertFalse("no ID section", sections.contains("ID / discount"));

        String bill = plain(collect(find(a, R.id.cardBookingSummary)));
        assertTrue(bill, bill.contains("Room charges") && bill.contains("₱7,000.00") && !bill.contains("Stay 1"));
        assertFalse("no amenities line of zero", bill.contains("Amenities"));
        assertEquals("₱7,000.00", plain(text(find(a, R.id.tvSummaryTotal))));
        assertTrue(bill, bill.contains("Pay now (20% to 50%)") && bill.contains("₱1,400.00 – ₱3,500.00"));
        assertTrue(bill, bill.contains("Balance to pay at the hotel") && bill.contains("₱3,500.00 – ₱5,600.00"));
        assertTrue("the one note about discounts", bill.contains("PWD and Senior Citizen discounts are verified and applied by the front desk at check-in."));
        assertFalse("no expected-discount line", bill.toLowerCase().contains("expected discount") || bill.contains("Senior Citizen (20"));
    }

    @Test
    public void review_aReservation_showsThePaymentMethodSection_andNoPartialPayment() {
        BookingWizardActivity a = readyToReview(BookingWizardState.Mode.RESERVATION);
        String sections = plain(collect(find(a, R.id.layoutReviewSections)));
        assertTrue(sections, sections.contains("Payment method") && sections.contains("GCash"));
        assertEquals(View.GONE, find(a, R.id.layoutPayNow).getVisibility());
        assertTrue(stepLabel(a).startsWith("Step 4 of 4"));
    }

    @Test
    public void review_aReservationMadeWithAmenitiesAndADiscount_keepsShowingThemReadOnly_withoutAnEstimate() {
        BookingWizardActivity a = open(app, BookingWizardState.Mode.RESERVATION);
        WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000, 3), 2);
        BookingWizardState s = a.getState();
        s.committedStays().get(0).setAmenityQty("1#1", new com.example.velocitysuites.AddOnAmenity("9", "Extra bed", "Comfort", "d", 300, 5), 2); // 600
        s.reloadActiveAmenities();
        s.setDiscount(new Discount("3", "Senior Citizen", "percentage", 20, "d", "active", null, null));
        s.who = BookingWizardState.Who.OWNER;
        s.paymentMethod = "cash";
        s.paymentMethodChosen = true;
        a.goToStep(4);
        idle();

        String sections = plain(collect(find(a, R.id.layoutReviewSections)));
        assertTrue(sections, sections.contains("Amenities") && sections.contains("Extra bed") && sections.contains("₱600.00"));
        assertTrue(sections, sections.contains("ID / discount") && sections.contains("Senior Citizen"));
        String bill = plain(collect(find(a, R.id.cardBookingSummary)));
        assertTrue(bill, bill.contains("Amenities") && bill.contains("₱600.00"));
        assertEquals("₱4,600.00", plain(text(find(a, R.id.tvSummaryTotal))));
        assertFalse("the estimate is gone", bill.contains("-₱"));
        assertTrue(bill, bill.contains("verified and applied by the front desk at check-in"));
    }

    @Test
    public void review_everySectionWithAStepHasAnEditLink_thatJumpsBackToIt() {
        BookingWizardActivity a = readyToReview(BookingWizardState.Mode.RESERVATION);
        // Stays -> 1, Representative -> 2, Guests -> 2, Payment method -> 3
        int[] expectedSteps = {1, 2, 2, 3};
        for (int i = 0; i < expectedSteps.length; i++) {
            a.goToStep(4);
            idle();
            advance();
            LinearLayout sections = find(a, R.id.layoutReviewSections);
            assertEquals(4, sections.getChildCount());
            View card = sections.getChildAt(i);
            View edit = ((ViewGroup) ((ViewGroup) card).getChildAt(0)).getChildAt(1);
            assertEquals("Edit", text(edit));
            assertTrue("a comfortable touch target", edit.getMinimumHeight() >= 48 * a.getResources().getDisplayMetrics().density - 1);
            edit.performClick();
            idle();
            assertTrue("section " + i + " opens step " + expectedSteps[i] + ": " + stepLabel(a), stepLabel(a).startsWith("Step " + expectedSteps[i] + " of 4"));
        }
    }

    @Test
    public void review_sectionsCollapseAndOpenAgain() {
        BookingWizardActivity a = readyToReview(BookingWizardState.Mode.BOOKING);
        ViewGroup card = (ViewGroup) ((LinearLayout) find(a, R.id.layoutReviewSections)).getChildAt(0);
        View header = card.getChildAt(0);
        View body = card.getChildAt(1);
        assertEquals(View.VISIBLE, body.getVisibility());
        header.performClick();
        assertEquals(View.GONE, body.getVisibility());
        header.performClick();
        assertEquals(View.VISIBLE, body.getVisibility());
    }

    // ---- leaving, repeat taps, process death ----

    @Test
    public void backOnTheFirstStep_withNothingChosen_justLeaves() {
        BookingWizardActivity a = open(app, BookingWizardState.Mode.BOOKING);
        a.getOnBackPressedDispatcher().onBackPressed();
        idle();
        assertTrue(a.isFinishing());
    }

    @Test
    public void backOnTheFirstStep_withChoicesMade_asksToDiscard() {
        BookingWizardActivity a = onConfirmedStay(app, 5, 2, room("1", 1000), 1);
        a.getOnBackPressedDispatcher().onBackPressed();
        idle();
        assertFalse(a.isFinishing());
        String ask = dialogText(latestDialog());
        assertTrue(ask, ask.contains("Discard this booking?"));
        answer(false);
        assertFalse("Keep editing", a.isFinishing());
        advance();
        a.getOnBackPressedDispatcher().onBackPressed();
        idle();
        answer(true);
        assertTrue(a.isFinishing());
    }

    @Test
    public void aReservationAsksToDiscardAReservation() {
        BookingWizardActivity a = open(app, BookingWizardState.Mode.RESERVATION);
        WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000), 1);
        a.getOnBackPressedDispatcher().onBackPressed();
        idle();
        assertTrue(dialogText(latestDialog()), dialogText(latestDialog()).contains("Discard this reservation?"));
    }

    @Test
    public void backBetweenSteps_neverAsks() {
        BookingWizardActivity a = atStepTwo();
        a.getOnBackPressedDispatcher().onBackPressed();
        idle();
        assertEquals(0, TapTestSupport.alertsShowing());
        assertTrue("back from the representative is the amenities step: " + stepLabel(a), stepLabel(a).startsWith("Step 2 of 4"));
        assertFalse(a.isFinishing());
    }

    @Test
    public void theWizardSurvivesBeingKilledInTheBackground() {
        org.robolectric.android.controller.ActivityController<BookingWizardActivity> controller = org.robolectric.Robolectric.buildActivity(
                BookingWizardActivity.class, BookingWizardActivity.newIntent(app, BookingWizardState.Mode.BOOKING)).setup();
        BookingWizardActivity a = controller.get();
        idle();
        WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000, 3), 2);
        a.getState().who = BookingWizardState.Who.OTHER;
        a.getState().repLast = "Cruz";
        a.getState().repFirst = "Ana";
        a.getState().adults = 2;
        a.goToStep(3);
        idle();
        android.os.Bundle saved = new android.os.Bundle();
        controller.saveInstanceState(saved);

        // the process is gone; a new one is started from what was saved
        BookingWizardActivity restored = org.robolectric.Robolectric.buildActivity(BookingWizardActivity.class,
                BookingWizardActivity.newIntent(app, BookingWizardState.Mode.BOOKING)).create(saved).start().resume().visible().get();
        idle();
        assertTrue(stepLabel(restored), stepLabel(restored).startsWith("Step 3 of 4"));
        BookingWizardState state = restored.getState();
        assertEquals(2, state.selectedRooms.size());
        assertNotNull(state.checkIn);
        assertTrue("the confirmed stay is still confirmed", state.allStaysConfirmed());
        assertEquals("Cruz", state.repLast);
        assertEquals(BookingWizardState.Who.OTHER, state.who);
        assertEquals(2, state.adults);
        assertEquals(2000.0 * 2, state.roomsTotal(), 0.001);
    }

    @Test
    public void aDraftWrittenByTheOlderFormIsNotRestored_theWizardStartsAgainWithAShortMessage() throws Exception {
        // a file in the old (6/7-step) format cannot be read back: it is a different serial version of the state
        java.io.File f = new java.io.File(app.getCacheDir(), "wizard_state.bin");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
            out.write(new byte[]{(byte) 0xAC, (byte) 0xED, 0, 5, 0x73, 0x72}); // a stream header and nothing readable after it
        }
        org.robolectric.android.controller.ActivityController<BookingWizardActivity> controller = org.robolectric.Robolectric.buildActivity(
                BookingWizardActivity.class, BookingWizardActivity.newIntent(app, BookingWizardState.Mode.BOOKING)).setup();
        idle();
        android.os.Bundle saved = new android.os.Bundle();
        saved.putInt("currentStepIndex", 5); // the old form was on its fifth step
        ShadowToast.reset();
        BookingWizardActivity restored = org.robolectric.Robolectric.buildActivity(BookingWizardActivity.class,
                BookingWizardActivity.newIntent(app, BookingWizardState.Mode.BOOKING)).create(saved).start().resume().visible().get();
        idle();

        assertTrue("back on the first step: " + stepLabel(restored), stepLabel(restored).startsWith("Step 1 of 4"));
        assertEquals(restored.getString(R.string.wizard_progress_lost_restart), ShadowToast.getTextOfLatestToast());
        assertTrue(ShadowToast.getTextOfLatestToast().contains("start again"));
        assertNotNull(controller);
    }

    @Test
    public void everyStepFitsAt360And393dp_inLightAndDark() {
        String[] configs = {"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"};
        try {
            for (String q : configs) {
                org.robolectric.RuntimeEnvironment.setQualifiers("+" + q);
                BookingWizardActivity a = atStepTwo();
                int width = a.getResources().getDisplayMetrics().widthPixels;
                a.getState().who = BookingWizardState.Who.NONE;
                LayoutAssert.assertFits(q + " representative and guests", step(a).getView(), width);
                find(a, R.id.btnWhoOwner).performClick();
                idle();
                LayoutAssert.assertFits(q + " representative: owner", step(a).getView(), width);
                advance();
                find(a, R.id.btnWhoOther).performClick();
                idle();
                ((TextView) otherField(a, R.id.etRepLast)).setText("Dela Cruz");
                find(a, R.id.btnChildrenPlus).performClick();
                LayoutAssert.assertFits(q + " representative: someone else, with a minor", step(a).getView(), width);

                a.goToStep(4);
                idle();
                LayoutAssert.assertFits(q + " review", step(a).getView(), width);
            }
        } finally {
            org.robolectric.RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
        }
    }

    @Test
    public void reviewAndReservationStepsFit_withEverythingFilledIn() {
        String[] configs = {"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"};
        try {
            for (String q : configs) {
                org.robolectric.RuntimeEnvironment.setQualifiers("+" + q);
                BookingWizardActivity booking = readyToReview(BookingWizardState.Mode.BOOKING);
                LayoutAssert.assertFits(q + " booking review", step(booking).getView(), booking.getResources().getDisplayMetrics().widthPixels);
                BookingWizardActivity reservation = readyToReview(BookingWizardState.Mode.RESERVATION);
                LayoutAssert.assertFits(q + " reservation review", step(reservation).getView(), reservation.getResources().getDisplayMetrics().widthPixels);
                reservation.goToStep(3);
                idle();
                LayoutAssert.assertFits(q + " payment method", step(reservation).getView(), reservation.getResources().getDisplayMetrics().widthPixels);
            }
        } finally {
            org.robolectric.RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
        }
    }
}
