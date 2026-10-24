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
import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.dto.BookingDto;
import com.example.velocitysuites.network.dto.ReservationDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 1.59.0: the final receipt groups amenities by date range (and room) with matching subtotals; a converted reservation keeps its date
 * ranges, reads "Converted to Booking #N" and lists its payment as moved; payments never read "N/A" and are labelled by where they were
 * taken, with the proof as a labelled thumbnail; the payment progress and the three receipts are listed near the top and in order.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Release159Test {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        ScreenTestSupport.goOnline(app);
        PaymentReceiptActivity.debugPreviewFixtures.clear();
        PaymentReceiptActivity.debugPreviewAmenities.clear();
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

    private List<String> details(Booking b) {
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, BookingDetailsActivity.newIntent(app, b)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        return textsOf(a.getWindow().getDecorView());
    }

    private static String after(List<String> t, String label) {
        int i = t.indexOf(label);
        return i >= 0 && i + 1 < t.size() ? t.get(i + 1) : null;
    }

    private static Booking.PaymentTransactionRecord payment(long id, String method, String ref, double amount, String date, String verifiedAt) {
        return new Booking.PaymentTransactionRecord(id, method, "deposit", "partial_payment", amount, "completed",
                verifiedAt != null ? "verified" : "pending_verification", null, null, ref, null, null, verifiedAt, null, date, amount, 0, null, null);
    }

    // ---- bug 1: the final receipt, amenities per date range ----

    @Test
    public void theFinalReceipt_listsEachRangesAmenitiesInThatRange_withMatchingSubtotals() {
        Booking source = MultiStayFixtures.booking();
        ReceiptDetail official = DebugReceiptFixtures.officialReceipt();
        official.setStays(source.getStays());
        ReceiptDetail.Highlight h = new ReceiptDetail.Highlight("checkout_final", null, null, null);
        h.finalLines = true;
        h.amounts.put("room_charges", 18600.0);
        h.amounts.put("amenities_total", 300.0);
        h.amounts.put("grand_total", 18900.0);
        h.amounts.put("total_paid", 18900.0);
        h.amounts.put("remaining_balance", 0.0);
        ReceiptDetail.Highlight.AmenityGroup all = new ReceiptDetail.Highlight.AmenityGroup(null);
        all.items.add(new ReceiptDetail.Highlight.AmenityItem("Additional Towel", 2, 50, 100));
        all.items.add(new ReceiptDetail.Highlight.AmenityItem("Additional Blanket", 2, 100, 200));
        h.amenityGroups.add(all);
        official.setHighlight(h);
        PaymentReceiptActivity.debugPreviewFixtures.put(official.getReceiptNumber(), official);
        PaymentReceiptActivity a = Robolectric.buildActivity(PaymentReceiptActivity.class,
                PaymentReceiptActivity.newIntentForReceipt(app, official.getReceiptNumber())).setup().get();
        shadowOf(Looper.getMainLooper()).idle();

        for (List<String> t : Arrays.asList(textsOf(a.findViewById(R.id.layoutDynamicReceiptContent)), textsOf(a.buildReceiptForPdf(1080)))) {
            int firstRange = t.indexOf("Oct 26 → Oct 27, 2026 · 1 night");
            int secondRange = t.indexOf("Oct 27 → Oct 28, 2026 · 1 night");
            assertTrue(t.toString(), firstRange >= 0 && secondRange > firstRange);
            int towel = t.indexOf("Additional Towel × 2");
            assertEquals("each amenity once, not repeated", 1, java.util.Collections.frequency(t, "Additional Towel × 2"));
            assertTrue("the amenities sit in the range they belong to", towel > firstRange && towel < secondRange);
            assertEquals("the first subtotal counts them: 9,300 + 300", "₱9,600.00", t.get(t.indexOf("Subtotal") + 1));
            assertEquals("₱9,300.00", t.get(t.lastIndexOf("Subtotal") + 1));
        }
    }

    // ---- bug 4: both date ranges' check-in and check-out are in the timeline ----

    @Test
    public void bothDateRangesCheckInAndOut_appearInTheTimeline() {
        Booking b = MultiStayFixtures.booking();
        b.setTimeline(Arrays.asList(
                new Booking.TimelineEntry("stay_1_checked_in", "Checked in (Oct 26 → Oct 27)", "Verified", "x", "2026-10-24T00:04:55Z"),
                new Booking.TimelineEntry("stay_2_checked_in", "Checked in (Oct 27 → Oct 28)", "Verified", "x", "2026-10-24T00:06:10Z"),
                new Booking.TimelineEntry("stay_1_checked_out", "Checked out (Oct 26 → Oct 27)", "Verified", "x", "2026-10-24T00:06:18Z"),
                new Booking.TimelineEntry("stay_2_checked_out", "Checked out (Oct 27 → Oct 28)", "Verified", "x", "2026-10-24T00:06:19Z")));
        List<String> t = details(b);
        for (String label : new String[]{"Checked in (Oct 26 → Oct 27)", "Checked in (Oct 27 → Oct 28)", "Checked out (Oct 26 → Oct 27)", "Checked out (Oct 27 → Oct 28)"}) {
            assertEquals(label + " is shown once", 1, java.util.Collections.frequency(t, label));
        }
    }

    // ---- bug 3: a converted reservation ----

    @Test
    public void aConvertedReservation_keepsItsDateRanges_readsConverted_andPointsToTheBooking() {
        ReservationDto dto = MultiStayFixtures.reservationDto("not_requested");
        dto.booking = new BookingDto();
        dto.booking.id = 592;
        Booking converted = ApiMapper.toHistoricalReservation(dto);
        assertEquals("the two real date ranges carry over", 2, converted.getStays().size());

        List<String> t = details(converted);
        assertTrue(t.toString(), t.contains("Oct 26 → Oct 27, 2026 · 1 night") && t.contains("Oct 27 → Oct 28, 2026 · 1 night"));
        assertTrue(t.toString(), t.contains("Converted to Booking #592"));
        assertTrue(t.toString(), t.contains("Converted") && !t.contains("Completed"));
        assertTrue(t.toString(), t.contains("View Booking #592"));
        assertFalse(t.toString(), t.contains("No payments yet.") && after(t, "Paid so far") != null && !"₱0.00".equals(after(t, "Paid so far")));
    }

    @Test
    public void aConvertedReservationsPayment_isListedAsMovedToTheBooking_neverNoPaymentsYet() {
        ReservationDto dto = MultiStayFixtures.reservationDto("not_requested");
        dto.booking = new BookingDto();
        dto.booking.id = 592;
        Booking converted = ApiMapper.toHistoricalReservation(dto);
        converted.setPaymentTransactions(Arrays.asList(payment(665, "gcash", "6564676886467", 3500, "2026-10-24T10:37:34Z", null)));
        List<String> t = details(converted);
        assertTrue(t.toString(), t.contains("Moved to Booking #592"));
        assertFalse(t.toString(), t.contains("No payments yet."));
    }

    // ---- bug 5 and the Payments rows ----

    @Test
    public void payments_areLabelledByWhereTheyWereTaken_neverNA_andShowCashTenderOnlyForCash() {
        Booking b = MultiStayFixtures.booking();
        Booking.PaymentTransactionRecord online = payment(1, "gcash", "6599559944949", 9800, "2026-10-24T00:02:32Z", "2026-10-24T00:03:19Z");
        Booking.PaymentTransactionRecord checkin = payment(2, "cash", "CHECKIN-589-20261024000455", 9800, "not a date", "2026-10-24T00:04:55Z");
        checkin.cashReceived = 10000.0;
        checkin.changeGiven = 200.0;
        b.setPaymentTransactions(Arrays.asList(online, checkin));
        List<String> t = details(b);
        String all = String.join("\n", t);
        assertTrue(all, t.contains("GCash · Paid online") && t.contains("Cash · Paid at check-in"));
        assertFalse("no Deposit label", all.contains("Deposit"));
        assertFalse("never N/A: " + all, all.contains("N/A"));
        assertTrue(all, all.contains("Cash received ₱10,000.00 · Change ₱200.00"));
        assertEquals("cash details only on the cash payment", 1, all.split("Cash received", -1).length - 1);
    }

    @Test
    public void theProof_isALabelledThumbnailInItsPayment_notInTheBill() {
        Booking b = MultiStayFixtures.booking();
        Booking.PaymentTransactionRecord online = payment(1, "gcash", "6599559944949", 9800, "2026-10-24T00:02:32Z", null);
        online.proofUrl = "https://example.test/proof.png";
        b.setPaymentTransactions(Arrays.asList(online));
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, BookingDetailsActivity.newIntent(app, b)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        List<String> payments = textsOf(a.findViewById(R.id.sectionPaymentHistoryContent));
        assertTrue(payments.toString(), payments.contains("Proof of payment"));
        assertFalse("nothing unlabelled in the bill", textsOf(a.findViewById(R.id.sectionPaymentSummaryContent)).contains("Proof of payment"));
        int images = 0;
        ViewGroup bill = a.findViewById(R.id.sectionPaymentSummaryContent);
        for (int i = 0; i < bill.getChildCount(); i++) if (bill.getChildAt(i) instanceof android.widget.ImageView) images++;
        assertEquals(0, images);
    }

    // ---- Part 2: payment progress, receipts list, header ----

    @Test
    public void paymentProgress_showsTheBarAndOnlyTheStepsThatApply() {
        Booking b = MultiStayFixtures.booking();
        b.setStatus("Checked-In");
        b.setPaymentTransactions(Arrays.asList(
                payment(1, "gcash", "6599559944949", 9800, "2026-10-24T00:02:32Z", "2026-10-24T00:03:19Z")));
        b.setPaymentSummary(new Booking.PaymentSummary(19600, 9800, 9800, "PARTIALLY_PAID", 50, false, 0));
        List<String> t = details(b);
        String all = String.join("\n", t);
        assertTrue(all, all.contains("₱9,800.00 of ₱19,600.00 paid · ₱9,800.00 left"));
        assertTrue(all, all.contains("✓ Paid online (GCash)") && all.contains("✓ Verified by the front desk"));
        assertTrue("check-out is the step still to come: " + all, all.contains("○ Paid at check-out") && all.contains("○ Fully paid"));
        assertFalse("check-in step is not shown when nothing was paid there and the guest is already in", all.contains("Paid at check-in"));
    }

    @Test
    public void receipts_areListedInOrder_withGreyedStepsForTheOnesToCome() {
        Booking b = MultiStayFixtures.booking();
        b.setReceipts(Arrays.asList(new Booking.ReceiptSummary("PR-20261024-000657", "PARTIAL_RECEIPT", "VERIFIED", 9800, 50, "2026-10-24T00:03:19Z")));
        List<String> t = details(b);
        int r1 = t.indexOf("Receipt 1 · Payment verified");
        int r2 = t.indexOf("Receipt 2 · Check-in");
        int r3 = t.indexOf("Receipt 3 · Check-out (final)");
        assertTrue(t.toString(), r1 > 0 && r2 > r1 && r3 > r2);
        assertTrue(t.toString(), t.contains("Issued") && t.contains("Issued at check-in") && t.contains("Issued at check-out"));
        assertTrue(t.toString(), t.get(r1 + 2).startsWith("PR-20261024-000657"));
    }

    @Test
    public void theHeaderHasOnePlainStatusLine_andNoSeparateTypeLine() {
        Booking b = MultiStayFixtures.booking();
        List<String> t = details(b);
        int id = t.indexOf("Booking #585");
        assertTrue(t.toString(), id >= 0);
        assertFalse("the ID already says it is a booking", t.subList(id + 1, Math.min(t.size(), id + 4)).contains("Booking"));
        assertTrue(t.toString(), t.contains("Waiting for front desk verification"));
    }

    @Test
    public void transactionHistoryDetails_useTheSameParts_andNoSingleReceiptBlock() {
        Booking b = MultiStayFixtures.booking();
        b.setPaymentSummary(new Booking.PaymentSummary(18900, 0, 18900, "PENDING", null, false, 0));
        PaymentTransaction tx = new PaymentTransaction(b, new Booking.PaymentRecord("1000.00", "GCASH", "REF1", "2026-10-24", "completed", null));
        TransactionDetailsActivity a = Robolectric.buildActivity(TransactionDetailsActivity.class, TransactionDetailsActivity.newIntent(app, tx)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        List<String> t = textsOf(a.getWindow().getDecorView());
        assertTrue(t.toString(), t.contains("Receipt 1 · Payment verified") && t.contains("Receipt 3 · Check-out (final)"));
        assertFalse(t.toString(), t.contains("View Receipt") || t.contains("Download Receipt") || t.contains("Payment Verified"));
        assertFalse(t.toString(), String.join("\n", t).contains("N/A"));
    }

    // ---- Part 3: the receipts' header ----

    @Test
    public void theReceiptHeader_saysItOnce_andRenamesTheRows() {
        ReceiptDetail receipt = DebugReceiptFixtures.partialReceipt();
        PaymentReceiptActivity.debugPreviewFixtures.put(receipt.getReceiptNumber(), receipt);
        PaymentReceiptActivity a = Robolectric.buildActivity(PaymentReceiptActivity.class,
                PaymentReceiptActivity.newIntentForReceipt(app, receipt.getReceiptNumber())).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        List<String> t = textsOf(a.findViewById(R.id.layoutDynamicReceiptContent));
        assertEquals(1, java.util.Collections.frequency(t, "Receipt 1 of 3 · Payment verified"));
        assertFalse(t.toString(), t.contains("Payment verified"));
        assertTrue(t.toString(), t.contains("Receipt number") && t.contains("Date issued") && t.contains("Transaction ID"));
        assertFalse(t.toString(), t.contains("Payment Reference") || t.contains("Receipt Date"));
        assertTrue("the balance at the hotel is said plainly: " + t, t.stream().anyMatch(x -> x.startsWith("Balance due at the hotel: ₱")));
    }

    @Test
    public void theFinalReceipt_carriesTheEarlierReceiptsHistoryAndTheDiscountId() {
        ReceiptDetail official = DebugReceiptFixtures.officialReceipt();
        official.setReceiptRefs(Arrays.asList(new ReceiptDetail.ReceiptRef(1, "PR-20261024-000657", "2026-10-24T00:03:19Z", 9800),
                new ReceiptDetail.ReceiptRef(2, "SR-20261024-000147", "2026-10-24T00:04:55Z", 9800),
                new ReceiptDetail.ReceiptRef(3, official.getReceiptNumber(), "2026-10-24T00:06:28Z", 19600)));
        official.setDiscountInfo("PWD", "approved");
        official.setHistory(Arrays.asList(
                new Booking.TimelineEntry("created", "Booking created", "Recorded", "x", "2026-10-24T00:02:32Z"),
                new Booking.TimelineEntry("stay_1_checked_in", "Checked in (Oct 26 → Oct 27)", "Verified", "x", "2026-10-24T00:04:55Z"),
                new Booking.TimelineEntry("stay_2_checked_in", "Checked in (Oct 27 → Oct 28)", "Verified", "x", "2026-10-24T00:06:10Z")));
        PaymentReceiptActivity.debugPreviewFixtures.put(official.getReceiptNumber(), official);
        PaymentReceiptActivity a = Robolectric.buildActivity(PaymentReceiptActivity.class,
                PaymentReceiptActivity.newIntentForReceipt(app, official.getReceiptNumber())).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        List<String> t = textsOf(a.findViewById(R.id.layoutDynamicReceiptContent));
        assertEquals("PR-20261024-000657", after(t, "Receipt 1"));
        assertEquals("SR-20261024-000147", after(t, "Receipt 2"));
        assertTrue(t.toString(), t.contains("Transaction History") && t.contains("Checked in (Oct 27 → Oct 28)"));
        assertEquals("PWD · applied", after(t, "ID / discount"));
        assertTrue(t.toString(), t.contains("No refunds for early check-out (hotel policy)."));
        assertEquals("₱0.00 · Fully paid", after(t, "Balance due"));
    }

    // ---- Part 4: Step 3 labels ----

    @Test
    public void step3Labels_sayAccountOwner_andTheRepresentativePrompt() {
        assertEquals("Account owner", app.getString(R.string.who_owner_me));
        assertEquals("Choose the representative who will check in at the hotel", app.getString(R.string.who_choose_error));
        assertEquals("Choose the representative who will check in at the hotel.", app.getString(R.string.who_is_staying_hint));
    }
}
