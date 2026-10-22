package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.velocitysuites.PaymentRules.Range;
import com.example.velocitysuites.PaymentRules.Verdict;

import org.junit.Test;

/**
 * The same examples as the server's GuestPayRemainingBalanceTest: a P2,000 reservation (20% = 400, 50% = 1,000). Online payment is a
 * partial payment only: never the whole bill, never a payment that pays off everything still owed.
 */
public class PaymentRulesTest {

    private static Range afterPaying(double paid) {
        return PaymentRules.of(2000, paid);
    }

    @Test
    public void aFullPaymentIsNeverAcceptedOnline() {
        assertEquals(Verdict.PARTIAL_ONLY, PaymentRules.check(afterPaying(600), true, 1400));
        assertEquals(Verdict.PARTIAL_ONLY, PaymentRules.check(afterPaying(600), true, 2000));
        assertEquals(Verdict.PARTIAL_ONLY, PaymentRules.check(afterPaying(0), true, 2000));
    }

    @Test
    public void firstPaymentIsAPartialOne_twentyToFiftyPercent() {
        Range fresh = afterPaying(0);
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(fresh, false, 399));
        assertEquals(Verdict.OK, PaymentRules.check(fresh, false, 400));
        assertEquals(Verdict.OK, PaymentRules.check(fresh, false, 1000));
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(fresh, false, 2000)); // the whole total is not a down payment
    }

    @Test
    public void aSecondPartialPaymentInsideTheRangeIsAccepted() {
        assertEquals(Verdict.OK, PaymentRules.check(afterPaying(600), false, 500));
    }

    @Test
    public void aPartialPaymentThatWouldPayOffTheBalanceIsNotAvailable() {
        Range range = afterPaying(1200); // 800 left: paying it all would settle the bill online
        assertEquals(0, range.max, 0);
        assertFalse(range.canPartial());
        assertEquals(Verdict.PAY_AT_FRONT_DESK, PaymentRules.check(range, false, 800));
        assertEquals(Verdict.PAY_AT_FRONT_DESK, PaymentRules.check(range, false, 500));
    }

    @Test
    public void whenWhatIsLeftEqualsTheFiftyPercentMaximumItIsPaidAtTheFrontDesk() {
        Range range = afterPaying(1000);
        assertFalse(range.canPartial());
        assertEquals(Verdict.PAY_AT_FRONT_DESK, PaymentRules.check(range, false, 1000));
    }

    @Test
    public void aPartialPaymentOutsideTwentyToFiftyPercentOfTheTotalIsRefused() {
        Range range = afterPaying(300); // 1,700 left
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(range, false, 399.99));
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(range, false, 1000.01));
        assertEquals(Verdict.OK, PaymentRules.check(range, false, 400));
    }

    @Test
    public void belowTheMinimumTheRestIsPaidAtTheFrontDesk() {
        Range range = afterPaying(1700); // 300 left, minimum is 400
        assertFalse(range.canPartial());
        assertEquals(Verdict.PAY_AT_FRONT_DESK, PaymentRules.check(range, false, 300));
        assertEquals(Verdict.PARTIAL_ONLY, PaymentRules.check(range, true, 300));
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
    public void percentageOptionsAreAShareOfTheOriginalTotalAndAreAcceptedWhenPossible() {
        Range range = afterPaying(600); // 1,400 left
        assertEquals(400, PaymentRules.partialAmount(range, 0.20), 0);
        assertEquals(600, PaymentRules.partialAmount(range, 0.30), 0);
        assertEquals(1000, PaymentRules.partialAmount(range, 0.50), 0);
        // when a down payment is possible at all, every option the app offers is accepted by the server
        for (double fraction : new double[]{0.20, 0.30, 0.40, 0.50}) {
            assertEquals(Verdict.OK, PaymentRules.check(range, false, PaymentRules.partialAmount(range, fraction)));
        }
    }

    @Test
    public void whenNoDownPaymentFitsNoPercentageOptionIsAccepted() {
        Range range = afterPaying(1700);
        for (double fraction : new double[]{0.20, 0.30, 0.40, 0.50}) {
            assertEquals(Verdict.PAY_AT_FRONT_DESK, PaymentRules.check(range, false, PaymentRules.partialAmount(range, fraction)));
        }
    }

    @Test
    public void onlyTheAmountPaidSoFarMattersNotHowItWasPaid() {
        // the caller passes verified payments only; a pending 600 is simply not "paid"
        assertEquals(2000, afterPaying(0).remaining, 0);
        assertEquals(1400, afterPaying(600).remaining, 0);
    }

    // ---- a discount waiting for the receptionist's ID check: deposits only ----

    @Test
    public void whileTheDiscountIsPending_aFullPaymentIsRefused_likeEverywhere() {
        Range range = PaymentRules.of(2000, 600, true);
        assertEquals(Verdict.PARTIAL_ONLY, PaymentRules.check(range, true, 1400));
        assertEquals(Verdict.PARTIAL_ONLY, PaymentRules.check(PaymentRules.of(2000, 0, true), true, 2000));
    }

    @Test
    public void whileTheDiscountIsPending_depositsKeepTheNormalRule() {
        Range range = PaymentRules.of(2000, 600, true); // 1,400 left
        assertEquals(Verdict.OK, PaymentRules.check(range, false, 500));
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(range, false, 1100));
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(range, false, 399));
    }

    @Test
    public void whileTheDiscountIsPending_withTooLittleLeftForADeposit_paymentIsOnHold() {
        Range range = PaymentRules.of(2000, 1700, true); // 300 left, minimum 400
        assertEquals(Verdict.DISCOUNT_ON_HOLD, PaymentRules.check(range, false, 300));
        assertEquals(Verdict.PARTIAL_ONLY, PaymentRules.check(range, true, 300));
    }

    @Test
    public void onceApproved_theServersTotalIncludesTheDiscount_andDepositsFollowTheDiscountedTotal() {
        // the server reports total_amount_due 1,600 (2,000 less the 20% Senior discount) - nothing is calculated here
        Range fresh = PaymentRules.of(1600, 0, false);
        assertEquals(Verdict.PARTIAL_ONLY, PaymentRules.check(fresh, true, 1600));
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(fresh, false, 319.99));
        assertEquals(Verdict.OK, PaymentRules.check(fresh, false, 320));
        assertEquals(Verdict.OK, PaymentRules.check(fresh, false, 800));
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(fresh, false, 801));
        Range afterDeposit = PaymentRules.of(1600, 600, false); // 1,000 left
        assertEquals(Verdict.OK, PaymentRules.check(afterDeposit, false, 500));
    }

    @Test
    public void whenRejectedOrNotRequested_theNormalRulesApply() {
        assertEquals(Verdict.PARTIAL_ONLY, PaymentRules.check(PaymentRules.of(2000, 0, false), true, 2000));
        assertEquals(Verdict.OK, PaymentRules.check(PaymentRules.of(2000, 600, false), false, 500));
    }

    @Test
    public void aSettledBillStaysSettledWhateverTheDiscountSays() {
        assertEquals(Verdict.SETTLED, PaymentRules.check(PaymentRules.of(2000, 2000, true), false, 500));
    }

    // ---- the deposit cap while a discount is pending (same examples as the server's DepositCapWhilePendingDiscountTest) ----

    /** A P2,000 bill with the 20% Senior discount pending: the server reports deposit_cap = min(1,000, 1,600) = 1,000. */
    private static Range pending(double paid, double cap) {
        return PaymentRules.of(2000, paid, true, cap);
    }

    @Test
    public void depositsThatWouldAddUpPastTheDiscountedTotalAreStopped() {
        // 1,000 + 500 + 300 on a 2,000 bill that becomes 1,600: after the first 1,000 (the cap) nothing more online
        assertEquals(Verdict.OK, PaymentRules.check(pending(0, 1000), false, 1000));
        Range afterFirst = pending(1000, 1000);
        assertEquals(Verdict.MAX_DEPOSIT_REACHED, PaymentRules.check(afterFirst, false, 500));
        assertEquals(Verdict.MAX_DEPOSIT_REACHED, PaymentRules.check(afterFirst, false, 300));
        assertEquals(Verdict.MAX_DEPOSIT_REACHED, PaymentRules.check(afterFirst, true, 1000));
        assertTrue(afterFirst.capReached());
    }

    @Test
    public void aSecondDepositCanUseWhatIsLeftUnderTheCapButNotMore() {
        Range range = pending(600, 1000); // 400 of the cap left
        assertEquals(400, range.max, 0);
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(range, false, 401));
        assertEquals(Verdict.OK, PaymentRules.check(range, false, 400));
        assertFalse(range.capReached());
    }

    @Test
    public void aLargeDiscountLowersTheCapBelowFiftyPercent() {
        // 60% discount: 2,000 -> 800, cap 800
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(pending(0, 800), false, 800.01));
        assertEquals(Verdict.OK, PaymentRules.check(pending(0, 800), false, 800));
        // 50%: 2,000 -> 1,000 = the 50% cap
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(pending(0, 1000), false, 1000.01));
        // 80%: 2,000 -> 400 = exactly the 20% minimum
        assertEquals(Verdict.OK, PaymentRules.check(pending(0, 400), false, 400));
        assertEquals(Verdict.OUT_OF_RANGE, PaymentRules.check(pending(0, 400), false, 401));
    }

    @Test
    public void aDiscountSoLargeThatItIsBelowTheMinimumDepositAllowsNoDepositOnline() {
        Range range = pending(0, 200); // 90%: 2,000 -> 200, under the 400 minimum
        assertFalse(range.canPartial());
        assertTrue(range.capReached());
        assertEquals(Verdict.MAX_DEPOSIT_REACHED, PaymentRules.check(range, false, 400));
        assertEquals(Verdict.MAX_DEPOSIT_REACHED, PaymentRules.check(range, true, 2000));
    }

    @Test
    public void withNoCapReportedTheOrdinaryDepositRuleApplies() {
        Range range = PaymentRules.of(2000, 600, true, null);
        assertEquals(Verdict.OK, PaymentRules.check(range, false, 500));
        assertEquals(Verdict.PARTIAL_ONLY, PaymentRules.check(range, true, 1400));
    }

    @Test
    public void theCapOnlyAppliesWhileTheDiscountIsPending() {
        Range approved = PaymentRules.of(1600, 0, false, 1000.0);
        assertEquals(Verdict.OK, PaymentRules.check(approved, false, 800));
        assertEquals(Double.MAX_VALUE, approved.capLeft, 0);
    }
}
