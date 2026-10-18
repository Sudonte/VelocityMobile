package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BillingAmountsTest {

    @Test
    public void beforeAnyPayment_theBlockShowsTheGrandTotal() {
        BillingAmounts a = BillingAmounts.compute(3500, 0, null);
        assertFalse(a.paymentMade);
        assertEquals(3500.0, a.amountToPay, 0.001);
        assertEquals(3500.0, a.remaining, 0.001);
        assertEquals(0.0, a.alreadyPaid, 0.001);
    }

    @Test
    public void afterAPartialPayment_theBlockShowsTheRemainingBalance() {
        BillingAmounts a = BillingAmounts.compute(10000, 3000, null);
        assertTrue(a.paymentMade);
        assertEquals(3000.0, a.alreadyPaid, 0.001);
        assertEquals(7000.0, a.remaining, 0.001);
        assertEquals(7000.0, a.amountToPay, 0.001);
    }

    @Test
    public void aPaymentWaitingForVerification_countsAsPaid() {
        BillingAmounts a = BillingAmounts.compute(8650, 0, 1730.0);
        assertTrue(a.paymentMade);
        assertEquals(1730.0, a.alreadyPaid, 0.001);
        assertEquals(6920.0, a.remaining, 0.001);
    }

    @Test
    public void fullPayment_leavesNothing() {
        BillingAmounts a = BillingAmounts.compute(4000, 4000, null);
        assertTrue(a.paymentMade);
        assertEquals(0.0, a.remaining, 0.001);
        assertEquals(0.0, a.amountToPay, 0.001);
    }

    @Test
    public void badNumbersBecomeZero_neverNaN() {
        BillingAmounts a = BillingAmounts.compute(Double.NaN, Double.POSITIVE_INFINITY, Double.NaN);
        assertEquals(0.0, a.grandTotal, 0.001);
        assertEquals(0.0, a.amountToPay, 0.001);
        assertFalse(a.paymentMade);
    }

    @Test
    public void paidIsNeverMoreThanTheTotal() {
        BillingAmounts a = BillingAmounts.compute(1000, 5000, null);
        assertEquals(1000.0, a.alreadyPaid, 0.001);
        assertEquals(0.0, a.remaining, 0.001);
    }
}
