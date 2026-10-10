package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.velocitysuites.PaymentBillingCheck.Bill;
import com.example.velocitysuites.PaymentBillingCheck.SubmitVerdict;

import org.junit.Test;

public class PaymentBillingCheckTest {

    private static final Bill SHOWN = new Bill(5000, 1000); // owes 4000

    @Test
    public void unchangedBillIsSilentAndSubmitProceeds() {
        Bill same = new Bill(5000, 1000);
        assertFalse(PaymentBillingCheck.billChanged(SHOWN, same));
        assertEquals(SubmitVerdict.PROCEED, PaymentBillingCheck.beforeSubmit(SHOWN, same, 4000));
    }

    @Test
    public void centavoNoiseIsNotAChange() {
        assertFalse(PaymentBillingCheck.billChanged(SHOWN, new Bill(5000.001, 1000)));
    }

    @Test
    public void changedTotalOrPaidIsAChange() {
        assertTrue(PaymentBillingCheck.billChanged(SHOWN, new Bill(4500, 1000))); // discount applied
        assertTrue(PaymentBillingCheck.billChanged(SHOWN, new Bill(5000, 2000))); // another payment verified
    }

    @Test
    public void failedSilentCheckSaysNothing() {
        assertFalse(PaymentBillingCheck.billChanged(SHOWN, null));
    }

    @Test
    public void failedFetchBlocksSubmit() {
        assertEquals(SubmitVerdict.CHECK_FAILED, PaymentBillingCheck.beforeSubmit(SHOWN, null, 4000));
    }

    @Test
    public void changedBillStopsSubmitEvenIfTheAmountStillFits() {
        assertEquals(SubmitVerdict.BILL_CHANGED, PaymentBillingCheck.beforeSubmit(SHOWN, new Bill(4500, 1000), 3000));
    }

    @Test
    public void typedAmountOverBalanceIsRejectedOnAnUnchangedBill() {
        assertEquals(SubmitVerdict.OVER_BALANCE, PaymentBillingCheck.beforeSubmit(SHOWN, new Bill(5000, 1000), 4000.01));
    }

    @Test
    public void amountEqualToBalanceIsNotOver() {
        assertEquals(SubmitVerdict.PROCEED, PaymentBillingCheck.beforeSubmit(SHOWN, new Bill(5000, 1000), 4000.00));
    }

    @Test
    public void paidInFullLeavesNothingToPay() {
        Bill settled = new Bill(5000, 5000);
        assertEquals(0, settled.balanceDue(), 0);
        assertEquals(SubmitVerdict.OVER_BALANCE, PaymentBillingCheck.beforeSubmit(settled, settled, 1));
        assertEquals(0, new Bill(5000, 6000).balanceDue(), 0); // never negative
    }

    @Test
    public void confirmingTheNewBillThenSubmittingProceeds() {
        Bill latest = new Bill(4500, 1000); // owes 3500
        assertEquals(SubmitVerdict.BILL_CHANGED, PaymentBillingCheck.beforeSubmit(SHOWN, latest, 3500));
        // The guest confirms: the new bill becomes the one they have seen, and the amount follows it.
        Bill confirmed = latest;
        assertEquals(SubmitVerdict.PROCEED, PaymentBillingCheck.beforeSubmit(confirmed, latest, 3500));
    }

    @Test
    public void confirmedBillThatChangesAgainIsCaughtAgain() {
        Bill confirmed = new Bill(4500, 1000);
        assertEquals(SubmitVerdict.BILL_CHANGED, PaymentBillingCheck.beforeSubmit(confirmed, new Bill(4500, 2000), 3500));
    }
}
