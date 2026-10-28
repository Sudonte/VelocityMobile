package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ui.TestApplication;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 1.69.0: each receipt row says what its amount is, a cash payment at the desk is one timeline entry, and "Nothing to pay" is green
 * (red only when something is owed) in both themes.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class Release169Test {

    @Rule
    public final ThemeResetRule themeReset = new ThemeResetRule();

    private static Context ctx() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_VelocitySuites);
    }

    private static void texts(View v, List<String> out) {
        if (v instanceof TextView && ((TextView) v).getText().length() > 0) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) texts(((ViewGroup) v).getChildAt(i), out);
    }

    private static Booking booking(String status) {
        Booking b = new Booking("596", "1", "Deluxe", "Deluxe", "Oct 28, 2026", "Oct 29, 2026", 2, 3000, status, "Oct 28, 2026");
        b.setHasBooking(true);
        b.setDirectBooking(true);
        b.setStatus(status);
        b.setPaymentSummary(new Booking.PaymentSummary(3000, 3000, 0, "PAID", 0, false, 0));
        return b;
    }

    private static Booking.ReceiptSummary receipt(String number, String type, double amount, String method, Boolean fullyPaid) {
        return new Booking.ReceiptSummary(number, type, "VERIFIED", amount, 100, "2026-10-28T07:04:48+00:00", method, fullyPaid);
    }

    private List<String> rowTexts(Booking b) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_VelocitySuites);
        LinearLayout host = new LinearLayout(activity);
        DetailsParts.receipts(activity, host, b);
        List<String> t = new ArrayList<>();
        texts(host, t);
        return t;
    }

    // ---- what each receipt row's amount is ----

    @Test
    public void eachReceiptRowSaysWhatItsAmountIs() {
        Booking b = booking("Completed");
        b.setReceipts(Arrays.asList(
                receipt("PR-20261028-000673", "PARTIAL_RECEIPT", 1750, "gcash", null),
                receipt("SR-20261028-000154", "CHECKIN_SETTLEMENT_RECEIPT", 1000, "cash", null),
                receipt("OR-20261028-000154", "OFFICIAL_RECEIPT", 3000, null, true)));
        List<String> t = rowTexts(b);
        assertTrue(t.toString(), t.contains("Receipt 1 · Payment verified"));
        assertTrue(t.toString(), t.contains("Paid online ₱1,750.00"));
        assertTrue(t.toString(), t.contains("Receipt 2 · Check-in"));
        assertTrue(t.toString(), t.contains("Paid at check-in ₱1,000.00"));
        assertTrue(t.toString(), t.contains("Receipt 3 · Check-out (final)"));
        assertTrue(t.toString(), t.contains("Total ₱3,000.00 · Fully paid"));
    }

    @Test
    public void aCheckInWithNoPayment_saysSo() {
        Booking b = booking("Checked-In");
        b.setReceipts(Collections.singletonList(receipt("SR-20261028-000154", "CHECKIN_SETTLEMENT_RECEIPT", 0, null, null)));
        List<String> t = rowTexts(b);
        assertTrue(t.toString(), t.contains("No payment at check-in"));
        assertFalse(t.toString(), t.contains("Paid at check-in ₱0.00"));
    }

    @Test
    public void aCashPaymentVerifiedAtBooking_andAFinalReceiptStillOwing_areLabelledToo() {
        Booking b = booking("Checked-In");
        b.setReceipts(Arrays.asList(
                receipt("PR-1", "PARTIAL_RECEIPT", 500, "cash", null),
                receipt("OR-1", "OFFICIAL_RECEIPT", 2000, null, false)));
        List<String> t = rowTexts(b);
        assertTrue(t.toString(), t.contains("Paid in cash ₱500.00"));
        assertTrue(t.toString(), t.contains("Total ₱2,000.00 · Balance still due"));
    }

    // ---- the timeline ----

    private static Booking.PaymentTransactionRecord payment(long id, String method, String type, String reference, String at) {
        return new Booking.PaymentTransactionRecord(id, method, "deposit", type, 1000, "completed", "verified", null, null, reference, null, null, at,
                null, at, 1000, 0, null, null);
    }

    private static List<String> titles(Booking b) {
        List<String> out = new ArrayList<>();
        for (TransactionTimeline.Entry e : TransactionTimeline.build(ctx(), b, null, Collections.emptyList())) out.add(e.title);
        return out;
    }

    @Test
    public void aCashPaymentAtCheckIn_isOneEntry_paidAtCheckInCash() {
        Booking b = booking("Checked-In");
        b.setPaymentTransactions(Collections.singletonList(payment(2, "cash", "PARTIAL_PAYMENT", "CHECKIN-596-20261028070448", "2026-10-28T07:04:48Z")));
        List<String> t = titles(b);
        assertEquals(t.toString(), 1, java.util.Collections.frequency(t, "Paid at check-in · Cash"));
        assertFalse(t.toString(), t.contains("Payment submitted"));
        assertFalse(t.toString(), t.contains("Payment verified by the front desk"));
    }

    @Test
    public void aCashPaymentAtCheckOut_isOneEntry_paidAtCheckOutCash() {
        Booking b = booking("Completed");
        b.setPaymentTransactions(Collections.singletonList(payment(3, "cash", "CHECKOUT_PAYMENT", "CHK-596", "2026-10-29T03:00:00Z")));
        List<String> t = titles(b);
        assertTrue(t.toString(), t.contains("Paid at check-out · Cash"));
        assertFalse(t.toString(), t.contains("Payment submitted"));
    }

    @Test
    public void aGcashPayment_keepsSubmittedThenVerifiedByTheFrontDesk() {
        Booking b = booking("Confirmed");
        b.setPaymentTransactions(Collections.singletonList(payment(1, "gcash", "PARTIAL_PAYMENT", "1945949989879", "2026-10-28T07:02:36Z")));
        List<String> t = titles(b);
        assertTrue(t.toString(), t.contains("Payment submitted"));
        assertTrue(t.toString(), t.contains("Payment verified by the front desk"));
        assertFalse(t.toString(), t.contains("Paid at check-in · GCash"));
    }

    // ---- the bottom summary ----

    private static TextView find(View v, String contains) {
        if (v instanceof TextView && ((TextView) v).getText().toString().contains(contains)) return (TextView) v;
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                TextView found = find(((ViewGroup) v).getChildAt(i), contains);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    public void nothingToPay_isGreen_andWhatIsOwedIsRed() {
        Context c = ctx();
        TextView settled = find(MoneySummaryPanel.box(c, MoneySummary.ofReceipt(c, 2, 3000, 0)), "Nothing to pay");
        assertEquals("₱0.00 · Nothing to pay", settled.getText().toString());
        assertEquals(ContextCompat.getColor(c, R.color.velocity_success_text), settled.getCurrentTextColor());

        TextView owed = find(MoneySummaryPanel.box(c, MoneySummary.ofReceipt(c, 2, 3000, 1800)), "at check-out");
        assertEquals(ContextCompat.getColor(c, R.color.velocity_action_text), owed.getCurrentTextColor());
        assertTrue(owed.getText().toString().contains("₱1,800.00"));
    }
}
