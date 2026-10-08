package com.example.velocitysuites;

import androidx.annotation.Nullable;

/**
 * Who may "Edit Reservation": a plain Reservation (never a Booking, never a
 * frozen historical record) that is still awaiting review ("Pending" - not yet
 * confirmed/converted, checked in, completed, cancelled or rejected) and has
 * not already used its one allowed edit. The server enforces the same rule
 * (Api\ReservationController::update()); this only decides whether to offer
 * the action. Shared by the Reservation list card and the Reservation Details
 * screen so the two can never disagree.
 */
public final class ReservationEditPolicy {

    private ReservationEditPolicy() {
    }

    /**
     * @param locallyModified the optimistic per-device "just edited" flag (see
     *                        LocalTransactionState) so the action disappears the
     *                        instant a save succeeds, before the next refresh.
     */
    public static boolean canEdit(@Nullable Booking b, boolean locallyModified) {
        return b != null
                && !b.isHasBooking()
                && !b.isDirectBooking()
                && !b.isHistoricalReservation()
                && "Pending".equalsIgnoreCase(b.getStatus())
                && !b.isEditedOnce()
                && !locallyModified;
    }
}
