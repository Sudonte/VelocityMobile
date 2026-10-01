package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.velocitysuites.TransactionStatusHelper.Status;
import com.example.velocitysuites.TransactionStatusHelper.Summary;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The status rules, end to end: PENDING by default, PAID only when the RECEPTIONIST-VERIFIED amount covers
 * the grand total, PARTIALLY_PAID when some but not all of it is verified, plus CANCELLED / REJECTED. The
 * cases mirror the flows the screens have to get right: a just-submitted payment, a full verification, a
 * partial verification, a second payment still awaiting review.
 */
public class TransactionStatusHelperTest {

    private static final double TOTAL = 1800.00;

    private static Booking booking(String status, double total) {
        return new Booking("588", "1", "Deluxe", "Deluxe", "Oct 01, 2026", "Oct 02, 2026", 2, total, status, "");
    }

    private static Booking.PaymentRecord payment(String amount, String status) {
        return new Booking.PaymentRecord(amount, "GCASH", "1234567890123", "Sep 30, 2026 • 10:09 PM", status);
    }

    private static Booking withPayments(String txStatus, double total, Booking.PaymentRecord... rows) {
        Booking b = booking(txStatus, total);
        b.setPaymentHistory(new ArrayList<>(Arrays.asList(rows)));
        // What the mappers set: any pending row flips the booking-level flag.
        for (Booking.PaymentRecord row : rows) {
            if ("pending".equalsIgnoreCase(row.status)) b.setPaymentPendingVerification(true);
        }
        return b;
    }

    // ---- PENDING is the default ----

    @Test
    public void newTransactionWithNoPayment_isPending() {
        Summary s = TransactionStatusHelper.summarize(booking("Pending", TOTAL));
        assertEquals(Status.PENDING, s.status);
        assertEquals(0, s.verifiedPaid, 0.0001);
        assertEquals(TOTAL, s.balance, 0.0001);
        assertFalse(s.hasPendingPayment);
    }

    @Test
    public void submittedButUnverifiedPayment_isPending_notPaid() {
        // The bug in the screenshot: a P1,800.00 payment was submitted and the card said "Not yet paid"/P0.00.
        Booking b = withPayments("Pending", TOTAL, payment("1800.00", "pending"));
        Summary s = TransactionStatusHelper.summarize(b);
        assertEquals(Status.PENDING, s.status);
        assertEquals(0, s.verifiedPaid, 0.0001);
        assertEquals(1800.00, s.pendingSubmitted, 0.0001);
        assertTrue(s.hasPendingPayment);
        assertEquals(TOTAL, s.balance, 0.0001);
    }

    @Test
    public void directBookingWhoseAmountPaidCountsSubmittedPayments_stillPendingUntilVerified() {
        // ApiMapper.toBooking(DirectBookingResponseDto) fills amountPaid with pending+completed ("submitted").
        // The status must not trust that field when itemized rows say nothing is verified yet.
        Booking b = withPayments("Confirmed", TOTAL, payment("1800.00", "pending"));
        b.setDirectBooking(true);
        b.setAmountPaid(1800.00);
        Summary s = TransactionStatusHelper.summarize(b);
        assertEquals(Status.PENDING, s.status);
        assertEquals(0, s.verifiedPaid, 0.0001);
    }

    // ---- PAID / PARTIALLY_PAID come only from verified money ----

    @Test
    public void receptionistVerifiesFullAmount_isPaid() {
        Booking b = withPayments("Confirmed", TOTAL, payment("1800.00", "completed"));
        Summary s = TransactionStatusHelper.summarize(b);
        assertEquals(Status.PAID, s.status);
        assertEquals(1800.00, s.verifiedPaid, 0.0001);
        assertEquals(0, s.balance, 0.0001);
    }

