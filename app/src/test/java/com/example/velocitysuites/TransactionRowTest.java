package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The Transaction History row snapshot: what the card shows, how the list is ordered and identified, and that a bad record can't break it. */
public class TransactionRowTest {

    private static Booking booking(String id, String status, double total) {
        Booking b = new Booking(id, "1", "Deluxe", "Deluxe", "Oct 01, 2026", "Oct 02, 2026", 2, total, status, "");
        b.setHasBooking(false);
        return b;
    }

    private static Booking.PaymentRecord payment(String amount, String status, String date) {
        return new Booking.PaymentRecord(amount, "GCASH", "1234567890123", date, status);
    }

    // ---- content ----

    @Test
    public void rowCarriesTheSharedStatusAndMoney() {
        Booking b = booking("588", "Pending", 1800);
        b.setPaymentHistory(new ArrayList<>(Collections.singletonList(payment("1800.00", "pending", "Sep 30, 2026 • 10:09 PM"))));
        b.setPaymentPendingVerification(true);

        TransactionRow row = TransactionRow.from(b);

        assertEquals(TransactionStatusHelper.Status.PENDING, row.summary.status);
        assertEquals(1800, row.summary.grandTotal, 0.0001);
        assertEquals(0, row.summary.verifiedPaid, 0.0001);
        assertEquals(1800, row.summary.pendingSubmitted, 0.0001);
        assertEquals(TransactionRow.Kind.RESERVATION, row.kind);
        assertEquals("588", row.id);
    }

    @Test
    public void aConvertedTransactionIsABookingKind() {
        Booking b = booking("12", "Confirmed", 1800);
        b.setHasBooking(true);
        assertEquals(TransactionRow.Kind.BOOKING, TransactionRow.from(b).kind);
    }

    @Test
    public void roomText_singleRoomType_withQuantity() {
        Booking b = booking("1", "Pending", 100);
        b.setRoomsRequested(2);
        assertEquals("Deluxe ×2", TransactionRow.from(b).roomText);
        b.setRoomsRequested(1);
        assertEquals("Deluxe", TransactionRow.from(b).roomText);
    }

    @Test
    public void roomText_multipleRoomLines_areJoined() {
        Booking b = booking("1", "Pending", 100);
        b.setRooms(Arrays.asList(
                new BookingRoom("1", "Deluxe", 2, 1000, 2, 4000, null),
                new BookingRoom("2", "Suite", 1, 2000, 2, 4000, null)));
        assertEquals("Deluxe ×2 • Suite", TransactionRow.from(b).roomText);
    }

    @Test
    public void roomText_nullsEverywhere_isEmptyNotNullOrTheWordNull() {
        Booking b = booking("1", "Pending", 100);
        b.setRoomType(null);
        b.setRoomName(null);
        assertEquals("", TransactionRow.from(b).roomText);
        b.setRooms(Collections.singletonList(new BookingRoom("1", null, 1, 0, 1, 0, null)));
        assertEquals("", TransactionRow.from(b).roomText);
    }

    @Test
    public void aRecordMissingMostFields_stillBuilds() {
        Booking b = booking(null, "Pending", 0);
        b.setCheckInDate(null);
        b.setCheckOutDate(null);
        b.setRoomType(null);
        b.setRoomName(null);
        TransactionRow row = TransactionRow.from(b);
        assertEquals("", row.id);
        assertEquals("", row.checkIn);
        assertNull(row.checkInDate);
    }

    @Test
    public void checkInIsParsedToACalendarDate() {
        assertEquals(LocalDate.of(2026, 10, 1), TransactionRow.from(booking("1", "Pending", 100)).checkInDate);
    }

    @Test
    public void receiptsAreCounted() {
        Booking b = booking("1", "Confirmed", 100);
        b.setReceipts(Arrays.asList(new Booking.ReceiptSummary("PR-1", "PARTIAL_RECEIPT", "issued", 50, 50, null),
                new Booking.ReceiptSummary("OR-1", "OFFICIAL_RECEIPT", "issued", 100, null, null)));
        assertEquals(2, TransactionRow.from(b).receiptCount);
    }

    @Test
    public void cancelledAndRejectedTransactionsAreFlaggedClosed() {
        assertTrue(TransactionRow.from(booking("1", "Cancelled", 1)).closed);
        assertTrue(TransactionRow.from(booking("1", "Rejected", 1)).closed);
        assertFalse(TransactionRow.from(booking("1", "Confirmed", 1)).closed);
    }

