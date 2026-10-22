package com.example.velocitysuites;

/**
 * Which payments the server accepts for a reservation or booking, as plain code (no Android) so it is unit tested with the same
 * examples as the server's GuestPayRemainingBalanceTest. It must stay identical to the backend's
 * ReservationWorkflowService::payableRange() and ::amountError():
 * <ul>
 *   <li><b>Online payment is a PARTIAL (down) payment only.</b> A full payment is never accepted online and a payment may never
 *       pay off everything still owed: the rest is paid at the hotel (check-in and/or check-out);</li>
 *   <li>remaining = total due - already paid (verified payments only - the caller passes what the server reports);</li>
 *   <li>Partial = 20% to 50% of the ORIGINAL total (config hotel.minimum/maximum_payment_ratio), never more than the
 *       remaining balance, and never the whole remaining balance: when the most that could be paid equals what is still
 *       owed, nothing more can be paid online ("paid at the front desk");</li>
 *   <li>remaining zero: nothing can be paid;</li>
 *   <li>while a Senior/PWD discount waits for the receptionist's ID check, with too little left for a deposit, payment is on
 *       hold until the discount is decided. Once the discount is approved the server's total already includes it; rejected or
 *       not requested: the normal rules;</li>
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
        /** A full payment was asked for: online payment is a partial payment only. */
        PARTIAL_ONLY,
        /** The balance is below the minimum, or a payment would pay it all off: it is paid at the front desk. */
        PAY_AT_FRONT_DESK,
        /** A partial payment outside min..max. */
        OUT_OF_RANGE,
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
            double most = Math.min(Math.min(round2(this.total * MAX_RATIO), remaining), capLeft);
            // Online payment may never settle the whole bill: what is left is paid at the hotel.
            this.max = most >= remaining - 0.009 ? 0 : most;
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

        /** True while a down payment still fits: the 20% minimum fits inside what may be paid and it leaves a balance. */
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

    /** Whether the server will accept {@code amount} as an online payment against {@code range} ({@code fullPayment} is never accepted). */
    public static Verdict check(Range range, boolean fullPayment, double amount) {
        if (range.isSettled()) return Verdict.SETTLED;
        if (range.discountPending && !range.canPartial() && range.capReached()) return Verdict.MAX_DEPOSIT_REACHED;
        if (fullPayment) return Verdict.PARTIAL_ONLY;
        if (!range.canPartial()) return range.discountPending ? Verdict.DISCOUNT_ON_HOLD : Verdict.PAY_AT_FRONT_DESK;
        return amount < range.min - EPSILON || amount > range.max + EPSILON ? Verdict.OUT_OF_RANGE : Verdict.OK;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
