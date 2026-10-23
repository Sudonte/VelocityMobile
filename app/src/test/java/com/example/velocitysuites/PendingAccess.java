package com.example.velocitysuites;

/** Test-only door to the package-private PendingBookingPayload (the reviewed wizard state a new booking pays from). */
public final class PendingAccess {
    private PendingAccess() {
    }

    public static void set(BookingWizardState state) {
        PendingBookingPayload.set(state);
    }
}
