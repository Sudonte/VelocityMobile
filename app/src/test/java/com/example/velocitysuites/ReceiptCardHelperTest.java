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
}
