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
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.PaymentReceiptActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.ReceiptDetail;
import com.example.velocitysuites.TransactionStatusHelper;
import com.example.velocitysuites.debug.DebugReceiptFixtures;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The real Payment Receipt screen, rendered from the project's own receipt fixtures (no network): it must open
 * without crashing from every receipt shape, show the status badge the shared rules give (Partial -> Partially
 * Paid, Full/Official -> Paid), show every required field - and "N/A" (never a blank, "null" or a crash) for any
 * field the payload lacks.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReceiptScreenTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        PaymentReceiptActivity.debugPreviewFixtures.clear();
        PaymentReceiptActivity.debugPreviewAmenities.clear();
    }

    @After
    public void tearDown() {
        PaymentReceiptActivity.debugPreviewFixtures.clear();
        PaymentReceiptActivity.debugPreviewAmenities.clear();
    }

    // ---- helpers ----

    private PaymentReceiptActivity open(ReceiptDetail detail) {
        PaymentReceiptActivity.debugPreviewFixtures.put(detail.getReceiptNumber(), detail);
        Intent intent = PaymentReceiptActivity.newIntentForReceipt(app, detail.getReceiptNumber());
        ActivityController<PaymentReceiptActivity> controller = Robolectric.buildActivity(PaymentReceiptActivity.class, intent);
        controller.setup();
        shadowOf(Looper.getMainLooper()).idle();
        return controller.get();
    }

    private static void collect(View view, List<String> out) {
        if (view instanceof TextView && view.getVisibility() == View.VISIBLE) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.length() > 0) out.add(text.toString());
        }
        if (view instanceof ViewGroup && view.getVisibility() == View.VISIBLE) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), out);
        }
    }

    private static List<String> texts(PaymentReceiptActivity activity) {
        List<String> out = new ArrayList<>();
        collect(activity.findViewById(R.id.layoutDynamicReceiptContent), out);
        return out;
    }

    /** The text of the value shown next to {@code label} in a label/value row (labels and values are separate TextViews in the same row). */
    private static String valueOf(PaymentReceiptActivity activity, String label) {
        View content = activity.findViewById(R.id.layoutDynamicReceiptContent);
        String[] found = {null};
        findRowValue(content, label, found);
        return found[0];
    }

    private static void findRowValue(View view, String label, String[] found) {
        if (found[0] != null) return;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof TextView && label.contentEquals(((TextView) child).getText()) && i + 1 < group.getChildCount()
                        && group.getChildAt(i + 1) instanceof TextView) {
                    found[0] = ((TextView) group.getChildAt(i + 1)).getText().toString();
                    return;
                }
                findRowValue(child, label, found);
            }
        }
    }

    private String statusLabelOf(ReceiptDetail detail) {
        return app.getString(TransactionStatusHelper.styleFor(TransactionStatusHelper.summarize(detail).status).labelRes);
    }

    // ---- opens, every shape ----

    @Test
    public void everyReceiptShape_opensWithoutCrashing_andShowsTheContent() {
        Map<String, ReceiptDetail> all = DebugReceiptFixtures.allReceiptDetails();
        assertTrue(all.size() >= 6);
        for (Map.Entry<String, ReceiptDetail> entry : all.entrySet()) {
            PaymentReceiptActivity.debugPreviewAmenities.putAll(DebugReceiptFixtures.allReceiptAmenities());
            PaymentReceiptActivity activity = open(entry.getValue());
            assertEquals(entry.getKey(), View.VISIBLE, activity.findViewById(R.id.receiptScrollView).getVisibility());
            assertEquals(entry.getKey(), View.GONE, activity.findViewById(R.id.layoutReceiptError).getVisibility());
            assertEquals(entry.getKey(), View.GONE, activity.findViewById(R.id.layoutReceiptLoading).getVisibility());
            List<String> texts = texts(activity);
            assertFalse(entry.getKey() + " must not print null: " + texts, texts.toString().contains("null"));
            assertFalse(entry.getKey() + " must not print N/A: " + texts, texts.contains("N/A"));
        }
    }

    /** The text right after {@code label} in the receipt (its amount or value): rows are a label then a value. */
    private static String after(List<String> texts, String label) {
        int i = texts.indexOf(label);
        return i >= 0 && i + 1 < texts.size() ? texts.get(i + 1) : null;
    }

    // ---- the status badge ----

    @Test
    public void aPartialReceiptReadsPartiallyPaid_nextToItsRemainingBalance() {
        List<String> texts = texts(open(DebugReceiptFixtures.partialReceipt()));
        assertTrue(texts.toString(), texts.contains("Partially Paid"));
        assertEquals("₱5,000.00", after(texts, "Paid so far"));
        assertEquals("₱5,000.00", after(texts, "Balance due"));
        assertTrue("the plain line under the bill", texts.contains("You pay the balance at the hotel."));
    }

    @Test
    public void aFullPaymentReceiptAndAnOfficialReceiptReadPaid() {
        assertTrue(texts(open(DebugReceiptFixtures.fullPaymentReceipt())).contains("Paid"));
        assertTrue(texts(open(DebugReceiptFixtures.officialReceipt())).contains("Paid"));
    }

    @Test
    public void theBadgeMatchesTheSharedRuleForEveryFixture() {
        for (ReceiptDetail detail : DebugReceiptFixtures.allReceiptDetails().values()) {
            PaymentReceiptActivity activity = open(detail);
            assertTrue(detail.getReceiptNumber() + " badge \"" + statusLabelOf(detail) + "\"",
                    texts(activity).contains(statusLabelOf(detail)));
        }
    }

    // ---- the one structure: header, Your Stay, Guests, Bill, Payments ----

    @Test
    public void showsEveryRequiredField_inTheSharedStructure() {
        List<String> texts = texts(open(DebugReceiptFixtures.partialReceipt()));
        assertEquals("PR-20260920-000501", after(texts, "Payment Reference"));
        assertEquals("Booking #250", after(texts, "Transaction ID"));
        int stay = texts.indexOf("Your Stay");
        int guests = texts.indexOf("Guests");
        int bill = texts.indexOf("Bill");
        int payments = texts.indexOf("Payments");
        assertTrue(texts.toString(), stay > 0 && guests > stay && bill > guests && payments > bill);
        assertTrue("the dates head the range", texts.get(stay + 1).startsWith("Sep 25 → Sep 27, 2026") && texts.get(stay + 1).endsWith("2 nights"));
        assertEquals("Deluxe Room × 2 · ₱2,000.00 × 2 nights", texts.get(stay + 2));
        assertEquals("₱8,000.00", texts.get(stay + 3));
        assertEquals("Representative", texts.get(guests + 1));
        assertEquals("Juan Dela Cruz", texts.get(guests + 2));
        assertEquals("₱10,000.00", after(texts, "Grand total"));
        assertEquals("each value once: the grand total is not repeated", 1, java.util.Collections.frequency(texts, "Grand total"));
        assertEquals(1, java.util.Collections.frequency(texts, "Balance due"));
    }

    @Test
    public void showsThePaymentMethod_date_amountReferenceAndStatus_once() {
        List<String> texts = texts(open(DebugReceiptFixtures.partialReceipt()));
        int payments = texts.indexOf("Payments");
        assertEquals("GCash · Deposit", texts.get(payments + 1));
        assertEquals("₱5,000.00", texts.get(payments + 2));
        String line = texts.get(payments + 3);
        assertTrue(line, line.contains("Sep 20, 2026") && line.contains("Ref. 4136 202 609205") && line.endsWith("Verified"));
    }

    @Test
    public void anOfficialReceiptEndsWithAZeroBalance_theNoRefundNote_andEveryPayment() {
        List<String> texts = texts(open(DebugReceiptFixtures.officialReceipt()));
        assertEquals("₱0.00 · Fully paid", after(texts, "Balance due"));
        assertTrue(texts.toString(), texts.contains("Cash · Balance") && texts.contains("GCash · Deposit"));
        assertTrue("the no-refund note", texts.contains("No refunds for early check-out (hotel policy)."));
        assertTrue(texts.contains("Check-out (final)"));
    }

    @Test
    public void feesAndDiscounts_areShown_onlyWhenThereAreSome() {
        PaymentReceiptActivity.debugPreviewAmenities.putAll(DebugReceiptFixtures.allReceiptAmenities());
        List<String> withDiscount = texts(open(DebugReceiptFixtures.officialReceiptWithDiscountAndLongNames()));
        assertTrue(withDiscount.toString(), withDiscount.contains("Discount") && "−₱5,380.00".equals(after(withDiscount, "Discount")));

        List<String> none = texts(open(DebugReceiptFixtures.fullPaymentReceipt()));
        assertFalse(none.toString(), none.contains("Discount") || none.contains("Other charges (front desk)"));
        assertTrue("no amenities says so", none.contains("No amenities added"));
    }

    // ---- missing data ----

    @Test
    public void aReceiptMissingMostFields_neverShowsNullOrCrashes() {
        ReceiptDetail sparse = new ReceiptDetail("FULL_PAYMENT_RECEIPT", "FR-SPARSE-1", null, null,
                null, null, null, null, null, null, 0, null, 0, 0, null, null, null, null);
        PaymentReceiptActivity activity = open(sparse);

        assertEquals(View.VISIBLE, activity.findViewById(R.id.receiptScrollView).getVisibility());
        List<String> texts = texts(activity);
        assertEquals("FR-SPARSE-1", after(texts, "Payment Reference"));
        assertFalse(texts.toString(), texts.toString().contains("null"));
    }

    @Test
    public void aReceiptWhoseTransactionsHaveNoMethodOrDate_stillListsThePaymentOnce() {
        Booking.PaymentSummary summary = new Booking.PaymentSummary(1800, 900, 900, "PARTIALLY_PAID", 50, false, 0);
        Booking.PaymentTransactionRecord bare = new Booking.PaymentTransactionRecord(1, null, null, null, 900, "completed", null,
                null, null, null, null, null, null, null, null, 900, 900, "PARTIAL_RECEIPT", "PR-BARE-1");
        ReceiptDetail.AnchorPayment anchor = new ReceiptDetail.AnchorPayment(900, null, 50, null, null, null, null);
        ReceiptDetail detail = new ReceiptDetail("PARTIAL_RECEIPT", "PR-BARE-1", "5", null, "Guest", null, "Deluxe", null,
                "Oct 01, 2026", "Oct 02, 2026", 1, null, 2, 0, summary, Collections.singletonList(bare), anchor, "2026-09-30T14:09:00Z");
        List<String> texts = texts(open(detail));

        assertFalse(texts.toString(), texts.toString().contains("null"));
        assertEquals("₱900.00", after(texts, "Cash · Payment"));
        assertTrue(texts.toString(), texts.contains("Partially Paid"));
    }

    // ---- download ----

    @Test
    public void downloading_neverCrashes_andAlwaysSaysHowItWent() {
        PaymentReceiptActivity activity = open(DebugReceiptFixtures.partialReceipt());
        View download = activity.findViewById(R.id.btnReceiptDownload);
        assertEquals(View.VISIBLE, download.getVisibility());
        download.performClick();
        shadowOf(Looper.getMainLooper()).idle();
        String toast = ShadowToast.getTextOfLatestToast();
        assertNotNull("the guest is told the outcome", toast);
        assertTrue(toast, toast.equals(app.getString(R.string.receipt_download_success))
                || toast.equals(app.getString(R.string.receipt_download_failed)));
    }
}
