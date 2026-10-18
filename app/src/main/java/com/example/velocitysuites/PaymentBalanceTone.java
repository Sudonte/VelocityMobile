package com.example.velocitysuites;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;

/**
 * How a balance is coloured wherever money still owed is shown: green when nothing is owed, red only when money is owed
 * and nothing has been paid yet, amber when it is partly paid. One rule for the Dashboard's Payment Status cards and
 * Transaction History, so a "₱0.00" is never red.
 */
enum PaymentBalanceTone {
    PAID(R.color.status_paid_fg, R.color.status_paid_bg, R.string.payment_chip_paid),
    PARTIAL(R.color.status_pending_fg, R.color.status_pending_bg, R.string.payment_chip_partial),
    OWED(R.color.velocity_red_primary, R.color.velocity_red_status_soft, R.string.payment_chip_unpaid);

    /** The balance, the progress bar and the chip's text all use this one colour. */
    @ColorRes final int colorRes;
    @ColorRes final int chipBgRes;
    @StringRes final int labelRes;

    PaymentBalanceTone(int colorRes, int chipBgRes, int labelRes) {
        this.colorRes = colorRes;
        this.chipBgRes = chipBgRes;
        this.labelRes = labelRes;
    }

    /** @param paid receptionist-verified money only (see TransactionStatusHelper) */
    static PaymentBalanceTone of(double total, double paid) {
        double balance = Math.max(0, total - paid);
        if (!MoneyFormat.isPositive(balance)) return PAID;
        return MoneyFormat.isPositive(paid) ? PARTIAL : OWED;
    }
}
