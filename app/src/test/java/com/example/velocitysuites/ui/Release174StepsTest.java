package com.example.velocitysuites.ui;

import static com.example.velocitysuites.ui.WizardDriver.addConfirmedStay;
import static com.example.velocitysuites.ui.WizardDriver.advance;
import static com.example.velocitysuites.ui.WizardDriver.answer;
import static com.example.velocitysuites.ui.WizardDriver.collect;
import static com.example.velocitysuites.ui.WizardDriver.day;
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
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingAndReservationActivity;
import com.example.velocitysuites.BookingWizardActivity;
import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.PendingWizardRooms;
import com.example.velocitysuites.R;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.network.dto.AmenityDto;
import com.example.velocitysuites.network.dto.RoomsResponse;
import com.example.velocitysuites.ui.ScreenTestSupport.FakeApi;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.74.0 Parts 2 to 4 (the same steps and words as the guest web): a booking is 4 steps - Dates and rooms, Amenities (optional: "Included
 * with your room" at 0 and "Additional amenities" that are paid, per date range and room), Representative and guests, Review and bill
 * summary; a reservation is 4 steps with no amenities - Dates and rooms, Representative and guests, Payment method, Review and bill
 * summary. Step 1 goes to "Confirm these dates" by itself when the guest arrives with dates and rooms, and the representative section
 * goes to Guests once it is valid. The amount to pay now is a share of the grand total, paid amenities included.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h640dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Release174StepsTest {

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

    @After
    public void tearDown() {
        PendingWizardRooms.consume();
        PendingWizardRooms.consumeDates();
        motion(true);
    }

    /** Animations off ("Remove animations"): a scroll is then a plain jump, which also makes it readable right away. */
    private void motion(boolean on) {
        Settings.Global.putFloat(app.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, on ? 1f : 0f);
    }

    // ---- helpers ----

    private static AmenityDto amenityDto(long id, String name, String price, int stock) {
        AmenityDto d = new AmenityDto();
        d.id = id;
        d.amenity_name = name;
        d.category = "Comfort";
        d.description = "About " + name;
        d.charge = price;
        d.quantity = stock;
        return d;
    }

    private static List<View> all(View root, int id) {
        List<View> found = new ArrayList<>();
        if (root.getId() == id) found.add(root);
        if (root instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) found.addAll(all(((ViewGroup) root).getChildAt(i), id));
        }
        return found;
    }

    private static String stepLabel(BookingWizardActivity a) {
        return plain(text(a.findViewById(R.id.tvWizardStepLabel)));
    }

    private static void tapNext(BookingWizardActivity a) {
        advance();
        a.findViewById(R.id.btnWizardNext).performClick();
        idle();
    }

    private static void layout(BookingWizardActivity a) {
        View decor = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels, h = a.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
        idle();
    }

    /**
     * A booking on its Amenities step: two date ranges - 2 x Room 1 for 2 nights at 1,000 (4,000) and 1 x Room 2 for one night at 3,000 -
     * and a catalog of one paid amenity listed first by the server, two included ones and another paid one.
     */
    private BookingWizardActivity atAmenities() {
        BookingWizardActivity a = onConfirmedStay(app, 5, 2, room("1", 1000, 2), 2);
        addConfirmedStay(a, 12, 1, room("2", 3000, 2), 1);
        a.goToStep(2);
        idle();
        List<AmenityDto> catalog = new ArrayList<>();
        catalog.add(amenityDto(9, "Extra bed", "300.00", 5));
        catalog.add(amenityDto(20, "Extra pillow", "0.00", 10));
        catalog.add(amenityDto(21, "Slippers", "0.00", 10));
        catalog.add(amenityDto(10, "Breakfast", "150.00", 3));
        api.<List<AmenityDto>>last("getAmenities").succeed(catalog);
        idle();
        return a;
    }

    private static List<View> plus(BookingWizardActivity a) {
        return all(step(a).getView(), R.id.btnAmenityRowPlus);
    }

    private static String liveTotal(BookingWizardActivity a) {
        return plain(text(find(a, R.id.tvAmenitiesLiveTotal)));
    }

    // ---- Part 2: a booking is 4 steps

    @Test
    public void aBooking_isFourSteps_datesAndRooms_amenities_representativeAndGuests_review() {
        BookingWizardActivity a = onConfirmedStay(app, 5, 2, room("1", 1000, 2), 1);
        assertEquals("Step 1 of 4 · Dates and rooms", stepLabel(a));
        String[] titles = {"Dates and rooms", "Amenities", "Representative and guests", "Review and bill summary"};
        for (int step = 2; step <= 4; step++) {
            a.goToStep(step);
            idle();
            assertEquals("Step " + step + " of 4 · " + titles[step - 1], stepLabel(a));
        }
        assertEquals("the last step has its own Confirm", View.GONE, a.findViewById(R.id.btnWizardNext).getVisibility());
    }

    @Test
    public void stepOne_next_asksProceedToAmenities_forABooking() {
        BookingWizardActivity a = onConfirmedStay(app, 5, 2, room("1", 1000, 2), 1);
        tapNext(a);
        latestDialog().findViewById(R.id.btnReviewContinue).performClick();
        idle();
        assertTrue(dialogText(latestDialog()), dialogText(latestDialog()).contains("Proceed to Amenities?"));
        answer(true);
        assertEquals("Step 2 of 4 · Amenities", stepLabel(a));
        assertEquals("the amenities are asked for with no price filter, so the included ones arrive", 1, api.count("getAmenities"));
        assertNull(api.lastArgs.get("getAmenities")[0]);
    }

    @Test
    public void amenities_areListedPerDateRangeAndRoom_inTwoGroups_includedFirst() {
        BookingWizardActivity a = atAmenities();
        String shown = plain(collect(step(a).getView()));
        assertTrue(shown, shown.contains("Optional. Amenities are added to your total; you can request more after booking."));
        assertTrue(shown, shown.contains("→") && shown.contains("Room 1 (room 1)") && shown.contains("Room 1 (room 2)") && shown.contains("Room 2 (room 1)"));

        ViewGroup list = find(a, R.id.layoutAmenitiesItemsContainer);
        List<String> order = new ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            if (v.getTag() instanceof String) order.add((String) v.getTag());
        }
        List<String> oneRoom = java.util.Arrays.asList("group_included", "amenity_included", "amenity_included", "group_paid", "amenity_paid", "amenity_paid");
        List<String> expected = new ArrayList<>();
        for (int room = 0; room < 3; room++) expected.addAll(oneRoom);
        assertEquals("each of the three rooms: Included with your room, then Additional amenities", expected, order);
        assertEquals(3, countText(list, "Included with your room"));
        assertEquals(3, countText(list, "Additional amenities"));

        List<View> names = all(step(a).getView(), R.id.tvAmenityRowName), prices = all(step(a).getView(), R.id.tvAmenityRowPrice);
        assertEquals("Extra pillow", text(names.get(0)));
        assertEquals("an Included label and a price of 0", "Included · ₱0.00", plain(text(prices.get(0))));
        assertEquals("Slippers", text(names.get(1)));
        assertEquals("Extra bed", text(names.get(2)));
        assertEquals("₱300.00 each", plain(text(prices.get(2))));
        assertEquals("Breakfast", text(names.get(3)));
        assertEquals("a stepper on every row", 12, plus(a).size());
    }

    private static int countText(View root, String wanted) {
        int n = root instanceof TextView && wanted.contentEquals(((TextView) root).getText()) ? 1 : 0;
        if (root instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) n += countText(((ViewGroup) root).getChildAt(i), wanted);
        }
        return n;
    }

    @Test
    public void amenities_includedOnesAddNothing_paidOnesAddToTheGrandTotal_live() {
        BookingWizardActivity a = atAmenities();
        assertEquals("Amenities ₱0.00 · Grand total ₱7,000.00", liveTotal(a));

        plus(a).get(0).performClick(); // Extra pillow (included), first room
        plus(a).get(0).performClick();
        assertEquals("included amenities add 0", "Amenities ₱0.00 · Grand total ₱7,000.00", liveTotal(a));
        assertEquals("2 × Included", plain(text(all(step(a).getView(), R.id.tvAmenityRowLine).get(0))));
        assertEquals(0.0, a.getState().amenitiesTotal(), 0.001);

        plus(a).get(2).performClick(); // Extra bed (paid), first room
        plus(a).get(2).performClick();
        assertEquals("Amenities ₱600.00 · Grand total ₱7,600.00", liveTotal(a));
        assertEquals("2 × ₱300.00 = ₱600.00", plain(text(all(step(a).getView(), R.id.tvAmenityRowLine).get(2))));
        plus(a).get(11).performClick(); // Breakfast (paid), the second date range's room
        assertEquals("Amenities ₱750.00 · Grand total ₱7,750.00", liveTotal(a));
        assertEquals("the wizard's own bar steps aside for the live total", View.GONE, a.findViewById(R.id.wizardSummaryBar).getVisibility());

        // kept by room line and slot, the way a booking has stored them since 1.68
        BookingWizardState.Stay first = a.getState().allStays().get(0);
        assertEquals(2, first.amenityQty("1#1", "20"));
        assertEquals(2, first.amenityQty("1#1", "9"));
        assertEquals(0, first.amenityQty("1#2", "9"));
        assertEquals(1, a.getState().allStays().get(1).amenityQty("2#1", "10"));
        assertTrue("sent as stays[] with the room named", a.getState().sendsAsStays());
    }

    @Test
    public void amenities_skip_asksFirst_andContinuesWithNone() {
        BookingWizardActivity a = atAmenities();
        assertEquals("Skip", text(find(a, R.id.btnSkipAmenities)));
        plus(a).get(2).performClick();
        assertTrue(a.getState().amenitiesTotal() > 0);
        advance();
        find(a, R.id.btnSkipAmenities).performClick();
        idle();
        String ask = dialogText(latestDialog());
        assertTrue(ask, ask.contains("Continue without additional amenities?") && ask.contains("No amenities · Total ₱7,000.00"));
        answer(false);
        assertEquals("Step 2 of 4 · Amenities", stepLabel(a));
        assertTrue("No keeps what was chosen", a.getState().amenitiesTotal() > 0);

        advance();
        find(a, R.id.btnSkipAmenities).performClick();
        idle();
        answer(true);
        assertEquals("Step 3 of 4 · Representative and guests", stepLabel(a));
        assertEquals(0.0, a.getState().amenitiesTotal(), 0.001);
        for (BookingWizardState.Stay s : a.getState().allStays()) assertTrue(s.amenities.isEmpty());
    }

    @Test
    public void amenities_next_reviewsWhatWasChosen_thenAsks_thenOpensRepresentativeAndGuests() {
        BookingWizardActivity a = atAmenities();
        plus(a).get(0).performClick(); // 1 Extra pillow (included)
        plus(a).get(2).performClick(); // 1 Extra bed (300)
        tapNext(a);
        Dialog sheet = latestDialog();
        String shown = dialogText(sheet);
        assertTrue(shown, shown.contains("Review your amenities") && shown.contains("Room 1 (room 1)"));
        assertTrue("the included one reads Included, not an amount: " + shown, shown.contains("Extra pillow × 1") && shown.contains("Included"));
        assertTrue(shown, shown.contains("Extra bed × 1") && shown.contains("₱300.00") && shown.contains("₱7,300.00"));
        sheet.findViewById(R.id.btnReviewContinue).performClick();
        idle();
        String ask = dialogText(latestDialog());
        assertTrue(ask, ask.contains("Proceed with these amenities?") && ask.contains("Amenities ₱300.00 · Grand total ₱7,300.00"));
        answer(true);
        assertEquals("Step 3 of 4 · Representative and guests", stepLabel(a));
    }

    @Test
    public void amenities_nextWithNothingChosen_asksToContinueWithout() {
        BookingWizardActivity a = atAmenities();
        tapNext(a);
        String ask = dialogText(latestDialog());
        assertTrue(ask, ask.contains("Continue without additional amenities?") && ask.contains("No amenities · Total ₱7,000.00"));
        answer(true);
        assertEquals("Step 3 of 4 · Representative and guests", stepLabel(a));
    }

    @Test
    public void amenities_whenTheHotelOffersNone_theStepSaysSo_andStillContinues() {
        BookingWizardActivity a = onConfirmedStay(app, 5, 2, room("1", 1000, 2), 1);
        a.goToStep(2);
        idle();
        api.<List<AmenityDto>>last("getAmenities").succeed(new ArrayList<>());
        idle();
        assertEquals(View.VISIBLE, find(a, R.id.layoutAmenitiesEmptyState).getVisibility());
        assertEquals("No amenities are offered right now.", text(find(a, R.id.tvAmenitiesEmptyState)));
        tapNext(a);
        answer(true);
        assertEquals("Step 3 of 4 · Representative and guests", stepLabel(a));
    }

    // ---- Part 3: a reservation is 4 steps, with no amenities

    @Test
    public void aReservation_isFourSteps_withNoAmenitiesStep() {
        BookingWizardActivity a = open(app, BookingWizardState.Mode.RESERVATION);
        WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000, 2), 1);
        assertEquals("Step 1 of 4 · Dates and rooms", stepLabel(a));
        tapNext(a);
        latestDialog().findViewById(R.id.btnReviewContinue).performClick();
        idle();
        assertTrue(dialogText(latestDialog()), dialogText(latestDialog()).contains("Proceed to Representative and guests?"));
        answer(true);
        assertEquals("Step 2 of 4 · Representative and guests", stepLabel(a));
        assertEquals("no amenities are loaded for a reservation", 0, api.count("getAmenities"));

        a.goToStep(3);
        idle();
        assertEquals("Step 3 of 4 · Payment method", stepLabel(a));
        a.getState().who = BookingWizardState.Who.OWNER;
        a.getState().paymentMethod = "cash";
        a.getState().paymentMethodChosen = true;
        a.goToStep(4);
        idle();
        assertEquals("Step 4 of 4 · Review and bill summary", stepLabel(a));
        String sections = plain(collect(find(a, R.id.layoutReviewSections)));
        assertFalse("a reservation's review has no amenities section: " + sections, sections.contains("Amenities"));
        assertTrue(sections, sections.contains("Payment method") && sections.contains("Cash"));
    }

    // ---- Step 1 goes to "Confirm these dates"; the representative goes to Guests

    private BookingWizardActivity arrive(BookingWizardState.Mode mode, boolean withDates) {
        List<Room> rooms = new ArrayList<>();
        Room r = room("1", 1200);
        rooms.add(r);
        rooms.add(r);
        PendingWizardRooms.set(rooms);
        if (withDates) PendingWizardRooms.setDates(day(5), day(7));
        BookingWizardActivity a = Robolectric.buildActivity(BookingWizardActivity.class, BookingWizardActivity.newIntent(app, mode)).setup().get();
        idle();
        layout(a);
        return a;
    }

    private void roomsArrive(BookingWizardActivity a) {
        api.<RoomsResponse>last("getRoomsFresh").succeed(LandingFixtures.rooms(LandingFixtures.roomType(1, "Room 1", "1200.00", 5),
                LandingFixtures.roomType(2, "Room 2", "1800.00", 5), LandingFixtures.roomType(3, "Room 3", "2500.00", 5), LandingFixtures.roomType(4, "Room 4", "3200.00", 5)));
        idle();
        layout(a);
    }

    private static boolean onScreen(NestedScrollView scroll, View v) {
        int top = 0;
        for (View cur = v; cur != scroll; cur = (View) cur.getParent()) top += cur.getTop();
        return top >= scroll.getScrollY() && top + v.getHeight() <= scroll.getScrollY() + scroll.getHeight();
    }

    @Test
    public void stepOne_arrivingWithDatesAndRooms_scrollsToConfirmTheseDates() {
        for (BookingWizardState.Mode mode : BookingWizardState.Mode.values()) {
            motion(false); // a plain jump, the way it is with animations off
            BookingWizardActivity a = arrive(mode, true);
            NestedScrollView scroll = find(a, R.id.staysScroll);
            assertEquals(mode + ": at the top while the rooms are being checked", 0, scroll.getScrollY());
            roomsArrive(a);

            View confirm = find(a, R.id.btnConfirmStay);
            assertEquals(View.VISIBLE, confirm.getVisibility());
            assertEquals("Confirm these dates", text(confirm));
            assertTrue(mode + ": the page moved down by itself (scrollY=" + scroll.getScrollY() + ")", scroll.getScrollY() > 0);
            assertTrue(mode + ": Confirm these dates is in view", onScreen(scroll, confirm));
            PendingWizardRooms.consume();
        }
    }

    @Test
    public void stepOne_withAnimationsOn_theScrollIsAnimated_notAJump() {
        motion(true);
        BookingWizardActivity a = arrive(BookingWizardState.Mode.BOOKING, true);
        roomsArrive(a);
        NestedScrollView scroll = find(a, R.id.staysScroll);
        // a smooth scroll moves a little on every drawn frame, and this test draws none: the page has not jumped (with animations off,
        // the same moment already shows it at "Confirm these dates" - see the test above)
        assertEquals("animated, so not there in one step", 0, scroll.getScrollY());
        assertEquals(View.VISIBLE, find(a, R.id.btnConfirmStay).getVisibility());
    }

    @Test
    public void stepOne_arrivingWithoutDates_staysOnTheDateFields() {
        motion(false);
        BookingWizardActivity a = arrive(BookingWizardState.Mode.BOOKING, false);
        NestedScrollView scroll = find(a, R.id.staysScroll);
        assertEquals("nothing to confirm yet: the dates come first", 0, scroll.getScrollY());
        assertEquals(View.GONE, find(a, R.id.btnConfirmStay).getVisibility());
    }

    private BookingWizardActivity atRepresentative(BookingWizardState.Mode mode) {
        BookingWizardActivity a = open(app, mode);
        WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000, 4), 1);
        a.goToStep(mode == BookingWizardState.Mode.BOOKING ? 3 : 2);
        idle();
        layout(a);
        return a;
    }

    @Test
    public void representative_choosingAccountOwner_scrollsDownToGuests() {
        for (BookingWizardState.Mode mode : BookingWizardState.Mode.values()) {
            motion(false);
            BookingWizardActivity a = atRepresentative(mode);
            NestedScrollView page = (NestedScrollView) step(a).getView();
            assertEquals(0, page.getScrollY());
            find(a, R.id.btnWhoOwner).performClick();
            idle();
            layout(a);
            int guestsTop = find(a, R.id.guestsContainer).getTop();
            int furthest = page.getChildAt(0).getHeight() - page.getHeight();
            assertTrue(mode + ": there is something to scroll", furthest > 0);
            assertEquals(mode + ": the Guests section was brought up", Math.min(guestsTop, furthest), page.getScrollY());
        }
    }

    @Test
    public void representative_someoneElse_scrollsToGuests_onlyOnceTheNamesAreValid() {
        motion(false);
        BookingWizardActivity a = atRepresentative(BookingWizardState.Mode.BOOKING);
        NestedScrollView page = (NestedScrollView) step(a).getView();
        find(a, R.id.btnWhoOther).performClick();
        idle();
        layout(a);
        assertEquals("names are still needed: the page stays on them", 0, page.getScrollY());

        TextView last = find(a, R.id.otherFields).findViewById(R.id.etRepLast), first = find(a, R.id.otherFields).findViewById(R.id.etRepFirst);
        last.requestFocus();
        last.setText("Dela Cruz");
        first.requestFocus(); // on to the next name field: nothing moves yet
        idle();
        layout(a);
        assertEquals("the first name is still missing", 0, page.getScrollY());

        first.setText("Juan");
        first.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE); // Done on the keyboard
        idle();
        layout(a);
        assertTrue("valid names: on to Guests (scrollY=" + page.getScrollY() + ")", page.getScrollY() > 0);
        assertNull("nothing is validated out loud by the scroll", ((com.google.android.material.textfield.TextInputLayout) find(a, R.id.otherFields).findViewById(R.id.tilRepLast)).getError());
    }

    @Test
    public void representative_nextStillValidatesBothSections() {
        BookingWizardActivity a = atRepresentative(BookingWizardState.Mode.BOOKING);
        tapNext(a);
        assertEquals("no question: nothing is chosen", 0, TapTestSupport.alertsShowing());
        assertEquals(View.VISIBLE, find(a, R.id.tvWhoError).getVisibility());
        assertEquals("Step 3 of 4 · Representative and guests", stepLabel(a));

        find(a, R.id.btnWhoOwner).performClick();
        idle();
        tapNext(a);
        assertTrue(dialogText(latestDialog()), dialogText(latestDialog()).contains("Continue with these details?"));
        answer(true);
        assertEquals("Step 4 of 4 · Review and bill summary", stepLabel(a));
    }

    // ---- Step 4: the review, and Part 4: the amount to pay now

    /** Two date ranges (7,000 of rooms), one included and one paid amenity in the first room (600), a representative and a minor. */
    private BookingWizardActivity atReviewWithAmenities() {
        BookingWizardActivity a = onConfirmedStay(app, 5, 2, room("1", 1000, 3), 2);
        addConfirmedStay(a, 12, 1, room("2", 3000, 3), 1);
        BookingWizardState s = a.getState();
        s.committedStays().get(0).setAmenityQty("1#1", new com.example.velocitysuites.AddOnAmenity("20", "Extra pillow", "Comfort", "d", 0, 10), 2);
        s.committedStays().get(0).setAmenityQty("1#1", new com.example.velocitysuites.AddOnAmenity("9", "Extra bed", "Comfort", "d", 300, 5), 2);
        s.reloadActiveAmenities();
        s.who = BookingWizardState.Who.OTHER;
        s.repLast = "Dela Cruz";
        s.repFirst = "Juan";
        s.adults = 2;
        s.children = 1;
        s.additionalGuests.add(new BookingAndReservationActivity.AdditionalGuest("Child 1", 5, null, "Child"));
        a.goToStep(4);
        idle();
        return a;
    }

    @Test
    public void review_showsStaysRoomsAmenitiesIncludedAndPaid_representativeGuests_theBillAndTheNote() {
        BookingWizardActivity a = atReviewWithAmenities();
        String sections = plain(collect(find(a, R.id.layoutReviewSections)));
        for (String needed : new String[]{"Stays", "2 × Room 1 × ₱1,000.00 × 2 nights", "1 × Room 2 × ₱3,000.00 × 1 night",
                "Amenities", "Room 1 (room 1)", "Extra pillow × 2", "Included", "Extra bed × 2", "₱600.00",
                "Representative", "Someone else", "Dela Cruz, Juan", "Guests", "Adults", "Minors"}) {
            assertTrue("missing '" + needed + "' in:\n" + sections, sections.contains(needed));
        }
        assertFalse("an included amenity never shows an amount of zero", sections.contains("₱0.00"));

        String bill = plain(collect(find(a, R.id.cardBookingSummary)));
        assertTrue(bill, bill.contains("Room charges") && bill.contains("₱7,000.00"));
        assertTrue("only the paid amenities are on the bill: " + bill, bill.contains("Amenities") && bill.contains("₱600.00"));
        assertEquals("₱7,600.00", plain(text(find(a, R.id.tvSummaryTotal))));
        assertTrue(bill, bill.contains("PWD and Senior Citizen discounts are verified and applied by the front desk at check-in."));
    }

    @Test
    public void theAmountToPayNow_isAShareOfTheGrandTotal_paidAmenitiesIncluded() {
        BookingWizardActivity a = atReviewWithAmenities();
        String bill = plain(collect(find(a, R.id.cardBookingSummary)));
        // 20% and 50% of 7,600 (7,000 of rooms + 600 of paid amenities; the included ones add nothing)
        assertTrue(bill, bill.contains("Pay now (20% to 50%)") && bill.contains("₱1,520.00 – ₱3,800.00"));
        assertTrue(bill, bill.contains("Balance to pay at the hotel") && bill.contains("₱3,800.00 – ₱6,080.00"));
    }

    @Test
    public void review_withNoAmenitiesChosen_aBookingSaysSo_andCanGoBackToTheStep() {
        BookingWizardActivity a = onConfirmedStay(app, 5, 2, room("1", 1000, 3), 1);
        a.getState().who = BookingWizardState.Who.OWNER;
        a.goToStep(4);
        idle();
        String sections = plain(collect(find(a, R.id.layoutReviewSections)));
        assertTrue(sections, sections.contains("Amenities") && sections.contains("No additional amenities"));
        assertFalse("no amenities line of zero on the bill", plain(collect(find(a, R.id.cardBookingSummary))).contains("Amenities"));
    }

    @Test
    public void review_theEditLinks_openTheRightStep_inBothFlows() {
        // a booking: Stays 1, Amenities 2, Representative 3, Guests 3
        int[][] expected = {{1, 2, 3, 3}, {1, 2, 2, 3}};
        BookingWizardState.Mode[] modes = {BookingWizardState.Mode.BOOKING, BookingWizardState.Mode.RESERVATION};
        for (int m = 0; m < modes.length; m++) {
            int count = expected[m].length;
            for (int i = 0; i < count; i++) {
                BookingWizardActivity a = open(app, modes[m]);
                WizardDriver.fillAndConfirm(a, 5, 2, room("1", 1000, 3), 1);
                a.getState().who = BookingWizardState.Who.OWNER;
                a.getState().paymentMethod = "cash";
                a.getState().paymentMethodChosen = true;
                a.goToStep(4);
                idle();
                ViewGroup sections = find(a, R.id.layoutReviewSections);
                assertEquals(modes[m] + ": the sections", count, sections.getChildCount());
                View edit = sections.getChildAt(i).findViewWithTag("edit");
                assertNotNull(modes[m] + " section " + i + " has an Edit link", edit);
                advance();
                edit.performClick();
                idle();
                assertTrue(modes[m] + " section " + i + " opens step " + expected[m][i] + ": " + stepLabel(a), stepLabel(a).startsWith("Step " + expected[m][i] + " of 4"));
            }
        }
    }

    // ---- layout

    @Test
    public void theAmenitiesStep_andItsReviewSheet_fitAndAreReadable_at360And393dp_inLightAndDark() {
        String[] configs = {"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"};
        try {
            for (String q : configs) {
                org.robolectric.RuntimeEnvironment.setQualifiers("+" + q);
                api = new FakeApi();
                ScreenTestSupport.freshRepository(app, api);
                BookingWizardActivity a = atAmenities();
                plus(a).get(0).performClick(); // an included one
                plus(a).get(2).performClick(); // a paid one
                layout(a);
                int width = a.getResources().getDisplayMetrics().widthPixels;
                LayoutAssert.assertFits(q + " amenities", step(a).getView(), width);
                Release160Test.assertReadable(q + " amenities", a, a.getWindow().getDecorView());
                tapNext(a);
                Dialog sheet = latestDialog();
                LayoutAssert.assertFits(q + " amenities review sheet", sheet.findViewById(R.id.reviewSheetRoot), width);
                sheet.dismiss();
                idle();

                // the review with included and paid amenities
                a.getState().who = BookingWizardState.Who.OWNER;
                a.goToStep(4);
                idle();
                layout(a);
                LayoutAssert.assertFits(q + " review with amenities", step(a).getView(), width);
                Release160Test.assertReadable(q + " review with amenities", a, a.getWindow().getDecorView());
            }
        } finally {
            org.robolectric.RuntimeEnvironment.setQualifiers("+w360dp-h640dp-notnight");
        }
    }

    @Test
    public void theAmenitiesStep_fitsAt360dp_andItsButtonsAreComfortableToTap() {
        BookingWizardActivity a = atAmenities();
        plus(a).get(0).performClick();
        plus(a).get(2).performClick();
        layout(a);
        int width = a.getResources().getDisplayMetrics().widthPixels;
        LayoutAssert.assertFits("amenities at 360dp", step(a).getView(), width);
        float density = a.getResources().getDisplayMetrics().density;
        assertTrue("Skip is a 48dp target", find(a, R.id.btnSkipAmenities).getHeight() >= 48 * density - 1);
        for (View p : plus(a)) assertTrue("a stepper of at least 40dp", p.getWidth() >= 40 * density - 1 && p.getHeight() >= 40 * density - 1);
    }
}
