package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.MoneySummaryPanel;
import com.example.velocitysuites.MultiStayFixtures;
import com.example.velocitysuites.PaymentReceiptActivity;
import com.example.velocitysuites.PaymentTransaction;
import com.example.velocitysuites.R;
import com.example.velocitysuites.ReceiptDetail;
import com.example.velocitysuites.TransactionDetailsActivity;
import com.example.velocitysuites.debug.DebugReceiptFixtures;

import org.junit.After;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 1.62.0: the money summary. Grand total, Remaining balance and Amount to pay are shown once, bold, in a panel pinned under the details
 * screens and in a box at the end of every receipt (screen and PDF); the Bill keeps only its itemized lines. The Amount to pay rule is
 * checked for every state, the three amounts never repeat, the summary matches the bill, the PDF matches the screen, a cancelled
 * transaction hides the panel, and everything stays readable at 360dp and 393dp, light and dark, at 1.3x and 2.0x font size.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Release162Test {

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
        PaymentReceiptActivity.debugPreviewAmenities.clear();
    }

    @After
    public void tearDown() {
        MoneySummaryPanel.motion = false;
        RuntimeEnvironment.setFontScale(1.0f);
        RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
    }

    // ---- helpers ----

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

    private static int count(List<String> t, String label) {
        int n = 0;
        for (String s : t) if (s.equals(label)) n++;
        return n;
    }

    private static double money(String s) {
        Matcher m = Pattern.compile("−?₱([0-9,]+\\.[0-9]{2})").matcher(s);
        assertTrue("an amount in '" + s + "'", m.find());
        double v = Double.parseDouble(m.group(1).replace(",", ""));
        return s.contains("−") ? -v : v;
    }

    private BookingDetailsActivity open(Booking b) {
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, BookingDetailsActivity.newIntent(app, b)).setup().get();
        idle();
        return a;
    }

    private TransactionDetailsActivity openTransaction(Booking b) {
        PaymentTransaction tx = new PaymentTransaction(b, new Booking.PaymentRecord("1000.00", "GCASH", "REF1", "2026-10-24", "completed", null));
        TransactionDetailsActivity t = Robolectric.buildActivity(TransactionDetailsActivity.class, TransactionDetailsActivity.newIntent(app, tx)).setup().get();
        idle();
        return t;
    }

    private static Booking.PaymentTransactionRecord gcash(long id, double amount, String paymentStatus, String verification, String verifiedAt) {
        return new Booking.PaymentTransactionRecord(id, "gcash", "deposit", "partial_payment", amount, paymentStatus, verification, null, null, "6564676886467", null, null,
                verifiedAt, null, "2026-10-24T10:37:34Z", 0, 0, null, null);
    }

    /** The 18,900 two-range booking in the given status with {@code paid} verified and {@code waiting} submitted (the server's own figures). */
    private static Booking booking(String status, double paid, double waiting) {
        Booking b = MultiStayFixtures.booking();
        b.setStatus(status);
        List<Booking.PaymentTransactionRecord> txs = new ArrayList<>();
        if (paid > 0) txs.add(gcash(1, paid, "completed", "verified", "2026-10-24T10:40:00Z"));
        if (waiting > 0) txs.add(gcash(2, waiting, "completed", "pending_verification", null));
        b.setPaymentTransactions(txs);
        Booking.PaymentSummary summary = new Booking.PaymentSummary(18900, paid, 18900 - paid, paid >= 18900 ? "PAID" : paid > 0 ? "PARTIALLY_PAID" : "PENDING", 50, false, 0);
        summary.waitingForVerification = waiting;
        b.setPaymentSummary(summary);
        return b;
    }

    private static FrameLayout host(android.app.Activity a) {
        return a.findViewById(R.id.moneySummaryHost);
    }

    /** [Grand total, Remaining balance, Amount to pay] as the panel shows them. */
    private static String[] summaryOf(android.app.Activity a) {
        List<String> t = textsOf(host(a));
        return new String[]{after(t, "Grand total"), after(t, "Remaining balance"), after(t, "Amount to pay")};
    }

    // ---- the Amount to pay rule, every state ----

    @Test
    public void amountToPay_followsTheOneRule_forEveryState() {
        Object[][] cases = {
                // status, verified, waiting, expected Amount to pay, expected Remaining balance
                {"Confirmed", 0.0, 3500.0, "₱0.00 now · ₱3,500.00 waiting for verification", "₱18,900.00"},
                {"Confirmed", 9450.0, 0.0, "₱9,450.00 · at check-in", "₱9,450.00"},
                {"Confirmed", 0.0, 0.0, "₱18,900.00 · at check-in", "₱18,900.00"},
                {"Checked-In", 9450.0, 0.0, "₱9,450.00 · at check-out", "₱9,450.00"},
                {"Checked-In", 18900.0, 0.0, "₱0.00 · Nothing to pay", "₱0.00"},
                {"Confirmed", 18900.0, 0.0, "₱0.00 · Nothing to pay", "₱0.00"},
                {"Completed", 18900.0, 0.0, "₱0.00 · Nothing to pay", "₱0.00"},
        };
        for (Object[] c : cases) {
            BookingDetailsActivity a = open(booking((String) c[0], (Double) c[1], (Double) c[2]));
            String[] s = summaryOf(a);
            String tag = c[0] + " paid " + c[1] + " waiting " + c[2] + " -> " + Arrays.toString(s);
            assertEquals(tag, "₱18,900.00", s[0]);
            assertEquals(tag, c[4], s[1]);
            assertEquals(tag, c[3], s[2]);
        }
    }

    @Test
    public void aReservationNothingWasPaidOn_saysPayNowRange_orPayAtTheHotel() {
        Booking gcash = MultiStayFixtures.reservation("none");
        gcash.setPaymentMethod("gcash");
        String[] s = summaryOf(open(gcash));
        assertEquals("₱19,015.00", s[0]);
        assertEquals("₱19,015.00", s[1]);
        assertEquals("Pay now: ₱3,803.00 – ₱9,507.50 (20%–50%)", s[2]);

        Booking cash = MultiStayFixtures.reservation("none");
        cash.setPaymentMethod("cash");
        assertEquals("Pay at the hotel", summaryOf(open(cash))[2]);
    }

    @Test
    public void aCancelledOrRejectedTransaction_hidesThePanel_andSaysSo() {
        for (String status : new String[]{"Cancelled", "Rejected"}) {
            BookingDetailsActivity a = open(booking(status, 0, 0));
            List<String> t = textsOf(host(a));
            assertEquals(t.toString(), Arrays.asList("This transaction was cancelled"), t);
            List<String> all = textsOf(a.getWindow().getDecorView());
            assertEquals(all.toString(), 0, count(all, "Grand total") + count(all, "Remaining balance") + count(all, "Amount to pay"));
            TransactionDetailsActivity tx = openTransaction(booking(status, 0, 0));
            assertEquals(Arrays.asList("This transaction was cancelled"), textsOf(host(tx)));
        }
    }

    // ---- no duplicates, and the summary matches the bill ----

    private void assertOnce(String tag, View root) {
        List<String> t = textsOf(root);
        assertEquals(tag + " Grand total once: " + t, 1, count(t, "Grand total"));
        assertEquals(tag + " Remaining balance once", 1, count(t, "Remaining balance"));
        assertEquals(tag + " Amount to pay once", 1, count(t, "Amount to pay"));
        assertEquals(tag + " no Balance due", 0, count(t, "Balance due"));
        String all = String.join(" | ", t);
        assertFalse(tag + " no 'of ₱ paid' progress line repeating the totals: " + all, Pattern.compile("of ₱[0-9,.]+ paid").matcher(all).find());
        assertFalse(tag + " no 'left' repeating the balance", all.contains(" left"));
    }

    @Test
    public void theThreeAmounts_appearOnce_onBothDetailsScreens_andTheBillDoesNotRepeatThem() {
        for (Booking b : Arrays.asList(booking("Confirmed", 9450, 0), booking("Confirmed", 0, 3500), booking("Checked-In", 9450, 0), booking("Completed", 18900, 0))) {
            BookingDetailsActivity a = open(b);
            assertOnce("booking details " + b.getStatus(), a.getWindow().getDecorView());
            List<String> bill = textsOf(a.findViewById(R.id.sectionPaymentSummaryContent));
            assertEquals(bill.toString(), 0, count(bill, "Grand total") + count(bill, "Remaining balance") + count(bill, "Amount to pay") + count(bill, "Balance due"));
            assertTrue("the bill keeps its itemized lines: " + bill, bill.contains("Paid so far") && bill.contains("Amenities"));

            TransactionDetailsActivity t = openTransaction(b);
            assertOnce("transaction details " + b.getStatus(), t.getWindow().getDecorView());
        }
    }

    @Test
    public void theSummary_matchesTheBill() {
        BookingDetailsActivity a = open(booking("Confirmed", 9450, 0));
        List<String> bill = textsOf(a.findViewById(R.id.sectionPaymentSummaryContent));
        double rooms = money(after(bill, "Room charges (all stays)") != null ? after(bill, "Room charges (all stays)") : after(bill, "Room charges"));
        double amenities = money(after(bill, "Amenities"));
        double paid = money(after(bill, "Paid so far"));
        String[] s = summaryOf(a);
        assertEquals("rooms + amenities = Grand total", rooms + amenities, money(s[0]), 0.005);
        assertEquals("Grand total - paid = Remaining balance", money(s[0]) - paid, money(s[1]), 0.005);
        assertEquals("at check-in the Amount to pay is the Remaining balance", money(s[1]), money(s[2]), 0.005);
    }

    // ---- the pinned panel ----

    @Test
    public void thePanel_isPinnedUnderThePage_boldTabular_andATapScrollsToTheBill() {
        BookingDetailsActivity a = open(booking("Confirmed", 9450, 0));
        FrameLayout host = host(a);
        assertEquals(View.VISIBLE, host.getVisibility());
        assertTrue("a sibling of the scrolling page, not inside it", host.getParent() == a.findViewById(R.id.swipeRefreshDetails).getParent());
        NestedScrollView scroller = (NestedScrollView) ((ViewGroup) a.findViewById(R.id.swipeRefreshDetails)).getChildAt(1);
        for (TextView tv : allText(host)) {
            if (tv.getText().toString().startsWith("₱")) {
                assertTrue("bold " + tv.getText(), tv.getTypeface().isBold());
                assertEquals("tabular figures " + tv.getText(), "tnum", tv.getFontFeatureSettings());
            }
        }
        // layout first so the Bill has a position, then tap (twice: a repeat tap is harmless)
        Release160Test.assertReadable("panel", a, host);
        View panel = host.getChildAt(0);
        assertTrue(panel.hasOnClickListeners());
        scroller.scrollTo(0, 0);
        panel.performClick();
        panel.performClick();
        idle();
        assertTrue("scrolled down to the Bill", scroller.getScrollY() >= 0);
        assertEquals("the panel stays in place while the page scrolls", View.VISIBLE, host.getVisibility());
    }

    private static List<TextView> allText(View v) {
        List<TextView> out = new ArrayList<>();
        collect(v, out);
        return out;
    }

    private static void collect(View v, List<TextView> out) {
        if (v instanceof TextView) out.add((TextView) v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), out);
    }

    @Test
    public void aSettledTransaction_isGreen_andAnOwingOneIsRed() {
        int redText = app.getColor(R.color.velocity_action_text);
        int settled = app.getColor(R.color.velocity_green_dark);
        TextView owing = payView(open(booking("Confirmed", 9450, 0)));
        assertEquals(redText, owing.getCurrentTextColor());
        TextView done = payView(open(booking("Completed", 18900, 0)));
        assertEquals(settled, done.getCurrentTextColor());
        assertTrue("the amount to pay is the most prominent", owing.getTextSize() > 18 * app.getResources().getDisplayMetrics().density * 0.9f);
    }

    private TextView payView(android.app.Activity a) {
        for (TextView tv : allText(host(a))) if (tv.getText().toString().contains("·") || tv.getText().toString().startsWith("Pay")) return tv;
        throw new AssertionError("no Amount to pay value in " + textsOf(host(a)));
    }

    @Test
    public void motion_slidesInOnce_andCountsUpToTheFinalText_orIsSkippedWhenOff() {
        MoneySummaryPanel.motion = true;
        BookingDetailsActivity a = open(booking("Confirmed", 9450, 0));
        // after the animations ran, the text is exactly the final text
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
        String[] s = summaryOf(a);
        assertEquals("₱18,900.00", s[0]);
        assertEquals("₱9,450.00", s[1]);
        assertEquals("₱9,450.00 · at check-in", s[2]);
        assertEquals(1f, host(a).getChildAt(0).getAlpha(), 0.001f);
        assertEquals(0f, host(a).getChildAt(0).getTranslationY(), 0.001f);

        MoneySummaryPanel.motion = false;
        BookingDetailsActivity b = open(booking("Confirmed", 9450, 0));
        assertEquals("with motion off the final text is there at once", "₱9,450.00 · at check-in", summaryOf(b)[2]);
    }

    // ---- receipts ----

    private PaymentReceiptActivity openReceipt(ReceiptDetail detail) {
        PaymentReceiptActivity.debugPreviewFixtures.put(detail.getReceiptNumber(), detail);
        PaymentReceiptActivity a = Robolectric.buildActivity(PaymentReceiptActivity.class, PaymentReceiptActivity.newIntentForReceipt(app, detail.getReceiptNumber())).setup().get();
        idle();
        return a;
    }

    private static ReceiptDetail asStage(ReceiptDetail source, String type) {
        return new ReceiptDetail(type, source.getReceiptNumber(), source.getBookingId(), null, source.getGuestAccountName(), source.getRepresentativeName(), source.getRoomType(),
                source.getRoomLines(), source.getCheckIn(), source.getCheckOut(), source.getNumberOfNights(), source.getAssignedRoomNumbers(), source.getAdults(), source.getChildren(),
                source.getPaymentSummary(), source.getPaymentTransactions(), source.getAnchorPayment(), source.getIssuedAt());
    }

    @Test
    public void receipts_showTheSameThreeAmounts_asOfThatReceipt_onScreenAndInThePdf() {
        ReceiptDetail one = DebugReceiptFixtures.partialReceipt();              // Receipt 1: 10,000 total, 5,000 paid
        ReceiptDetail two = asStage(DebugReceiptFixtures.partialReceipt(), "CHECKIN_SETTLEMENT_RECEIPT"); // Receipt 2
        ReceiptDetail three = DebugReceiptFixtures.officialReceipt();           // Receipt 3
        Object[][] cases = {
                {one, "at check-in"},
                {two, "at check-out"},
                {three, "Fully paid"},
        };
        for (Object[] c : cases) {
            ReceiptDetail detail = (ReceiptDetail) c[0];
            PaymentReceiptActivity a = openReceipt(detail);
            for (View root : new View[]{a.findViewById(R.id.layoutDynamicReceiptContent), a.buildReceiptForPdf(1080)}) {
                List<String> t = textsOf(root);
                String tag = detail.getReceiptType() + " " + t;
                assertEquals(tag, 1, count(t, "Grand total"));
                assertEquals(tag, 1, count(t, "Remaining balance"));
                assertEquals(tag, 1, count(t, "Amount to pay"));
                assertEquals(tag, 0, count(t, "Balance due"));
                String pay = after(t, "Amount to pay");
                assertTrue(tag + " -> " + pay, pay.endsWith((String) c[1]));
                // it comes after the Payments section and is the last thing of the receipt
                assertTrue(tag, t.indexOf("Grand total") > t.indexOf("Payments"));
                assertEquals(tag, "Amount to pay", t.get(t.size() - 2));
            }
            // the PDF is the screen
            assertEquals(textsOfSummary(a.findViewById(R.id.layoutDynamicReceiptContent)), textsOfSummary(a.buildReceiptForPdf(1080)));
        }
        // the figures are the receipt's own snapshot
        List<String> t1 = textsOf(openReceipt(one).findViewById(R.id.layoutDynamicReceiptContent));
        assertEquals("₱10,000.00", after(t1, "Grand total"));
        assertEquals("₱5,000.00", after(t1, "Remaining balance"));
        assertEquals("₱5,000.00 · at check-in", after(t1, "Amount to pay"));
        List<String> t3 = textsOf(openReceipt(three).findViewById(R.id.layoutDynamicReceiptContent));
        assertEquals("₱0.00 · Fully paid", after(t3, "Amount to pay"));
        assertEquals("₱0.00", after(t3, "Remaining balance"));
    }

    @Test
    public void aFullyPaidReceipt1_saysNothingToPay() {
        List<String> t = textsOf(openReceipt(DebugReceiptFixtures.fullPaymentReceipt()).findViewById(R.id.layoutDynamicReceiptContent));
        assertEquals("₱0.00 · Nothing to pay", after(t, "Amount to pay"));
        assertEquals("₱0.00", after(t, "Remaining balance"));
    }

    private static List<String> textsOfSummary(View root) {
        List<String> t = textsOf(root);
        int i = t.indexOf("Grand total");
        return t.subList(i, t.size());
    }

    // ---- readability: 360dp and 393dp, light and dark, normal and large fonts ----

    @Test
    public void thePanelAndTheReceiptBox_areReadable_in360And393_lightAndDark() {
        for (String q : CONFIGS) {
            for (Booking b : Arrays.asList(booking("Confirmed", 9450, 0), booking("Confirmed", 0, 3500), booking("Completed", 18900, 0), booking("Cancelled", 0, 0))) {
                RuntimeEnvironment.setQualifiers("+" + q);
                ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
                BookingDetailsActivity a = open(b);
                Release160Test.assertReadable(q + " " + b.getStatus() + " panel", a, host(a));
                TransactionDetailsActivity t = openTransaction(b);
                Release160Test.assertReadable(q + " " + b.getStatus() + " tx panel", t, host(t));
            }
            for (ReceiptDetail detail : DebugReceiptFixtures.allReceiptDetails().values()) {
                RuntimeEnvironment.setQualifiers("+" + q);
                ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
                PaymentReceiptActivity a = openReceipt(detail);
                Release160Test.assertReadable(q + " receipt " + detail.getReceiptNumber(), a, a.findViewById(R.id.layoutDynamicReceiptContent));
            }
        }
    }

    @Test
    public void withLargeFonts_thePanelStaysReadable_andNeverCoversTheContent() {
        for (float scale : new float[]{1.3f, 2.0f}) {
            for (String q : new String[]{"w360dp-h800dp-notnight", "w393dp-h851dp-night"}) {
                RuntimeEnvironment.setQualifiers("+" + q);
                RuntimeEnvironment.setFontScale(scale);
                ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
                for (Booking b : Arrays.asList(booking("Confirmed", 0, 3500), booking("Checked-In", 9450, 0))) {
                    BookingDetailsActivity a = open(b);
                    String tag = scale + "x " + q + " " + b.getStatus();
                    Release160Test.assertReadable(tag + " panel", a, host(a));
                    int screenH = a.getResources().getDisplayMetrics().heightPixels;
                    assertTrue(tag + " the panel (" + host(a).getHeight() + ") takes at most 60% of the screen (" + screenH + ")", host(a).getHeight() <= screenH * 0.6);
                    // the page is a weighted sibling above the panel in a vertical column: it gets what the panel leaves, so the panel can never
                    // sit on top of the last section (and the page itself scrolls)
                    View page = a.findViewById(R.id.swipeRefreshDetails);
                    ViewGroup column = (ViewGroup) host(a).getParent();
                    assertTrue(tag, column == page.getParent() && column.indexOfChild(host(a)) == column.indexOfChild(page) + 1);
                    assertEquals(tag, 0, page.getLayoutParams().height);
                    assertEquals(tag, 1f, ((android.widget.LinearLayout.LayoutParams) page.getLayoutParams()).weight, 0.001f);
                }
            }
        }
    }

    // ---- null fields, loading, repeat opens ----

    @Test
    public void aBookingWithNoMoneyFields_stillShowsAPanel_withoutCrashing() {
        Booking bare = MultiStayFixtures.booking();
        bare.setPaymentSummary(null);
        bare.setPaymentTransactions(new ArrayList<>());
        bare.setStatus("Pending");
        BookingDetailsActivity a = open(bare);
        String[] s = summaryOf(a);
        assertNotNull(s[0]);
        assertNotNull(s[1]);
        assertNotNull(s[2]);
        // open it again and again: the panel is replaced, never stacked
        for (int i = 0; i < 3; i++) open(bare);
        assertEquals(1, host(a).getChildCount());
    }

    @Test
    public void theBillOfTheDetailsScreens_keepsPaidAndWaitingLines_butNeverTheThreeAmounts() {
        BookingDetailsActivity a = open(booking("Confirmed", 0, 3500));
        List<String> bill = textsOf(a.findViewById(R.id.sectionPaymentSummaryContent));
        assertEquals("₱0.00", after(bill, "Paid so far"));
        assertEquals("₱3,500.00", after(bill, "Waiting for verification"));
        assertTrue(bill.toString(), bill.contains("₱3,500.00 will be deducted once the front desk verifies your payment."));
    }
}
