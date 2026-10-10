package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.velocitysuites.PaymentRules.Range;
import com.example.velocitysuites.PaymentRules.Verdict;

import org.junit.Test;

/** The same examples as the server's GuestPayRemainingBalanceTest: a P2,000 reservation (20% = 400, 50% = 1,000). */
public class PaymentRulesTest {

    private static Range afterPaying(double paid) {
        return PaymentRules.of(2000, paid);
    }

    @Test
    public void fullPaymentOfTheRemainingBalanceIsAcceptedAfterADeposit() {
        assertEquals(Verdict.OK, PaymentRules.check(afterPaying(600), true, 1400));
    }

    @Test
    public void fullPaymentOfTheOriginalTotalIsRefusedOnceSomethingIsPaid() {
        assertEquals(Verdict.FULL_MUST_EQUAL_BALANCE, PaymentRules.check(afterPaying(600), true, 2000));
    }

    @Test
    public void firstPaymentIsUnchanged() {
        Range fresh = afterPaying(0);
        assertEquals(Verdict.FULL_MUST_EQUAL_BALANCE, PaymentRules.check(fresh, true, 1999));
        assertEquals(Verdict.OK, PaymentRules.check(fresh, true, 2000));
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(fresh, false, 399));
        assertEquals(Verdict.OK, PaymentRules.check(fresh, false, 400));
        assertEquals(Verdict.OK, PaymentRules.check(fresh, false, 1000));
    }

    @Test
    public void aSecondPartialPaymentInsideTheRangeIsAccepted() {
        assertEquals(Verdict.OK, PaymentRules.check(afterPaying(600), false, 500));
    }

    @Test
    public void aPartialPaymentIsCappedByTheRemainingBalanceNotOnlyByFiftyPercent() {
        Range range = afterPaying(1200); // 800 left; 50% of the total (1,000) would overshoot
        assertEquals(800, range.max, 0);
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(range, false, 900));
        assertEquals(Verdict.OK, PaymentRules.check(range, false, 800));
    }

    @Test
    public void aPartialPaymentOutsideTwentyToFiftyPercentOfTheTotalIsRefused() {
        Range range = afterPaying(300); // 1,700 left
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(range, false, 399.99));
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(range, false, 1000.01));
        assertEquals(Verdict.OK, PaymentRules.check(range, false, 400));
    }

    @Test
    public void belowTheMinimumOnlyAFullPaymentOfTheBalanceIsAllowed() {
        Range range = afterPaying(1700); // 300 left, minimum is 400
        assertFalse(range.canPartial());
        assertEquals(Verdict.FULL_REQUIRED, PaymentRules.check(range, false, 300));
        assertEquals(Verdict.OK, PaymentRules.check(range, true, 300));
    }

    @Test
    public void nothingCanBePaidOnceTheBalanceIsZero() {
        Range range = afterPaying(2000);
        assertTrue(range.isSettled());
        assertFalse(range.canPartial());
        assertEquals(Verdict.SETTLED, PaymentRules.check(range, true, 2000));
        assertEquals(Verdict.SETTLED, PaymentRules.check(range, false, 500));
        assertEquals(Verdict.SETTLED, PaymentRules.check(afterPaying(2500), true, 100)); // overpaid is never negative
    }

    @Test
    public void percentageOptionsAreAShareOfTheOriginalTotalCappedAtTheBalance() {
        Range range = afterPaying(1200); // 800 left
        assertEquals(400, PaymentRules.partialAmount(range, 0.20), 0);
        assertEquals(600, PaymentRules.partialAmount(range, 0.30), 0);
        assertEquals(800, PaymentRules.partialAmount(range, 0.50), 0); // 1,000 capped to what is left
        // and when it is possible at all, every option the app offers is accepted by the server
        for (double fraction : new double[]{0.20, 0.30, 0.40, 0.50}) {
            assertEquals(Verdict.OK, PaymentRules.check(range, false, PaymentRules.partialAmount(range, fraction)));
        }
    }

    @Test
    public void whenTheMinimumDoesNotFitNoPercentageOptionIsAccepted() {
        Range range = afterPaying(1700);
        for (double fraction : new double[]{0.20, 0.30, 0.40, 0.50}) {
            assertEquals(Verdict.FULL_REQUIRED, PaymentRules.check(range, false, PaymentRules.partialAmount(range, fraction)));
        }
    }

    @Test
    public void onlyTheAmountPaidSoFarMattersNotHowItWasPaid() {
        // the caller passes verified payments only; a pending 600 is simply not "paid"
        assertEquals(2000, afterPaying(0).remaining, 0);
        assertEquals(1400, afterPaying(600).remaining, 0);
    }
}
