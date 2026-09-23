package com.example.velocitysuites;

/**
 * In-process, consume-once handoff of a cancelled transaction's guest-info
 * fields (name, ID type, additional guests) from BookingAndReservationActivity's
 * "Book Again"/"Reserve Again" action into BookingWizardActivity's Steps 4-5,
 * so the guest doesn't have to retype them for a brand-new transaction. Room
 * selection (Step 1) is carried separately via PendingWizardRooms - this only
 * carries what BookingWizardActivity#seedStateForBookAgain() needs. Same
 * static in-memory convention as PendingWizardRooms (Booking is Serializable,
 * but both ends of this handoff always run in the same process/session, so a
 * plain static field is simpler than an Intent extra).
 */
final class PendingBookAgainPrefill {

    private static Booking pendingOriginal;

    private PendingBookAgainPrefill() {}

    static void set(Booking original) {
        pendingOriginal = original;
    }

    /** Returns the pending original and clears it so a stale prefill can never leak into a later, unrelated wizard run. */
    static Booking consume() {
        Booking result = pendingOriginal;
        pendingOriginal = null;
        return result;
    }
}
