package com.example.velocitysuites;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * Paints a payment-status badge - the same label, fill, text color and leading icon on the Transaction History
 * card, the transaction detail header and the Payment Receipt, all from {@link TransactionStatusHelper#styleFor}.
 * The badge TextView itself (pill background, padding, caps, size) comes from its layout; this only applies the
 * status.
 */
final class StatusBadges {

    private StatusBadges() {
    }

    static void bind(@NonNull TextView badge, @NonNull TransactionStatusHelper.Status status) {
        Context ctx = badge.getContext();
        TransactionStatusHelper.Style style = TransactionStatusHelper.styleFor(status);
        badge.setText(style.labelRes);
        badge.setBackgroundTintList(ctx.getColorStateList(style.bgColorRes));
        badge.setTextColor(ctx.getColor(style.fgColorRes));

        // Icon sized with the text (1.2x) so it grows with the guest's system font instead of staying a speck
        // beside large letters, and tinted like the label (set just above) - on a fresh copy of the drawable,
        // so tinting one badge can never recolor another.
        TextIcons.setRelative(badge, style.iconRes, 0, 1.2f);
    }
}
