package com.example.velocitysuites;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Pure-JVM coverage for NotificationStatusResolver#resolveKey() - the
 * Context-free decision resolve() delegates to (this project has no
 * Robolectric, so resolve() itself, which touches Context.getString(),
 * can't be unit tested directly - see PaymentStatusResolverTest for the
 * identical pattern elsewhere in this codebase).
 */
public class NotificationStatusResolverTest {

    @Test
    public void bookingPending_resolvesToPending() {
        // New title introduced by the Booking-vs-Reservation category split
        // (NotificationService::notifyNewDirectBooking()) - must still resolve a
        // pending pill exactly like the pre-existing "Reservation Pending" did.
        assertEquals(NotificationStatusResolver.StatusKey.PENDING,
                NotificationStatusResolver.resolveKey("Booking Pending"));
    }

    @Test
    public void bookingCancelledNoShow_resolvesToCancelled() {
        // New title introduced by NotificationService::notifyBookingNoShow().
        assertEquals(NotificationStatusResolver.StatusKey.CANCELLED,
                NotificationStatusResolver.resolveKey("Booking Cancelled - No Show"));
    }

    @Test
    public void reservationModified_resolvesToNoStatus() {
        // New title introduced by NotificationService::notifyReservationModified() -
        // contains none of the recognized keywords, so no status pill should show
        // (an edit isn't itself a Pending/Confirmed/Cancelled outcome).
        assertNull(NotificationStatusResolver.resolveKey("Reservation Modified"));
    }

    @Test
    public void reservationConfirmed_resolvesToConfirmed() {
        assertEquals(NotificationStatusResolver.StatusKey.CONFIRMED,
                NotificationStatusResolver.resolveKey("Reservation Confirmed"));
    }

    @Test
    public void nullTitle_resolvesToNull() {
        assertNull(NotificationStatusResolver.resolveKey(null));
    }
}
