package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingAndReservationActivity;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.MultiStayFixtures;
import com.example.velocitysuites.PaymentReceiptActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.ReceiptDetail;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.debug.DebugReceiptFixtures;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.PaymentSummaryDto;
import com.google.android.material.card.MaterialCardView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 1.60.0: a submitted but unverified payment is NOT paid. The Bill shows it on its own line and computes the balance from verified payments
 * only; the progress steps, the list cards and the receipts agree; a rejected payment leaves "waiting" and says why. Plus the layout and
 * contrast checks (360dp and 393dp, light and dark) for the payment progress, Payments rows, receipt list and receipt headers.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Release160Test {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static final String[] CONFIGS = {"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"};

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com", "09171234567", "Female", "Jan 01, 1990", "");
        PaymentReceiptActivity.debugPreviewFixtures.clear();
        PaymentReceiptActivity.debugPreviewAmenities.clear();
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

    private static String after(List<String> t, String label) {
        int i = t.indexOf(label);
        return i >= 0 && i + 1 < t.size() ? t.get(i + 1) : null;
    }

    private BookingDetailsActivity open(Booking b) {
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, BookingDetailsActivity.newIntent(app, b)).setup().get();
        idle();
        return a;
    }

    private static Booking.PaymentTransactionRecord gcash(long id, String paymentStatus, String verification, String verifiedAt, String rejection) {
        return new Booking.PaymentTransactionRecord(id, "gcash", "deposit", "partial_payment", 3500, paymentStatus, verification, null, null, "6564676886467", null, null,
                verifiedAt, rejection, "2026-10-24T10:37:34Z", 0, 0, null, null);
    }

    /** The 18,900 two-range booking with one GCash payment of 3,500 in the given state (the server's own figures: verified money only in "paid"). */
    private Booking withPayment(String state) {
        Booking b = MultiStayFixtures.booking();
        b.setStatus("Confirmed");
        Booking.PaymentSummary summary;
        switch (state) {
            case "pending":
                b.setPaymentTransactions(Arrays.asList(gcash(1, "completed", "pending_verification", null, null)));
                summary = new Booking.PaymentSummary(18900, 0, 18900, "PENDING", 50, false, 0);
                summary.waitingForVerification = 3500;
                break;
            case "verified":
                b.setPaymentTransactions(Arrays.asList(gcash(1, "completed", "verified", "2026-10-24T10:40:00Z", null)));
                summary = new Booking.PaymentSummary(18900, 3500, 15400, "PARTIALLY_PAID", 50, false, 0);
                break;
            default: // rejected
                b.setPaymentTransactions(Arrays.asList(gcash(1, "rejected", "rejected", null, "The reference number does not match.")));
                summary = new Booking.PaymentSummary(18900, 0, 18900, "PENDING", 50, false, 0);
        }
        b.setPaymentSummary(summary);
        return b;
    }

    // ---- the Bill ----

    @Test
    public void pending_isOnItsOwnLine_notInPaidSoFar_andTheBalanceIsFromVerifiedMoneyOnly() {
        List<String> t = textsOf(open(withPayment("pending")).getWindow().getDecorView());
        assertEquals(t.toString(), "₱0.00", after(t, "Paid so far"));
        assertEquals(t.toString(), "₱3,500.00", after(t, "Waiting for verification"));
        assertEquals("the balance ignores the waiting payment", "₱18,900.00", after(t, "Remaining balance"));
        assertEquals("₱0.00 now · ₱3,500.00 waiting for verification", after(t, "Amount to pay"));
        assertTrue(t.toString(), t.contains("₱3,500.00 will be deducted once the front desk verifies your payment."));
        assertFalse("not verified yet", String.join(String.valueOf((char) 10), t).contains("✓ Verified by the front desk") || t.contains("Fully paid"));
    }

    @Test
    public void verified_isPaid_andThereIsNoWaitingLine() {
        List<String> t = textsOf(open(withPayment("verified")).getWindow().getDecorView());
        assertEquals(t.toString(), "₱3,500.00", after(t, "Paid so far"));
        assertEquals("₱15,400.00", after(t, "Remaining balance"));
        assertEquals("₱15,400.00 · at check-out", after(t, "Amount to pay"));
        assertFalse(t.toString(), t.contains("Waiting for verification") && !t.contains("Waiting for front desk verification"));
        assertFalse(t.toString(), String.join("\n", t).contains("will be deducted"));
    }

    @Test
    public void rejected_isNotWaiting_andSaysWhy() {
        List<String> t = textsOf(open(withPayment("rejected")).getWindow().getDecorView());
        assertFalse(t.toString(), t.contains("Waiting for verification"));
        assertFalse(String.join("\n", t), String.join("\n", t).contains("will be deducted"));
        assertEquals("₱0.00", after(t, "Paid so far"));
        String all = String.join("\n", t);
        assertTrue(all, all.contains("Payment rejected: The reference number does not match."));
    }

    // ---- the progress steps ----

    @Test
    public void progress_pending_says_submitted_notPaidOnline_andVerificationIsTheStepToCome() {
        String all = String.join("\n", textsOf(open(withPayment("pending")).getWindow().getDecorView()));
        assertTrue(all, all.contains("0% paid"));
        assertTrue(all, all.contains("₱3,500.00 waiting for verification"));
        assertTrue(all, all.contains("✓ Payment submitted (GCash)") && all.contains("○ Verified by the front desk"));
        assertFalse(all, all.contains("✓ Paid online (GCash)"));
    }

    @Test
    public void progress_verified_and_rejected() {
        String verified = String.join("\n", textsOf(open(withPayment("verified")).getWindow().getDecorView()));
        assertTrue(verified, verified.contains("18% paid") && verified.contains("✓ Paid online (GCash)") && verified.contains("✓ Verified by the front desk"));
        String rejected = String.join("\n", textsOf(open(withPayment("rejected")).getWindow().getDecorView()));
        assertTrue(rejected, rejected.contains("○ Payment rejected: The reference number does not match."));
        assertFalse(rejected, rejected.contains("waiting for verification"));
    }

    // ---- the list cards ----

    private DirectBookingResponseDto cardBooking(String paymentStatus, double verifiedPaid, double waiting) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));
        DirectBookingResponseDto d = ScreenTestSupport.directBooking(567, 3500);
        d.check_in = today.plusDays(1).toString();
        d.check_out = today.plusDays(2).toString();
        d.number_of_guests = 1;
        d.booking_status = "CONFIRMED";
        d.payment_method = "GCASH";
        d.payments = new ArrayList<>(Arrays.asList(ScreenTestSupport.payment(9, "1750.00", paymentStatus, "gcash")));
        PaymentSummaryDto summary = new PaymentSummaryDto();
        summary.grand_total = 3500;
        summary.total_amount_paid = verifiedPaid;
        summary.remaining_balance = 3500 - verifiedPaid;
        summary.waiting_for_verification = waiting;
        summary.payment_status = verifiedPaid > 0 ? "PARTIALLY_PAID" : "PENDING";
        d.payment_summary = summary;
        return d;
    }

    private String cardPaidLine(DirectBookingResponseDto d) {
        BookingAndReservationActivity a = Robolectric.buildActivity(BookingAndReservationActivity.class, new Intent(app, BookingAndReservationActivity.class)).setup().get();
        idle();
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1, ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(d));
        idle();
        RecyclerView rv = a.findViewById(R.id.rvItemList);
        RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(0);
        assertNotNull(h);
        return ((TextView) h.itemView.findViewById(R.id.tvBookingPaidLine)).getText().toString();
    }

    @Test
    public void theCard_pending_showsPaidZero_andWaitingOnItsOwnLine() {
        String line = cardPaidLine(cardBooking("pending", 0, 1750));
        assertEquals("Paid ₱0.00 · Balance ₱3,500.00\nWaiting for verification: ₱1,750.00", line);
    }

    @Test
    public void theCard_verified_hasNoWaitingLine_andRejectedNeitherWaitsNorPays() {
        assertEquals("Paid ₱1,750.00 · Balance ₱1,750.00", cardPaidLine(cardBooking("completed", 1750, 0)));
        assertEquals("Paid ₱0.00 · Balance ₱3,500.00", cardPaidLine(cardBooking("rejected", 0, 0)));
    }

    // ---- the receipts ----

    @Test
    public void receipts_followVerification_greyedUntilIssued_andNeverCountWaitingMoney() {
        List<String> pending = textsOf(open(withPayment("pending")).getWindow().getDecorView());
        assertTrue(pending.toString(), pending.contains("Issued after the front desk verifies your payment"));
        assertFalse(pending.toString(), pending.contains("Issued"));

        Booking verified = withPayment("verified");
        verified.setReceipts(Arrays.asList(new Booking.ReceiptSummary("PR-20261024-000657", "PARTIAL_RECEIPT", "VERIFIED", 3500, 50, "2026-10-24T10:40:00Z")));
        List<String> t = textsOf(open(verified).getWindow().getDecorView());
        assertTrue(t.toString(), t.contains("Issued") && !t.contains("Issued after the front desk verifies your payment"));

        ReceiptDetail receipt = DebugReceiptFixtures.partialReceipt();
        PaymentReceiptActivity.debugPreviewFixtures.put(receipt.getReceiptNumber(), receipt);
        PaymentReceiptActivity r = Robolectric.buildActivity(PaymentReceiptActivity.class, PaymentReceiptActivity.newIntentForReceipt(app, receipt.getReceiptNumber())).setup().get();
        idle();
        assertFalse("a receipt shows verified money only", textsOf(r.findViewById(R.id.layoutDynamicReceiptContent)).contains("Waiting for verification"));
    }

    // ---- layout and contrast: 360dp and 393dp, light and dark ----

    private static double luminance(int c) {
        double[] rgb = {Color.red(c) / 255.0, Color.green(c) / 255.0, Color.blue(c) / 255.0};
        for (int i = 0; i < 3; i++) rgb[i] = rgb[i] <= 0.03928 ? rgb[i] / 12.92 : Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
    }

    private static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static int over(int fg, int bg) {
        int alpha = Color.alpha(fg);
        if (alpha >= 255) return fg;
        float a = alpha / 255f;
        return Color.rgb(Math.round(Color.red(fg) * a + Color.red(bg) * (1 - a)), Math.round(Color.green(fg) * a + Color.green(bg) * (1 - a)), Math.round(Color.blue(fg) * a + Color.blue(bg) * (1 - a)));
    }

    private static int backgroundBehind(View v, int windowBackground) {
        if (v.getBackground() != null && v.getBackgroundTintList() != null && Color.alpha(v.getBackgroundTintList().getDefaultColor()) == 255) return v.getBackgroundTintList().getDefaultColor();
        View cur = v.getParent() instanceof View ? (View) v.getParent() : null;
        while (cur != null) {
            Drawable d = cur.getBackground();
            if (cur instanceof MaterialCardView && Color.alpha(((MaterialCardView) cur).getCardBackgroundColor().getDefaultColor()) == 255) return ((MaterialCardView) cur).getCardBackgroundColor().getDefaultColor();
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

    private static void layout(android.app.Activity a) {
        View decor = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels;
        int h = a.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h * 6, View.MeasureSpec.AT_MOST));
        decor.layout(0, 0, w, decor.getMeasuredHeight());
    }

    private static boolean shown(View v) {
        for (View cur = v; cur != null; cur = cur.getParent() instanceof View ? (View) cur.getParent() : null) if (cur.getVisibility() != View.VISIBLE) return false;
        return true;
    }

    private static void collect(View v, List<TextView> out) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof TextView && !(v instanceof android.widget.EditText)) out.add((TextView) v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), out);
    }

    private static int xOf(View root, View v) {
        int x = 0;
        for (View cur = v; cur != null && cur != root; cur = cur.getParent() instanceof View ? (View) cur.getParent() : null) x += cur.getLeft();
        return x;
    }

    /** Every visible text under {@code scope}: shown, not empty, inside the screen's width, and at least 4.5:1 against what is behind it. */
    static void assertReadable(String tag, android.app.Activity a, View scope) {
        layout(a);
        View root = a.getWindow().getDecorView();
        int windowBg = com.google.android.material.color.MaterialColors.getColor(a, android.R.attr.colorBackground, Color.WHITE);
        int screen = a.getResources().getDisplayMetrics().widthPixels;
        List<TextView> all = new ArrayList<>();
        collect(scope, all);
        assertFalse(tag + ": something is shown", all.isEmpty());
        for (TextView t : all) {
            String name = "'" + t.getText() + "'";
            if (t instanceof Button) continue;
            assertTrue(tag + " " + name + " is shown", shown(t));
            assertTrue(tag + " " + name + " is not empty", t.getText().toString().trim().length() > 0);
            assertTrue(tag + " " + name + " has width", t.getWidth() >= 8 || t.getText().length() <= 1);
            int x = xOf(root, t);
            assertTrue(tag + " " + name + " inside the screen (" + x + ".." + (x + t.getWidth()) + " of " + screen + ")", x >= 0 && x + t.getWidth() <= screen);
            int bg = backgroundBehind(t, windowBg);
            double ratio = contrast(over(t.getCurrentTextColor(), bg), bg);
            assertTrue(tag + " " + name + " contrast " + String.format(java.util.Locale.US, "%.2f", ratio) + " (text " + Integer.toHexString(t.getCurrentTextColor()) + " on " + Integer.toHexString(bg) + ")", ratio >= 4.5);
        }
    }

    @Test
    public void paymentProgress_paymentsReceiptsList_areReadable_in360And393_lightAndDark() {
        for (String q : CONFIGS) {
            for (String state : new String[]{"pending", "verified", "rejected"}) {
                RuntimeEnvironment.setQualifiers("+" + q);
                ScreenTestSupport.freshRepository(app, api = new ScreenTestSupport.FakeApi());
                Booking b = withPayment(state);
                b.setReceipts(Arrays.asList(new Booking.ReceiptSummary("PR-20261024-000657", "PARTIAL_RECEIPT", "VERIFIED", 3500, 50, "2026-10-24T10:40:00Z")));
                BookingDetailsActivity a = open(b);
                String tag = q + " " + state;
                assertReadable(tag + " progress", a, a.findViewById(R.id.sectionBalanceContent));
                assertReadable(tag + " bill", a, a.findViewById(R.id.sectionPaymentSummaryContent));
                assertReadable(tag + " payments", a, a.findViewById(R.id.sectionPaymentHistoryContent));
                assertReadable(tag + " receipts", a, a.findViewById(R.id.sectionReceiptsContent));
            }
        }
        RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
    }

    @Test
    public void receiptHeaders_areReadable_in360And393_lightAndDark() {
        for (String q : CONFIGS) {
            for (ReceiptDetail detail : DebugReceiptFixtures.allReceiptDetails().values()) {
                RuntimeEnvironment.setQualifiers("+" + q);
                ScreenTestSupport.freshRepository(app, api = new ScreenTestSupport.FakeApi());
                PaymentReceiptActivity.debugPreviewFixtures.put(detail.getReceiptNumber(), detail);
                PaymentReceiptActivity a = Robolectric.buildActivity(PaymentReceiptActivity.class, PaymentReceiptActivity.newIntentForReceipt(app, detail.getReceiptNumber())).setup().get();
                idle();
                assertReadable(q + " " + detail.getReceiptNumber(), a, a.findViewById(R.id.layoutDynamicReceiptContent));
            }
        }
        RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
    }
}
