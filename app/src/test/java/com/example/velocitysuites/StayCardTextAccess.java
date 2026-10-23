package com.example.velocitysuites;

import android.content.Context;

/** Test-only door to the package-private StayCardText. */
public final class StayCardTextAccess {
    private StayCardTextAccess() {
    }

    public static void showCreated(Context context, boolean isBooking, String id, Booking b, Runnable onAcknowledged) {
        TransactionCreatedDialogHelper.show(context, isBooking, id, b, onAcknowledged::run);
    }

    public static String nightsAndGuests(Context context, Booking b) {
        return StayCardText.nightsAndGuests(context, b);
    }
}
