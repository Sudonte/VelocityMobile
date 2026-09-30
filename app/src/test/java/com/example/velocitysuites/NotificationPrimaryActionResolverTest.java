package com.example.velocitysuites;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Direct regression guard for the bug this rework fixes: adding
 * Notification.TYPE_RESERVATION without also updating
 * NotificationDetailsActivity#bindPrimaryAction()'s gating would have left a
 * Reservation-category notification with a resolvable related record and
 * still no "View Transaction Details" button - canViewTransaction would stay
 * false despite a reference id being present. These tests pin the intended
 * behavior at the unit level, pure logic, no Context needed.
 */
public class NotificationPrimaryActionResolverTest {

    @Test
    public void canViewTransaction_reservationType_withReferenceId_returnsTrue() {
        assertTrue(NotificationPrimaryActionResolver.canViewTransaction(Notification.TYPE_RESERVATION, "42"));
    }

    @Test
    public void canViewTransaction_bookingType_withReferenceId_returnsTrue() {
        assertTrue(NotificationPrimaryActionResolver.canViewTransaction(Notification.TYPE_BOOKING, "42"));
    }

    @Test
    public void canViewTransaction_paymentType_withReferenceId_returnsTrue() {
        assertTrue(NotificationPrimaryActionResolver.canViewTransaction(Notification.TYPE_PAYMENT, "42"));
    }

    @Test
    public void canViewTransaction_checkInType_withReferenceId_returnsTrue() {
        assertTrue(NotificationPrimaryActionResolver.canViewTransaction(Notification.TYPE_CHECK_IN, "42"));
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

    @Test
    public void canViewTransaction_systemType_returnsFalse() {
        assertFalse(NotificationPrimaryActionResolver.canViewTransaction(Notification.TYPE_SYSTEM, "42"));
    }

    @Test
    public void transactionHistoryFilterFor_reservationType_returnsReservationsFilter() {
        assertEquals(TransactionHistoryActivity.FILTER_RESERVATIONS,
                NotificationPrimaryActionResolver.transactionHistoryFilterFor(Notification.TYPE_RESERVATION));
    }

    @Test
    public void transactionHistoryFilterFor_paymentType_returnsPaymentsFilter() {
        assertEquals(TransactionHistoryActivity.FILTER_PAYMENTS,
                NotificationPrimaryActionResolver.transactionHistoryFilterFor(Notification.TYPE_PAYMENT));
    }

    @Test
    public void transactionHistoryFilterFor_bookingType_returnsBookingsFilter() {
        assertEquals(TransactionHistoryActivity.FILTER_BOOKINGS,
                NotificationPrimaryActionResolver.transactionHistoryFilterFor(Notification.TYPE_BOOKING));
    }

    @Test
    public void transactionHistoryFilterFor_checkInType_returnsBookingsFilter() {
        // Check-in only ever happens on a real Booking - a Reservation must convert first.
        assertEquals(TransactionHistoryActivity.FILTER_BOOKINGS,
                NotificationPrimaryActionResolver.transactionHistoryFilterFor(Notification.TYPE_CHECK_IN));
    }
}
