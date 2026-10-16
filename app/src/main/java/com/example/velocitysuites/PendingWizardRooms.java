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
public final class PendingWizardRooms {

    private static List<Room> pendingRooms;
    // Dates the guest searched in Room Browsing; carried with the rooms so the wizard opens with them filled in.
    private static java.util.Calendar pendingCheckIn;
    private static java.util.Calendar pendingCheckOut;

    private PendingWizardRooms() {}

    public static void set(List<Room> rooms) {
        pendingRooms = rooms == null ? null : new ArrayList<>(rooms);
        setDates(null, null); // a new handoff never inherits an earlier search's dates; callers re-add theirs
    }

    /** Remembers the searched stay dates for the next wizard run (both or neither; pass nulls to clear). */
    public static void setDates(java.util.Calendar checkIn, java.util.Calendar checkOut) {
        boolean both = checkIn != null && checkOut != null;
        pendingCheckIn = both ? (java.util.Calendar) checkIn.clone() : null;
        pendingCheckOut = both ? (java.util.Calendar) checkOut.clone() : null;
    }

    /** Returns {checkIn, checkOut} (consume-once) or null when no dates were searched. */
    public static java.util.Calendar[] consumeDates() {
        java.util.Calendar[] result = (pendingCheckIn != null && pendingCheckOut != null)
                ? new java.util.Calendar[]{pendingCheckIn, pendingCheckOut} : null;
        pendingCheckIn = null;
        pendingCheckOut = null;
        return result;
    }

    /** Returns the pending rooms and clears them so a stale selection can never leak into a later, unrelated wizard run. */
    public static List<Room> consume() {
        List<Room> result = pendingRooms;
        pendingRooms = null;
        return result == null ? Collections.emptyList() : result;
    }

    /** Non-consuming read, for the StartingTransactionActivity intro screen's room recap - the wizard itself still consumes via consume(). */
    public static List<Room> peek() {
        return pendingRooms == null ? Collections.emptyList() : Collections.unmodifiableList(pendingRooms);
    }
}
