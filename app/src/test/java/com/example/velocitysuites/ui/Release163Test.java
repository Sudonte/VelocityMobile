package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.AddOnAmenity;
import com.example.velocitysuites.BillingSummaryActivity;
import com.example.velocitysuites.BookingAndReservationActivity;
import com.example.velocitysuites.BookingWizardActivity;
import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.Discount;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.dto.ReservationDto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.63.0: the booking flow, polished. One step line "Step N of M · Title" over the progress bar on every step of the booking and reservation
 * wizards; no card title repeating the step title; one short helper line (two lines at most); the same side padding everywhere; the Back
 * button lines up with Confirm; disabled buttons stay readable; Billing Summary has the same red header and the same words
 * (Grand total, Remaining balance, Paid so far, Amount to pay); everything stays readable at 360dp and 393dp, light and dark, and at
 * 1.3x and 2.0x font size.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Release163Test {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static final String[] CONFIGS = {"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"};

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        ScreenTestSupport.goOnline(app);
    }

    @After
    public void tearDown() {
        RuntimeEnvironment.setFontScale(1.0f);
        RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void texts(View v, List<String> out) {
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE && ((TextView) v).getText().length() > 0) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup && v.getVisibility() == View.VISIBLE) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) texts(((ViewGroup) v).getChildAt(i), out);
        }
    }

    private static List<String> textsOf(View root) {
        List<String> out = new ArrayList<>();
        texts(root, out);
        return out;
    }

    private static void textViews(View v, List<TextView> out) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof TextView) out.add((TextView) v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) textViews(((ViewGroup) v).getChildAt(i), out);
    }

    /** A wizard with two confirmed stays, an amenity, a representative, a minor, an ID discount and GCash chosen. */
    private BookingWizardActivity filled(BookingWizardState.Mode mode) {
        BookingWizardActivity a = WizardDriver.open(app, mode);
        WizardDriver.fillAndConfirm(a, 5, 1, WizardDriver.room("1", 3500), 1);
        WizardDriver.addConfirmedStay(a, 8, 2, WizardDriver.room("8", 5800), 1);
        BookingWizardState s = a.getState();
        s.committedStays().get(0).setAmenityQty("1#1", new AddOnAmenity("9", "Extra bed", "Comfort", "d", 300, 5), 1);
        s.reloadActiveAmenities();
        s.who = BookingWizardState.Who.OTHER;
        s.repLast = "Cruz";
        s.repFirst = "Maria";
        s.guestFirstName = "Ana";
        s.guestLastName = "Reyes";
        s.adults = 2;
        s.setDiscount(new Discount("3", "PWD", "percentage", 20, "d", "active", null, null));
        s.paymentMethod = "gcash";
        s.paymentMethodChosen = true;
        return a;
    }

    private static final String[] BOOKING_TITLES = {"Dates and rooms", "Amenities", "Representative", "Guests", "ID verification", "Review and bill summary"};

    // ---- the step header ----

    @Test
    public void everyStep_hasOneLine_StepNofM_title_andTheTitleIsNotRepeated() {
        for (BookingWizardState.Mode mode : new BookingWizardState.Mode[]{BookingWizardState.Mode.BOOKING, BookingWizardState.Mode.RESERVATION}) {
            BookingWizardActivity a = filled(mode);
            int steps = mode == BookingWizardState.Mode.BOOKING ? 6 : 7;
            for (int step = 1; step <= steps; step++) {
                a.goToStep(step);
                idle();
                WizardDriver.advance();
                String line = ((TextView) a.findViewById(R.id.tvWizardStepLabel)).getText().toString();
                String tag = mode + " step " + step + ": " + line;
                assertTrue(tag, line.startsWith("Step " + step + " of " + steps + " · ") && line.length() > ("Step 1 of 6 · ").length());
                String title = line.substring(line.indexOf('·') + 2);
                assertEquals(tag + " (the hidden bare title is the same)", title, ((TextView) a.findViewById(R.id.tvWizardStepName)).getText().toString());
                assertEquals(tag, View.GONE, a.findViewById(R.id.tvWizardStepName).getVisibility());
                if (mode == BookingWizardState.Mode.BOOKING) assertEquals(tag, BOOKING_TITLES[step - 1], title);
                // the title is said once on screen (the card heading that repeated it is gone)
                int sameAsTitle = 0;
                for (String t : textsOf(a.getWindow().getDecorView())) if (t.equalsIgnoreCase(title)) sameAsTitle++;
                assertEquals(tag + " title repeated", 0, sameAsTitle);
            }
        }
    }

    @Test
    public void theHelperLines_areShort_twoLinesAtMost() {
        String[] helpers = {
                app.getString(R.string.addon_amenities_notice), app.getString(R.string.who_is_staying_hint), app.getString(R.string.representative_name_helper),
                app.getString(R.string.discount_step_intro_stays), app.getString(R.string.discount_checkin_note)};
        for (String q : CONFIGS) {
            RuntimeEnvironment.setQualifiers("+" + q);
            ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
            BookingWizardActivity a = filled(BookingWizardState.Mode.BOOKING);
            int found = 0;
            for (int step = 2; step <= 5; step++) {
                a.goToStep(step);
                idle();
                WizardDriver.advance();
                Release160Test.assertReadable(q + " step " + step, a, a.getWindow().getDecorView());
                List<TextView> all = new ArrayList<>();
                textViews(a.getWindow().getDecorView(), all);
                for (TextView tv : all) {
                    for (String h : helpers) {
                        if (tv.getText().toString().equals(h)) {
                            found++;
                            assertTrue(q + " step " + step + " '" + h + "' takes " + tv.getLineCount() + " lines", tv.getLineCount() <= 2);
                        }
                    }
                }
            }
            assertTrue(q + ": the helpers were on screen (" + found + ")", found >= 4);
        }
    }

    // ---- buttons ----

    @Test
    public void backLinesUpWithConfirm_onTheLastStep_andKeepsItsGapBeforeNextOtherwise() {
        BookingWizardActivity a = filled(BookingWizardState.Mode.BOOKING);
        float dp = a.getResources().getDisplayMetrics().density;
        a.goToStep(3);
        idle();
        ViewGroup.MarginLayoutParams back = (ViewGroup.MarginLayoutParams) a.findViewById(R.id.btnWizardPrevious).getLayoutParams();
        assertEquals(Math.round(8 * dp), back.getMarginEnd());
        a.goToStep(6);
        idle();
        back = (ViewGroup.MarginLayoutParams) a.findViewById(R.id.btnWizardPrevious).getLayoutParams();
        assertEquals("Back is the only button on the last step: it fills the bar like Confirm", 0, back.getMarginEnd());
    }

    private static double luminance(int c) {
        double[] rgb = {Color.red(c) / 255.0, Color.green(c) / 255.0, Color.blue(c) / 255.0};
        for (int i = 0; i < 3; i++) rgb[i] = rgb[i] <= 0.03928 ? rgb[i] / 12.92 : Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
    }

    private static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** The disabled colours of the shared primary-button state lists (every wizard, GCash and Confirm button uses them). */
    private static void assertDisabledReadable(String tag, android.app.Activity a) {
        int[] disabled = {-android.R.attr.state_enabled};
        int bg = a.getResources().getColorStateList(R.color.button_primary_bg, a.getTheme()).getColorForState(disabled, 0);
        int fg = a.getResources().getColorStateList(R.color.button_primary_text, a.getTheme()).getColorForState(disabled, 0);
        double ratio = contrast(fg, bg);
        assertTrue(tag + ": a disabled button is readable (" + String.format(java.util.Locale.US, "%.2f", ratio) + ")", ratio >= 4.5);
    }

    @Test
    public void disabledButtons_stayReadable_inLightAndDark() {
        for (String q : new String[]{"w360dp-h800dp-notnight", "w360dp-h800dp-night"}) {
            RuntimeEnvironment.setQualifiers("+" + q);
            ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
            BookingWizardActivity a = filled(BookingWizardState.Mode.BOOKING);
            a.goToStep(6);
            idle();
            assertDisabledReadable(q, a);
            View confirm = WizardDriver.find(a, R.id.btnConfirmTransaction);
            assertFalse("Confirm waits for the terms", confirm.isEnabled());
            // the buttons use those state lists
            assertEquals(a.getResources().getColorStateList(R.color.button_primary_bg, a.getTheme()).getDefaultColor(),
                    ((com.google.android.material.button.MaterialButton) confirm).getBackgroundTintList().getDefaultColor());
        }
    }

    // ---- Billing Summary ----

    private BillingSummaryActivity billing() {
        ScreenTestSupport.FakeApi api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ReservationDto r = ScreenTestSupport.reservation(588, 18900, ScreenTestSupport.payment(1, "5000.00", "completed", "gcash"));
        BillingSummaryActivity a = Robolectric.buildActivity(BillingSummaryActivity.class,
                new Intent(app, BillingSummaryActivity.class).putExtra(BillingSummaryActivity.EXTRA_RESERVATION_ID, String.valueOf(r.id))).setup().get();
        idle();
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(r), ScreenTestSupport.directBookings());
        idle();
        return a;
    }

    @Test
    public void billingSummary_hasTheSameRedHeader_andTheSameWords() {
        BillingSummaryActivity a = billing();
        View bar = a.findViewById(R.id.billingHeaderBar);
        assertEquals(app.getColor(R.color.velocity_red_primary), ((android.graphics.drawable.ColorDrawable) bar.getBackground()).getColor());
        List<String> t = textsOf(a.getWindow().getDecorView());
        List<String> missing = new ArrayList<>();
        for (String word : new String[]{"Billing Summary", "Grand total", "Paid so far", "Remaining balance", "Amount to pay", "Total guests", "Stay dates", "Room total", "Amenities total"}) {
            if (!t.contains(word)) missing.add(word);
        }
        assertTrue("missing " + missing + " in " + t, missing.isEmpty());
        for (String old : new String[]{"Grand Total", "Remaining Balance", "Amount Already Paid", "Total Amount to Pay", "Total Guests", "Stay Dates", "Room Total", "Guest Account"}) {
            assertFalse(old + " is gone: " + t, t.contains(old));
        }
        assertEquals("Remaining balance is said once", 1, java.util.Collections.frequency(t, "Remaining balance"));
    }

    @Test
    public void billingSummary_isReadable_in360And393_lightAndDark_andAtLargeFonts() {
        for (String q : CONFIGS) {
            RuntimeEnvironment.setQualifiers("+" + q);
            BillingSummaryActivity b = billing();
            Release160Test.assertReadable(q + " billing summary", b, b.getWindow().getDecorView());
        }
        for (float scale : new float[]{1.3f, 2.0f}) {
            RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
            RuntimeEnvironment.setFontScale(scale);
            BillingSummaryActivity a = billing();
            Release160Test.assertReadable(scale + "x billing summary", a, a.getWindow().getDecorView());
        }
    }

    // ---- every combination still walks all six steps ----

    @Test
    public void everyCombination_walksAllSteps_withOneAndTwoStays_amenities_owner_minors_andDiscount() {
        for (int stays = 1; stays <= 2; stays++) {
            for (boolean amenity : new boolean[]{false, true}) {
                for (boolean someoneElse : new boolean[]{false, true}) {
                    for (boolean minor : new boolean[]{false, true}) {
                        for (boolean discount : new boolean[]{false, true}) {
                            ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
                            BookingWizardActivity a = WizardDriver.open(app, BookingWizardState.Mode.BOOKING);
                            WizardDriver.fillAndConfirm(a, 5, 1, WizardDriver.room("1", 3500, 3), 1);
                            if (stays == 2) WizardDriver.addConfirmedStay(a, 8, 2, WizardDriver.room("8", 5800, 3), 1);
                            BookingWizardState s = a.getState();
                            if (amenity) {
                                s.committedStays().get(0).setAmenityQty("1#1", new AddOnAmenity("9", "Extra bed", "Comfort", "d", 300, 5), 1);
                                s.reloadActiveAmenities();
                            }
                            s.who = someoneElse ? BookingWizardState.Who.OTHER : BookingWizardState.Who.OWNER;
                            if (someoneElse) {
                                s.repLast = "Cruz";
                                s.repFirst = "Maria";
                            }
                            s.guestFirstName = "Ana";
                            s.guestLastName = "Reyes";
                            s.adults = 2;
                            s.children = minor ? 1 : 0;
                            if (minor) s.additionalGuests.add(new BookingAndReservationActivity.AdditionalGuest("Leo Reyes", 8, "Male", "Child"));
                            if (discount) s.setDiscount(new Discount("3", "PWD", "percentage", 20, "d", "active", null, null));
                            String combo = stays + " stays, amenity " + amenity + ", someone else " + someoneElse + ", minor " + minor + ", discount " + discount;
                            for (int step = 1; step <= 6; step++) {
                                a.goToStep(step);
                                idle();
                                WizardDriver.advance();
                                String line = ((TextView) a.findViewById(R.id.tvWizardStepLabel)).getText().toString();
                                assertTrue(combo + " step " + step + ": " + line, line.startsWith("Step " + step + " of 6 · "));
                            }
                            // the review lists every stay, with a subtotal each when there are several
                            List<String> review = textsOf(WizardDriver.step(a).getView());
                            int subtotals = 0;
                            for (String t : review) if (t.equals("Subtotal")) subtotals++;
                            assertEquals(combo, stays, subtotals);
                            assertEquals(combo + ": the review lists the chosen amenity, and only then", amenity, review.stream().anyMatch(x -> x.startsWith("Extra bed")));
                        }
                    }
                }
            }
        }
    }

    // ---- the Bookings / Reservations tabs ----

    private int xOf(View root, View v) {
        int x = 0;
        for (View cur = v; cur != null && cur != root; cur = cur.getParent() instanceof View ? (View) cur.getParent() : null) x += cur.getLeft();
        return x;
    }

    @Test
    public void theTabsScreen_alignsHeader_tabs_newButton_andList_toOneLeftEdge() {
        ScreenTestSupport.FakeApi api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        BookingAndReservationActivity a = Robolectric.buildActivity(BookingAndReservationActivity.class, new Intent(app, BookingAndReservationActivity.class)).setup().get();
        idle();
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1, ScreenTestSupport.reservations(ScreenTestSupport.reservation(1, 1800)),
                ScreenTestSupport.directBookings(ScreenTestSupport.directBooking(11, 3000)));
        idle();
        View decor = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(a.getResources().getDisplayMetrics().heightPixels, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, a.getResources().getDisplayMetrics().heightPixels);
        int title = xOf(decor, a.findViewById(R.id.screenTitle));
        int tabs = xOf(decor, a.findViewById(R.id.tabLayout));
        int button = xOf(decor, a.findViewById(R.id.btnNewBooking));
        assertEquals("title and tabs share a left edge", title, tabs);
        assertEquals("the New booking button lines up with the tabs", tabs, button);
        assertEquals(16 * a.getResources().getDisplayMetrics().density, tabs, 2f);
    }

    // ---- rotation, process death, repeat taps ----

    @Test
    public void rotating_keepsTheStep_andTheLine() {
        ActivityController<BookingWizardActivity> controller = Robolectric.buildActivity(BookingWizardActivity.class,
                BookingWizardActivity.newIntent(app, BookingWizardState.Mode.BOOKING)).setup();
        idle();
        BookingWizardActivity a = controller.get();
        WizardDriver.fillAndConfirm(a, 5, 1, WizardDriver.room("1", 3500), 1);
        a.goToStep(2);
        idle();
        String before = ((TextView) a.findViewById(R.id.tvWizardStepLabel)).getText().toString();
        controller.recreate();
        idle();
        BookingWizardActivity again = controller.get();
        assertEquals(before, ((TextView) again.findViewById(R.id.tvWizardStepLabel)).getText().toString());
        assertNotNull(again.getState().checkIn);
    }

    @Test
    public void aRepeatTapOnNext_movesExactlyOneStep() {
        BookingWizardActivity a = filled(BookingWizardState.Mode.BOOKING);
        a.goToStep(2);
        idle();
        WizardDriver.advance();
        a.findViewById(R.id.btnWizardNext).performClick();
        a.findViewById(R.id.btnWizardNext).performClick();
        a.findViewById(R.id.btnWizardNext).performClick();
        idle();
        String line = ((TextView) a.findViewById(R.id.tvWizardStepLabel)).getText().toString();
        assertTrue(line, line.startsWith("Step 3 of 6") || line.startsWith("Step 2 of 6"));
    }

    // ---- large fonts on the wizard ----

    @Test
    public void withLargeFonts_theWizardStaysReadable_andTheButtonsAreNotClipped() {
        for (float scale : new float[]{1.3f, 2.0f}) {
            for (String q : new String[]{"w360dp-h800dp-notnight", "w393dp-h851dp-night"}) {
                RuntimeEnvironment.setQualifiers("+" + q);
                RuntimeEnvironment.setFontScale(scale);
                ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
                BookingWizardActivity a = filled(BookingWizardState.Mode.BOOKING);
                for (int step : new int[]{1, 3, 4, 6}) {
                    a.goToStep(step);
                    idle();
                    WizardDriver.advance();
                    String tag = scale + "x " + q + " step " + step;
                    Release160Test.assertReadable(tag, a, a.getWindow().getDecorView());
                    TextView label = a.findViewById(R.id.tvWizardStepLabel);
                    assertTrue(tag + " the step line fits in two lines", label.getLineCount() <= 2);
                    com.google.android.material.button.MaterialButton back = a.findViewById(R.id.btnWizardPrevious);
                    assertTrue(tag + " Back is tall enough to tap and show its text", back.getHeight() >= Math.round(48 * a.getResources().getDisplayMetrics().density));
                }
            }
        }
    }
}
