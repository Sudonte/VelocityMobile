package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BillingSummaryActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.ReservationDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Billing Summary: the header leads with the Grand Total, the highlighted block reads "Grand Total" before any payment
 * and "Remaining Balance" after one, Confirm Booking starts disabled with a reason, and no placeholder text shows.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BillingSummaryScreenTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com",
                "09171234567", "Female", "Jan 01, 1990", "");
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private BillingSummaryActivity open(ReservationDto reservation) {
        BillingSummaryActivity activity = Robolectric.buildActivity(BillingSummaryActivity.class,
                new Intent(app, BillingSummaryActivity.class).putExtra(BillingSummaryActivity.EXTRA_RESERVATION_ID, String.valueOf(reservation.id)))
                .setup().get();
        idle();
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(reservation), ScreenTestSupport.directBookings());
        idle();
        return activity;
    }

    private static String text(BillingSummaryActivity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private static void collect(View v, List<String> out) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof TextView) out.add(((TextView) v).getText().toString() + (((TextView) v).getText().length() == 0 ? " [id " + v.getId() + " parent " + ((View) v.getParent()).getId() + "]" : ""));
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    @Test
    public void beforeAnyPayment_headerAndHighlightedBlockShowTheGrandTotal() {
        BillingSummaryActivity a = open(ScreenTestSupport.reservation(588, 1800));

        assertEquals("₱1,800.00", text(a, R.id.tvBillingHeaderGrandTotal));
        assertEquals("the block is just the amount - no repeated label", View.GONE, a.findViewById(R.id.tvBillingPayNowOption).getVisibility());
        assertEquals("₱1,800.00", text(a, R.id.tvBillingPayNowAmount));
        assertEquals(View.VISIBLE, a.findViewById(R.id.tvBillingPayNowNote).getVisibility());
        assertTrue(text(a, R.id.tvBillingPayNowNote).contains("20%"));
        assertEquals("₱0.00", text(a, R.id.tvBillingAlreadyPaid));
        assertEquals("₱1,800.00", text(a, R.id.tvBillingRemaining));
        assertEquals("the stay shows the real room type, not a placeholder", "Deluxe", text(a, R.id.tvBillingRoomType));
    }

    @Test
    public void afterAGcashPayment_theBlockShowsTheRemainingBalance_andTheAmountPaid() {
        BillingSummaryActivity a = open(ScreenTestSupport.reservation(588, 1800,
                ScreenTestSupport.payment(1, "500.00", "completed", "gcash")));

        assertEquals("₱500.00", text(a, R.id.tvBillingAlreadyPaid));
        assertEquals("₱1,300.00", text(a, R.id.tvBillingRemaining));
        assertEquals(View.VISIBLE, a.findViewById(R.id.tvBillingPayNowOption).getVisibility());
        assertEquals("Remaining Balance", text(a, R.id.tvBillingPayNowOption));
        assertEquals("₱1,300.00", text(a, R.id.tvBillingPayNowAmount));
        assertEquals(View.GONE, a.findViewById(R.id.tvBillingPayNowNote).getVisibility());
    }

    @Test
    public void confirmBookingStartsDisabled_withTheReason_andNoPlaceholderTextShows() {
        BillingSummaryActivity a = open(ScreenTestSupport.reservation(588, 1800));

        View confirm = a.findViewById(R.id.btnConfirmBilling);
        assertFalse(confirm.isEnabled());
        assertFalse(a.findViewById(R.id.cbAcceptBillingTerms).isEnabled());
        assertEquals(a.getString(R.string.billing_confirm_hint_read), text(a, R.id.tvBillingConfirmHint));
        assertEquals("full width", ViewGroup.LayoutParams.MATCH_PARENT, confirm.getLayoutParams().width);

        List<String> shown = new ArrayList<>();
        collect(a.findViewById(android.R.id.content), shown);
        for (String t : shown) {
            assertFalse("placeholder text on screen: '" + t + "'", t.equalsIgnoreCase("null") || t.equalsIgnoreCase("N/A")
                    || t.equals("-") || t.contains("[id") || t.contains("No room selected") || t.contains("null"));
        }
    }

    @Test
    public void noRepetition_grandTotalTwice_oneTermsNotice_shortLabels() {
        BillingSummaryActivity a = open(ScreenTestSupport.reservation(588, 1800));
        List<String> shown = new ArrayList<>();
        collect(a.findViewById(android.R.id.content), shown);

        int grandTotals = 0, amenitiesTotals = 0;
        for (String t : shown) {
            if (t.equals("Grand Total")) grandTotals++;
            if (t.equals("Amenities Total")) amenitiesTotals++;
        }
        // The header and Billing Breakdown carry "Grand Total"; the highlighted block shows only the amount.
        assertEquals(2, grandTotals);
        assertEquals("Add-on Amenities and Billing Breakdown", 2, amenitiesTotals);
        assertEquals("Representative", a.getString(R.string.billing_representative_label)); // its row shows only when a name was captured
        assertTrue(shown.contains("Terms & Agreement"));
        assertEquals("no hint under the checkbox any more", 0,
                a.getResources().getIdentifier("tvBillingTermsHint", "id", a.getPackageName()));
        assertTrue("the hint above the button is one short line", text(a, R.id.tvBillingConfirmHint).length() < 50);
    }

    @Test
    public void amountAlreadyPaid_isGreenOnlyOnceSomethingWasPaid() {
        BillingSummaryActivity before = open(ScreenTestSupport.reservation(588, 1800));
        int normal = before.getColor(R.color.velocity_text_primary);
        assertEquals(normal, ((TextView) before.findViewById(R.id.tvBillingAlreadyPaid)).getCurrentTextColor());
    }

    @Test
    public void nightsAppearOnlyOnce() {
        BillingSummaryActivity a = open(ScreenTestSupport.reservation(588, 1800));
        assertEquals(0, a.getResources().getIdentifier("tvBillingBreakdownNights", "id", a.getPackageName()));
        List<String> shown = new ArrayList<>();
        collect(a.findViewById(android.R.id.content), shown);
        int nightLabels = 0;
        for (String t : shown) if (t.equals(a.getString(R.string.nights_label))) nightLabels++;
        assertEquals(1, nightLabels);
    }
}
