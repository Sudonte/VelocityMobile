package com.example.velocitysuites;

import androidx.annotation.Nullable;

/**
 * The money rules for "the bill may have changed while the guest was off paying in the GCash app".
 * <p>
 * Plain code with no Android in it so every rule can be unit tested. The screen keeps the {@link Bill} the guest
 * last SAW (and, if it changed, confirmed); each fresh read from the server is compared against that one.
 */
public final class PaymentBillingCheck {

    /** Half a centavo - amounts are pesos with two decimals, so anything below this is the same amount. */
    private static final double EPSILON = 0.005;

    private PaymentBillingCheck() {
    }

    /** The two figures a payment depends on; what is still owed follows from them. */
    public static final class Bill {
        public final double total;
        public final double paid;
        /** A discount is waiting for the receptionist's ID check - deposits only (PaymentRules). */
        public final boolean discountPending;

        public Bill(double total, double paid) {
            this(total, paid, false);
        }

        public Bill(double total, double paid, boolean discountPending) {
            this.total = total;
            this.paid = paid;
            this.discountPending = discountPending;
        }

        public static Bill of(Booking booking) {
            return new Bill(booking.getTotalAmount(), booking.getAmountPaid(), booking.isDiscountPending());
        }

        /** What is still owed; never negative. */
        public double balanceDue() {
            return Math.max(0, total - paid);
        }

        public boolean sameAs(Bill other) {
            return other != null
                    && Math.abs(total - other.total) < EPSILON
                    && Math.abs(paid - other.paid) < EPSILON
                    && discountPending == other.discountPending;
        }
    }

    /** What the pre-submit check decided. */
    public enum SubmitVerdict {
        /** Nothing changed and the amount fits: go ahead and send it. */
        PROCEED,
        /** The bill moved since the guest last saw it: show the new amount and ask again. */
        BILL_CHANGED,
        /** The latest bill could not be read: do not send; offer Retry. */
        CHECK_FAILED,
        /** The bill is unchanged but the amount typed is more than is owed: inline error. */
        OVER_BALANCE,
        /** The bill is unchanged and the amount is not over the balance, but the server's rule (PaymentRules) would still refuse it. */
        AMOUNT_NOT_ALLOWED
    }

    /**
     * Resume check: did the bill change from what the guest has on screen?
     *
     * @param shown  what the guest last saw
     * @param latest the fresh read, or null if it failed (a silent check says nothing then)
     */
    public static boolean billChanged(Bill shown, @Nullable Bill latest) {
        return latest != null && !latest.sameAs(shown);
    }

    /**
     * Pre-submit check, run on a fresh read right before anything is sent.
     *
     * @param shown       what the guest last saw / confirmed
     * @param latest      the fresh read, or null if it could not be fetched
     * @param typedAmount the amount about to be sent
     * @param fullPayment true for Full Payment, false for one of the partial options
     */
    public static SubmitVerdict beforeSubmit(Bill shown, @Nullable Bill latest, double typedAmount, boolean fullPayment) {
        if (latest == null) return SubmitVerdict.CHECK_FAILED;
        if (!latest.sameAs(shown)) return SubmitVerdict.BILL_CHANGED;
        if (typedAmount > latest.balanceDue() + EPSILON) return SubmitVerdict.OVER_BALANCE;
        PaymentRules.Verdict rule = PaymentRules.check(PaymentRules.of(latest.total, latest.paid, latest.discountPending), fullPayment, typedAmount);
        if (rule != PaymentRules.Verdict.OK) return SubmitVerdict.AMOUNT_NOT_ALLOWED;
        return SubmitVerdict.PROCEED;
    }
}
