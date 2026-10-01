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

    @Test
    public void paymentPendingValidation_isPending() {
        assertEquals(NotificationStatusResolver.StatusKey.PENDING,
                NotificationStatusResolver.resolveKey("Payment Pending Validation"));
    }

    @Test
    public void paymentVerified_withoutReceiptInfo_isVerified() {
        // "Payment Verified" used to resolve to no status at all (no pill, the category's green check).
        assertEquals(NotificationStatusResolver.StatusKey.VERIFIED,
                NotificationStatusResolver.resolveKey("Payment Verified"));
    }

    @Test
    public void paymentVerified_withPartialReceipt_isPartiallyPaid() {
        assertEquals(NotificationStatusResolver.StatusKey.PARTIALLY_PAID,
                NotificationStatusResolver.resolveKey("Payment Verified", "PARTIAL_RECEIPT"));
    }

    @Test
    public void paymentVerified_withFullOrOfficialReceipt_isPaid() {
        assertEquals(NotificationStatusResolver.StatusKey.FULLY_PAID,
                NotificationStatusResolver.resolveKey("Payment Verified", "FULL_PAYMENT_RECEIPT"));
        assertEquals(NotificationStatusResolver.StatusKey.FULLY_PAID,
                NotificationStatusResolver.resolveKey("Checked Out", "OFFICIAL_RECEIPT"));
    }

    @Test
    public void paymentRejected_isRejected_evenIfAReceiptTypeIsPresent() {
        assertEquals(NotificationStatusResolver.StatusKey.REJECTED,
                NotificationStatusResolver.resolveKey("Payment Rejected", "PARTIAL_RECEIPT"));
    }

    @Test
    public void pendingVerificationTitle_staysPending_notVerified() {
        // "pending" must win over "verif..." words in the same title.
        assertEquals(NotificationStatusResolver.StatusKey.PENDING,
                NotificationStatusResolver.resolveKey("Payment Pending Verification"));
    }

    @Test
    public void americanSpellingCanceled_isCancelled() {
        assertEquals(NotificationStatusResolver.StatusKey.CANCELLED,
                NotificationStatusResolver.resolveKey("Reservation Canceled"));
    }

    @Test
    public void aReceiptTypeAloneDecidesWhenTheTitleSaysNothing() {
        assertEquals(NotificationStatusResolver.StatusKey.PARTIALLY_PAID,
                NotificationStatusResolver.resolveKey(null, "PARTIAL_RECEIPT"));
    }
}