    // ---- ordering ----

    @Test
    public void newestActivityFirst_byCreationOrLatestPayment() {
        Booking older = booking("1", "Pending", 100);
        older.setCreatedAtMillis(1_000_000_000_000L);
        Booking newer = booking("2", "Pending", 100);
        newer.setCreatedAtMillis(1_700_000_000_000L);
        // "older" got a payment much later than "newer" was created - it is the more recently active one.
        older.setPaymentHistory(new ArrayList<>(Collections.singletonList(payment("100", "pending", "Jan 01, 2030 • 9:00 AM"))));

        List<TransactionRow> rows = TransactionRow.fromAll(Arrays.asList(newer, older));

        assertEquals("1", rows.get(0).id);
        assertEquals("2", rows.get(1).id);
    }

    @Test
    public void tiesFallBackToTheHigherId() {
        Booking a = booking("7", "Pending", 100);
        Booking b = booking("12", "Pending", 100);
        List<TransactionRow> rows = TransactionRow.fromAll(Arrays.asList(a, b));
        assertEquals("12", rows.get(0).id);
        assertEquals("7", rows.get(1).id);
    }

    @Test
    public void fromAll_ignoresNullEntries_andNullList() {
        assertEquals(0, TransactionRow.fromAll(null).size());
        assertEquals(1, TransactionRow.fromAll(Arrays.asList(null, booking("1", "Pending", 1))).size());
    }

    // ---- identity ----

    @Test
    public void aReservationAndADirectBookingWithTheSameIdAreDifferentTransactions() {
        Booking reservation = booking("5", "Pending", 100);
        Booking direct = booking("5", "Confirmed", 100);
        direct.setDirectBooking(true);
        direct.setHasBooking(true);

        TransactionRow r = TransactionRow.from(reservation);
        TransactionRow d = TransactionRow.from(direct);

        assertFalse(r.sameTransactionAs(d));
        assertNotEquals(r.stableId(), d.stableId());
        assertTrue(r.sameTransactionAs(TransactionRow.from(reservation)));
        assertEquals(r.stableId(), TransactionRow.from(reservation).stableId());
    }

    @Test
    public void aStatusChangeIsAContentChange_identicalDataIsNot() {
        Booking pending = booking("5", "Pending", 1800);
        pending.setPaymentHistory(new ArrayList<>(Collections.singletonList(payment("1800.00", "pending", "Sep 30, 2026 • 10:09 PM"))));
        pending.setPaymentPendingVerification(true);
        TransactionRow before = TransactionRow.from(pending);

        // The SAME Booking object is mutated in place by the repository when the receptionist verifies it -
        // the snapshot taken earlier must still show the old state, so a diff sees the change.
        pending.setPaymentHistory(new ArrayList<>(Collections.singletonList(payment("1800.00", "completed", "Sep 30, 2026 • 10:09 PM"))));
        pending.setPaymentPendingVerification(false);
        TransactionRow after = TransactionRow.from(pending);

        assertEquals(TransactionStatusHelper.Status.PENDING, before.summary.status);
        assertEquals(TransactionStatusHelper.Status.PAID, after.summary.status);
        assertFalse(before.sameContentAs(after));
        assertTrue(after.sameContentAs(TransactionRow.from(pending)));
    }

    // ---- search text ----

    @Test
    public void searchTextCoversReferenceRoomStatusAmountAndPaymentReferences() {
        Booking b = booking("588", "Pending", 1800);
        b.setTransactionRef("GC-REF-9");
        b.setPaymentHistory(new ArrayList<>(Collections.singletonList(payment("900.00", "completed", "Sep 30, 2026 • 10:09 PM"))));
        b.setReceipts(Collections.singletonList(new Booking.ReceiptSummary("PR-20260930-000001", "PARTIAL_RECEIPT", "issued", 900, 50, null)));

        String text = TransactionRow.from(b).searchText;

        assertTrue(text.contains("588"));
        assertTrue(text.contains("#588"));
        assertTrue(text.contains("reservation"));
        assertTrue(text.contains("deluxe"));
        assertTrue(text.contains("partially paid"));
        assertTrue(text.contains("gc-ref-9"));
        assertTrue(text.contains("1234567890123"));
        assertTrue(text.contains("pr-20260930-000001"));
        assertTrue(text.contains("1,800.00"));
        assertTrue(text.contains("1800"));
        assertNotNull(text);
    }
}
