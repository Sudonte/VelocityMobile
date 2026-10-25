package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.LoginActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.RegistrationActivity;
import com.example.velocitysuites.WelcomeActivity;
import com.example.velocitysuites.WelcomeEffects;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.64.0: the Welcome screen redesign. Smaller logo and type so it all fits one 393dp phone without scrolling (and scrolls gracefully on
 * smaller phones and large fonts); four equal, tappable cards with one-line titles; each opens a bottom sheet with its real step-by-step
 * content; one sheet at a time, restored after rotation, closed by Back before the screen; readable at 360dp and 393dp, light and dark, and
 * at 1.3x and 2.0x font size.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Release164Test {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static final String[] CONFIGS = {"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"};
    private static final int[] CARDS = {R.id.welcomeCardRooms, R.id.welcomeCardPay, R.id.welcomeCardTrack, R.id.welcomeCardSupport};
    private static final int[] TITLES = {R.id.welcomeCardRoomsTitle, R.id.welcomeCardPayTitle, R.id.welcomeCardTrackTitle, R.id.welcomeCardSupportTitle};
    private static final String[] CARD_TITLES = {"Easy booking", "GCash payments", "Track transactions", "Front-desk help"};
    private static final int[] STEP_COUNTS = {7, 5, 5, 3};

    @After
    public void tearDown() {
        WelcomeEffects.motion = false;
        RuntimeEnvironment.setFontScale(1.0f);
        RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
    }

    private static WelcomeActivity launch() {
        WelcomeActivity a = Robolectric.buildActivity(WelcomeActivity.class).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        return a;
    }

    private static void layout(android.app.Activity a) {
        View decor = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels;
        int h = a.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
    }

    private static Dialog sheet() {
        return ShadowDialog.getLatestDialog();
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

    private static void layoutDialog(android.app.Activity a, Dialog d) {
        View decor = d.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels;
        int h = a.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
    }

    private static Dialog open(WelcomeActivity a, int card) {
        a.findViewById(CARDS[card]).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        Dialog d = sheet();
        assertNotNull(d);
        assertTrue(d.isShowing());
        return d;
    }

    private static List<String> stepTitles(Dialog d) {
        LinearLayout steps = d.findViewById(R.id.welcomeSheetSteps);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < steps.getChildCount(); i++) out.add(((TextView) steps.getChildAt(i).findViewById(R.id.stepTitle)).getText().toString());
        return out;
    }

    // ---- the screen ----

    @Test
    public void theScreen_fitsOneScreenOn393dp_withoutScrolling() {
        RuntimeEnvironment.setQualifiers("+w393dp-h851dp-notnight");
        WelcomeActivity a = launch();
        layout(a);
        androidx.core.widget.NestedScrollView scroll = a.findViewById(R.id.welcomeScroll);
        assertTrue("content " + scroll.getChildAt(0).getHeight() + "px fits the " + scroll.getHeight() + "px viewport", scroll.getChildAt(0).getHeight() <= scroll.getHeight());
        float dp = a.getResources().getDisplayMetrics().density;
        assertEquals("the logo card is about 20% smaller (112dp to 90dp)", 90f, a.findViewById(R.id.logoBorder).getWidth() / dp, 0.6f);
        assertEquals("the headline is 26sp", 26f, ((TextView) a.findViewById(R.id.welcomeTitleText)).getTextSize() / a.getResources().getDisplayMetrics().scaledDensity, 0.3f);
        assertEquals("one-line description", 1, ((TextView) a.findViewById(R.id.welcomeDescriptionText)).getLineCount());
        assertEquals("16dp side padding on the actions", 16f, a.findViewById(R.id.registrationButton).getLeft() / dp, 0.6f);
    }

    @Test
    public void theFourCards_areEqual_tappable_withOneLineTitles_inLightAndDark_at360And393() {
        for (String q : CONFIGS) {
            RuntimeEnvironment.setQualifiers("+" + q);
            WelcomeActivity a = launch();
            layout(a);
            View first = a.findViewById(CARDS[0]);
            for (int i = 0; i < CARDS.length; i++) {
                View card = a.findViewById(CARDS[i]);
                TextView title = a.findViewById(TITLES[i]);
                assertEquals(q + " equal width", first.getWidth(), card.getWidth(), 1);
                assertEquals(q + " equal height", first.getHeight(), card.getHeight(), 1);
                assertTrue(q + " tappable", card.isClickable() && card.hasOnClickListeners());
                assertEquals(q + " title", CARD_TITLES[i], title.getText().toString());
                assertEquals(q + " '" + title.getText() + "' on one line", 1, title.getLineCount());
                assertEquals(q + " not cut", 0, title.getLayout().getEllipsisCount(0));
            }
            List<String> t = textsOf(a.findViewById(R.id.welcomePoints));
            int cues = 0;
            for (String s : t) if (s.equals("Learn more ›")) cues++;
            assertEquals(q + " every card says it opens", 4, cues);
            Release160Test.assertReadable(q + " welcome", a, a.getWindow().getDecorView());
        }
    }

    // ---- the sheets ----

    @Test
    public void eachCard_opensItsSheet_withTheRightTitle_andSteps() {
        WelcomeActivity a = launch();
        for (int i = 0; i < CARDS.length; i++) {
            Dialog d = open(a, i);
            assertEquals(CARD_TITLES[i], ((TextView) d.findViewById(R.id.welcomeSheetTitle)).getText().toString());
            assertEquals(STEP_COUNTS[i], stepTitles(d).size());
            LinearLayout steps = d.findViewById(R.id.welcomeSheetSteps);
            for (int k = 0; k < steps.getChildCount(); k++) {
                assertEquals(String.valueOf(k + 1), ((TextView) steps.getChildAt(k).findViewById(R.id.stepNumber)).getText().toString());
            }
            assertEquals("Create an account", ((TextView) d.findViewById(R.id.welcomeSheetCreate)).getText().toString());
            assertEquals("Already have an account? Sign in", ((TextView) d.findViewById(R.id.welcomeSheetSignIn)).getText().toString());
            assertNotNull("close ×", d.findViewById(R.id.welcomeSheetClose));
            assertNotNull("drag handle", d.findViewById(R.id.welcomeSheetHandle));
            d.findViewById(R.id.welcomeSheetClose).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            assertFalse(d.isShowing());
        }
    }

    @Test
    public void theContent_matchesTheRealFlows() {
        WelcomeActivity a = launch();
        // booking: the wizard's own step names, "Add another date", booking vs reservation
        Dialog booking = open(a, 0);
        List<String> b = stepTitles(booking);
        for (String wizardStep : new String[]{"Dates and rooms", "Amenities", "Representative", "Guests", "ID verification", "Review and bill summary"}) {
            assertTrue(wizardStep + " in " + b, b.contains(wizardStep));
        }
        assertTrue(b.toString(), b.contains("Payment method (reservations)"));
        String bText = String.join(" | ", textsOf(booking.getWindow().getDecorView()));
        assertTrue(bText, bText.contains("Add another date") && bText.contains("partial payment") && bText.contains("20%–50%") && bText.contains("pay later"));
        booking.dismiss();
        shadowOf(Looper.getMainLooper()).idle();

        // GCash: the five steps (named as the payment screen names them), the 20%-50% rule, front-desk verification
        Dialog gcash = open(a, 1);
        List<String> g = stepTitles(gcash);
        assertEquals(java.util.Arrays.asList(a.getString(R.string.gcash_step_name_1), a.getString(R.string.gcash_step_name_2), a.getString(R.string.gcash_step_name_3),
                a.getString(R.string.gcash_step_name_4), a.getString(R.string.gcash_step_name_5)), g);
        String gText = String.join(" | ", textsOf(gcash.getWindow().getDecorView()));
        assertTrue(gText, gText.contains("20%–50%") && gText.contains("verifies your payment before it counts") && gText.contains("13-digit"));
        gcash.dismiss();
        shadowOf(Looper.getMainLooper()).idle();

        // tracking: the list, details, the three receipts, notifications, the cancel rule and no refunds
        Dialog track = open(a, 2);
        String tText = String.join(" | ", textsOf(track.getWindow().getDecorView()));
        assertTrue(tText, tText.contains("Booking & Reservation") && tText.contains("Payment verified") && tText.contains("check-in") && tText.contains("check-out")
                && tText.contains("Notifications") && tText.contains("cancel until the front desk verifies it") && tText.contains("non-refundable"));
        track.dismiss();
        shadowOf(Looper.getMainLooper()).idle();

        // front desk: only the contact details the app already has, never invented ones
        Dialog desk = open(a, 3);
        String dText = String.join(" | ", textsOf(desk.getWindow().getDecorView()));
        assertTrue(dText, dText.contains("Call " + a.getString(R.string.landing_hotel_phone)) && dText.contains("Email " + a.getString(R.string.landing_hotel_email)));
        assertTrue(dText, dText.contains("24 hours a day, 7 days a week") && dText.contains("At check-in") && dText.contains("Amenities") && dText.contains("Payment questions"));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{7,}").matcher(dText);
        while (m.find()) assertEquals("no other phone number appears", a.getString(R.string.landing_hotel_phone), m.group());
    }

    @Test
    public void theSheetButtons_openCreateAccount_andSignIn() {
        WelcomeActivity a = launch();
        Dialog d = open(a, 1);
        d.findViewById(R.id.welcomeSheetCreate).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        Intent toRegistration = shadowOf(a).getNextStartedActivity();
        assertEquals(new ComponentName(a, RegistrationActivity.class), toRegistration.getComponent());
        assertFalse("the sheet closed", d.isShowing());

        Dialog again = open(a, 2);
        again.findViewById(R.id.welcomeSheetSignIn).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        Intent toLogin = shadowOf(a).getNextStartedActivity();
        assertEquals(new ComponentName(a, LoginActivity.class), toLogin.getComponent());
    }

    @Test
    public void repeatTaps_openOneSheet_andBackClosesTheSheetBeforeTheScreen() {
        WelcomeActivity a = launch();
        int before = ShadowDialog.getShownDialogs().size();
        a.findViewById(CARDS[0]).performClick();
        a.findViewById(CARDS[0]).performClick();
        a.findViewById(CARDS[1]).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("one sheet only", before + 1, ShadowDialog.getShownDialogs().size());
        Dialog d = sheet();
        assertEquals("the first tap wins", "Easy booking", ((TextView) d.findViewById(R.id.welcomeSheetTitle)).getText().toString());

        d.onBackPressed(); // Back on an open sheet closes the sheet
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse("Back closed the sheet", d.isShowing());
        assertFalse("and the screen is still there", a.isFinishing());

    }

    @Test
    public void rotation_keepsTheOpenSheet() {
        ActivityController<WelcomeActivity> controller = Robolectric.buildActivity(WelcomeActivity.class).setup();
        WelcomeActivity a = controller.get();
        shadowOf(Looper.getMainLooper()).idle();
        open(a, 2);
        controller.recreate();
        shadowOf(Looper.getMainLooper()).idle();
        Dialog d = sheet();
        assertTrue("the sheet is back after the screen was recreated", d.isShowing());
        assertEquals("Track transactions", ((TextView) d.findViewById(R.id.welcomeSheetTitle)).getText().toString());

        // closed before recreating: nothing comes back
        d.findViewById(R.id.welcomeSheetClose).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        controller.recreate();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(sheet().isShowing());
    }

    // ---- readability and large fonts ----

    @Test
    public void sheets_areReadable_at360And393_lightAndDark() {
        for (String q : CONFIGS) {
            RuntimeEnvironment.setQualifiers("+" + q);
            WelcomeActivity a = launch();
            for (int i = 0; i < CARDS.length; i++) {
                Dialog d = open(a, i);
                layoutDialog(a, d);
                Release160Test.assertReadable(q + " sheet " + CARD_TITLES[i], a, d.getWindow().getDecorView());
                d.dismiss();
                shadowOf(Looper.getMainLooper()).idle();
            }
        }
    }

    @Test
    public void withLargeFonts_theScreenStaysReadable_scrolls_andTheSheetsKeepTheirButtons() {
        for (float scale : new float[]{1.3f, 2.0f}) {
            for (String q : new String[]{"w360dp-h800dp-notnight", "w393dp-h851dp-night", "w320dp-h568dp-notnight"}) {
                RuntimeEnvironment.setQualifiers("+" + q);
                RuntimeEnvironment.setFontScale(scale);
                WelcomeActivity a = launch();
                layout(a);
                String tag = scale + "x " + q;
                Release160Test.assertReadable(tag + " welcome", a, a.getWindow().getDecorView());
                View actions = a.findViewById(R.id.buttonContainer);
                assertTrue(tag + " the action bar is on screen", actions.getBottom() <= a.getWindow().getDecorView().getHeight());
                for (int i = 0; i < CARDS.length; i++) {
                    Dialog d = open(a, i);
                    layoutDialog(a, d);
                    Release160Test.assertReadable(tag + " sheet " + CARD_TITLES[i], a, d.getWindow().getDecorView());
                    View create = d.findViewById(R.id.welcomeSheetCreate);
                    int screenH = a.getResources().getDisplayMetrics().heightPixels;
                    assertTrue(tag + " the primary button stays on screen (" + create.getBottom() + " of " + screenH + ")", create.getBottom() <= screenH);
                    assertTrue(tag + " the body scrolls inside the sheet, not past it", d.findViewById(R.id.welcomeSheetScroll).getHeight() <= screenH * 0.62f);
                    d.dismiss();
                    shadowOf(Looper.getMainLooper()).idle();
                }
            }
        }
    }

    // ---- effects ----

    @Test
    public void theEntryEffects_startHidden_thenSettle_andAreSkippedWhenOff() {
        WelcomeEffects.motion = true;
        WelcomeActivity a = launch();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
        for (int id : new int[]{R.id.logoBorder, R.id.welcomeTitleText, R.id.welcomeCardRooms, R.id.welcomeCardSupport}) {
            assertEquals(1f, a.findViewById(id).getAlpha(), 0.001f);
            assertEquals(0f, a.findViewById(id).getTranslationY(), 0.001f);
            assertEquals(1f, a.findViewById(id).getScaleX(), 0.001f);
        }
        WelcomeEffects.motion = false;
        WelcomeActivity b = Robolectric.buildActivity(WelcomeActivity.class).setup().get();
        assertEquals("with motion off everything is simply there", 1f, b.findViewById(R.id.welcomeCardPay).getAlpha(), 0.001f);

        Dialog d = open(b, 0);
        for (View step : steps(d)) assertEquals(1f, step.getAlpha(), 0.001f);
    }

    private static List<View> steps(Dialog d) {
        LinearLayout steps = d.findViewById(R.id.welcomeSheetSteps);
        List<View> out = new ArrayList<>();
        for (int i = 0; i < steps.getChildCount(); i++) out.add(steps.getChildAt(i));
        return out;
    }

    @Test
    public void aCardPress_scalesDown_withoutSwallowingTheTap() {
        WelcomeEffects.motion = true;
        WelcomeActivity a = launch();
        View card = a.findViewById(CARDS[0]);
        long t = android.os.SystemClock.uptimeMillis();
        card.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, 5, 5, 0));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
        assertTrue("pressed: slightly smaller", card.getScaleX() < 1f && card.getScaleX() >= 0.95f);
        card.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 50, android.view.MotionEvent.ACTION_UP, 5, 5, 0));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
        assertEquals(1f, card.getScaleX(), 0.001f);
    }
}
