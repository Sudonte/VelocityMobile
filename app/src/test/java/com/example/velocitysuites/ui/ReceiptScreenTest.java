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
 * Paid, Full/Official -> Paid), show every required field - and "—" (never a blank, "null" or a crash) for any
 * field the payload lacks.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReceiptScreenTest {

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

    // ---- the status badge ----

    @Test
    public void aPartialReceiptReadsPartiallyPaid_nextToItsRemainingBalance() {
        PaymentReceiptActivity activity = open(DebugReceiptFixtures.partialReceipt());
        List<String> texts = texts(activity);
        assertTrue(texts.toString(), texts.contains("Partially Paid"));
        assertEquals("₱5,000.00", valueOf(activity, "Remaining Balance At This Point"));
        assertEquals("Partially Paid", valueOf(activity, "Payment Status"));
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

    // ---- required fields ----

    @Test
    public void showsEveryRequiredField() {
        PaymentReceiptActivity activity = open(DebugReceiptFixtures.partialReceipt());
        assertEquals("PR-20260920-000501", valueOf(activity, "Payment Reference"));
        assertEquals("250", valueOf(activity, "Booking ID"));
        assertEquals("Juan Dela Cruz", valueOf(activity, "Guest Account Name"));
        assertTrue(valueOf(activity, "Room Type").contains("Deluxe Room"));
        assertTrue(valueOf(activity, "Room Rate").contains("/ night"));
        assertEquals("Sep 25, 2026", valueOf(activity, "Check-in"));
        assertEquals("Sep 27, 2026", valueOf(activity, "Check-out"));
        assertEquals("2", valueOf(activity, "Number of Nights"));
        assertEquals("₱5,000.00", valueOf(activity, "Amount Paid This Transaction"));
        assertEquals("₱5,000.00", valueOf(activity, "Total Amount Paid At This Point"));
        List<String> texts = texts(activity);
        assertTrue(texts.toString(), texts.contains("Grand Total"));
        assertTrue(texts.toString(), texts.contains("₱10,000.00") || texts.stream().anyMatch(t -> t.contains("10,000.00")));
    }

    @Test
    public void showsThePaymentMethod_date_andTime() {
        PaymentReceiptActivity activity = open(DebugReceiptFixtures.partialReceipt());
        assertEquals("GCash", valueOf(activity, "Payment Method"));
        assertEquals("Sep 20, 2026", valueOf(activity, "Payment Date"));
        assertEquals("2:15 PM", valueOf(activity, "Payment Time"));
    }

    @Test
    public void anOfficialReceiptShowsTheFinalPaymentsMethodAndDate() {
        PaymentReceiptActivity activity = open(DebugReceiptFixtures.officialReceipt());
        // The last payment of the official receipt fixture was cash at checkout.
        assertEquals("Cash", valueOf(activity, "Payment Method"));
        assertEquals("Sep 23, 2026", valueOf(activity, "Payment Date"));
        assertEquals("₱0.00", valueOf(activity, "Remaining Balance"));
    }

    @Test
    public void feesAndDiscounts_areShown_orExplicitlyNone() {
        PaymentReceiptActivity.debugPreviewAmenities.putAll(DebugReceiptFixtures.allReceiptAmenities());
        List<String> withDiscount = texts(open(DebugReceiptFixtures.officialReceiptWithDiscountAndLongNames()));
        assertTrue(withDiscount.toString(), withDiscount.contains("Discount"));

        List<String> none = texts(open(DebugReceiptFixtures.fullPaymentReceipt()));
        assertTrue(none.toString(), none.contains("No additional fees or discounts"));
    }

    // ---- missing data ----

    @Test
    public void aReceiptMissingMostFields_showsDashes_neverNullOrACrash() {
        ReceiptDetail sparse = new ReceiptDetail("FULL_PAYMENT_RECEIPT", "FR-SPARSE-1", null, null,
                null, null, null, null, null, null, 0, null, 0, 0, null, null, null, null);
        PaymentReceiptActivity activity = open(sparse);

        assertEquals(View.VISIBLE, activity.findViewById(R.id.receiptScrollView).getVisibility());
        assertEquals("FR-SPARSE-1", valueOf(activity, "Payment Reference"));
        assertEquals("—", valueOf(activity, "Booking ID"));
        assertEquals("—", valueOf(activity, "Guest Account Name"));
        assertEquals("—", valueOf(activity, "Room Type"));
        assertEquals("—", valueOf(activity, "Room Rate"));
        assertEquals("—", valueOf(activity, "Check-in"));
        assertEquals("—", valueOf(activity, "Check-out"));
        assertEquals("—", valueOf(activity, "Number of Nights"));
        assertEquals("—", valueOf(activity, "Receipt Date"));
        assertEquals("—", valueOf(activity, "Grand Total"));
        assertEquals("—", valueOf(activity, "Amount Paid"));
        assertEquals("—", valueOf(activity, "Remaining Balance"));
        assertFalse(texts(activity).toString().contains("null"));
    }

    @Test
    public void aReceiptWhoseTransactionsHaveNoMethodOrDate_showsDashesForThose() {
        Booking.PaymentSummary summary = new Booking.PaymentSummary(1800, 900, 900, "PARTIALLY_PAID", 50, false, 0);
        Booking.PaymentTransactionRecord bare = new Booking.PaymentTransactionRecord(1, null, null, null, 900, "completed", null,
                null, null, null, null, null, null, null, null, 900, 900, "PARTIAL_RECEIPT", "PR-BARE-1");
        ReceiptDetail.AnchorPayment anchor = new ReceiptDetail.AnchorPayment(900, null, 50, null, null, null, null);
        ReceiptDetail detail = new ReceiptDetail("PARTIAL_RECEIPT", "PR-BARE-1", "5", null, "Guest", null, "Deluxe", null,
                "Oct 01, 2026", "Oct 02, 2026", 1, null, 2, 0, summary, Collections.singletonList(bare), anchor, "2026-09-30T14:09:00Z");
        PaymentReceiptActivity activity = open(detail);

        assertEquals("—", valueOf(activity, "Payment Method"));
        assertEquals("—", valueOf(activity, "Payment Date"));
        assertEquals("—", valueOf(activity, "Payment Time"));
        assertEquals("Partially Paid", valueOf(activity, "Payment Status"));
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
