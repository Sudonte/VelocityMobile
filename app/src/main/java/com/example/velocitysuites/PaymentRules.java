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
 *   <li>remaining zero: nothing can be paid;</li>
 *   <li>while a Senior/PWD discount waits for the receptionist's ID check, only a deposit: Full payment is refused
 *       (and with too little left for a deposit, payment is on hold until the discount is decided). Once the discount
 *       is approved the server's total already includes it; rejected or not requested: the normal rules;</li>
 *   <li>while it is pending, ALL payments together (already paid + this one) stay within the deposit cap the server
 *       reports (the smaller of 50% of the undiscounted total and the total after the requested discount) - several
 *       deposits can never add up past what the bill becomes.</li>
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
        OUT_OF_RANGE,
        /** Full payment while the discount is still being verified - a deposit only. */
        DISCOUNT_PENDING_FULL,
        /** Discount being verified and too little left for the minimum deposit: nothing can be paid yet. */
        DISCOUNT_ON_HOLD,
        /** Discount being verified and the deposit cap is used up: the rest is settled at the front desk. */
        MAX_DEPOSIT_REACHED
    }

    /** The numbers the rule works from, for one bill at one moment. */
    public static final class Range {
        public final double total;
        public final double paid;
        public final double remaining;
        public final double min;
        public final double max;
        /** True while a discount waits for the receptionist's ID check (discount_verification_status = pending). */
        public final boolean discountPending;
        /** While a discount is pending: the most ALL payments together may add up to, as the server reports it; null otherwise. */
        public final Double depositCap;

        Range(double total, double paid, boolean discountPending, Double depositCap) {
            this.discountPending = discountPending;
            this.depositCap = discountPending ? depositCap : null;
            this.total = round2(total);
            this.paid = round2(paid);
            this.remaining = Math.max(0, round2(this.total - this.paid));
            this.min = round2(this.total * MIN_RATIO);
            double capLeft = this.depositCap == null ? Double.MAX_VALUE : Math.max(0, round2(this.depositCap - this.paid));
            this.max = Math.min(Math.min(round2(this.total * MAX_RATIO), remaining), capLeft);
            this.capLeft = capLeft;
        }

        /** What is left under the deposit cap (Double.MAX_VALUE when there is none). */
        public final double capLeft;

        /** True when it is the deposit cap, not the balance, that leaves no room for even the minimum deposit. */
        public boolean capReached() {
            return discountPending && depositCap != null && capLeft + 0.009 < min;
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
        return new Range(total, paid, false, null);
    }

    public static Range of(double total, double paid, boolean discountPending) {
        return new Range(total, paid, discountPending, null);
    }

    /** {@code depositCap}: the server's deposit_cap (only used while the discount is pending). */
    public static Range of(double total, double paid, boolean discountPending, Double depositCap) {
        return new Range(total, paid, discountPending, depositCap);
    }

    /** The peso amount a 20/30/40/50% option sends: that share of the ORIGINAL total, capped at what is left (and at what the deposit cap leaves). */
    public static double partialAmount(Range range, double fraction) {
        return Math.min(round2(range.total * fraction), Math.min(range.remaining, range.capLeft));
    }

    /** Whether the server will accept {@code amount} as a Full (or Partial) payment against {@code range}. */
    public static Verdict check(Range range, boolean fullPayment, double amount) {
        if (range.isSettled()) return Verdict.SETTLED;
        if (range.discountPending) {
            if (!range.canPartial() && range.capReached()) return Verdict.MAX_DEPOSIT_REACHED;
            if (fullPayment) return Verdict.DISCOUNT_PENDING_FULL;
            if (!range.canPartial()) return Verdict.DISCOUNT_ON_HOLD;
        }
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
