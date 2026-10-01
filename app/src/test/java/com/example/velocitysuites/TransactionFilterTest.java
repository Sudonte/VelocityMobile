package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Search, status/type, room type, booking status and date filters - alone and combined. */
public class TransactionFilterTest {

    private static Booking make(String id, String status, double total, String roomType, String checkIn, boolean hasBooking) {
        Booking b = new Booking(id, "1", roomType, roomType, checkIn, "Dec 31, 2026", 2, total, status, "");
        b.setHasBooking(hasBooking);
        return b;
    }

    private static void pay(Booking b, String amount, String status) {
        List<Booking.PaymentRecord> rows = new ArrayList<>(b.getPaymentHistory());
        rows.add(new Booking.PaymentRecord(amount, "GCASH", "REF" + b.getId(), "Sep 30, 2026 • 10:09 PM", status));
        b.setPaymentHistory(rows);
        if ("pending".equals(status)) b.setPaymentPendingVerification(true);
    }

    /** A small, varied history: pending reservation, paid booking, partially paid booking, cancelled, rejected, checked-out stay. */
    private static List<TransactionRow> fixtures() {
        Booking pending = make("1", "Pending", 1800, "Deluxe", "Oct 01, 2026", false);
        pay(pending, "1800.00", "pending");

        Booking paid = make("2", "Confirmed", 3000, "Suite", "Oct 10, 2026", true);
        pay(paid, "3000.00", "completed");

        Booking partial = make("3", "Confirmed", 2000, "Deluxe", "Oct 20, 2026", true);
        pay(partial, "1000.00", "completed");

        Booking cancelled = make("4", "Cancelled", 1500, "Standard", "Nov 05, 2026", false);

        Booking rejected = make("5", "Rejected", 1500, "Standard", "Nov 15, 2026", false);

        Booking stay = make("6", "Checked-Out", 2500, "Suite", "Sep 01, 2026", true);
        pay(stay, "2500.00", "completed");

        return TransactionRow.fromAll(Arrays.asList(pending, paid, partial, cancelled, rejected, stay));
    }

    private static List<String> ids(List<TransactionRow> rows) {
        List<String> out = new ArrayList<>();
        for (TransactionRow r : rows) out.add(r.id);
        Collections.sort(out);
        return out;
    }

    private static List<String> run(TransactionFilter.Criteria c) {
        return ids(TransactionFilter.apply(fixtures(), c));
    }

    private static TransactionFilter.Criteria criteria() {
        return new TransactionFilter.Criteria();
    }

    // ---- the five badge statuses are exactly the status filters ----

    @Test
    public void nothingSelected_showsEverything() {
        assertEquals(Arrays.asList("1", "2", "3", "4", "5", "6"), run(criteria()));
        assertFalse(criteria().isActive());
    }

    @Test
    public void pendingFilter_isTheShownPendingBadge() {
        TransactionFilter.Criteria c = criteria();
        c.type = TransactionFilter.Type.PENDING;
        assertEquals(Collections.singletonList("1"), run(c));
    }

    @Test
    public void paidFilter_isOnlyVerifiedFullPayments() {
        TransactionFilter.Criteria c = criteria();
        c.type = TransactionFilter.Type.PAID;
        assertEquals(Arrays.asList("2", "6"), run(c));
    }

    @Test
    public void partiallyPaidFilter() {
        TransactionFilter.Criteria c = criteria();
        c.type = TransactionFilter.Type.PARTIALLY_PAID;
        assertEquals(Collections.singletonList("3"), run(c));
    }

    @Test
    public void cancelledAndRejectedFiltersAreSeparate() {
        TransactionFilter.Criteria c = criteria();
        c.type = TransactionFilter.Type.CANCELLED;
        assertEquals(Collections.singletonList("4"), run(c));
        c.type = TransactionFilter.Type.REJECTED;
        assertEquals(Collections.singletonList("5"), run(c));
    }

    @Test
    public void bookingsAndReservationsExcludeClosedRecords() {
        TransactionFilter.Criteria c = criteria();
        c.type = TransactionFilter.Type.BOOKINGS;
        assertEquals(Arrays.asList("2", "3", "6"), run(c));
        c.type = TransactionFilter.Type.RESERVATIONS;
        assertEquals(Collections.singletonList("1"), run(c)); // 4 and 5 are closed
    }

    @Test
    public void paymentsFilter_isAnythingThatHadAPayment() {
        TransactionFilter.Criteria c = criteria();
        c.type = TransactionFilter.Type.PAYMENTS;
        assertEquals(Arrays.asList("1", "2", "3", "6"), run(c));
    }

    @Test
    public void staysFilter_isCheckedInOrOut() {
        TransactionFilter.Criteria c = criteria();
        c.type = TransactionFilter.Type.STAYS;
        assertEquals(Collections.singletonList("6"), run(c));
    }

