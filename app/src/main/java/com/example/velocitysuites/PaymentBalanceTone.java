package com.example.velocitysuites;

import androidx.annotation.ColorRes;

/**
 * How a balance is coloured wherever money still owed is shown: green when nothing is owed, red only when money is owed
 * and nothing has been paid yet, amber when it is partly paid. One rule for the Dashboard's Payment Status cards and
 * Transaction History, so a "₱0.00" is never red.
 */
enum PaymentBalanceTone {
    PAID(R.color.status_paid_fg),
    PARTIAL(R.color.status_pending_fg),
    OWED(R.color.velocity_red_primary);

    @ColorRes final int colorRes;

    PaymentBalanceTone(int colorRes) {
        this.colorRes = colorRes;
    }

    /** @param paid receptionist-verified money only (see TransactionStatusHelper) */
    static PaymentBalanceTone of(double total, double paid) {
        double balance = Math.max(0, total - paid);
        if (!MoneyFormat.isPositive(balance)) return PAID;
        return MoneyFormat.isPositive(paid) ? PARTIAL : OWED;
    }
}