    @Test
    public void receptionistVerifiesPartialAmount_isPartiallyPaid_withCorrectBalance() {
        Booking b = withPayments("Confirmed", TOTAL, payment("900.00", "completed"));
        Summary s = TransactionStatusHelper.summarize(b);
        assertEquals(Status.PARTIALLY_PAID, s.status);
        assertEquals(900.00, s.verifiedPaid, 0.0001);
        assertEquals(900.00, s.balance, 0.0001);
    }

    @Test
    public void twoVerifiedPaymentsThatSumToTheTotal_isPaid() {
        Booking b = withPayments("Confirmed", TOTAL, payment("900.00", "completed"), payment("900.00", "completed"));
        assertEquals(Status.PAID, TransactionStatusHelper.statusOf(b));
    }

    @Test
    public void verifiedPartialPlusSecondPaymentStillAwaitingReview_isPartiallyPaid_andFlagsThePendingOne() {
        Booking b = withPayments("Confirmed", TOTAL, payment("900.00", "completed"), payment("900.00", "pending"));
        Summary s = TransactionStatusHelper.summarize(b);
        assertEquals(Status.PARTIALLY_PAID, s.status);
        assertEquals(900.00, s.verifiedPaid, 0.0001);
        assertEquals(900.00, s.pendingSubmitted, 0.0001);
        assertEquals(900.00, s.balance, 0.0001);
        assertTrue(s.hasPendingPayment);
    }

    @Test
    public void pendingAmountNeverCountsTowardPaid_evenWhenItWouldCoverTheTotal() {
        Booking b = withPayments("Confirmed", TOTAL, payment("900.00", "completed"), payment("900.00", "pending"));
        // 900 verified + 900 submitted would "cover" 1800 - but only 900 is verified.
        assertFalse(TransactionStatusHelper.statusOf(b) == Status.PAID);
    }

    @Test
    public void overpayment_isPaid_andBalanceNeverNegative() {
        Booking b = withPayments("Confirmed", TOTAL, payment("2000.00", "completed"));
        Summary s = TransactionStatusHelper.summarize(b);
        assertEquals(Status.PAID, s.status);
        assertEquals(0, s.balance, 0.0001);
    }

    @Test
    public void halfACentavoShortOfTheTotal_isStillPaid() {
        Booking b = withPayments("Confirmed", TOTAL, payment("1799.996", "completed"));
        assertEquals(Status.PAID, TransactionStatusHelper.statusOf(b));
    }

    @Test
    public void aFullCentavoShort_isPartiallyPaid() {
        Booking b = withPayments("Confirmed", TOTAL, payment("1799.99", "completed"));
        assertEquals(Status.PARTIALLY_PAID, TransactionStatusHelper.statusOf(b));
    }

    @Test
    public void rejectedAndFailedPaymentsNeverCount() {
        Booking b = withPayments("Pending", TOTAL, payment("1800.00", "rejected"), payment("1800.00", "failed"));
        assertEquals(0, TransactionStatusHelper.verifiedPaidOf(b), 0.0001);
    }

    // ---- CANCELLED / REJECTED ----

    @Test
    public void cancelledTransaction_isCancelled_evenWithVerifiedPayments() {
        Booking b = withPayments("Cancelled", TOTAL, payment("900.00", "completed"));
        Summary s = TransactionStatusHelper.summarize(b);
        assertEquals(Status.CANCELLED, s.status);
        assertEquals(900.00, s.verifiedPaid, 0.0001); // what was paid is still reported honestly
    }

    @Test
    public void rejectedTransaction_isRejected() {
        assertEquals(Status.REJECTED, TransactionStatusHelper.statusOf(booking("Rejected", TOTAL)));
    }

    @Test
    public void americanSpellingOfCancelled_isCancelled() {
        assertEquals(Status.CANCELLED, TransactionStatusHelper.resolve("canceled", false, TOTAL, 0));
    }

