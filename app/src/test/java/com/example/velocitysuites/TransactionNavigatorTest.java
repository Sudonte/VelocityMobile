package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * "View Transaction must open the EXACT reservation or booking the notification refers to" - including when a
 * reservation and a direct booking share an id (separate tables, independent id sequences).
 */
public class TransactionNavigatorTest {

    private static Booking reservation(String id, long createdAt) {
        Booking b = new Booking(id, "1", "Deluxe", "Deluxe", "Oct 01, 2026", "Oct 02, 2026", 2, 1800, "Pending", "");
        b.setCreatedAtMillis(createdAt);
        return b;
    }

    private static Booking direct(String id, long createdAt) {
        Booking b = reservation(id, createdAt);
        b.setDirectBooking(true);
        b.setHasBooking(true);
        return b;
    }

    private static Booking.ReceiptSummary receipt(String number) {
        return new Booking.ReceiptSummary(number, "PARTIAL_RECEIPT", "issued", 900, 50, null);
    }

    // ---- simple cases ----

    @Test
    public void noCandidate_isNull() {
        assertNull(TransactionNavigator.pick(Arrays.asList(reservation("1", 0)), "99", null, Notification.TYPE_PAYMENT, null));
        assertNull(TransactionNavigator.pick(null, "1", null, null, null));
        assertNull(TransactionNavigator.pick(Arrays.asList(reservation("1", 0)), null, null, null, null));
        assertNull(TransactionNavigator.pick(Arrays.asList(reservation("1", 0)), "  ", null, null, null));
    }

    @Test
    public void aUniqueIdMatchIsReturned_whateverTheTypeHint() {
        Booking only = reservation("588", 0);
        List<Booking> pool = Arrays.asList(reservation("1", 0), only, direct("7", 0));
        assertSame(only, TransactionNavigator.pick(pool, "588", null, Notification.TYPE_BOOKING, null));
        assertSame(only, TransactionNavigator.pick(pool, " 588 ", null, null, null));
    }

    @Test
    public void nullEntriesInThePoolAreIgnored() {
        Booking hit = reservation("3", 0);
        assertSame(hit, TransactionNavigator.pick(Arrays.asList(null, hit), "3", null, null, null));
    }

    // ---- an id shared by a reservation AND a direct booking ----

    @Test
    public void aCallerThatKnowsTheFamilyWins() {
        Booking r = reservation("5", 100);
        Booking d = direct("5", 50);
        List<Booking> pool = Arrays.asList(r, d);
        assertSame(d, TransactionNavigator.pick(pool, "5", Boolean.TRUE, null, null));
        assertSame(r, TransactionNavigator.pick(pool, "5", Boolean.FALSE, null, null));
    }

    @Test
    public void aWrongFamilyHintStillLeadsSomewhere_insteadOfNotFound() {
        Booking only = reservation("5", 100);
        assertSame(only, TransactionNavigator.pick(Collections.singletonList(only), "5", Boolean.TRUE, null, null));
    }

    @Test
    public void aReceiptNumberBelongsToExactlyOneTransaction() {
        Booking r = reservation("5", 100);
        Booking d = direct("5", 500); // newer - would win on recency alone
        r.setReceipts(Collections.singletonList(receipt("PR-20260930-000001")));
        assertSame(r, TransactionNavigator.pick(Arrays.asList(d, r), "5", null, Notification.TYPE_PAYMENT, "PR-20260930-000001"));
    }

    @Test
    public void aReceiptFoundOnlyInPaymentTransactionsStillMatches() {
        Booking r = reservation("5", 100);
        Booking d = direct("5", 500);
        d.setPaymentTransactions(Collections.singletonList(new Booking.PaymentTransactionRecord(1, "GCASH", "DEPOSIT", "PARTIAL_PAYMENT",
                900, "completed", "verified", null, null, "REF", 50, null, null, null, null, 900, 900, "PARTIAL_RECEIPT", "PR-9")));
        assertSame(d, TransactionNavigator.pick(Arrays.asList(r, d), "5", null, Notification.TYPE_PAYMENT, "PR-9"));
    }

    @Test
    public void aReservationNotificationIsNeverAboutADirectBooking() {
        Booking r = reservation("5", 100);
        Booking d = direct("5", 900); // more recent
        assertSame(r, TransactionNavigator.pick(Arrays.asList(d, r), "5", null, Notification.TYPE_RESERVATION, null));
    }

    @Test
    public void stillAmbiguous_theMoreRecentlyActiveOneWins() {
        Booking older = reservation("5", 100);
        Booking newer = direct("5", 900);
        assertSame(newer, TransactionNavigator.pick(Arrays.asList(older, newer), "5", null, Notification.TYPE_PAYMENT, null));
        assertSame(newer, TransactionNavigator.pick(Arrays.asList(newer, older), "5", null, Notification.TYPE_PAYMENT, null));
    }

    @Test
    public void aLatePaymentCountsAsRecentActivity() {
        Booking a = reservation("5", 100);
        Booking b = direct("5", 900);
        a.setPaymentHistory(new ArrayList<>(Collections.singletonList(
                new Booking.PaymentRecord("100", "GCASH", "R", "Jan 01, 2030 • 9:00 AM", "pending"))));
        assertSame(a, TransactionNavigator.pick(Arrays.asList(a, b), "5", null, Notification.TYPE_PAYMENT, null));
    }

    @Test
    public void equalActivity_prefersTheReservationDerivedOne() {
        Booking r = reservation("5", 100);
        Booking d = direct("5", 100);
        assertSame(r, TransactionNavigator.pick(Arrays.asList(d, r), "5", null, Notification.TYPE_PAYMENT, null));
        assertSame(r, TransactionNavigator.pick(Arrays.asList(r, d), "5", null, Notification.TYPE_PAYMENT, null));
    }

    // ---- which table to ask first when it is not loaded ----

    @Test
    public void lookupOrderFollowsTheCategory() {
        assertEquals(Arrays.asList(RoomRepository.TransactionFamily.RESERVATION, RoomRepository.TransactionFamily.DIRECT_BOOKING),
                TransactionNavigator.lookupOrder(Notification.TYPE_RESERVATION));
        assertEquals(Arrays.asList(RoomRepository.TransactionFamily.DIRECT_BOOKING, RoomRepository.TransactionFamily.RESERVATION),
                TransactionNavigator.lookupOrder(Notification.TYPE_BOOKING));
        assertEquals(Arrays.asList(RoomRepository.TransactionFamily.DIRECT_BOOKING, RoomRepository.TransactionFamily.RESERVATION),
                TransactionNavigator.lookupOrder(Notification.TYPE_CHECK_IN));
        assertEquals(Arrays.asList(RoomRepository.TransactionFamily.RESERVATION, RoomRepository.TransactionFamily.DIRECT_BOOKING),
                TransactionNavigator.lookupOrder(Notification.TYPE_PAYMENT));
        assertEquals(2, TransactionNavigator.lookupOrder(null).size());
    }
}
