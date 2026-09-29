package com.example.velocitysuites;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Direct regression guard for the bug this rework fixes: adding
 * Notification.TYPE_RESERVATION without also updating
 * NotificationDetailsActivity#bindPrimaryAction()'s gating would have left a
 * Reservation-category notification with a resolvable related record and
 * still no "View Details" button - canViewBookingDetails/canViewTransaction
 * would both stay false despite hasRelatedBooking being true. These tests
 * pin the intended behavior at the unit level, pure logic, no Context needed.
 */
public class NotificationPrimaryActionResolverTest {

    @Test
    public void canViewBookingDetails_reservationType_relatedBookingResolved_returnsTrue() {
        assertTrue(NotificationPrimaryActionResolver.canViewBookingDetails(Notification.TYPE_RESERVATION, true));
    }

    @Test
    public void canViewBookingDetails_reservationType_relatedBookingUnresolved_returnsFalse() {
        assertFalse(NotificationPrimaryActionResolver.canViewBookingDetails(Notification.TYPE_RESERVATION, false));
    }

    @Test
    public void canViewBookingDetails_bookingType_relatedBookingResolved_returnsTrue() {
        assertTrue(NotificationPrimaryActionResolver.canViewBookingDetails(Notification.TYPE_BOOKING, true));
    }

    @Test
    public void canViewBookingDetails_paymentType_returnsFalse() {
        // Payment notifications fall through to canViewTransaction()/the Transaction
        // History filter instead - they have no richer "details" screen of their own.
        assertFalse(NotificationPrimaryActionResolver.canViewBookingDetails(Notification.TYPE_PAYMENT, true));
    }

    @Test
    public void canViewTransaction_reservationType_withReferenceId_returnsTrue() {
        assertTrue(NotificationPrimaryActionResolver.canViewTransaction(Notification.TYPE_RESERVATION, "42"));
    }

    @Test
    public void canViewTransaction_nullReferenceId_returnsFalse() {
        assertFalse(NotificationPrimaryActionResolver.canViewTransaction(Notification.TYPE_RESERVATION, null));
    }

    @Test
    public void canViewTransaction_promotionType_returnsFalse() {
        // A promotion notification has no linked booking/reservation/payment to open.
        assertFalse(NotificationPrimaryActionResolver.canViewTransaction(Notification.TYPE_PROMOTION, "42"));
    }
}
