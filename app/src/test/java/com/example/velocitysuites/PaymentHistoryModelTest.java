package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The Payment History rules: what is listed, the method / date filters, and the totals above the list. */
public class PaymentHistoryModelTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Manila");
    private static final long NOW = ZonedDateTime.of(2026, 10, 15, 12, 0, 0, 0, ZONE).toInstant().toEpochMilli();

    private static Booking.PaymentRecord payment(String amount, String method, String date, String status) {
        return new Booking.PaymentRecord(amount, method, "REF", date, status);
    }

    private static Booking booking(String id, boolean direct, Booking.PaymentRecord... payments) {
        Booking b = new Booking(id, "1", "Deluxe", "Deluxe", "Oct 01, 2026", "Oct 02, 2026", 2, 1800, "Confirmed", "");
        b.setDirectBooking(direct);
        b.setPaymentHistory(new ArrayList<>(Arrays.asList(payments)));
        return b;
    }

    private static List<PaymentHistoryModel.Entry> sample() {
        return PaymentHistoryModel.collect(Arrays.asList(
                booking("1", false,
                        payment("1800.00", "GCASH", "Oct 14, 2026 3:00 PM", "completed"),
                        payment("600.00", "GCASH", "Oct 15, 2026 9:00 AM", "pending")),
                booking("2", true, payment("1200.00", "CASH", "Sep 01, 2026 10:00 AM", "completed"))));
    }

    @Test
    public void everyPaymentOfEveryTransactionIsListedNewestFirst() {
        List<PaymentHistoryModel.Entry> all = sample();
        assertEquals(3, all.size());
        assertEquals("600.00", all.get(0).record.amount);
        assertEquals("1200.00", all.get(2).record.amount);
    }

    @Test
    public void aHiddenBookingsPaymentsStillCount() {
        Booking hidden = booking("3", false, payment("500.00", "CASH", "Oct 01, 2026 1:00 PM", "completed"));
        hidden.setHiddenByGuest(true);
        assertEquals(1, PaymentHistoryModel.collect(Arrays.asList(hidden)).size());
    }

    @Test
    public void methodFilter() {
        List<PaymentHistoryModel.Entry> all = sample();
        assertEquals(2, PaymentHistoryModel.filter(all, PaymentHistoryModel.Method.GCASH, PaymentHistoryModel.Range.ALL_TIME, NOW, ZONE).size());
        List<PaymentHistoryModel.Entry> cash = PaymentHistoryModel.filter(all, PaymentHistoryModel.Method.CASH, PaymentHistoryModel.Range.ALL_TIME, NOW, ZONE);
        assertEquals(1, cash.size());
        assertTrue(cash.get(0).isCash());
        assertEquals(3, PaymentHistoryModel.filter(all, PaymentHistoryModel.Method.ALL, PaymentHistoryModel.Range.ALL_TIME, NOW, ZONE).size());
    }

    @Test
    public void dateFilter() {
        List<PaymentHistoryModel.Entry> all = sample();
        // "now" is Oct 15: Last 30 days = Sep 16 - Oct 15 (drops the Sep 1 cash payment); This month = from Oct 1.
        assertEquals(2, PaymentHistoryModel.filter(all, PaymentHistoryModel.Method.ALL, PaymentHistoryModel.Range.LAST_30_DAYS, NOW, ZONE).size());
        assertEquals(2, PaymentHistoryModel.filter(all, PaymentHistoryModel.Method.ALL, PaymentHistoryModel.Range.THIS_MONTH, NOW, ZONE).size());
        assertEquals(0, PaymentHistoryModel.filter(all, PaymentHistoryModel.Method.CASH, PaymentHistoryModel.Range.THIS_MONTH, NOW, ZONE).size());
    }

    @Test
    public void aPaymentWithNoReadableDateOnlyShowsUnderAllTime() {
        List<PaymentHistoryModel.Entry> all = PaymentHistoryModel.collect(Arrays.asList(
                booking("4", false, payment("100.00", "CASH", null, "completed"))));
        assertEquals(1, PaymentHistoryModel.filter(all, PaymentHistoryModel.Method.ALL, PaymentHistoryModel.Range.ALL_TIME, NOW, ZONE).size());
        assertEquals(0, PaymentHistoryModel.filter(all, PaymentHistoryModel.Method.ALL, PaymentHistoryModel.Range.LAST_30_DAYS, NOW, ZONE).size());
    }

    @Test
    public void totalsCountVerifiedMoneyAndKeepPendingApart() {
        PaymentHistoryModel.Totals t = PaymentHistoryModel.totals(sample());
        assertEquals(3000.0, t.paid, 0.001);
        assertEquals(600.0, t.pending, 0.001);
        assertEquals(3, t.count);
    }

    @Test
    public void totalsFollowTheFilter() {
        List<PaymentHistoryModel.Entry> cash = PaymentHistoryModel.filter(sample(), PaymentHistoryModel.Method.CASH, PaymentHistoryModel.Range.ALL_TIME, NOW, ZONE);
        PaymentHistoryModel.Totals t = PaymentHistoryModel.totals(cash);
        assertEquals(1200.0, t.paid, 0.001);
        assertEquals(0.0, t.pending, 0.001);
    }

    @Test
    public void cashDetailsShowOnlyForACashPaymentThatRecordedThem() {
        Booking.PaymentRecord cash = payment("1200.00", "CASH", "Oct 14, 2026 3:00 PM", "completed");
        assertFalse("no tender recorded (an older payment)", cash.hasCashTender());
        cash.cashReceived = "2000.00";
        cash.changeGiven = "800.00";
        assertTrue(cash.hasCashTender());

        Booking.PaymentRecord gcash = payment("1200.00", "GCASH", "Oct 14, 2026 3:00 PM", "completed");
        gcash.cashReceived = "2000.00";
        assertFalse("GCash never shows cash rows, whatever the data says", gcash.hasCashTender());
    }

    @Test
    public void statusTextIsCapitalised() {
        assertEquals("Completed", PaymentHistoryModel.statusText("completed"));
        assertEquals("", PaymentHistoryModel.statusText(null));
    }
}
