package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BillingAmountsTest {

    @Test
    public void partialPayment_usesTheStoredAmount() {
        BillingAmounts a = BillingAmounts.compute(10000, 0, 30.0, 3000.0);
        assertEquals(BillingAmounts.Option.PARTIAL, a.option);
        assertEquals(3000.0, a.amountToPay, 0.001);
        assertEquals(30, a.percentage);
        assertEquals(10000.0, a.remaining, 0.001);
    }

    @Test
    public void partialPayment_withNoStoredAmount_isComputedFromThePercentage() {
        BillingAmounts a = BillingAmounts.compute(8650, 0, 20.0, null);
        assertEquals(BillingAmounts.Option.PARTIAL, a.option);
        assertEquals(1730.0, a.amountToPay, 0.001);
    }

    @Test
    public void fullPayment_isTheWholeRemainingTotal() {
        BillingAmounts a = BillingAmounts.compute(8650, 0, 100.0, null);
        assertEquals(BillingAmounts.Option.FULL, a.option);
        assertEquals(8650.0, a.amountToPay, 0.001);
        assertEquals(100, a.percentage);
    }

    @Test
    public void nothingRecorded_fallsBackToTheBalance_neverNull() {
        BillingAmounts a = BillingAmounts.compute(5000, 0, null, null);
        assertEquals(BillingAmounts.Option.BALANCE, a.option);
        assertEquals(5000.0, a.amountToPay, 0.001);
    }

    @Test
    public void afterAPayment_theBalanceIsWhatIsLeft() {
        BillingAmounts a = BillingAmounts.compute(10000, 3000, 30.0, 3000.0);
        assertEquals(BillingAmounts.Option.BALANCE, a.option);
        assertEquals(7000.0, a.amountToPay, 0.001);
        assertEquals(7000.0, a.remaining, 0.001);
    }

    @Test
    public void legitimateZero_staysZero_andBadNumbersAreZero() {
        BillingAmounts paid = BillingAmounts.compute(4000, 4000, null, null);
        assertEquals(0.0, paid.amountToPay, 0.001);
        BillingAmounts bad = BillingAmounts.compute(Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, -5.0);
        assertEquals(0.0, bad.grandTotal, 0.001);
        assertEquals(0.0, bad.amountToPay, 0.001);
    }

    @Test
    public void storedAmountAboveTheBalance_isCapped() {
        BillingAmounts a = BillingAmounts.compute(1000, 0, 50.0, 9999.0);
        assertEquals(1000.0, a.amountToPay, 0.001);
        assertEquals(BillingAmounts.Option.FULL, a.option);
    }
}