    @Test
    public void onlyPaymentAttemptRejected_isRejected_soTheGuestKnowsToPayAgain() {
        Booking b = withPayments("Pending", TOTAL, payment("1800.00", "rejected"));
        b.setPaymentVerificationStatus("rejected");
        assertEquals(Status.REJECTED, TransactionStatusHelper.statusOf(b));
    }

    @Test
    public void rejectedAttemptFollowedByANewSubmission_isPendingAgain() {
        Booking b = withPayments("Pending", TOTAL, payment("1800.00", "rejected"), payment("1800.00", "pending"));
        // The latest payment is the new, pending one - so verification_status is no longer "rejected".
        b.setPaymentVerificationStatus("pending_verification");
        assertEquals(Status.PENDING, TransactionStatusHelper.statusOf(b));
    }

    @Test
    public void laterRejectedAttemptDoesNotUndoEarlierVerifiedMoney() {
        Booking b = withPayments("Confirmed", TOTAL, payment("900.00", "completed"), payment("900.00", "rejected"));
        b.setPaymentVerificationStatus("rejected");
        assertEquals(Status.PARTIALLY_PAID, TransactionStatusHelper.statusOf(b));
    }

    // ---- Where the numbers come from ----

    @Test
    public void authoritativePaymentSummary_isUsedAsIs() {
        Booking b = booking("Confirmed", 0); // a stale list total must lose to the backend's own
        b.setPaymentSummary(new Booking.PaymentSummary(TOTAL, 900.00, 900.00, "PARTIALLY_PAID", 50, false, 0));
        Summary s = TransactionStatusHelper.summarize(b);
        assertEquals(TOTAL, s.grandTotal, 0.0001);
        assertEquals(900.00, s.verifiedPaid, 0.0001);
        assertEquals(Status.PARTIALLY_PAID, s.status);
    }

    @Test
    public void authoritativeSummaryWithFullPayment_isPaid_withoutAnyItemizedRows() {
        Booking b = booking("Confirmed", TOTAL);
        b.setPaymentSummary(new Booking.PaymentSummary(TOTAL, TOTAL, 0, "PAID", 100, false, 0));
        assertEquals(Status.PAID, TransactionStatusHelper.statusOf(b));
    }

    @Test
    public void noItemizedRows_fallsBackToTheTransactionsOwnAmountPaid() {
        Booking b = booking("Checked-Out", TOTAL);
        b.setAmountPaid(TOTAL);
        assertEquals(Status.PAID, TransactionStatusHelper.statusOf(b));
    }

    @Test
    public void billingMarkedPaidWithNoVerifiedRow_isPaid() {
        // A desk settlement: the backend's billing row says paid although no online payment row was verified.
        Booking b = withPayments("Confirmed", TOTAL, payment("1800.00", "pending"));
        b.setBillingStatus("paid");
        Summary s = TransactionStatusHelper.summarize(b);
        assertEquals(Status.PAID, s.status);
    }

    @Test
    public void unknownPendingAmount_fallsBackToTheRequiredPaymentAmount() {
        Booking b = booking("Pending", TOTAL);
        b.setPaymentPendingVerification(true);
        b.setRequiredPaymentAmount(900.00);
        Summary s = TransactionStatusHelper.summarize(b);
        assertTrue(s.hasPendingPayment);
        assertEquals(900.00, s.pendingSubmitted, 0.0001);
    }

    // ---- Bad data must never throw ----

    @Test
    public void paymentRowsWithNullOrGarbageFields_areIgnored_notFatal() {
        Booking.PaymentRecord nullAmount = new Booking.PaymentRecord(null, null, null, null, "completed");
        Booking.PaymentRecord garbage = new Booking.PaymentRecord("N/A", "CASH", null, null, "completed");
        Booking.PaymentRecord nullStatus = new Booking.PaymentRecord("500.00", "CASH", null, null, null);
        Booking b = booking("Pending", TOTAL);
        b.setPaymentHistory(new ArrayList<>(Arrays.asList(nullAmount, garbage, nullStatus, null)));
        Summary s = TransactionStatusHelper.summarize(b);
        assertEquals(Status.PENDING, s.status);
        assertEquals(0, s.verifiedPaid, 0.0001);
    }

