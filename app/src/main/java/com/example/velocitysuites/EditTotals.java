package com.example.velocitysuites;

/**
 * The money picture shown before the guest confirms an Edit Reservation: what
 * the reservation cost before, what it costs with the changes, what has
 * already been paid against it, and what that leaves - an amount still due or
 * an excess already paid. Amounts are rounded to centavos so a float drift
 * never shows as "₱0.00 due" next to a ₱0.01 excess.
 */
public final class EditTotals {

    public final double oldTotal;
    public final double newTotal;
    public final double amountPaid;
    /** New total minus what is paid, never negative. */
    public final double balanceDue;
    /** What is paid beyond the new total, never negative. */
    public final double excess;

    private EditTotals(double oldTotal, double newTotal, double amountPaid) {
        this.oldTotal = round(oldTotal);
        this.newTotal = round(newTotal);
        this.amountPaid = round(amountPaid);
        double diff = round(this.newTotal - this.amountPaid);
        this.balanceDue = Math.max(0, diff);
        this.excess = Math.max(0, -diff);
    }

    public static EditTotals of(double oldTotal, double newTotal, double amountPaid) {
        return new EditTotals(oldTotal, newTotal, Math.max(0, amountPaid));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
