package com.example.velocitysuites;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Transaction History is required to list the most recent transaction first. The screen sorts by
 * PaymentTransaction#getDateMillis() descending (TransactionHistoryActivity#buildPaymentTransactions()),
 * so these tests pin that value - in particular for a row with no payment date, which used to be
 * 0 and therefore sank below every dated row no matter how recently the transaction was created.
 */
public class PaymentTransactionOrderingTest {

    private static long millis(String pattern, String text) throws Exception {
        return new java.text.SimpleDateFormat(pattern, Locale.ENGLISH).parse(text).getTime();
    }

    private static Booking booking(String id, long createdAtMillis) {
        Booking b = new Booking(id, "R1", "Deluxe Room", "Deluxe",
                "Oct 01, 2026", "Oct 03, 2026", 2, 5000.0, "Pending", "Sep 20, 2026");
        b.setCreatedAtMillis(createdAtMillis);
        return b;
    }

    private static PaymentTransaction paid(Booking parent, String date) {
        return new PaymentTransaction(parent, new Booking.PaymentRecord("1000.00", "GCASH", "REF-" + date, date, "completed", null));
    }

    /** The exact comparator TransactionHistoryActivity sorts with. */
    private static List<PaymentTransaction> sortedMostRecentFirst(PaymentTransaction... rows) {
        List<PaymentTransaction> list = new ArrayList<>(Arrays.asList(rows));
        Collections.sort(list, (a, c) -> Long.compare(c.getDateMillis(), a.getDateMillis()));
        return list;
    }

    @Test
    public void paymentDate_isParsedFromTheCurrentDisplayFormat() throws Exception {
        PaymentTransaction tx = paid(booking("1", 0L), "Sep 02, 2026 • 10:24 AM");

        assertEquals(millis("MMM d, yyyy • h:mm a", "Sep 02, 2026 • 10:24 AM"), tx.getDateMillis());
    }

    @Test
    public void olderDateFormatsStillParse() throws Exception {
        assertEquals(millis("MMM dd, yyyy h:mm a", "Sep 02, 2026 10:24 AM"),
                paid(booking("1", 0L), "Sep 02, 2026 10:24 AM").getDateMillis());
        assertEquals(millis("MMM dd, yyyy", "Sep 02, 2026"),
                paid(booking("1", 0L), "Sep 02, 2026").getDateMillis());
    }

    @Test
    public void rowsAreOrderedMostRecentPaymentFirst() {
        Booking b = booking("1", 0L);
        PaymentTransaction oldest = paid(b, "Sep 01, 2026 • 9:00 AM");
        PaymentTransaction middle = paid(b, "Sep 15, 2026 • 3:30 PM");
        PaymentTransaction newest = paid(b, "Sep 28, 2026 • 8:05 AM");

        List<PaymentTransaction> sorted = sortedMostRecentFirst(oldest, newest, middle);

        assertEquals(Arrays.asList(newest, middle, oldest), sorted);
    }

    @Test
    public void twoPaymentsOnTheSameDay_areOrderedByTimeOfDay() {
        Booking b = booking("1", 0L);
        PaymentTransaction morning = paid(b, "Sep 28, 2026 • 8:05 AM");
        PaymentTransaction evening = paid(b, "Sep 28, 2026 • 7:40 PM");

        assertEquals(Arrays.asList(evening, morning), sortedMostRecentFirst(morning, evening));
    }

    @Test
    public void aRowWithNoPaymentDate_sortsByWhenTheTransactionWasCreated_notToTheBottom() throws Exception {
        Booking withPayments = booking("1", 0L);
        PaymentTransaction oldPayment = paid(withPayments, "Sep 01, 2026 • 9:00 AM");
        PaymentTransaction recentPayment = paid(withPayments, "Sep 20, 2026 • 9:00 AM");

        // A reservation nobody has paid against yet, created on Sep 25 - between the two payments' dates and "now".
        long createdSep25 = millis("MMM dd, yyyy h:mm a", "Sep 25, 2026 12:00 PM");
        PaymentTransaction unpaid = new PaymentTransaction(booking("2", createdSep25), null);

        List<PaymentTransaction> sorted = sortedMostRecentFirst(oldPayment, unpaid, recentPayment);

        assertEquals("newest first: the Sep 25 reservation, then the Sep 20 payment, then Sep 1",
                Arrays.asList(unpaid, recentPayment, oldPayment), sorted);
    }

    @Test
    public void aRowWhoseDateCannotBeParsed_alsoFallsBackToCreationTime() {
        Booking b = booking("1", 1_700_000_000_000L);

        assertEquals(1_700_000_000_000L, paid(b, "not a date").getDateMillis());
    }

    @Test
    public void aRowWithNothingToSortBy_isZero_andSortsLast() {
        PaymentTransaction undated = new PaymentTransaction(booking("1", 0L), null);
        PaymentTransaction dated = paid(booking("2", 0L), "Sep 02, 2026 • 10:24 AM");

        assertEquals(0L, undated.getDateMillis());
        assertTrue(sortedMostRecentFirst(undated, dated).get(0) == dated);
    }

    @Test
    public void createdAtIsAnInstant_soItComparesCorrectlyAgainstAPaymentDateParsedFromText() {
        long now = new Date().getTime();
        PaymentTransaction unpaidJustNow = new PaymentTransaction(booking("2", now), null);
        PaymentTransaction oldPayment = paid(booking("1", 0L), "Jan 05, 2026 • 9:00 AM");

        assertEquals(Arrays.asList(unpaidJustNow, oldPayment), sortedMostRecentFirst(oldPayment, unpaidJustNow));
    }
}