    @Test
    public void emptyPaymentHistory_isFine() {
        Booking b = booking("Pending", TOTAL);
        b.setPaymentHistory(Collections.<Booking.PaymentRecord>emptyList());
        assertEquals(Status.PENDING, TransactionStatusHelper.statusOf(b));
    }

    @Test
    public void zeroGrandTotalWithAVerifiedPayment_doesNotCrash_andIsPaid() {
        Booking b = withPayments("Confirmed", 0, payment("500.00", "completed"));
        assertEquals(Status.PAID, TransactionStatusHelper.statusOf(b));
    }

    // ---- Receipts use the same rule on their own (frozen) figures ----

    private static ReceiptDetail receipt(String type, Booking.PaymentSummary summary) {
        return new ReceiptDetail(type, "PR-1", "588", null, "Guest", "Rep", "Deluxe", null,
                "Oct 01, 2026", "Oct 02, 2026", 1, null, 2, 0, summary, null, null, "Sep 30, 2026");
    }

    @Test
    public void partialReceipt_readsPartiallyPaid() {
        Summary s = TransactionStatusHelper.summarize(receipt("PARTIAL_RECEIPT",
                new Booking.PaymentSummary(TOTAL, 900.00, 900.00, "PARTIALLY_PAID", 50, false, 0)));
        assertEquals(Status.PARTIALLY_PAID, s.status);
        assertEquals(900.00, s.balance, 0.0001);
    }

    @Test
    public void fullPaymentReceipt_readsPaid() {
        Summary s = TransactionStatusHelper.summarize(receipt("FULL_PAYMENT_RECEIPT",
                new Booking.PaymentSummary(TOTAL, TOTAL, 0, "PAID", 100, false, 0)));
        assertEquals(Status.PAID, s.status);
    }

    @Test
    public void receiptWithoutASummary_fallsBackToTheBackendStatedType() {
        assertEquals(Status.PARTIALLY_PAID, TransactionStatusHelper.summarize(receipt("PARTIAL_RECEIPT", null)).status);
        assertEquals(Status.PAID, TransactionStatusHelper.summarize(receipt("OFFICIAL_RECEIPT", null)).status);
        assertEquals(Status.PAID, TransactionStatusHelper.summarize(receipt(null, null)).status);
    }

    @Test
    public void receiptWhoseSummaryShowsNothingPaid_neverClaimsPaid() {
        Summary s = TransactionStatusHelper.summarize(receipt("FULL_PAYMENT_RECEIPT",
                new Booking.PaymentSummary(TOTAL, 0, TOTAL, "PENDING", null, false, 0)));
        assertEquals(Status.PENDING, s.status);
    }

    // ---- Presentation ----

    @Test
    public void everyStatusHasADistinctLabelColorAndIcon() {
        List<Integer> labels = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (Status status : Status.values()) {
            TransactionStatusHelper.Style style = TransactionStatusHelper.styleFor(status);
            labels.add(style.labelRes);
            colors.add(style.fgColorRes);
        }
        assertEquals("labels", Status.values().length, new java.util.HashSet<>(labels).size());
        assertEquals("colors", Status.values().length, new java.util.HashSet<>(colors).size());
    }

    @Test
    public void pendingHeadlineDistinguishesSubmittedFromNotYetPaid() {
        Summary awaiting = TransactionStatusHelper.summarize(withPayments("Pending", TOTAL, payment("1800.00", "pending")));
        Summary none = TransactionStatusHelper.summarize(booking("Pending", TOTAL));
        assertEquals(R.string.txn_headline_pending_verification, TransactionStatusHelper.headlineRes(awaiting));
        assertEquals(R.string.txn_headline_pending_payment, TransactionStatusHelper.headlineRes(none));
    }
}
