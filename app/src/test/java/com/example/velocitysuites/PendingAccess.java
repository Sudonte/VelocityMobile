package com.example.velocitysuites;

/** Test-only door to the package-private PendingBookingPayload (the reviewed wizard state a new booking pays from). */
public final class PendingAccess {
    private PendingAccess() {
    }

    public static void set(BookingWizardState state) {
        PendingBookingPayload.set(state);
    }

    /** Where a guest who picked a room before signing in lands after it (PendingRoomSelection, package-private). */
    public static android.content.Intent postAuthIntent(android.content.Context context, String userName) {
        return PendingRoomSelection.createPostAuthIntent(context, userName);
    }
}
