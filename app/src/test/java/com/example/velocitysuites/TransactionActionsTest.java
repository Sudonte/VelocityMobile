package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Who may cancel and delete: every status x payment state x verified flag, for bookings and reservations (pure rules, no UI). */
public class TransactionActionsTest {

    private static final double TOTAL = 3000;

    /** kind: "B" direct booking, "R" reservation, "C" booking made from a reservation, "H" frozen converted copy. */
    private static Booking item(String kind, String status, double paid, boolean verified) {
        Booking b = new Booking("1", "1", "Deluxe", "Deluxe", "Nov 01, 2026", "Nov 02, 2026", 2, TOTAL, status, "Oct 01, 2026");
        b.setHasBooking(!"R".equals(kind) && !"H".equals(kind));
        b.setDirectBooking("B".equals(kind));
        b.setHistoricalReservation("H".equals(kind));
        b.setAmountPaid(paid);
        b.setStaffVerified(verified);
        return b;
    }

    // ---- cancel

    @Test
    public void booking_cancel_table() {
        assertTrue("pending, unpaid", TransactionActions.canCancel(item("B", "Pending", 0, false)));
        assertTrue("pending, partly paid", TransactionActions.canCancel(item("B", "Pending", 1500, false)));
        assertTrue("pending, fully paid (an older booking): still cancellable until the front desk verifies it", TransactionActions.canCancel(item("B", "Pending", TOTAL, false)));
        assertFalse("pending but verified", TransactionActions.canCancel(item("B", "Pending", 0, true)));
        assertTrue("a new booking is stored as ACTIVE_BOOKING (shown 'Confirmed') until the front desk verifies it",
                TransactionActions.canCancel(item("B", "Confirmed", 1500, false)));
        assertFalse("confirmed and verified by the front desk", TransactionActions.canCancel(item("B", "Confirmed", 1500, true)));
        assertTrue("awaiting verification and fully paid (an older booking): still cancellable until verified", TransactionActions.canCancel(item("B", "Confirmed", TOTAL, false)));
        assertFalse("verified by the front desk", TransactionActions.canCancel(item("B", "Confirmed", TOTAL, true)));
        assertFalse("checked in", TransactionActions.canCancel(item("B", "Checked-In", 1500, false)));
        assertFalse("checked out", TransactionActions.canCancel(item("B", "Checked-Out", TOTAL, true)));
        for (String s : new String[]{"Cancelled", "Rejected", "Completed"}) {
            assertFalse(s, TransactionActions.canCancel(item("B", s, 0, false)));
        }
    }

    @Test
    public void reservation_cancel_table() {
        assertTrue("pending", TransactionActions.canCancel(item("R", "Pending", 0, false)));
        assertTrue("pending, partly paid", TransactionActions.canCancel(item("R", "Pending", 1500, false)));
        assertFalse("pending but verified by the front desk", TransactionActions.canCancel(item("R", "Pending", 0, true)));
        assertFalse("confirmed", TransactionActions.canCancel(item("R", "Confirmed", 0, false)));
        assertFalse("converted to a booking", TransactionActions.canCancel(item("C", "Pending", 0, false)));
        assertFalse("the frozen converted copy", TransactionActions.canCancel(item("H", "Completed", TOTAL, true)));
        assertFalse("already cancelled", TransactionActions.canCancel(item("R", "Cancelled", 0, false)));
        assertFalse(TransactionActions.canCancel(null));
    }

    @Test
    public void whyCancelIsNotOffered_isOneSmallReason() {
        assertEquals("a fully paid, unverified booking can still be cancelled: nothing to explain", 0, TransactionActions.cancelBlockedReason(item("B", "Pending", TOTAL, false)));
        assertEquals(R.string.cancel_blocked_verified, TransactionActions.cancelBlockedReason(item("B", "Pending", 0, true)));
        assertEquals(R.string.cancel_blocked_verified, TransactionActions.cancelBlockedReason(item("R", "Confirmed", 0, false)));
        assertEquals("an unverified Confirmed booking can be cancelled, paid or not", 0, TransactionActions.cancelBlockedReason(item("B", "Confirmed", TOTAL, false)));
        assertEquals(R.string.cancel_blocked_stay_started, TransactionActions.cancelBlockedReason(item("B", "Checked-In", 0, false)));
        assertEquals(R.string.cancel_blocked_stay_started, TransactionActions.cancelBlockedReason(item("B", "Checked-Out", TOTAL, true)));
        assertEquals("nothing to explain when it can be cancelled", 0, TransactionActions.cancelBlockedReason(item("B", "Pending", 0, false)));
        assertEquals("nothing to explain once it is cancelled", 0, TransactionActions.cancelBlockedReason(item("B", "Cancelled", 0, false)));
    }

    // ---- delete

