package com.example.velocitysuites;

/**
 * Which payments the server accepts for a reservation, as plain code (no Android) so it is unit tested with the same
 * examples as the server's GuestPayRemainingBalanceTest. It must stay identical to the backend's
 * ReservationWorkflowService::payableRange() and Api\PaymentController::amountError():
 * <ul>
 *   <li>remaining = total due - already paid (verified payments only - the caller passes what the server reports);</li>
 *   <li>Full payment = exactly the remaining balance;</li>
 *   <li>Partial = 20% to 50% of the ORIGINAL total (config hotel.minimum/maximum_payment_ratio), never more than the
 *       remaining balance;</li>
 *   <li>remaining below the 20% minimum: no partial payment is possible, only Full;</li>
 *   <li>remaining zero: nothing can be paid.</li>
 * </ul>
 */
public final class PaymentRules {

    /** hotel.minimum_payment_ratio / hotel.maximum_payment_ratio on the server. */
    public static final double MIN_RATIO = 0.20;
    public static final double MAX_RATIO = 0.50;

    private static final double EPSILON = 0.005;

    private PaymentRules() {
    }

    public enum Verdict {
        OK,
        /** Nothing is owed any more. */
        SETTLED,
        /** Full payment must be exactly the remaining balance. */
        FULL_MUST_EQUAL_BALANCE,
        /** The balance is under the minimum, so only a Full payment of it is possible. */
        FULL_REQUIRED,
        /** A partial payment outside min..max. */
        OUT_OF_RANGE
    }

    /** The numbers the rule works from, for one bill at one moment. */
    public static final class Range {
        public final double total;
        public final double paid;
        public final double remaining;
        public final double min;
        public final double max;

        Range(double total, double paid) {
            this.total = round2(total);
            this.paid = round2(paid);
            this.remaining = Math.max(0, round2(this.total - this.paid));
            this.min = round2(this.total * MIN_RATIO);
            this.max = Math.min(round2(this.total * MAX_RATIO), remaining);
        }

        public boolean isSettled() {
            return remaining <= 0.009;
        }

        /** True while the 20% minimum still fits inside what is left to pay. */
        public boolean canPartial() {
            return !isSettled() && min <= max + 0.009;
        }
    }

    public static Range of(double total, double paid) {
        return new Range(total, paid);
    }

    /** The peso amount a 20/30/40/50% option sends: that share of the ORIGINAL total, capped at what is left. */
    public static double partialAmount(Range range, double fraction) {
        return Math.min(round2(range.total * fraction), range.remaining);
    }

    /** Whether the server will accept {@code amount} as a Full (or Partial) payment against {@code range}. */
    public static Verdict check(Range range, boolean fullPayment, double amount) {
        if (range.isSettled()) return Verdict.SETTLED;
        if (fullPayment) {
            return Math.abs(amount - range.remaining) > 0.01 ? Verdict.FULL_MUST_EQUAL_BALANCE : Verdict.OK;
        }
        if (!range.canPartial()) return Verdict.FULL_REQUIRED;
        return amount < range.min - EPSILON || amount > range.max + EPSILON ? Verdict.OUT_OF_RANGE : Verdict.OK;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
