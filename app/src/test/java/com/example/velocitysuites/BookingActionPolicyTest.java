package com.example.velocitysuites;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BookingActionPolicyTest {

    private static Booking partialBooking(boolean verified) {
        Booking b = new Booking("B1", "R1", "Deluxe 101", "Deluxe",
                "May 01, 2025", "May 05, 2025", 2, 5000.0, "Pending", "Apr 30, 2025");
        b.setHasBooking(true);
        b.setBillingStatus("partial");
        b.setAmountPaid(2500.0);
        b.setStaffVerified(verified);
        return b;
    }

    @Test
    public void unverifiedPartialBooking_cancelVisible() {
        assertTrue(BookingActionPolicy.canCancel(partialBooking(false)));
    }

    @Test
    public void verifiedPartialBooking_cancelHidden() {
        assertFalse(BookingActionPolicy.canCancel(partialBooking(true)));
    }

    @Test
    public void fullyPaidBooking_cancelHidden() {
        Booking b = partialBooking(false);
        b.setBillingStatus("paid");
        assertFalse(BookingActionPolicy.canCancel(b));
    }

    @Test
    public void verifiedReservationNotYetConverted_unchanged() {
        Booking b = partialBooking(true);
        b.setHasBooking(false);
        assertTrue(BookingActionPolicy.canCancel(b));
    }

    @Test
    public void missingStatusAndBillingStatus_unverifiedStaysCancellable() {
        Booking b = new Booking("B2", "R1", "Deluxe 101", "Deluxe",
                "May 01, 2025", "May 05, 2025", 1, 3000.0, "", "Apr 30, 2025");
        b.setHasBooking(true);
        b.setBillingStatus(null);
        assertTrue(BookingActionPolicy.canCancel(b));
    }

    @Test
    public void nullBooking_safeDefaultIsNotCancellable() {
        assertFalse(BookingActionPolicy.canCancel(null));
    }
}
