package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.MultiStayFixtures;
import com.example.velocitysuites.PaymentReceiptActivity;
import com.example.velocitysuites.PaymentTransaction;
import com.example.velocitysuites.R;
import com.example.velocitysuites.ReceiptDetail;
import com.example.velocitysuites.TransactionDetailsActivity;
import com.example.velocitysuites.debug.DebugReceiptFixtures;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 1.61.0: a date range marked as a no-show reads "No-show" on the details screen, the timeline and the final receipt (still charged), and every
 * title on the details screens is readable in dark mode (contrast of at least 4.5:1) at 360dp and 393dp.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Release161Test {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static final String[] CONFIGS = {"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"};

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        ScreenTestSupport.goOnline(app);
        PaymentReceiptActivity.debugPreviewFixtures.clear();
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

    /** The two-range booking where the second range was marked as a no-show by the front desk. */
    private Booking withNoShowRange() {
        Booking b = MultiStayFixtures.booking();
        Booking.StayRecord first = b.getStays().get(0);
        Booking.StayRecord second = b.getStays().get(1);
        b.setStays(Arrays.asList(first, new Booking.StayRecord(second.id, second.position, second.checkIn, second.checkOut, second.nights, "no_show",
                second.rooms, second.amenities, second.roomsSubtotal, second.amenitiesSubtotal, second.subtotal)));
        b.setTimeline(Arrays.asList(
                new Booking.TimelineEntry("stay_1_checked_in", "Checked in (Oct 26 → Oct 27)", "Verified", "x", "2026-10-26T01:00:00Z"),
                new Booking.TimelineEntry("stay_2_no_show", "No-show (Oct 27 → Oct 28)", "Verified", "x", "2026-10-28T02:00:00Z")));
        return b;
    }

    @Test
    public void aNoShowRange_isLabelled_onTheDetails_andTheTimeline_andStillCharged() {
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, BookingDetailsActivity.newIntent(app, withNoShowRange())).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        List<String> t = textsOf(a.getWindow().getDecorView());
        assertTrue(t.toString(), t.contains("Oct 27 → Oct 28, 2026 · 1 night · No-show"));
        assertTrue("the other range is not marked", t.contains("Oct 26 → Oct 27, 2026 · 1 night"));
        assertTrue(t.toString(), t.contains("No-show (Oct 27 → Oct 28)"));
        int total = t.indexOf("Grand total");
        assertEquals("still charged in full (rooms of both ranges)", "₱18,900.00", t.get(total + 1));
    }

    @Test
    public void theFinalReceipt_shows_theNoShowRange() {
        ReceiptDetail official = DebugReceiptFixtures.officialReceipt();
        official.setStays(withNoShowRange().getStays());
        PaymentReceiptActivity.debugPreviewFixtures.put(official.getReceiptNumber(), official);
        PaymentReceiptActivity a = Robolectric.buildActivity(PaymentReceiptActivity.class, PaymentReceiptActivity.newIntentForReceipt(app, official.getReceiptNumber())).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        for (List<String> t : Arrays.asList(textsOf(a.findViewById(R.id.layoutDynamicReceiptContent)), textsOf(a.buildReceiptForPdf(1080)))) {
            assertTrue(t.toString(), t.contains("Oct 27 → Oct 28, 2026 · 1 night · No-show"));
            assertFalse(t.toString(), t.contains("Oct 26 → Oct 27, 2026 · 1 night · No-show"));
        }
    }

    @Test
    public void everyTitleOnTheDetailsScreens_isReadable_in360And393_lightAndDark() {
        for (String q : CONFIGS) {
            RuntimeEnvironment.setQualifiers("+" + q);
            ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
            Booking b = withNoShowRange();
            BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, BookingDetailsActivity.newIntent(app, b)).setup().get();
            shadowOf(Looper.getMainLooper()).idle();
            View scroll = a.getWindow().getDecorView();
            Release160Test.assertReadable(q + " booking details", a, scroll);

            PaymentTransaction tx = new PaymentTransaction(b, new Booking.PaymentRecord("1000.00", "GCASH", "REF1", "2026-10-24", "completed", null));
            TransactionDetailsActivity t = Robolectric.buildActivity(TransactionDetailsActivity.class, TransactionDetailsActivity.newIntent(app, tx)).setup().get();
            shadowOf(Looper.getMainLooper()).idle();
            Release160Test.assertReadable(q + " transaction details", t, t.getWindow().getDecorView());
        }
        RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
    }
}
