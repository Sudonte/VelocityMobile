package com.example.velocitysuites;

import androidx.annotation.Nullable;

/**
 * The money lines of the Billing Summary: what the stay costs, what is already paid, what is left, and the one figure
 * shown in the highlighted "Total Amount to Pay" block. Before any payment that is the Grand Total (the guest picks
 * Partial 20-50% or Full on the payment screen, not here); once something was paid it is the Remaining Balance. Every
 * value is a real, finite, non-negative number, so nothing can print as "null". Pure Java.
 */
final class BillingAmounts {

    final double grandTotal;
    final double alreadyPaid;
    final double remaining;
    /** The figure for the highlighted block: the grand total before a payment, the remaining balance after one. */
    final double amountToPay;
    /** True once something was paid (the block then reads "Remaining Balance", else "Grand Total"). */
    final boolean paymentMade;

    private BillingAmounts(double grandTotal, double alreadyPaid, double remaining, double amountToPay, boolean paymentMade) {
        this.grandTotal = grandTotal;
        this.alreadyPaid = alreadyPaid;
        this.remaining = remaining;
        this.amountToPay = amountToPay;
        this.paymentMade = paymentMade;
    }

    /**
     * @param verifiedPaid        the verified amount paid so far
     * @param submittedUnverified a GCash payment sent but not yet verified by the front desk, or null; counted as paid
     *                            when nothing verified is on record yet
     */
    static BillingAmounts compute(double grandTotal, double verifiedPaid, @Nullable Double submittedUnverified) {
        double total = clean(grandTotal);
        double paid = clean(verifiedPaid);
        if (paid <= 0.009 && submittedUnverified != null && isFinite(submittedUnverified) && submittedUnverified > 0.009) {
            paid = clean(submittedUnverified);
        }
        paid = Math.min(paid, total > 0.009 ? total : paid);
        double remaining = round2(Math.max(0, total - paid));
        boolean made = paid > 0.009;
        return new BillingAmounts(total, paid, remaining, made ? remaining : total, made);
    }

    private static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private static double clean(double v) {
        return isFinite(v) ? Math.max(0, round2(v)) : 0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
