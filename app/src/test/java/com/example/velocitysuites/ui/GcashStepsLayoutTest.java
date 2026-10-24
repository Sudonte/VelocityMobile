package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.MultiStayFixtures;
import com.example.velocitysuites.PaymentActivity;
import com.example.velocitysuites.PendingAccess;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.google.android.material.card.MaterialCardView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowDialog;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The GCash payment screen, all five steps, for a one-stay and a two-stay transaction - an existing reservation AND a new booking that is
 * still being paid for (the path where the breakdown once went blank) - at 360dp and 393dp, light and dark. Every visible text is
 * non-empty, inside the screen, and readable (contrast of at least 4.5:1 against what is behind it); Step 1 has no dead space between
 * rows; the big amount is the bottom bar's amount, on every step.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GcashStepsLayoutTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static final String[] CONFIGS = {"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"};
    private static final String NUMBER = "9171234567";
    private static final String REFERENCE = "9876543210987";

    private Context app;
    private ScreenTestSupport.FakeApi api;
    private ActivityController<PaymentActivity> controller;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
    }

    @org.junit.After
    public void tearDown() {
        if (controller != null) {
            try {
                controller.pause().stop().destroy();
            } catch (RuntimeException ignored) {
                // already finished
            }
        }
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
    }

    // ---- ways to open the screen ----

    private ReservationDto oneStayReservation() {
        ReservationDto r = ScreenTestSupport.reservation(583, 3500);
        r.check_in = "2026-10-26";
        r.check_out = "2026-10-27";
        return r;
    }

    private PaymentActivity openExisting(ReservationDto reservation) {
        controller = Robolectric.buildActivity(PaymentActivity.class, new Intent(app, PaymentActivity.class).putExtra("BOOKING_ID", String.valueOf(reservation.id)));
        controller.setup();
        idle();
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(reservation), ScreenTestSupport.directBookings());
        idle();
        for (int i = 0; i < api.count("getRequestableAmenities"); i++) {
            ScreenTestSupport.FakeCall<List<com.example.velocitysuites.network.dto.RequestableAmenityDto>> c = api.call("getRequestableAmenities", i);
            c.succeed(new ArrayList<>());
            idle();
        }
        settle();
        return controller.get();
    }

    /** A new booking not yet made: the reviewed wizard state, with {@code stays} stays (Deluxe + Executive in each), paid at 50%. */
    private PaymentActivity openPendingBooking(int stays) {
        return openPending(pendingState(stays, true));
    }

    private BookingWizardState pendingState(int stays, boolean withExtraBed) {
        BookingWizardState state = new BookingWizardState(BookingWizardState.Mode.BOOKING);
        state.checkIn = WizardDriver.day(5);
        state.checkOut = WizardDriver.day(6);
        state.selectedRooms.add(named("1", "Deluxe", 3500));
        state.selectedRooms.add(named("8", "Executive", 5800));
        state.confirmActive();
        if (stays > 1) {
            state.addStay();
            state.checkIn = WizardDriver.day(6);
            state.checkOut = WizardDriver.day(7);
            state.selectedRooms.add(named("1", "Deluxe", 3500));
            state.selectedRooms.add(named("8", "Executive", 5800));
            state.confirmActive();
        }
        if (withExtraBed) {
            state.committedStays().get(0).setAmenityQty("1#1", new com.example.velocitysuites.AddOnAmenity("9", "Extra bed", "Comfort", "d", 300, 5), 1);
            state.reloadActiveAmenities();
        }
        state.guestFirstName = "Ana";
        state.guestLastName = "Cruz";
        state.setDiscount(new com.example.velocitysuites.Discount("3", "PWD", "percentage", 20, "d", "active", null, null));
        return state;
    }

    private PaymentActivity openPending(BookingWizardState state) {
        PendingAccess.set(state);
        Intent intent = new Intent(app, PaymentActivity.class).putExtra(PaymentActivity.EXTRA_PENDING_BOOKING, true).putExtra("ALLOW_CASH", false);
        controller = Robolectric.buildActivity(PaymentActivity.class, intent);
        controller.setup();
        settle();
        return controller.get();
    }

    private static com.example.velocitysuites.Room named(String id, String name, double price) {
        return new com.example.velocitysuites.Room(id, name, "Deluxe", 2, price, "desc", 0, true,
                java.util.Collections.<com.example.velocitysuites.RoomAmenity>emptyList(), "Queen", "24 sqm", "No smoking");
    }

    private static void layout(Activity a) {
        View decor = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels;
        int h = a.getResources().getDisplayMetrics().heightPixels;
        decor.getViewTreeObserver().dispatchOnPreDraw();
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
    }

    private static void type(Activity a, int id, String text) {
        ((EditText) a.findViewById(id)).setText(text);
        idle();
    }

    private void attachReceipt(PaymentActivity a) {
        a.findViewById(R.id.btnUploadReceipt).performClick();
        ShadowActivity.IntentForResult launched = shadowOf(a).getNextStartedActivityForResult();
        assertNotNull(launched);
        Uri uri = Uri.parse("content://velocity.test.receipts/receipt.jpg");
        shadowOf(app.getContentResolver()).registerInputStream(uri, new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));
        shadowOf(a).receiveResult(launched.intent, Activity.RESULT_OK, new Intent().setData(uri));
        idle();
    }

    /** From the review screen into the GCash steps (answering the amount confirmation). */
    private void intoGcash(PaymentActivity a) {
        a.findViewById(R.id.proceedToGcashButton).performClick();
        idle();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        settle();
    }

    private static void next(PaymentActivity a, int step) {
        int[] ids = {R.id.btnGcashStep1Next, R.id.btnGcashStep2Next, R.id.btnGcashStep3Next, R.id.btnGcashStep4Next};
        a.findViewById(ids[step - 1]).performClick();
        idle();
        settle();
    }

    // ---- the checks ----

    private static int dp(View v, float dp) {
        return Math.round(dp * v.getResources().getDisplayMetrics().density);
    }

    private static double luminance(int c) {
        double[] rgb = {Color.red(c) / 255.0, Color.green(c) / 255.0, Color.blue(c) / 255.0};
        for (int i = 0; i < 3; i++) rgb[i] = rgb[i] <= 0.03928 ? rgb[i] / 12.92 : Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
    }

    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static int over(int fg, int bg) {
        int alpha = Color.alpha(fg);
        if (alpha >= 255) return fg;
        float a = alpha / 255f;
        return Color.rgb(Math.round(Color.red(fg) * a + Color.red(bg) * (1 - a)), Math.round(Color.green(fg) * a + Color.green(bg) * (1 - a)), Math.round(Color.blue(fg) * a + Color.blue(bg) * (1 - a)));
    }

    /** The colour behind {@code v}: the nearest ancestor with a solid background, else the window's. */
    private static int backgroundBehind(View v, int windowBackground) {
        // a text on its own tinted pill (the numbered badges) is read against that pill
        if (v.getBackground() != null && v.getBackgroundTintList() != null && Color.alpha(v.getBackgroundTintList().getDefaultColor()) == 255) return v.getBackgroundTintList().getDefaultColor();
        View cur = (View) v.getParent();
        while (cur != null) {
            Drawable d = cur.getBackground();
            if (cur instanceof MaterialCardView) return ((MaterialCardView) cur).getCardBackgroundColor().getDefaultColor();
            if (d instanceof ColorDrawable && Color.alpha(((ColorDrawable) d).getColor()) == 255) return ((ColorDrawable) d).getColor();
            if (d instanceof GradientDrawable) {
                android.content.res.ColorStateList fill = ((GradientDrawable) d).getColor();
                if (fill != null && Color.alpha(fill.getDefaultColor()) == 255) return fill.getDefaultColor();
            }
            if (d != null && cur.getBackgroundTintList() != null && Color.alpha(cur.getBackgroundTintList().getDefaultColor()) == 255) return cur.getBackgroundTintList().getDefaultColor();
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
        return windowBackground;
    }

    private static boolean shown(View v) {
        for (View cur = v; cur != null; cur = cur.getParent() instanceof View ? (View) cur.getParent() : null) {
            if (cur.getVisibility() != View.VISIBLE) return false;
        }
        return true;
    }

    private static void collectTexts(View v, List<TextView> out) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof TextView && !(v instanceof EditText)) out.add((TextView) v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collectTexts(((ViewGroup) v).getChildAt(i), out);
    }

    private static int[] positionIn(View root, View v) {
        int x = 0, y = 0;
        View cur = v;
        while (cur != null && cur != root) {
            x += cur.getLeft() - (cur.getParent() instanceof View ? ((View) cur.getParent()).getScrollX() : 0);
            y += cur.getTop() - (cur.getParent() instanceof View ? ((View) cur.getParent()).getScrollY() : 0);
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
        return new int[]{x, y};
    }

    private void assertReadable(String tag, PaymentActivity a) {
        layout(a);
        View root = a.getWindow().getDecorView();
        int windowBg = com.google.android.material.color.MaterialColors.getColor(a, android.R.attr.colorBackground, Color.WHITE);
        View form = a.findViewById(R.id.cardGcashSubmissionForm);
        List<TextView> texts = new ArrayList<>();
        collectTexts(form, texts);
        assertFalse(tag + ": the step shows something", texts.isEmpty());
        int screen = a.getResources().getDisplayMetrics().widthPixels;
        for (TextView t : texts) {
            String name = t.getClass().getSimpleName() + "'" + t.getText() + "'";
            assertTrue(tag + ": " + name + " is shown", shown(t));
            if (t instanceof Button) continue; // buttons draw their own surface
            assertTrue(tag + ": " + name + " is not empty", t.getText().toString().trim().length() > 0);
            assertTrue(tag + ": " + name + " has width (" + t.getWidth() + ")", t.getWidth() >= dp(t, 8) || t.getText().length() <= 1);
            int[] pos = positionIn(root, t);
            assertTrue(tag + ": " + name + " inside the screen (" + pos[0] + ".." + (pos[0] + t.getWidth()) + " of " + screen + ")", pos[0] >= 0 && pos[0] + t.getWidth() <= screen);
            int bg = backgroundBehind(t, windowBg);
            int fg = over(t.getCurrentTextColor(), bg);
            double ratio = contrast(fg, bg);
            assertTrue(tag + ": " + name + " is readable: contrast " + String.format(java.util.Locale.US, "%.2f", ratio) + " (text " + Integer.toHexString(fg) + " on " + Integer.toHexString(bg) + ")", ratio >= 4.5);
        }
    }

    /** No gap taller than ~48dp between rows, and no container much taller than what it holds. */
    private void assertNoDeadSpace(String tag, View v) {
        if (v.getVisibility() != View.VISIBLE || !(v instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) v;
        int limit = dp(v, 48);
        if (g instanceof LinearLayout && ((LinearLayout) g).getOrientation() == LinearLayout.VERTICAL && g.getChildCount() > 0) {
            int used = g.getPaddingTop() + g.getPaddingBottom();
            int prevBottom = -1;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c.getVisibility() == View.GONE) continue;
                if (prevBottom >= 0) assertTrue(tag + ": a gap of " + (c.getTop() - prevBottom) + "px before " + c.getClass().getSimpleName() + " in " + g.getClass().getSimpleName(),
                        c.getTop() - prevBottom <= limit);
                prevBottom = c.getBottom();
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) c.getLayoutParams();
                used += c.getHeight() + lp.topMargin + lp.bottomMargin;
            }
            assertTrue(tag + ": " + g.getClass().getSimpleName() + " is " + g.getHeight() + "px tall but holds " + used + "px", g.getHeight() - used <= limit);
        }
        for (int i = 0; i < g.getChildCount(); i++) assertNoDeadSpace(tag, g.getChildAt(i));
    }

    private static String text(PaymentActivity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    /** Walks Step 1 to Step 5 checking each; {@code a} must be on the review screen. */
    private void walkAllFiveSteps(String tag, PaymentActivity a, boolean twoStays) {
        intoGcash(a);
        for (int step = 1; step <= 5; step++) {
            String t = tag + " step " + step;
            assertTrue(t + ": label", text(a, R.id.tvGcashStepLabel).startsWith("Step " + step + " of 5 · "));
            assertReadable(t, a);
            String bar = text(a, R.id.tvBarAmount);
            if (step == 1) {
                assertEquals(t + ": the big amount is the bottom bar's", bar, text(a, R.id.tvPortalAmount));
                String share = text(a, R.id.tvGcashShareLine);
                assertTrue(t + ": " + share, share.contains("of ₱") && share.contains("is paid at the hotel"));
                assertTrue(t + ": " + text(a, R.id.tvGcashTxLine), text(a, R.id.tvGcashTxLine).contains("·"));
                assertEquals(t + ": the breakdown is open", View.VISIBLE, a.findViewById(R.id.layoutGcashStays).getVisibility());
                String breakdown = ReviewTexts.of(a.findViewById(R.id.cardGcashPayingFor));
                assertTrue(t + ": " + breakdown, breakdown.contains("Subtotal") == twoStays && !breakdown.contains("Stay 1") && breakdown.contains("Deluxe × 1") && (!twoStays || breakdown.contains("Executive × 1")) && breakdown.contains("Room charges") && breakdown.contains("Amenities total") && breakdown.contains("Grand total"));
                assertNoDeadSpace(t, a.findViewById(R.id.gcashStep1Amount));
                assertNoDeadSpace(t, a.findViewById(R.id.cardGcashPayingFor));
                assertNoDeadSpace(t, a.findViewById(R.id.cardGcashSubmissionForm));
            } else {
                assertEquals(t + ": the breakdown is one line until opened", View.GONE, a.findViewById(R.id.layoutGcashStays).getVisibility());
            }
            if (step == 2) assertEquals(t + ": the amount to send is the bar's", bar, text(a, R.id.tvSendAmount));
            if (step == 5) {
                assertEquals(t, bar, text(a, R.id.tvReviewAmount));
                assertTrue(t + ": the reference and receipt show", text(a, R.id.tvReviewReferenceNumber).replace(" ", "").contains(REFERENCE) && a.findViewById(R.id.ivReviewReceiptThumb).getVisibility() == View.VISIBLE);
                break;
            }
            if (step == 3) {
                type(a, R.id.etGcashNumber, NUMBER);
                type(a, R.id.etGcashReferenceNumber, REFERENCE);
            }
            if (step == 4) attachReceipt(a);
            next(a, step);
        }
    }

    @Test
    public void everyStep_existingReservation_oneAndTwoStays_light_dark_360_393() {
        for (String q : CONFIGS) {
            for (boolean two : new boolean[]{false, true}) {
                RuntimeEnvironment.setQualifiers("+" + q);
                ScreenTestSupport.freshRepository(app, api = new ScreenTestSupport.FakeApi());
                ReservationDto dto = two ? MultiStayFixtures.reservationDto("pending") : oneStayReservation();
                PaymentActivity a = openExisting(dto);
                walkAllFiveSteps(q + (two ? " 2 stays (reservation)" : " 1 stay (reservation)"), a, two);
                controller.pause().stop().destroy();
                controller = null;
            }
        }
        RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
    }

    @Test
    public void everyStep_newBooking_oneAndTwoStays_light_dark_360_393() {
        for (String q : CONFIGS) {
            for (int stays = 1; stays <= 2; stays++) {
                RuntimeEnvironment.setQualifiers("+" + q);
                ScreenTestSupport.freshRepository(app, api = new ScreenTestSupport.FakeApi());
                PaymentActivity a = openPendingBooking(stays);
                walkAllFiveSteps(q + " " + stays + " stay(s) (new booking)", a, stays > 1);
                controller.pause().stop().destroy();
                controller = null;
            }
        }
        RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
    }

    @Test
    public void theIntroNamesTheTransactionType() {
        PaymentActivity booking = openPendingBooking(2);
        assertTrue(text(booking, R.id.paymentGreeting), text(booking, R.id.paymentGreeting).contains("confirm your booking"));
        controller.pause().stop().destroy();
        ScreenTestSupport.freshRepository(app, api = new ScreenTestSupport.FakeApi());
        PaymentActivity reservation = openExisting(MultiStayFixtures.reservationDto("pending"));
        assertTrue(text(reservation, R.id.paymentGreeting), text(reservation, R.id.paymentGreeting).contains("confirm your reservation"));
    }

    @Test
    public void step1_leadsWithTheAmount_explainsIt_andTheBreakdownCollapses() {
        PaymentActivity a = openPendingBooking(2);
        intoGcash(a);
        layout(a);
        assertEquals("Amount to pay now", ((TextView) ((ViewGroup) ((ViewGroup) a.findViewById(R.id.tvPortalAmount).getParent())).getChildAt(0)).getText().toString());
        assertTrue("large", ((TextView) a.findViewById(R.id.tvPortalAmount)).getTextSize() / a.getResources().getDisplayMetrics().scaledDensity >= 36f);
        String share = text(a, R.id.tvGcashShareLine);
        assertTrue(share, share.matches("\\d+% of ₱[\\d,]+\\.\\d\\d \\(grand total\\) · the rest \\(₱[\\d,]+\\.\\d\\d\\) is paid at the hotel"));
        assertTrue(text(a, R.id.tvGcashTxLine), text(a, R.id.tvGcashTxLine).startsWith("New booking · 2 stays · "));
        View header = a.findViewById(R.id.headerGcashPayingFor);
        header.performClick();
        assertEquals(View.GONE, a.findViewById(R.id.layoutGcashStays).getVisibility());
        header.performClick();
        assertEquals(View.VISIBLE, a.findViewById(R.id.layoutGcashStays).getVisibility());
    }

    // ---- the breakdown: amenities, the grand total, and the pay-now share of it ----

    private static final com.example.velocitysuites.AddOnAmenity TOWEL = new com.example.velocitysuites.AddOnAmenity("5", "Additional Towel", "Bath", "d", 50, 9);
    private static final com.example.velocitysuites.AddOnAmenity BED = new com.example.velocitysuites.AddOnAmenity("9", "Extra bed", "Comfort", "d", 300, 5);

    /** Rooms 3,500 + 5,800 = 9,300; amenities 50 + 300 + 50 = 400; grand total 9,700. */
    private BookingWizardState roomsAndAmenities() {
        BookingWizardState state = pendingState(1, false);
        BookingWizardState.Stay stay = state.committedStays().get(0);
        stay.setAmenityQty("1#1", TOWEL, 1);
        stay.setAmenityQty("8#1", BED, 1);
        stay.setAmenityQty("8#1", TOWEL, 1);
        state.reloadActiveAmenities();
        state.setDiscount(null);
        return state;
    }

    @Test
    public void step1_listsTheAmenitiesUnderTheirRoom_andTheGrandTotalIsRoomsPlusAmenities() {
        PaymentActivity a = openPending(roomsAndAmenities());
        intoGcash(a);
        String breakdown = ReviewTexts.of(a.findViewById(R.id.cardGcashPayingFor));
        for (String needed : new String[]{"Deluxe × 1\n₱3,500.00", "Executive × 1\n₱5,800.00",
                "Deluxe (room 1): Additional Towel × 1\n₱50.00", "Executive (room 1): Extra bed × 1\n₱300.00", "Executive (room 1): Additional Towel × 1\n₱50.00",
                "Room charges\n₱9,300.00", "Amenities total\n₱400.00", "Grand total\n₱9,700.00"}) {
            assertTrue("missing '" + needed + "' in:\n" + breakdown, breakdown.contains(needed));
        }
        assertFalse(breakdown, breakdown.contains("No amenities added"));
    }

    @Test
    public void payNow_isHalfOfTheGrandTotal_theLineSaysSo_andTheBarMatches() {
        PaymentActivity a = openPending(roomsAndAmenities());
        a.findViewById(R.id.chipPercent50).performClick();
        idle();
        intoGcash(a);
        assertEquals("₱4,850.00", text(a, R.id.tvPortalAmount));
        assertEquals("₱4,850.00", text(a, R.id.tvBarAmount));
        assertEquals("50% of ₱9,700.00 (grand total) · the rest (₱4,850.00) is paid at the hotel", text(a, R.id.tvGcashShareLine));
    }

    @Test
    public void payNow_atTwentyPercent_isOfTheGrandTotal_notTheRoomCharges() {
        PaymentActivity a = openPending(roomsAndAmenities());
        a.findViewById(R.id.chipPercent20).performClick();
        idle();
        intoGcash(a);
        assertEquals("20% of 9,700 = 1,940 (of the rooms alone it would be 1,860)", "₱1,940.00", text(a, R.id.tvPortalAmount));
        assertEquals("₱1,940.00", text(a, R.id.tvBarAmount));
        assertTrue(text(a, R.id.tvGcashShareLine), text(a, R.id.tvGcashShareLine).startsWith("20% of ₱9,700.00 (grand total)"));
    }

    @Test
    public void theRangeTheAppAllowsIsTheRangeTheServerAccepts() {
        // the server's PayNowGrandTotalTest accepts exactly 1,940.00 to 4,850.00 for this bill
        com.example.velocitysuites.PaymentRules.Range range = com.example.velocitysuites.PaymentRules.of(9300 + 400, 0, false, null);
        assertEquals(1940.00, range.min, 0.001);
        assertEquals(4850.00, range.max, 0.001);
    }

    @Test
    public void withoutAmenities_theBreakdownSaysSo() {
        PaymentActivity a = openPending(pendingState(1, false));
        intoGcash(a);
        String breakdown = ReviewTexts.of(a.findViewById(R.id.cardGcashPayingFor));
        assertTrue(breakdown, breakdown.contains("No amenities added") && breakdown.contains("Amenities total\n₱0.00") && breakdown.contains("Grand total\n₱9,300.00"));
        TextView none = null;
        for (TextView t : texts(a.findViewById(R.id.layoutGcashStays))) if (t.getText().toString().equals("No amenities added")) none = t;
        assertNotNull(none);
        assertEquals("in the secondary colour", a.getColor(R.color.velocity_text_secondary), none.getCurrentTextColor());
    }

    @Test
    public void twoStays_eachStayShowsItsOwnAmenitiesAndSubtotal_andTheGrandTotalIsTheSum() {
        BookingWizardState state = pendingState(2, false);
        state.committedStays().get(0).setAmenityQty("1#1", TOWEL, 2);
        state.committedStays().get(1).setAmenityQty(BookingWizardState.Stay.ANY_ROOM, BED, 1);
        state.reloadActiveAmenities();
        PaymentActivity a = openPending(state);
        intoGcash(a);
        String breakdown = ReviewTexts.of(a.findViewById(R.id.cardGcashPayingFor));
        for (String needed : new String[]{"Deluxe (room 1): Additional Towel × 2\n₱100.00", "Amenities for this stay", "Extra bed × 1\n₱300.00",
                "Subtotal\n₱9,400.00", "Subtotal\n₱9,600.00", "Room charges (all stays)\n₱18,600.00", "Amenities total\n₱400.00", "Grand total\n₱19,000.00"}) {
            assertTrue("missing '" + needed + "' in:\n" + breakdown, breakdown.contains(needed));
        }
        String firstSubtotal = "Subtotal" + (char) 10 + "₱9,400.00";
        assertTrue("the first range's amenities come before its subtotal, and the second range after", breakdown.indexOf("Additional Towel") < breakdown.indexOf(firstSubtotal)
                && breakdown.indexOf(firstSubtotal) < breakdown.indexOf("Extra bed"));
    }

    @Test
    public void onTheLaterSteps_theOneLineSummaryHasTheGrandTotal() {
        PaymentActivity a = openPending(roomsAndAmenities());
        intoGcash(a);
        assertEquals("open on Step 1: no summary beside the title", View.GONE, a.findViewById(R.id.tvGcashPayingForTotal).getVisibility());
        next(a, 1);
        assertEquals(View.VISIBLE, a.findViewById(R.id.tvGcashPayingForTotal).getVisibility());
        assertEquals("Grand total ₱9,700.00", text(a, R.id.tvGcashPayingForTotal));
    }

    @Test
    public void theSecurityLineClaimsOnlyWhatIsTrue_andNoEndToEndWordingExists() throws Exception {
        PaymentActivity a = openPending(roomsAndAmenities());
        String screen = ReviewTexts.of(a.getWindow().getDecorView());
        assertTrue(screen, screen.contains("Your payment details are sent over a secure connection."));
        assertFalse(screen, screen.toLowerCase(java.util.Locale.ROOT).contains("end-to-end") || screen.toLowerCase(java.util.Locale.ROOT).contains("encrypt"));
        java.io.File res = new java.io.File("src/main/res");
        java.util.List<String> offenders = new ArrayList<>();
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(res.toPath())) {
            for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) files.filter(f -> f.toString().endsWith(".xml"))::iterator) {
                String content = new String(java.nio.file.Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
                if (content.contains("end-to-end") || content.contains("end to end encrypt")) offenders.add(p.toString());
            }
        }
        assertTrue("claims of end-to-end encryption in: " + offenders, offenders.isEmpty());
    }

    private static List<TextView> texts(View root) {
        List<TextView> out = new ArrayList<>();
        collectTexts(root, out);
        return out;
    }

    /** Joins every visible text under a view. */
    static final class ReviewTexts {
        static String of(View v) {
            StringBuilder sb = new StringBuilder();
            if (v instanceof TextView && v.getVisibility() == View.VISIBLE) sb.append(((TextView) v).getText()).append('\n');
            if (v instanceof ViewGroup && v.getVisibility() == View.VISIBLE) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) sb.append(of(((ViewGroup) v).getChildAt(i)));
            return sb.toString().replace((char) 160, ' ');
        }
    }
}
