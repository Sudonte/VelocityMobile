package com.example.velocitysuites;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM coverage for the notification receipt-navigation contract
 * NotificationDetailsActivity#bindReceiptAction() relies on:
 * Notification#hasStructuredReceipt() (the priority gate - a non-null,
 * non-empty receipt_number always wins and must open PaymentReceiptActivity
 * by that EXACT receipt_number, never the legacy Booking-snapshot receipt)
 * and ReceiptTypeMapper's label mapping. Doesn't instantiate
 * NotificationDetailsActivity itself or assert on the actual Intent built -
 * this project has no Robolectric, so Activity/Intent construction isn't
 * unit-testable here (see ReceiptDetailMappingTest's identical doc) - but
 * every decision that method makes is provably covered at this layer.
 */
public class NotificationReceiptNavigationTest {

    private static Notification withReceipt(String receiptNumber, String receiptType) {
        return new Notification("1", "Payment Verified", "msg", "now", Notification.TYPE_PAYMENT, false,
                "42", null, null, receiptNumber, receiptType);
    }

    @Test
    public void partialReceiptNotification_hasStructuredReceiptAndCorrectLabel() {
        Notification n = withReceipt("PR-20260925-000501", "PARTIAL_RECEIPT");

        assertTrue(n.hasStructuredReceipt());
        assertEquals("PR-20260925-000501", n.getReceiptNumber());
        assertEquals("Partial Payment Receipt", ReceiptTypeMapper.labelForReceiptType(n.getReceiptType()));
    }

    @Test
    public void fullPaymentReceiptNotification_hasStructuredReceiptAndCorrectLabel() {
        Notification n = withReceipt("FR-20260925-000502", "FULL_PAYMENT_RECEIPT");

        assertTrue(n.hasStructuredReceipt());
        assertEquals("FR-20260925-000502", n.getReceiptNumber());
        assertEquals("Payment Receipt", ReceiptTypeMapper.labelForReceiptType(n.getReceiptType()));
    }

    @Test
    public void officialReceiptNotification_hasStructuredReceiptAndCorrectLabel() {
        Notification n = withReceipt("OR-20260925-000044", "OFFICIAL_RECEIPT");

        assertTrue(n.hasStructuredReceipt());
        assertEquals("OR-20260925-000044", n.getReceiptNumber());
        assertEquals("Official Payment Receipt", ReceiptTypeMapper.labelForReceiptType(n.getReceiptType()));
    }

    @Test
    public void legacyNotification_withNullReceiptNumber_fallsBackToLegacyNavigation() {
        Notification n = withReceipt(null, null);

        assertFalse(n.hasStructuredReceipt());
    }

    @Test
    public void legacyNotification_withBlankReceiptNumber_fallsBackToLegacyNavigation() {
        Notification n = withReceipt("   ", null);

        assertFalse(n.hasStructuredReceipt());
    }

    @Test
    public void unknownReceiptTypeWithValidReceiptNumber_stillNavigatesByNumber() {
        // Never blocks navigation on an unrecognized/future receipt_type -
        // only the receipt_number's presence gates whether to navigate
        // structurally; the label just falls back to the generic wording.
        Notification n = withReceipt("XX-20260925-000001", "SOME_FUTURE_TYPE");

        assertTrue(n.hasStructuredReceipt());
        assertEquals("XX-20260925-000001", n.getReceiptNumber());
        assertEquals("Payment Receipt", ReceiptTypeMapper.labelForReceiptType(n.getReceiptType()));
    }

    @Test
    public void oldNineArgConstructor_stillProducesNoStructuredReceipt() {
        // The pre-Phase-5 constructor overload every existing call site not
        // yet touched by this change still compiles against and uses -
        // must keep defaulting both new fields to null.
        Notification n = new Notification("2", "Booking Confirmed", "msg", "now",
                Notification.TYPE_BOOKING, false, "42", null, null);

        assertFalse(n.hasStructuredReceipt());
        assertEquals(null, n.getReceiptType());
    }
}