    @Test
    public void legacyDeepLinkKeysMapToTheNewTypes() {
        assertEquals(TransactionFilter.Type.PAID, TransactionFilter.Type.fromKey("FullyPaid"));
        assertEquals(TransactionFilter.Type.PENDING, TransactionFilter.Type.fromKey("PendingPayment"));
        assertEquals(TransactionFilter.Type.PAYMENTS, TransactionFilter.Type.fromKey("Payments"));
        assertEquals(TransactionFilter.Type.BOOKINGS, TransactionFilter.Type.fromKey("Bookings"));
        assertEquals(TransactionFilter.Type.RESERVATIONS, TransactionFilter.Type.fromKey("Reservations"));
        assertEquals(TransactionFilter.Type.ALL, TransactionFilter.Type.fromKey(null));
        assertEquals(TransactionFilter.Type.ALL, TransactionFilter.Type.fromKey("Nonsense"));
    }

    // ---- search ----

    @Test
    public void searchMatchesReferenceRoomAndStatusWords() {
        TransactionFilter.Criteria c = criteria();
        c.query = "suite";
        assertEquals(Arrays.asList("2", "6"), run(c));
        c.query = "#3";
        assertEquals(Collections.singletonList("3"), run(c));
        c.query = "partially";
        assertEquals(Collections.singletonList("3"), run(c));
        c.query = "REF2";
        assertEquals(Collections.singletonList("2"), run(c));
    }

    @Test
    public void everySearchWordMustMatch() {
        TransactionFilter.Criteria c = criteria();
        c.query = "deluxe pending";
        assertEquals(Collections.singletonList("1"), run(c));
        c.query = "deluxe cancelled";
        assertEquals(Collections.emptyList(), run(c));
    }

    @Test
    public void searchIsCaseAndWhitespaceInsensitive() {
        TransactionFilter.Criteria c = criteria();
        c.query = "  SUITE   ";
        assertEquals(Arrays.asList("2", "6"), run(c));
    }

    @Test
    public void searchFindsAnAmountFormattedOrBare() {
        TransactionFilter.Criteria c = criteria();
        c.query = "3,000";
        assertEquals(Collections.singletonList("2"), run(c));
        c.query = "3000";
        assertEquals(Collections.singletonList("2"), run(c));
    }

    // ---- room type / booking status ----

    @Test
    public void roomTypeFilter() {
        TransactionFilter.Criteria c = criteria();
        c.roomType = "DELUXE";
        assertEquals(Arrays.asList("1", "3"), run(c));
    }

    @Test
    public void bookingStatusFilter_appliesToBookingsOnly() {
        TransactionFilter.Criteria c = criteria();
        c.bookingStatus = "Confirmed";
        assertEquals(Arrays.asList("2", "3"), run(c));
    }

    // ---- date range ----

    @Test
    public void dateRange_isInclusiveAtBothEnds_onCalendarDates() {
        TransactionFilter.Criteria c = criteria();
        c.from = LocalDate.of(2026, 10, 1);   // the FIRST day of the range must be included
        c.to = LocalDate.of(2026, 10, 10);    // ...and so must the last
        assertEquals(Arrays.asList("1", "2"), run(c));
    }

    @Test
    public void openEndedRanges() {
        TransactionFilter.Criteria c = criteria();
        c.from = LocalDate.of(2026, 11, 1);
        assertEquals(Arrays.asList("4", "5"), run(c));
        c = criteria();
        c.to = LocalDate.of(2026, 9, 30);
        assertEquals(Collections.singletonList("6"), run(c));
    }

    @Test
    public void aTransactionWithNoReadableCheckInIsExcludedFromADateRange_butNotFromNoRange() {
        Booking b = make("9", "Pending", 100, "Deluxe", "not a date", false);
        List<TransactionRow> rows = TransactionRow.fromAll(Collections.singletonList(b));
        TransactionFilter.Criteria c = criteria();
        assertEquals(1, TransactionFilter.apply(rows, c).size());
        c.from = LocalDate.of(2026, 1, 1);
        assertEquals(0, TransactionFilter.apply(rows, c).size());
    }

    // ---- everything together ----

    @Test
    public void allFiltersNarrowTheSameList() {
        TransactionFilter.Criteria c = criteria();
        c.type = TransactionFilter.Type.BOOKINGS;
        c.query = "deluxe";
        c.roomType = "Deluxe";
        c.bookingStatus = "Confirmed";
        c.from = LocalDate.of(2026, 10, 15);
        c.to = LocalDate.of(2026, 10, 31);
        assertEquals(Collections.singletonList("3"), run(c));
        assertTrue(c.isActive());
    }
}
