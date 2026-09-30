package com.example.velocitysuites;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM coverage for ReceiptCardHelper's Context/Activity-free static
 * methods - the Activity-bound binders (bindLegacyReceiptActionCard,
 * buildReceiptsSection, buildTransactionRow, statusLabelFor) can't be unit
 * tested here (no Robolectric in this project - see ReceiptDetailMappingTest's
 * identical doc), so only the pure gating/formatting logic is covered.
 */
public class ReceiptCardHelperTest {

    private static Booking booking() {
        return new Booking("B1", "R1", "Deluxe 101", "Deluxe",
                "May 01, 2025", "May 05, 2025", 2, 10000.0, "Confirmed", "Apr 30, 2025");
    }

    @Test
    public void hasLegacyReceiptCandidate_falseWithNoPaymentAttemptAtAll() {
        Booking b = booking();
        b.setAmountPaid(0.0);
        assertFalse(ReceiptCardHelper.hasLegacyReceiptCandidate(b));
    }

    @Test
    public void hasLegacyReceiptCandidate_trueOncePaymentPendingVerification() {
        Booking b = booking();
        b.setAmountPaid(0.0);
        b.setPaymentPendingVerification(true);
        assertTrue(ReceiptCardHelper.hasLegacyReceiptCandidate(b));
    }

    @Test
    public void isLegacyReceiptVerified_requiresBothStaffVerifiedAndRealAmountPaid() {
        Booking staffVerifiedButUnpaid = booking();
        staffVerifiedButUnpaid.setAmountPaid(0.0);
        staffVerifiedButUnpaid.setStaffVerified(true);
        assertFalse(ReceiptCardHelper.isLegacyReceiptVerified(staffVerifiedButUnpaid));

        Booking paidButNotVerified = booking();
        paidButNotVerified.setAmountPaid(5000.0);
        paidButNotVerified.setStaffVerified(false);
        assertFalse(ReceiptCardHelper.isLegacyReceiptVerified(paidButNotVerified));

        Booking paidAndVerified = booking();
        paidAndVerified.setAmountPaid(5000.0);
        paidAndVerified.setStaffVerified(true);
        assertTrue(ReceiptCardHelper.isLegacyReceiptVerified(paidAndVerified));
    }

    @Test
    public void formatPrice_usesPhpSignAndTwoDecimals() {
        assertEquals("₱5,000.00", ReceiptCardHelper.formatPrice(5000.0));
        assertEquals("₱0.00", ReceiptCardHelper.formatPrice(0.0));
    }

    // ---- isReceiptForBooking: which transaction record a fetched receipt belongs to ----

    private static ReceiptDetail receiptFor(String bookingId, String reservationId) {
        return new ReceiptDetail("OFFICIAL_RECEIPT", "OR-1", bookingId, reservationId, null, null, null, null,
                null, null, 2, null, 0, 0, null, null, null, null);
    }

    private static Booking record(String id, boolean direct) {
        Booking b = new Booking(id, "R1", "Deluxe 101", "Deluxe",
                "May 01, 2025", "May 05, 2025", 2, 10000.0, "Confirmed", "Apr 30, 2025");
        b.setDirectBooking(direct);
        return b;
    }

    @Test
    public void isReceiptForBooking_directBookingReceipt_matchesTheDirectBookingWithThatBookingId() {
        ReceiptDetail receipt = receiptFor("250", null);

        assertTrue(ReceiptCardHelper.isReceiptForBooking(receipt, record("250", true)));
        assertFalse("a different booking id", ReceiptCardHelper.isReceiptForBooking(receipt, record("251", true)));
    }

    @Test
    public void isReceiptForBooking_reservationDerivedReceipt_matchesTheReservationKeyedRecord() {
        // The converted booking is #250, but the client keeps this transaction under its original reservation id 100.
        ReceiptDetail receipt = receiptFor("250", "100");

        assertTrue(ReceiptCardHelper.isReceiptForBooking(receipt, record("100", false)));
        assertFalse("the booking id 250 is not how the client keys a reservation-derived transaction",
                ReceiptCardHelper.isReceiptForBooking(receipt, record("250", false)));
    }

    @Test
    public void isReceiptForBooking_neverCrossesTheReservationAndDirectBookingIdSpaces() {
        // Reservation #100 and direct booking #100 are two unrelated transactions.
        ReceiptDetail reservationReceipt = receiptFor("250", "100");
        ReceiptDetail directReceipt = receiptFor("100", null);

        assertFalse(ReceiptCardHelper.isReceiptForBooking(reservationReceipt, record("100", true)));
        assertFalse(ReceiptCardHelper.isReceiptForBooking(directReceipt, record("100", false)));
    }

    @Test
    public void isReceiptForBooking_blankReservationId_isTreatedAsADirectBookingReceipt() {
        ReceiptDetail receipt = receiptFor("250", "  ");

        assertTrue(ReceiptCardHelper.isReceiptForBooking(receipt, record("250", true)));
    }
}
