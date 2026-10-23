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
 * The guided steps after "Dates and rooms": amenities by stay and room with a live total, the representative (Owner (me) / Someone
 * else), guests (Adults and Minors), the "Proceed to ...?" questions between steps, the final review with its sections and Edit links,
 * the step counts (6 for a booking, 7 for a reservation), and leaving the wizard.
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

    private static AmenityDto amenityDto(long id, String name, String desc, String price, int stock) {
        AmenityDto d = new AmenityDto();
        d.id = id;
        d.amenity_name = name;
        d.category = "Comfort";
        d.description = desc;
        d.charge = price;
        d.quantity = stock;
        return d;
    }

    /** Two stays: 2 x Room 1 for 2 nights at 1,000 (4,000) and 1 x Room 2 for one night at 3,000 - ready on the amenities step. */
    private BookingWizardActivity atAmenities() {
        BookingWizardActivity a = onConfirmedStay(app, 5, 2, room("1", 1000, 2), 2);
        addConfirmedStay(a, 12, 1, room("2", 3000, 2), 1);
        a.goToStep(2);
        idle();
        List<AmenityDto> catalog = new ArrayList<>();
        catalog.add(amenityDto(9, "Extra bed", "A rollaway bed with fresh linens.", "300.00", 5));
        catalog.add(amenityDto(10, "Breakfast", "Buffet breakfast for one.", "150.00", 3));
        api.<List<AmenityDto>>last("getAmenities").succeed(catalog);
        idle();
        return a;
    }

    private static List<View> all(View root, int id) {
        List<View> found = new ArrayList<>();
        if (root.getId() == id) found.add(root);
        if (root instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) found.addAll(all(((ViewGroup) root).getChildAt(i), id));
        }
        return found;
    }

    private static List<View> plusButtons(BookingWizardActivity a) {
        return all(step(a).getView(), R.id.btnAmenityRowPlus);
    }

    private static List<View> minusButtons(BookingWizardActivity a) {
        return all(step(a).getView(), R.id.btnAmenityRowMinus);
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

    private static String liveTotal(BookingWizardActivity a) {
        return plain(text(find(a, R.id.tvAmenitiesLiveTotal)));
    }

    // ---- amenities (Part 2) ----

    @Test
    public void amenities_areOrganisedByStayThenRoom_withBoldNameDescriptionPriceAndAStepper() {
        BookingWizardActivity a = atAmenities();
        String shown = plain(collect(step(a).getView()));
        assertTrue(shown, shown.contains("Stay 1 · ") && shown.contains("→"));
        assertTrue(shown, shown.contains("Room 1 (room 1)") && shown.contains("Room 1 (room 2)"));
        assertTrue(shown, shown.contains("Stay 2 · ") && shown.contains("Room 2 (room 1)"));
        assertTrue(shown, shown.contains("Extra bed") && shown.contains("A rollaway bed with fresh linens.") && shown.contains("₱300.00 each"));
        assertTrue(shown, shown.contains("Breakfast") && shown.contains("₱150.00 each"));
        assertEquals("two amenities in each of the three rooms", 6, plusButtons(a).size());
        assertEquals(6, minusButtons(a).size());
        TextView name = (TextView) all(step(a).getView(), R.id.tvAmenityRowName).get(0);
        assertEquals("Extra bed", text(name)); // set bold by the row layout (sans-serif-bold)
        assertEquals("a quantity of 0 to start", "0", text(all(step(a).getView(), R.id.tvAmenityRowQty).get(0)));
    }

    @Test
    public void amenities_theStickyTotalUpdatesOnEveryChange() {
        BookingWizardActivity a = atAmenities();
        assertEquals("Amenities ₱0.00 · Grand total ₱7,000.00", liveTotal(a));
        plusButtons(a).get(0).performClick(); // Extra bed, stay 1 room 1
        plusButtons(a).get(0).performClick();
        assertEquals("Amenities ₱600.00 · Grand total ₱7,600.00", liveTotal(a));
        plusButtons(a).get(1).performClick(); // Breakfast, stay 1 room 1
        assertEquals("Amenities ₱750.00 · Grand total ₱7,750.00", liveTotal(a));
        plusButtons(a).get(4).performClick(); // Extra bed, stay 2
        assertEquals("Amenities ₱1,050.00 · Grand total ₱8,050.00", liveTotal(a));
        minusButtons(a).get(0).performClick();
        assertEquals("Amenities ₱750.00 · Grand total ₱7,750.00", liveTotal(a));
        assertEquals("the sticky bar is part of the step, at the bottom", View.VISIBLE, find(a, R.id.layoutAmenitiesTotalBar).getVisibility());
        assertEquals("the wizard's own bar steps aside here", View.GONE, a.findViewById(R.id.wizardSummaryBar).getVisibility());
    }

    @Test
    public void amenities_ofARoom_areAddedUpPerStayWhenTheBookingIsSent() {
        BookingWizardActivity a = atAmenities();
        plusButtons(a).get(0).performClick(); // stay 1, room 1: 2 extra beds
        plusButtons(a).get(0).performClick();
        plusButtons(a).get(2).performClick(); // stay 1, room 2: 1 extra bed
        plusButtons(a).get(4).performClick(); // stay 2: 1 extra bed
        BookingWizardState state = a.getState();
        assertEquals(1, state.allStays().get(0).amenities.size());
        assertEquals("the server prices amenities per stay: 2 + 1", 3, state.allStays().get(0).amenities.get(0).getQuantity());
        assertEquals(1, state.allStays().get(1).amenities.get(0).getQuantity());
        assertEquals(1200.0, state.amenitiesTotal(), 0.001);
    }

    @Test
    public void amenities_areCappedByTheRoomsCapacityAndTheStock_inlineOnTheRow() {
        BookingWizardActivity a = atAmenities();
        ShadowToast.reset();
        plusButtons(a).get(0).performClick();
        plusButtons(a).get(0).performClick(); // room holds 2
        plusButtons(a).get(0).performClick();
        TextView limit = (TextView) all(step(a).getView(), R.id.tvAmenityRowLimit).get(0);
        assertEquals(View.VISIBLE, limit.getVisibility());
        assertEquals(a.getString(R.string.error_amenity_qty_exceeds_capacity), text(limit));
        assertEquals("2", text(all(step(a).getView(), R.id.tvAmenityRowQty).get(0)));
        assertEquals("no toast", 0, ShadowToast.shownToastCount());

        // Breakfast: stock 3 in the stay - 2 in room 1, 1 in room 2, then no more
        plusButtons(a).get(1).performClick();
        plusButtons(a).get(1).performClick();
        plusButtons(a).get(3).performClick();
        plusButtons(a).get(3).performClick();
        TextView stockLimit = (TextView) all(step(a).getView(), R.id.tvAmenityRowLimit).get(3);
        assertEquals(View.VISIBLE, stockLimit.getVisibility());
        assertEquals("Only 3 left", text(stockLimit));
        minusButtons(a).get(3).performClick();
        assertEquals("the message goes when the quantity changes", View.GONE, stockLimit.getVisibility());
    }

    @Test
    public void amenities_next_showsTheReview_thenAsks_beforeStep3() {
        BookingWizardActivity a = atAmenities();
        plusButtons(a).get(0).performClick();
        plusButtons(a).get(0).performClick(); // 600 in stay 1, room 1
        plusButtons(a).get(4).performClick(); // 300 in stay 2
        tapNext(a);
        tapNext(a); // a repeat tap opens nothing else
        Dialog sheet = latestDialog();
        String shown = dialogText(sheet);
        assertTrue(shown, shown.contains("Review your amenities"));
        assertTrue(shown, shown.contains("Stay 1") && shown.contains("Room 1 (room 1)") && shown.contains("Extra bed") && shown.contains("₱600.00"));
        assertTrue(shown, shown.contains("Stay 2") && shown.contains("Room 2 (room 1)") && shown.contains("₱300.00"));
        assertTrue(shown, shown.contains("Amenities") && shown.contains("₱900.00") && shown.contains("Grand total") && shown.contains("₱7,900.00"));
        assertEquals("Edit", text(sheet.findViewById(R.id.btnReviewEdit)));
        assertEquals("Continue", text(sheet.findViewById(R.id.btnReviewContinue)));

        sheet.findViewById(R.id.btnReviewEdit).performClick();
        idle();
        assertTrue(stepLabel(a).startsWith("Step 2 of 6"));
        tapNext(a);
        latestDialog().findViewById(R.id.btnReviewContinue).performClick();
        idle();
        String ask = dialogText(latestDialog());
        assertTrue(ask, ask.contains("Proceed with these amenities?"));
        answer(false);
        assertTrue(stepLabel(a).startsWith("Step 2 of 6"));
        tapNext(a);
        latestDialog().findViewById(R.id.btnReviewContinue).performClick();
        idle();
        answer(true);
        assertTrue(stepLabel(a), stepLabel(a).startsWith("Step 3 of 6"));
    }

    @Test
    public void amenities_nextWithNothingChosen_asksToContinueWithoutAmenities() {
        BookingWizardActivity a = atAmenities();
        tapNext(a);
        Dialog ask = latestDialog();
        assertTrue("no review sheet: there is nothing to review", ask.findViewById(R.id.btnReviewContinue) == null);
        String shown = dialogText(ask);
        assertTrue(shown, shown.contains("Continue without additional amenities?"));
        assertTrue(shown, shown.contains("₱7,000.00"));
        answer(false);
        assertTrue(stepLabel(a).startsWith("Step 2 of 6"));
        tapNext(a);
        answer(true);
        assertTrue(stepLabel(a).startsWith("Step 3 of 6"));
    }

    @Test
    public void amenities_skipForNow_asksTheSameQuestion_andClearsWhatWasChosen() {
        BookingWizardActivity a = atAmenities();
        assertEquals("Skip for now", text(find(a, R.id.btnSkipAmenities)));
        plusButtons(a).get(0).performClick();
        assertTrue(a.getState().amenitiesTotal() > 0);
        advance();
        find(a, R.id.btnSkipAmenities).performClick();
        idle();
        assertTrue(dialogText(latestDialog()), dialogText(latestDialog()).contains("Continue without additional amenities?"));
        answer(true);
        assertTrue(stepLabel(a).startsWith("Step 3 of 6"));
        assertEquals(0.0, a.getState().amenitiesTotal(), 0.001);
    }

    @Test
    public void amenities_anEmptyCatalog_stillLetsTheGuestContinue() {
        BookingWizardActivity a = onConfirmedStay(app, 5, 2, room("1", 1000, 2), 1);
        a.goToStep(2);
        idle();
        api.<List<AmenityDto>>last("getAmenities").succeed(new ArrayList<>());
        idle();
        assertEquals(View.VISIBLE, find(a, R.id.layoutAmenitiesEmptyState).getVisibility());
        tapNext(a);
        answer(true);
        assertTrue(stepLabel(a).startsWith("Step 3 of 6"));
    }

    // ---- representative (Part 3) ----

    private BookingWizardActivity atRepresentative(BookingWizardState.Mode mode) {
        BookingWizardActivity a = open(app, mode);
        WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000, 4), 1);
        a.goToStep(3);
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
        assertEquals("Owner (me)", text(find(a, R.id.btnWhoOwner)));
        assertEquals("Someone else", text(find(a, R.id.btnWhoOther)));
        assertTrue("large touch targets", find(a, R.id.btnWhoOwner).getLayoutParams().height >= 64 * a.getResources().getDisplayMetrics().density - 1);
        assertEquals(View.GONE, find(a, R.id.layoutWhoOwner).getVisibility());
        assertEquals(View.GONE, find(a, R.id.layoutWhoOther).getVisibility());
        assertTrue("the ID helper text stays", text(find(a, R.id.tvRepresentativeHelper)).contains("1 valid ID"));

        tapNext(a);
        assertEquals("no question: Next is blocked", 0, TapTestSupport.alertsShowing());
        assertTrue(stepLabel(a).startsWith("Step 3 of 6"));
        assertEquals(View.VISIBLE, find(a, R.id.tvWhoError).getVisibility());
        assertEquals("Choose who will check in", text(find(a, R.id.tvWhoError)));
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
        assertTrue(ask, ask.contains("Proceed to guest details?") && ask.contains("Owner (me)") && ask.contains("Santos, Maria Reyes"));
        answer(true);
        assertTrue(stepLabel(a).startsWith("Step 4 of 6"));
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
        assertTrue(ask, ask.contains("Proceed to guest details?") && ask.contains("Someone else") && ask.contains("Dela Cruz, Juan"));
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
        a.goToStep(2); // leave and come back
        idle();
        a.goToStep(3);
        idle();
        find(a, R.id.btnWhoOther).performClick();
        idle();
        assertEquals("Cruz", text(otherField(a, R.id.etRepLast)));
        assertEquals("Ana", text(otherField(a, R.id.etRepFirst)));
    }

    // ---- guests (Part 4) ----

    private BookingWizardActivity atGuests(int capacity) {
        BookingWizardActivity a = open(app, BookingWizardState.Mode.BOOKING);
        WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000, capacity), 1);
        a.getState().who = BookingWizardState.Who.OWNER;
        a.goToStep(4);
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
        assertTrue(stepLabel(a).startsWith("Step 4 of 6"));
        TextInputLayout til = (TextInputLayout) ((LinearLayout) find(a, R.id.layoutChildAgeFields)).getChildAt(0);
        assertNotNull(til.getError());
        ((TextView) til.getEditText()).setText("5");
        tapNext(a);
        String ask = dialogText(latestDialog());
        assertTrue(ask, ask.contains("Proceed to ID verification?") && ask.contains("1 Adult") && ask.contains("1 Minor"));
        answer(true);
        assertTrue(stepLabel(a).startsWith("Step 5 of 6"));
        List<BookingAndReservationActivity.AdditionalGuest> kept = a.getState().additionalGuests;
        assertEquals("Child 1", kept.get(0).name); // the stored name stays as the server knows it
        assertEquals(5, kept.get(0).age);
    }

    // ---- ID step and the final steps (Part 5) ----

    @Test
    public void aBooking_hasSixSteps_idStepAsksBeforeTheReview_andTheReviewIsStep6() {
        BookingWizardActivity a = atGuests(4);
        a.getState().who = BookingWizardState.Who.OWNER;
        a.goToStep(5);
        idle();
        assertTrue(stepLabel(a), stepLabel(a).startsWith("Step 5 of 6"));
        assertEquals("ID verification", stepName(a));
        tapNext(a);
        String ask = dialogText(latestDialog());
        assertTrue(ask, ask.contains("Proceed to your bill summary and review?"));
        answer(true);
        assertEquals("Step 6 of 6", plain(stepLabel(a)).substring(0, 11));
        assertEquals("Review and bill summary", stepName(a));
        assertEquals("the last step has its own Confirm button", View.GONE, a.findViewById(R.id.btnWizardNext).getVisibility());
    }

    @Test
    public void aReservation_hasSevenSteps_paymentMethodComesBeforeTheReview_andOneChoiceIsRequired() {
        BookingWizardActivity a = open(app, BookingWizardState.Mode.RESERVATION);
        WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000, 4), 1);
        a.getState().who = BookingWizardState.Who.OWNER;
        a.goToStep(5);
        idle();
        assertTrue(stepLabel(a), stepLabel(a).startsWith("Step 5 of 7"));
        tapNext(a);
        assertTrue(dialogText(latestDialog()), dialogText(latestDialog()).contains("Proceed to payment method?"));
        answer(true);
        assertTrue(stepLabel(a).startsWith("Step 6 of 7"));
        assertEquals("Payment method", stepName(a));

        tapNext(a);
        assertEquals("no question without a choice", 0, TapTestSupport.alertsShowing());
        assertEquals(View.VISIBLE, find(a, R.id.tvPaymentMethodError).getVisibility());
        assertEquals(a.getString(R.string.error_payment_method_required), text(find(a, R.id.tvPaymentMethodError)));
        assertTrue(stepLabel(a).startsWith("Step 6 of 7"));

        find(a, R.id.cardPaymentCash).performClick();
        assertEquals(View.GONE, find(a, R.id.tvPaymentMethodError).getVisibility());
        tapNext(a);
        assertTrue(dialogText(latestDialog()), dialogText(latestDialog()).contains("Proceed to view your total amount to pay and review?"));
        answer(true);
        assertTrue(stepLabel(a).startsWith("Step 7 of 7"));
        assertEquals("Review and bill summary", stepName(a));
    }

    /** Two stays with amenities, a discount, a representative and a minor: what the review has to show. */
    private BookingWizardActivity readyToReview(BookingWizardState.Mode mode) {
        BookingWizardActivity a = open(app, mode);
        WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000, 3), 2); // 4,000
        addConfirmedStay(a, 12, 1, room("2", 3000, 3), 1); // 3,000
        BookingWizardState s = a.getState();
        s.committedStays().get(0).setAmenityQty("1#1", new com.example.velocitysuites.AddOnAmenity("9", "Extra bed", "Comfort", "d", 300, 5), 2); // 600
        s.reloadActiveAmenities();
        s.who = BookingWizardState.Who.OTHER;
        s.repLast = "Dela Cruz";
        s.repFirst = "Juan";
        s.adults = 2;
        s.children = 1;
        s.additionalGuests.add(new BookingAndReservationActivity.AdditionalGuest("Child 1", 5, null, "Child"));
        s.setDiscount(new Discount("3", "Senior Citizen", "percentage", 20, "d", "active", null, null));
        s.paymentMethod = "gcash";
        s.paymentMethodChosen = true;
        a.goToStep(mode == BookingWizardState.Mode.BOOKING ? 6 : 7);
        idle();
        return a;
    }

    @Test
    public void review_showsEverythingChosen_inSections_withTheBillBreakdown_andThePartialPaymentForABooking() {
        BookingWizardActivity a = readyToReview(BookingWizardState.Mode.BOOKING);
        String sections = plain(collect(find(a, R.id.layoutReviewSections)));
        for (String needed : new String[]{"Stays", "Stay 1", "Stay 2", "2 × Room 1 × ₱1,000.00 × 2 nights", "1 × Room 2 × ₱3,000.00 × 1 night",
                "Amenities", "Room 1 (room 1)", "Extra bed", "₱600.00",
                "Representative", "Someone else", "Dela Cruz, Juan",
                "Guests", "Adults", "Minors", "Minor 1 age", "5",
                "ID / discount", "Senior Citizen"}) {
            assertTrue("missing '" + needed + "' in:\n" + sections, sections.contains(needed));
        }
        assertFalse("a booking has no payment-method section", sections.contains("Payment method"));

        String bill = plain(collect(find(a, R.id.cardBookingSummary)));
        assertTrue(bill, bill.contains("Room charges · Stay 1") && bill.contains("₱4,000.00") && bill.contains("Room charges · Stay 2") && bill.contains("₱3,000.00"));
        assertTrue(bill, bill.contains("Amenities · Stay 1") && bill.contains("₱600.00"));
        assertEquals("₱7,600.00", plain(text(find(a, R.id.tvSummaryTotal))));
        assertTrue(bill, bill.contains("Senior Citizen") && bill.contains("-₱1,520.00"));
        assertTrue(bill, bill.contains("Pay now (20% to 50%)") && bill.contains("₱1,520.00 – ₱3,800.00"));
        assertTrue(bill, bill.contains("Balance to pay at the hotel") && bill.contains("₱3,800.00 – ₱6,080.00"));
        assertEquals("the grand total is bold and on the right", View.VISIBLE, find(a, R.id.tvSummaryTotal).getVisibility());
    }

    @Test
    public void review_aReservation_showsThePaymentMethodSection_andNoPartialPayment() {
        BookingWizardActivity a = readyToReview(BookingWizardState.Mode.RESERVATION);
        String sections = plain(collect(find(a, R.id.layoutReviewSections)));
        assertTrue(sections, sections.contains("Payment method") && sections.contains("GCash"));
        assertEquals(View.GONE, find(a, R.id.layoutPayNow).getVisibility());
        assertTrue(stepLabel(a).startsWith("Step 7 of 7"));
    }

    @Test
    public void review_everySectionHasAnEditLink_thatJumpsBackToItsStep() {
        BookingWizardActivity a = readyToReview(BookingWizardState.Mode.RESERVATION);
        int[] expectedSteps = {1, 2, 3, 4, 5, 6};
        for (int i = 0; i < expectedSteps.length; i++) {
            a.goToStep(7);
            idle();
            advance();
            LinearLayout sections = find(a, R.id.layoutReviewSections);
            assertEquals(6, sections.getChildCount());
            View card = sections.getChildAt(i);
            View edit = ((ViewGroup) ((ViewGroup) card).getChildAt(0)).getChildAt(1);
            assertEquals("Edit", text(edit));
            assertTrue("a comfortable touch target", edit.getMinimumHeight() >= 48 * a.getResources().getDisplayMetrics().density - 1);
            edit.performClick();
            idle();
            assertTrue("section " + i + " opens step " + expectedSteps[i] + ": " + stepLabel(a), stepLabel(a).startsWith("Step " + expectedSteps[i] + " of 7"));
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

    // ---- leaving, repeat taps, process death (Part 7) ----

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
        BookingWizardActivity a = atAmenities();
        a.getOnBackPressedDispatcher().onBackPressed();
        idle();
        assertEquals(0, TapTestSupport.alertsShowing());
        assertTrue(stepLabel(a).startsWith("Step 1 of 6"));
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
        a.goToStep(4);
        idle();
        android.os.Bundle saved = new android.os.Bundle();
        controller.saveInstanceState(saved);

        // the process is gone; a new one is started from what was saved
        BookingWizardActivity restored = org.robolectric.Robolectric.buildActivity(BookingWizardActivity.class,
                BookingWizardActivity.newIntent(app, BookingWizardState.Mode.BOOKING)).create(saved).start().resume().visible().get();
        idle();
        assertTrue(stepLabel(restored), stepLabel(restored).startsWith("Step 4 of 6"));
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
    public void everyStepFitsAt360And393dp_inLightAndDark() {
        String[] configs = {"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"};
        try {
            for (String q : configs) {
                org.robolectric.RuntimeEnvironment.setQualifiers("+" + q);
                BookingWizardActivity a = atAmenities();
                int width = a.getResources().getDisplayMetrics().widthPixels;
                LayoutAssert.assertFits(q + " amenities", step(a).getView(), width);
                plusButtons(a).get(0).performClick();
                plusButtons(a).get(4).performClick();
                LayoutAssert.assertFits(q + " amenities with a choice", step(a).getView(), width);

                tapNext(a); // the review sheet
                Dialog sheet = latestDialog();
                LayoutAssert.assertFits(q + " amenities sheet", sheet.findViewById(R.id.reviewSheetRoot), width);
                sheet.dismiss();
                idle();

                a.getState().who = BookingWizardState.Who.NONE;
                a.goToStep(3);
                idle();
                LayoutAssert.assertFits(q + " representative", step(a).getView(), width);
                find(a, R.id.btnWhoOwner).performClick();
                idle();
                LayoutAssert.assertFits(q + " representative: owner", step(a).getView(), width);
                advance();
                find(a, R.id.btnWhoOther).performClick();
                idle();
                ((TextView) otherField(a, R.id.etRepLast)).setText("Dela Cruz");
                LayoutAssert.assertFits(q + " representative: someone else", step(a).getView(), width);
                find(a, R.id.tvWhoError);

                a.getState().adults = 1;
                a.goToStep(4);
                idle();
                find(a, R.id.btnChildrenPlus).performClick();
                LayoutAssert.assertFits(q + " guests", step(a).getView(), width);

                a.goToStep(6);
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
                reservation.goToStep(6);
                idle();
                LayoutAssert.assertFits(q + " payment method", step(reservation).getView(), reservation.getResources().getDisplayMetrics().widthPixels);
            }
        } finally {
            org.robolectric.RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
        }
    }

    @Test
    public void amenities_withNoRoom_showInAnyRoomGroup_andCanBeMovedToARoom() {
        BookingWizardActivity a = onConfirmedStay(app, 5, 2, room("1", 1000, 2), 1);
        a.getState().committedStays().get(0).setAmenityQty(com.example.velocitysuites.BookingWizardState.Stay.ANY_ROOM,
                new com.example.velocitysuites.AddOnAmenity("9", "Extra bed", "Comfort", "A bed", 300, 5), 1);
        a.getState().reloadActiveAmenities();
        a.goToStep(2);
        idle();
        api.<List<AmenityDto>>last("getAmenities").succeed(new ArrayList<>(java.util.Collections.singletonList(amenityDto(9, "Extra bed", "A bed", "300.00", 5))));
        idle();

        String shown = plain(collect(step(a).getView()));
        assertTrue(shown, shown.contains("Any room") && shown.contains("Room 1 (room 1)"));
        // the Any room row comes first: its quantity is 1; the real room's is 0
        List<View> qty = all(step(a).getView(), R.id.tvAmenityRowQty);
        assertEquals("1", text(qty.get(0)));
        assertEquals("0", text(qty.get(1)));
        assertEquals("Amenities ₱300.00 · Grand total ₱2,300.00", liveTotal(a));

        // moving it: one less in Any room, one more in the room - the total does not change
        minusButtons(a).get(0).performClick();
        plusButtons(a).get(1).performClick();
        com.example.velocitysuites.BookingWizardState.Stay stay = a.getState().committedStays().get(0);
        assertFalse(stay.hasAnyRoomAmenities());
        assertEquals(1, stay.amenityQty("1#1", "9"));
        assertEquals("Amenities ₱300.00 · Grand total ₱2,300.00", liveTotal(a));
    }
}
