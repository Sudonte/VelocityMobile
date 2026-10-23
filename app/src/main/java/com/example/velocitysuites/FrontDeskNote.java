package com.example.velocitysuites;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

/** The "Remaining balance: P X. Please pay at the Velocity Suites front desk." line shown instead of Pay Now on a confirmed booking. */
public final class FrontDeskNote {

    private FrontDeskNote() {
    }

    public static String messageFor(Context context, Booking booking) {
        // A GCash payment the front desk has not checked yet: say so, instead of sending the guest to the desk to pay a balance
        if (booking.isPaymentPendingVerification() && !booking.isStaffVerified()) {
            return context.getString(R.string.payment_under_review_note);
        }
        return context.getString(R.string.front_desk_balance_message,
                MoneyFormat.format(PaymentEligibility.frontDeskBalance(booking)));
    }

    /** Shows the message in {@code view} when {@code booking} is a confirmed booking with a balance, hides it otherwise. */
    public static void bind(@Nullable TextView view, @Nullable Booking booking) {
        if (view == null) return;
        boolean show = PaymentEligibility.needsFrontDeskPayment(booking);
        view.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) view.setText(messageFor(view.getContext(), booking));
    }
}
