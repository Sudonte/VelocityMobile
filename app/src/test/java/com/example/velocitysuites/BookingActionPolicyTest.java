package com.example.velocitysuites;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BookingActionPolicyTest {

    private static Booking booking(String status, boolean hasBooking, boolean direct, boolean verified) {
        Booking b = new Booking("B1", "R1", "Deluxe 101", "Deluxe",
                "May 01, 2025", "May 05, 2025", 2, 5000.0, status, "Apr 30, 2025");
        b.setHasBooking(hasBooking);
        b.setDirectBooking(direct);
        b.setStaffVerified(verified);
        b.setBillingStatus("partial");
        b.setAmountPaid(2500.0);
        return b;
    }

    @Test
    public void unverifiedDirectBooking_cancelVisible() {
        assertTrue(BookingActionPolicy.canCancel(booking("Pending", true, true, false)));
    }

    @Test
    public void verifiedDirectBooking_cancelHidden() {
        assertFalse(BookingActionPolicy.canCancel(booking("Confirmed", true, true, true)));
    }

    @Test
    public void unverifiedPendingReservation_cancelVisible() {
        assertTrue(BookingActionPolicy.canCancel(booking("Pending", false, false, false)));
    }

    @Test
    public void verifiedCashReservation_cancelHidden() {
        assertFalse(BookingActionPolicy.canCancel(booking("Pending", false, false, true)));
    }

    @Test
    public void reservationConvertedToBooking_cancelHidden_evenWhenUnverified() {
        // GCash Pay Later converted by the receptionist, or a Cash one converted.
        assertFalse(BookingActionPolicy.canCancel(booking("Confirmed", true, false, false)));
    }

    @Test
    public void closedStatuses_cancelHidden() {
        for (String status : new String[]{"Cancelled", "Rejected", "Checked-In", "Checked-Out", "Completed"}) {
            assertFalse(status, BookingActionPolicy.canCancel(booking(status, false, false, false)));
        }
    }

    @Test
    public void fullyPaidButUnverifiedBooking_stillCancellable_untilTheFrontDeskVerifiesIt() {
        Booking b = booking("Pending", true, true, false);
        b.setBillingStatus("paid");
        assertTrue(BookingActionPolicy.canCancel(b));
        b.setStaffVerified(true);
        assertFalse(BookingActionPolicy.canCancel(b));
    }

    @Test
    public void missingStatus_unverifiedReservationStaysCancellable() {
        Booking b = booking("", false, false, false);
        b.setBillingStatus(null);
        assertTrue(BookingActionPolicy.canCancel(b));
    }

    @Test
    public void nullBooking_safeDefaultIsNotCancellable() {
        assertFalse(BookingActionPolicy.canCancel(null));
    }
}
