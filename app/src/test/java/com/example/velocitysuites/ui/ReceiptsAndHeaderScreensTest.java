package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.LandingActivity;
import com.example.velocitysuites.PaymentReceiptActivity;
import com.example.velocitysuites.ProfileManagementActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.ReceiptDetail;
import com.example.velocitysuites.ThemePreferences;
import com.example.velocitysuites.debug.DebugReceiptFixtures;
import com.example.velocitysuites.network.SessionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The guest-header theme toggle, the landing hero, the highlighted bottom block of each receipt, and the Booking Details
 * balance card + progress tracker.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReceiptsAndHeaderScreensTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        drainStaleWindowCallbacks();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        ScreenTestSupport.goOnline(app);
        ThemePreferences.setMode(app, ThemePreferences.MODE_LIGHT);
        PaymentReceiptActivity.debugPreviewFixtures.clear();
        PaymentReceiptActivity.debugPreviewAmenities.clear();
    }

    /**
     * ThemePreferences.setMode() makes AppCompat recreate EVERY activity it still tracks (a static list), including ones other
     * test classes left open - and recreating those, mid-test, throws a barrier-token error. Forget the old ones here, so only
     * the activities this test opens are affected.
     */
    @SuppressWarnings("unchecked")
    private static void drainStaleWindowCallbacks() {
        try {
            java.lang.reflect.Field f = androidx.appcompat.app.AppCompatDelegate.class.getDeclaredField("sActivityDelegates");
            f.setAccessible(true);
            ((java.util.Collection<Object>) f.get(null)).clear();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new AssertionError("AppCompatDelegate no longer keeps its delegates in sActivityDelegates - update this helper", e);
        }
        shadowOf(Looper.getMainLooper()).idle();
    }

    @After
    public void tearDown() {
        ThemePreferences.setMode(app, ThemePreferences.MODE_LIGHT);
        PaymentReceiptActivity.debugPreviewFixtures.clear();
        PaymentReceiptActivity.debugPreviewAmenities.clear();
    }

    private static void texts(View v, List<String> out) {
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE && ((TextView) v).getText().length() > 0) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup && v.getVisibility() == View.VISIBLE) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) texts(((ViewGroup) v).getChildAt(i), out);
        }
    }

    private static String all(View v) {
        List<String> out = new ArrayList<>();
        texts(v, out);
        return String.join("\n", out);
    }

    // ---- guest header: the theme toggle sits right of the bell

    @Test
    public void theGuestHeaderHasAThemeToggleRightOfTheBell_thatSwitchesAndSavesTheMode() {
        SessionManager.saveSession(app, "t", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com", "09171234567", "Female", "Jan 01, 1990", "");
        ActivityController<ProfileManagementActivity> ctl = Robolectric.buildActivity(ProfileManagementActivity.class).setup();
        ProfileManagementActivity a = ctl.get();
        shadowOf(Looper.getMainLooper()).idle();
        View toggle = a.findViewById(R.id.headerThemeToggle);
        assertNotNull(toggle);
        ConstraintLayout.LayoutParams bell = (ConstraintLayout.LayoutParams) a.findViewById(R.id.headerNotificationContainer).getLayoutParams();
        ConstraintLayout.LayoutParams theme = (ConstraintLayout.LayoutParams) toggle.getLayoutParams();
        assertEquals("the bell is followed by the toggle", R.id.headerThemeToggle, bell.endToStart);
        assertEquals("and the toggle by the profile picture", R.id.headerProfileContainer, theme.endToStart);
        assertTrue("a 48dp touch target", toggle.getLayoutParams().width >= Math.round(48 * a.getResources().getDisplayMetrics().density));
        assertFalse(ThemePreferences.isDarkMode(app));

        toggle.performClick();
        assertTrue("one tap turns Dark on and remembers it", ThemePreferences.isDarkMode(app));
        ctl.pause().stop().destroy();
    }

    @Test
    public void theHeaderIconShowsTheMoonInLightAndTheSunInDark() {
        SessionManager.saveSession(app, "t", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com", "09171234567", "Female", "Jan 01, 1990", "");
        ThemePreferences.setMode(app, ThemePreferences.MODE_DARK);
        ActivityController<ProfileManagementActivity> c = Robolectric.buildActivity(ProfileManagementActivity.class).setup();
        shadowOf(Looper.getMainLooper()).idle();
        ImageView icon = c.get().findViewById(R.id.headerThemeIcon);
        assertEquals(R.drawable.ic_light_mode, shadowOf(icon.getDrawable()).getCreatedFromResId());
        c.pause().stop().destroy(); // leave no live window behind for the next test
    }

    // ---- landing

    @Test
    public void landingSaysVelocitySuites_hasTheHighlightsAndAScrollHint_andFitsTheHeroToTheScreen() {
        ActivityController<LandingActivity> landing = Robolectric.buildActivity(LandingActivity.class, new Intent(app, LandingActivity.class)).setup();
        LandingActivity a = landing.get();
        shadowOf(Looper.getMainLooper()).idle();
        String text = all(a.findViewById(R.id.landingHeroFrame));
        assertTrue(text, text.contains("Velocity Suites"));
        assertFalse("the old heading is gone", text.contains("Luxury Redefined"));
        for (String highlight : new String[]{"Easy Room Booking", "Real-Time Status", "Secure Payments", "Digital Receipts", "Booking Notifications", "24/7 Guest Support"}) {
            assertTrue(highlight + " in: " + text, text.contains(highlight));
        }
        assertNotNull(a.findViewById(R.id.landingScrollHint));
        a.findViewById(R.id.landingScrollHint).performClick(); // scrolls to the rooms without crashing

        View scroller = a.findViewById(R.id.landingScroll);
        View hero = a.findViewById(R.id.landingHeroFrame);
        int topPadding = Math.round(14 * a.getResources().getDisplayMetrics().density);
        if (scroller.getHeight() > 0) {
            assertTrue("hero is as tall as the screen: " + hero.getMinimumHeight() + " vs " + scroller.getHeight(),
                    hero.getMinimumHeight() >= scroller.getHeight() - topPadding - 2);
        }
        landing.pause().stop().destroy(); // leave no live window behind for the next test
    }

    // ---- receipts: the highlighted bottom block

    private PaymentReceiptActivity open(ReceiptDetail detail) {
        PaymentReceiptActivity.debugPreviewFixtures.put(detail.getReceiptNumber(), detail);
        ActivityController<PaymentReceiptActivity> c = Robolectric.buildActivity(PaymentReceiptActivity.class,
                PaymentReceiptActivity.newIntentForReceipt(app, detail.getReceiptNumber()));
        c.setup();
        shadowOf(Looper.getMainLooper()).idle();
        return c.get();
    }

    private static ReceiptDetail.Highlight highlight(String kind, Object... pairs) {
        ReceiptDetail.Highlight h = new ReceiptDetail.Highlight(kind, "partial", "cash", "Due at check-in");
        for (int i = 0; i < pairs.length; i += 2) h.amounts.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        return h;
    }

    @Test
    public void receipt2_showsThePaymentDateAndTime_fromWhenTheReceiptWasIssued() {
        ReceiptDetail partial = DebugReceiptFixtures.allReceiptDetails().values().stream()
                .filter(d -> "PARTIAL_RECEIPT".equals(d.getReceiptType())).findFirst().get();
        partial.setCheckinSettlement(new ReceiptDetail.CheckinSettlement(ReceiptDetail.CheckinSettlement.PARTIAL_TO_FULL, 500, "cash", 0,
                2000, 2000, 0, "not_requested", null, 0, null, "Desk"));
        partial.setIssuedAtRaw("2026-10-17T02:01:09+00:00"); // 10:01:09 AM in Manila
        String text = all(open(partial).findViewById(R.id.layoutDynamicReceiptContent));
        assertTrue(text, text.contains("Oct 17, 2026"));
        assertTrue(text, text.contains("10:01:09 AM"));
    }

    /** The text right after {@code label}: rows are a label then an amount. */
    private static String after(String text, String label) {
        String[] lines = text.split("\n");
        for (int i = 0; i + 1 < lines.length; i++) if (lines[i].equals(label)) return lines[i + 1];
        return null;
    }

    @Test
    public void receipt1_endsWithWhatIsPaidSoFarAndTheBalanceDue() {
        Map<String, ReceiptDetail> all = DebugReceiptFixtures.allReceiptDetails();
        ReceiptDetail partial = all.values().stream().filter(d -> "PARTIAL_RECEIPT".equals(d.getReceiptType())).findFirst().get();
        partial.setHighlight(highlight("booking_verification", "total_amount", 2000, "amount_paid_now", 500, "total_paid", 500, "remaining_balance", 1500, "amount_to_pay", 1500));
        String text = all(open(partial).findViewById(R.id.layoutDynamicReceiptContent));
        assertEquals(text, "₱500.00", after(text, "Paid so far"));
        assertEquals(text, "₱1,500.00", after(text, "Balance due"));
        assertTrue(text, text.contains("You pay the balance at the hotel."));
        assertTrue(text, text.contains("Payment verified"));
    }

    @Test
    public void receipt2_showsEverythingPaidAndZeroStillToPay() {
        ReceiptDetail any = DebugReceiptFixtures.allReceiptDetails().values().iterator().next();
        any.setHighlight(highlight("checkin_payment", "total_amount", 2000, "previous_payments", 500, "amount_paid_now", 1500, "total_paid", 2000, "remaining_balance", 0, "amount_to_pay", 0));
        String text = all(open(any).findViewById(R.id.layoutDynamicReceiptContent));
        assertEquals(text, "₱2,000.00", after(text, "Paid so far"));
        assertEquals(text, "₱0.00 · Fully paid", after(text, "Balance due"));
        assertTrue(text, text.contains("Check-in"));
    }

    @Test
    public void receipt3_showsTheFullBillAndGrandTotal_once() {
        ReceiptDetail official = DebugReceiptFixtures.allReceiptDetails().values().stream().filter(d -> "OFFICIAL_RECEIPT".equals(d.getReceiptType())).findFirst().get();
        official.setHighlight(highlight("checkout_final", "room_charges", 2000, "amenities_total", 300, "additional_charges_total", 400, "discount", 0,
                "grand_total", 2700, "paid_receipt_1", 500, "paid_receipt_2", 1500, "paid_at_checkout", 700, "total_paid", 2700, "remaining_balance", 0));
        String text = all(open(official).findViewById(R.id.layoutDynamicReceiptContent));
        assertEquals(text, "₱2,000.00", after(text, "Room charges"));
        assertEquals(text, "₱300.00", after(text, "Amenities"));
        assertEquals(text, "₱400.00", after(text, "Other charges (front desk)"));
        assertEquals(text, "₱2,700.00", after(text, "Grand total"));
        assertEquals(text, "₱2,700.00", after(text, "Paid so far"));
        assertEquals(text, "₱0.00 · Fully paid", after(text, "Balance due"));
        assertEquals("the grand total is printed once", text.indexOf("Grand total"), text.lastIndexOf("Grand total"));
    }

    @Test
    public void aReceiptWithoutAStoredHighlightStillOpens() {
        ReceiptDetail any = DebugReceiptFixtures.allReceiptDetails().values().iterator().next();
        any.setHighlight(null);
        assertFalse(all(open(any).findViewById(R.id.layoutDynamicReceiptContent)).contains("Amount to pay"));
    }

    // ---- Booking Details: one bill, no second balance card

    private BookingDetailsActivity details(Booking b) {
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, BookingDetailsActivity.newIntent(app, b)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        return a;
    }

    @Test
    public void bookingDetailsShowsWhatTheGuestStillOwes_exactlyOnce() {
        Booking b = new Booking("77", "1", "Deluxe", "Deluxe", "Oct 20, 2026", "Oct 22, 2026", 2, 5000.0, "Pending", "Oct 10, 2026");
        b.setHasBooking(true);
        String text = all(details(b).getWindow().getDecorView());
        assertEquals(text, "₱5,000.00", after(text, "Balance due"));
        assertEquals("Balance due appears once", text.indexOf("Balance due"), text.lastIndexOf("Balance due"));
        assertEquals(text, "₱0.00", after(text, "Paid so far"));
        assertTrue(text, text.contains("You pay the balance at the hotel."));
        assertFalse("the old balance card is gone", text.contains("You still need to pay") || text.contains("Your balance"));
    }

    @Test
    public void aFullyPaidBookingSaysSo() {
        Booking b = new Booking("78", "1", "Deluxe", "Deluxe", "Oct 20, 2026", "Oct 22, 2026", 2, 5000.0, "Confirmed", "Oct 10, 2026");
        b.setHasBooking(true);
        b.setStaffVerified(true);
        String text = all(details(b).getWindow().getDecorView());
        assertTrue(text, text.contains("Balance due"));
    }

    // ---- the receipt PDF is paper: white page, dark text, in either theme

    private static void collectTextColors(View v, List<String> darkOnly, List<String> offenders) {
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE && ((TextView) v).getText().length() > 0) {
            int c = ((TextView) v).getCurrentTextColor();
            double lum = (0.299 * ((c >> 16) & 0xFF) + 0.587 * ((c >> 8) & 0xFF) + 0.114 * (c & 0xFF)) / 255.0;
            (lum > 0.6 ? offenders : darkOnly).add(((TextView) v).getText() + " #" + Integer.toHexString(c));
        }
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collectTextColors(((ViewGroup) v).getChildAt(i), darkOnly, offenders);
        }
    }

    @Test
    public void thePdfIsAWhitePageWithDarkText_evenWhenTheAppIsInDarkMode() {
        ThemePreferences.setMode(app, ThemePreferences.MODE_DARK);
        // Dark mode is forced on the resources themselves (not only through AppCompat), so the result never depends on which tests ran before.
        org.robolectric.RuntimeEnvironment.setQualifiers("+night");
        Map<String, ReceiptDetail> all = DebugReceiptFixtures.allReceiptDetails();
        ReceiptDetail partial = all.values().stream().filter(d -> "PARTIAL_RECEIPT".equals(d.getReceiptType())).findFirst().get();
        partial.setHighlight(highlight("booking_verification", "total_amount", 2000, "amount_paid_now", 500, "total_paid", 500, "remaining_balance", 1500, "amount_to_pay", 1500));
        PaymentReceiptActivity screen;
        try {
            screen = open(partial);
        } finally {
            org.robolectric.RuntimeEnvironment.setQualifiers("+notnight");
        }

        View page = screen.buildReceiptForPdf(1080);

        assertEquals("a white page", android.graphics.Color.WHITE, ((android.graphics.drawable.ColorDrawable) page.getBackground()).getColor());
        List<String> dark = new ArrayList<>();
        List<String> light = new ArrayList<>();
        collectTextColors(page, dark, light);
        assertTrue("there is text on the page", dark.size() > 10);
        assertTrue("no light-coloured text on the white page: " + light, light.isEmpty());
        assertTrue("the bill is in the PDF", all(page).contains("Balance due"));
        assertTrue("laid out with a real height", page.getMeasuredHeight() > 600);

        // the on-screen receipt is unchanged: still the dark theme's own light text
        List<String> screenDark = new ArrayList<>();
        List<String> screenLight = new ArrayList<>();
        collectTextColors(screen.findViewById(R.id.layoutDynamicReceiptContent), screenDark, screenLight);
        assertFalse("the screen keeps following the dark theme", screenLight.isEmpty());
    }
}
