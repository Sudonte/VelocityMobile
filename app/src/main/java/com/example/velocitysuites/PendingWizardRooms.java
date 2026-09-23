package com.example.velocitysuites;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-process, consume-once handoff of already-resolved {@link Room} objects
 * from RoomBrowsingActivity/LandingActivity into BookingWizardActivity's
 * Step 1, so a guest who already picked room(s) upstream never has to
 * reselect them. Deliberately a static in-memory holder (same convention as
 * PendingRoomSelection, which persists only a room id across the auth
 * round-trip) rather than an Intent extra - Room is not Parcelable and both
 * ends of this handoff always run in the same process/session.
 */
final class PendingWizardRooms {

    private static List<Room> pendingRooms;

    private PendingWizardRooms() {}

    static void set(List<Room> rooms) {
        pendingRooms = rooms == null ? null : new ArrayList<>(rooms);
    }

    /** Returns the pending rooms and clears them so a stale selection can never leak into a later, unrelated wizard run. */
    static List<Room> consume() {
        List<Room> result = pendingRooms;
        pendingRooms = null;
        return result == null ? Collections.emptyList() : result;
    }

    /** Non-consuming read, for the StartingTransactionActivity intro screen's room recap - the wizard itself still consumes via consume(). */
    static List<Room> peek() {
        return pendingRooms == null ? Collections.emptyList() : Collections.unmodifiableList(pendingRooms);
    }
}
