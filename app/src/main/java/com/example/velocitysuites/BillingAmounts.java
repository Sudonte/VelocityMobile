package com.example.velocitysuites;

import androidx.annotation.Nullable;

/**
 * The money lines at the foot of the Billing Summary: what the stay costs, what is already paid, what is left, and the
 * one figure the guest is about to pay now - for the chosen option (Partial = its percentage of the total, Full = all of
 * it). Every value is a real, finite, non-negative number; a field the server did not send (a null percentage or required
 * amount) falls back to the remaining balance instead of leaking "null" onto the screen. Pure Java.
 */
final class BillingAmounts {

    enum Option { FULL, PARTIAL, BALANCE }

    final double grandTotal;
    final double alreadyPaid;
    final double remaining;
    final double amountToPay;
    final Option option;
    /** The percentage of the total that {@link #amountToPay} is (0-100), rounded. */
    final int percentage;

    private BillingAmounts(double grandTotal, double alreadyPaid, double remaining, double amountToPay, Option option, int percentage) {
        this.grandTotal = grandTotal;
        this.alreadyPaid = alreadyPaid;
        this.remaining = remaining;
        this.amountToPay = amountToPay;
        this.option = option;
        this.percentage = percentage;
    }

    /**
     * @param selectedPercentage the 20/30/40/50/100 the reservation was created with, or null if none was recorded
     * @param requiredAmount     the peso amount that goes with it, or null if the server has not set it
     */
    static BillingAmounts compute(double grandTotal, double alreadyPaid, @Nullable Double selectedPercentage, @Nullable Double requiredAmount) {
        double total = clean(grandTotal);
        double paid = clean(alreadyPaid);
        double remaining = round2(Math.max(0, total - paid));
        boolean hasRequired = requiredAmount != null && isFinite(requiredAmount) && requiredAmount > 0.009;
        boolean hasPercentage = selectedPercentage != null && isFinite(selectedPercentage) && selectedPercentage > 0;

        double amount;
        Option option;
        if (paid > 0.009 || (!hasRequired && !hasPercentage)) {
            // Something was already paid (the stored option belongs to that payment) or no option was ever recorded:
            // what is left to pay is simply the balance.
            amount = remaining;
            option = Option.BALANCE;
        } else {
            double chosen = hasRequired ? requiredAmount : round2(total * selectedPercentage / 100.0);
            amount = round2(Math.min(chosen, remaining));
            boolean full = (hasPercentage && selectedPercentage >= 100) || Math.abs(amount - remaining) < 0.01;
            option = full ? Option.FULL : Option.PARTIAL;
        }
        int pct = total > 0.009 ? (int) Math.round(amount / total * 100.0) : 0;
        return new BillingAmounts(total, paid, remaining, amount, option, Math.min(100, pct));
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