    @Test
    public void delete_table() {
        for (String kind : new String[]{"B", "R"}) {
            for (String status : new String[]{"Cancelled", "Rejected", "Completed", "Checked-Out"}) {
                assertTrue(kind + " " + status, TransactionActions.canDelete(item(kind, status, 0, false)));
            }
            for (String status : new String[]{"Pending", "Confirmed", "Checked-In"}) {
                assertFalse(kind + " " + status + " can never be deleted", TransactionActions.canDelete(item(kind, status, TOTAL, true)));
                assertFalse(kind + " " + status, TransactionActions.canDelete(item(kind, status, 0, false)));
            }
        }
        assertTrue("a converted reservation", TransactionActions.canDelete(item("H", "Completed", TOTAL, true)));
        assertFalse(TransactionActions.canDelete(null));
    }

    // ---- the action plan of a card

    @Test
    public void plan_pendingGcashReservation() {
        Booking b = item("R", "Pending", 0, false);
        b.setPaymentMethod("GCASH");
        TransactionActions.Plan p = TransactionActions.plan(b, false, false);
        assertTrue(p.payNow);
        assertTrue(p.viewDetailsOutlined);
        assertTrue(p.edit);
        assertTrue(p.cancel);
        assertFalse(p.delete);
        assertEquals(TransactionActions.InfoLine.NONE, p.info);
    }

    @Test
    public void plan_pendingCashReservation_hasPayAtFrontDeskAsAnInfoLine_andCancel() {
        Booking b = item("R", "Pending", 0, false);
        b.setPaymentMethod("CASH");
        TransactionActions.Plan p = TransactionActions.plan(b, false, false);
        assertFalse(p.payNow);
        assertTrue(p.viewDetailsOutlined && p.edit && p.cancel);
        assertEquals(TransactionActions.InfoLine.PAY_AT_FRONT_DESK, p.info);
    }

    @Test
    public void plan_pendingPartlyPaidBooking_hasOnlyViewDetailsAndCancel() {
        TransactionActions.Plan p = TransactionActions.plan(item("B", "Pending", 1500, false), true, false);
        assertTrue(p.viewDetailsOutlined && p.cancel);
        assertFalse(p.payNow || p.edit || p.delete || p.bookAgain);
    }

    @Test
    public void plan_activeBookingThatCannotBeCancelled_hasOnlyViewDetails() {
        for (Booking b : new Booking[]{item("B", "Pending", TOTAL, true), item("B", "Confirmed", 1500, true), item("B", "Confirmed", TOTAL, true), item("B", "Checked-In", 1500, false)}) {
            TransactionActions.Plan p = TransactionActions.plan(b, true, false);
            assertTrue(b.getStatus(), p.viewDetailsOutlined);
            assertFalse(b.getStatus(), p.cancel || p.edit || p.delete || p.payNow || p.bookAgain);
        }
    }

    @Test
    public void plan_closedItems_haveBookAgain_viewDetailsAsText_andDelete() {
        for (String status : new String[]{"Completed", "Cancelled", "Rejected", "Checked-Out"}) {
            TransactionActions.Plan p = TransactionActions.plan(item("B", status, 0, false), true, false);
            assertTrue(status, p.bookAgain && p.viewDetailsText && p.delete);
            assertFalse(status, p.viewDetailsOutlined || p.cancel || p.edit || p.payNow);
        }
    }

    @Test
    public void plan_convertedReservation_linksToTheBooking_andCanBeDeleted() {
        TransactionActions.Plan p = TransactionActions.plan(item("H", "Completed", TOTAL, true), false, false);
        assertTrue(p.viewConvertedBooking && p.delete);
        assertEquals(TransactionActions.InfoLine.CONVERTED, p.info);
        assertFalse(p.cancel || p.edit || p.payNow || p.viewDetailsOutlined);
    }

    // ---- status groups

    @Test
    public void everyStatusMapsToExactlyOneFilterGroup_andTransactionFailedIsCancelled() {
        for (String status : new String[]{"Pending", "Confirmed", "Checked-In", "Checked-Out", "Completed", "Cancelled", "Rejected"}) {
            int groups = 0;
            TransactionCategorizer.Category c = TransactionCategorizer.categorize(item("B", status, 0, false));
            if (c == TransactionCategorizer.Category.ACTIVE || c == TransactionCategorizer.Category.UPCOMING) groups++; // the "Active" filter
            if (c == TransactionCategorizer.Category.COMPLETED) groups++;
            if (c == TransactionCategorizer.Category.CANCELLED) groups++;
            assertEquals(status, 1, groups);
        }
        // "Transaction failed" is a cancelled booking that had a payment attempt: it is in Cancelled / rejected
        Booking failed = item("B", "Cancelled", 0, false);
        failed.setTransactionRef("123");
        assertEquals(TransactionCategorizer.Category.CANCELLED, TransactionCategorizer.categorize(failed));
    }
}
