package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * The one "Amount to pay" rule (see {@link MoneySummary}), state by state, on the same figures the server reports: Remaining balance =
 * Grand total - verified payments, and a receipt shows the value as of its own moment.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = com.example.velocitysuites.ui.TestApplication.class)
public class MoneySummaryRuleTest {

    private final Context c = ApplicationProvider.getApplicationContext();

    private static Booking.PaymentTransactionRecord gcash(double amount, String verification, String verifiedAt) {
        return new Booking.PaymentTransactionRecord(1, "gcash", "deposit", "partial_payment", amount, "completed", verification, null, null, "6564676886467", null, null,
                verifiedAt, null, "2026-10-24T10:37:34Z", 0, 0, null, null);
    }

    private static Booking booking(boolean hasBooking, String status, String method, double total, double paid, double waiting) {
        Booking b = new Booking("7", "1", "Deluxe", "Deluxe", "Nov 01, 2026", "Nov 02, 2026", 2, total, status, "Oct 01, 2026");
        b.setHasBooking(hasBooking);
        b.setStatus(status);
        b.setPaymentMethod(method);
        b.setTotalAmount(total);
        List<Booking.PaymentTransactionRecord> txs = new ArrayList<>();
        if (paid > 0) txs.add(gcash(paid, "verified", "2026-10-24T10:40:00Z"));
        if (waiting > 0) txs.add(gcash(waiting, "pending_verification", null));
        b.setPaymentTransactions(txs);
        Booking.PaymentSummary s = new Booking.PaymentSummary(total, paid, total - paid, "PENDING", 0, false, 0);
        s.waitingForVerification = waiting;
        b.setPaymentSummary(s);
        return b;
    }

    @Test
    public void reservationNotPaid_gcash_isThePayNowRange_cashIsPayAtTheHotel() {
        MoneySummary g = MoneySummary.of(c, booking(false, "Pending", "gcash", 10000, 0, 0));
        assertEquals(MoneySummary.Kind.PAY_RANGE, g.kind);
        assertEquals("Pay now: ₱2,000.00 – ₱5,000.00 (20%–50%)", g.amountToPay);
        assertEquals(10000, g.grandTotal, 0.001);
        assertEquals(10000, g.remaining, 0.001);
        MoneySummary cash = MoneySummary.of(c, booking(false, "Pending", "cash", 10000, 0, 0));
        assertEquals(MoneySummary.Kind.PAY_AT_HOTEL, cash.kind);
        assertEquals("Pay at the hotel", cash.amountToPay);
    }

    @Test
    public void submittedAndWaiting_isZeroNow() {
        MoneySummary s = MoneySummary.of(c, booking(true, "Confirmed", "gcash", 10000, 0, 2000));
        assertEquals(MoneySummary.Kind.WAITING, s.kind);
        assertEquals("₱0.00 now · ₱2,000.00 waiting for verification", s.amountToPay);
        assertEquals("the waiting money is not deducted from the balance", 10000, s.remaining, 0.001);
        assertEquals("₱0.00 now".length(), s.amountEnd);
    }

    @Test
    public void verifiedNotCheckedIn_isTheRemainingBalanceAtCheckIn() {
        MoneySummary s = MoneySummary.of(c, booking(true, "Confirmed", "gcash", 10000, 4000, 0));
        assertEquals(MoneySummary.Kind.AT_CHECK_IN, s.kind);
        assertEquals("₱6,000.00 · at check-out", s.amountToPay);
        assertEquals(6000, s.remaining, 0.001);
        assertEquals("₱6,000.00".length(), s.amountEnd);
    }

    @Test
    public void checkedInWithABalance_isAtCheckOut() {
        MoneySummary s = MoneySummary.of(c, booking(true, "Checked-In", "gcash", 10000, 4000, 0));
        assertEquals(MoneySummary.Kind.AT_CHECK_OUT, s.kind);
        assertEquals("₱6,000.00 · at check-out", s.amountToPay);
    }

    @Test
    public void fullyPaidOrCompleted_isNothingToPay() {
        for (String status : new String[]{"Confirmed", "Checked-In", "Completed", "Checked-Out"}) {
            MoneySummary s = MoneySummary.of(c, booking(true, status, "gcash", 10000, 10000, 0));
            assertEquals(status, "₱0.00 · Nothing to pay", s.amountToPay);
            assertTrue(s.isSettled());
            assertEquals(0, s.remaining, 0.001);
        }
        assertEquals("a completed transaction owes nothing", "₱0.00 · Nothing to pay", MoneySummary.of(c, booking(true, "Completed", "gcash", 10000, 9000, 0)).amountToPay);
    }

    @Test
    public void cancelledOrRejected_hasNoAmounts() {
        for (String status : new String[]{"Cancelled", "Rejected"}) {
            MoneySummary s = MoneySummary.of(c, booking(true, status, "gcash", 10000, 0, 0));
            assertTrue(s.isCancelled());
            assertEquals("This transaction was cancelled", s.amountToPay);
        }
    }

    @Test
    public void receipts_showTheValueAsOfThatReceipt() {
        MoneySummary one = MoneySummary.ofReceipt(c, 1, 10000, 6000);
        assertEquals("₱6,000.00 · at check-out", one.amountToPay);
        MoneySummary two = MoneySummary.ofReceipt(c, 2, 10000, 2500);
        assertEquals("₱2,500.00 · at check-out", two.amountToPay);
        MoneySummary three = MoneySummary.ofReceipt(c, 3, 10000, 0);
        assertEquals("₱0.00 · Fully paid", three.amountToPay);
        assertEquals(MoneySummary.Kind.FULLY_PAID, three.kind);
        assertEquals("Receipt 1 of a bill paid in full online owes nothing", "₱0.00 · Nothing to pay", MoneySummary.ofReceipt(c, 1, 10000, 0).amountToPay);
        assertFalse(one.isSettled());
    }
}
