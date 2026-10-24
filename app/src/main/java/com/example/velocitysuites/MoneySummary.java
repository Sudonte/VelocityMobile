package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The three amounts every details screen and receipt ends with - Grand total, Remaining balance and Amount to pay - and the ONE rule for
 * what "Amount to pay" means. The figures are the server's (the payment summary / the receipt's own snapshot), never added up here:
 * Remaining balance = Grand total - verified payments, and the Grand total includes a discount only once it was applied.
 *
 * <p>Amount to pay, in the order the rule is read:
 * <ol>
 *   <li>cancelled or rejected: no amounts at all ({@link Kind#CANCELLED});</li>
 *   <li>nothing left to pay (fully paid or completed): "₱0.00 · Nothing to pay";</li>
 *   <li>a payment submitted and waiting for the front desk: "₱0.00 now · ₱X waiting for verification";</li>
 *   <li>checked in with a balance: "₱X · at check-out";</li>
 *   <li>a booking (verified or desk-confirmed) not yet checked in: "₱X · at check-in";</li>
 *   <li>a reservation nothing was paid on: "Pay now: ₱20% – ₱50% (20%–50%)" for GCash, "Pay at the hotel" for cash.</li>
 * </ol>
 * A receipt shows the value as of that receipt: Receipt 1 at check-in, Receipt 2 at check-out, Receipt 3 "₱0.00 · Fully paid".
 */
final class MoneySummary {

    enum Kind { PAY_RANGE, PAY_AT_HOTEL, WAITING, AT_CHECK_IN, AT_CHECK_OUT, AT_FRONT_DESK, NOTHING_TO_PAY, FULLY_PAID, CANCELLED }

    final Kind kind;
    final double grandTotal;
    final double remaining;
    /** The whole Amount to pay line ("₱9,800.00 · at check-in"); the cancelled message when {@code kind} is CANCELLED. */
    @NonNull final String amountToPay;
    /** Where the plain amount ends and the small "· at check-in" part begins (the whole text when there is no suffix). */
    final int amountEnd;

    private MoneySummary(Kind kind, double grandTotal, double remaining, String amountToPay, int amountEnd) {
        this.kind = kind;
        this.grandTotal = grandTotal;
        this.remaining = remaining;
        this.amountToPay = amountToPay;
        this.amountEnd = amountEnd;
    }

    boolean isCancelled() {
        return kind == Kind.CANCELLED;
    }

    /** Nothing is owed: shown green. */
    boolean isSettled() {
        return kind == Kind.NOTHING_TO_PAY || kind == Kind.FULLY_PAID;
    }

    // ---- the details screens ----

    @NonNull
    static MoneySummary of(@NonNull Context c, @NonNull Booking b) {
        TransactionStatusHelper.Summary s = TransactionStatusHelper.summarize(b);
        double total = s.grandTotal;
        double remaining = s.balance;
        BalanceBreakdown server = b.getStay() != null ? b.getStay().balanceBreakdown : null;
        if (server != null) {
            // in house or finished and billed at check-in: the server's own final figures (the ones the third receipt prints)
            total = server.finalGrandTotal;
            remaining = server.balanceDue;
        }
        if (s.status == TransactionStatusHelper.Status.CANCELLED || s.status == TransactionStatusHelper.Status.REJECTED) {
            return cancelled(c);
        }
        String status = b.getStatus() == null ? "" : b.getStatus();
        boolean finished = "Completed".equalsIgnoreCase(status) || "Checked-Out".equalsIgnoreCase(status);
        boolean checkedIn = "Checked-In".equalsIgnoreCase(status);
        if (finished || remaining <= MoneyFormat.EPSILON) {
            return nothingToPay(c, total, finished ? 0 : remaining);
        }
        if (s.pendingSubmitted > MoneyFormat.EPSILON) {
            return make(Kind.WAITING, total, remaining, c.getString(R.string.money_waiting_format, MoneyFormat.format(0), MoneyFormat.format(s.pendingSubmitted)),
                    MoneyFormat.format(0).length() + 4);
        }
        if (checkedIn) return suffixed(c, Kind.AT_CHECK_OUT, total, remaining, R.string.money_at_check_out_format);
        if (b.isNoShow()) return suffixed(c, Kind.AT_FRONT_DESK, total, remaining, R.string.money_at_front_desk_format);
        if (b.isHasBooking() || s.verifiedPaid > MoneyFormat.EPSILON) return suffixed(c, Kind.AT_CHECK_IN, total, remaining, R.string.money_at_check_in_format);
        // a reservation nothing was paid on yet
        boolean gcash = "gcash".equalsIgnoreCase(b.getPaymentMethod());
        PaymentRules.Range range = PaymentRules.of(total, s.verifiedPaid);
        if (gcash && range.canPartial()) {
            String text = c.getString(R.string.money_pay_now_range_format, MoneyFormat.format(range.min), MoneyFormat.format(range.max));
            return make(Kind.PAY_RANGE, total, remaining, text, text.length());
        }
        String text = c.getString(R.string.money_pay_at_hotel);
        return make(Kind.PAY_AT_HOTEL, total, remaining, text, text.length());
    }

    // ---- a receipt, as of its own moment ----

    /** {@code stage} 1, 2 or 3 (0 = a receipt of unknown stage: treated as paid at check-in); {@code total}/{@code remaining} are the receipt's snapshot. */
    @NonNull
    static MoneySummary ofReceipt(@NonNull Context c, int stage, double total, double remaining) {
        if (stage == 3) {
            String text = c.getString(R.string.money_fully_paid_format, MoneyFormat.format(0));
            return make(Kind.FULLY_PAID, total, remaining, text, MoneyFormat.format(0).length());
        }
        if (remaining <= MoneyFormat.EPSILON) return nothingToPay(c, total, 0);
        if (stage == 2) return suffixed(c, Kind.AT_CHECK_OUT, total, remaining, R.string.money_at_check_out_format);
        return suffixed(c, Kind.AT_CHECK_IN, total, remaining, R.string.money_at_check_in_format);
    }

    // ---- helpers ----

    private static MoneySummary cancelled(Context c) {
        String text = c.getString(R.string.money_cancelled);
        return make(Kind.CANCELLED, 0, 0, text, text.length());
    }

    private static MoneySummary nothingToPay(Context c, double total, double remaining) {
        String text = c.getString(R.string.money_nothing_to_pay_format, MoneyFormat.format(0));
        return make(Kind.NOTHING_TO_PAY, total, remaining, text, MoneyFormat.format(0).length());
    }

    private static MoneySummary suffixed(Context c, Kind kind, double total, double remaining, int format) {
        String shown = MoneyFormat.format(remaining);
        return make(kind, total, remaining, c.getString(format, shown), shown.length());
    }

    private static MoneySummary make(Kind kind, double total, double remaining, String text, int amountEnd) {
        return new MoneySummary(kind, total, remaining, text, Math.min(amountEnd, text.length()));
    }

    /** For tests and logs. */
    @Nullable
    @Override
    public String toString() {
        return kind + " total=" + grandTotal + " remaining=" + remaining + " pay='" + amountToPay + "'";
    }
}
