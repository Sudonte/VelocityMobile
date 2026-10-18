package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BillingConfirmGateTest {

    @Test
    public void confirmNeedsTheTermsReadAndTheBoxTicked() {
        assertFalse(BillingSummaryActivity.canConfirm(false, false));
        assertFalse(BillingSummaryActivity.canConfirm(false, true));
        assertFalse(BillingSummaryActivity.canConfirm(true, false));
        assertTrue(BillingSummaryActivity.canConfirm(true, true));
    }

    @Test
    public void theHintSaysWhatIsMissing_andDisappearsWhenEnabled() {
        assertEquals(R.string.billing_confirm_hint_read, BillingSummaryActivity.confirmHintRes(false, false));
        assertEquals(R.string.billing_confirm_hint_tick, BillingSummaryActivity.confirmHintRes(true, false));
        assertEquals(0, BillingSummaryActivity.confirmHintRes(true, true));
    }
}
