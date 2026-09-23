package com.example.velocitysuites;

/**
 * Three-tier availability display (AVAILABLE / LIMITED / FULLY_BOOKED),
 * computed client-side from the backend-authoritative availableCount - the
 * API itself only ever returns a binary is_fully_booked, so this is the
 * Android-side fallback the spec calls for when the backend doesn't expose
 * a richer status. Never recomputes availability itself (that stays
 * server-side); this only buckets the count the server already sent.
 */
public enum RoomAvailabilityStatus {
    AVAILABLE, LIMITED, FULLY_BOOKED;

    private static final int LIMITED_THRESHOLD = 2;

    public static RoomAvailabilityStatus of(Room room) {
        int count = room.getAvailableCount();
        if (count <= 0) {
            return FULLY_BOOKED;
        }
        if (count <= LIMITED_THRESHOLD) {
            return LIMITED;
        }
        return AVAILABLE;
    }
}
