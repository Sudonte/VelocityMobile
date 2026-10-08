package com.example.velocitysuites;

/**
 * The one guest-count rule shared by the Booking and Reservation wizards
 * (and mirrored server-side in the API): adults + children may not exceed the
 * summed maximum capacity of every selected room, and at least one adult is
 * required. There is no separate per-category cap (children used to be fixed
 * at 3).
 */
public final class GuestCapacity {

    private GuestCapacity() {
    }

    /** A room set with unknown/zero capacity still has to host the one required adult. */
    public static int effectiveMax(int totalRoomCapacity) {
        return Math.max(1, totalRoomCapacity);
    }

    public static boolean isValid(int adults, int children, int totalRoomCapacity) {
        return adults >= 1 && children >= 0 && adults + children <= effectiveMax(totalRoomCapacity);
    }

    /** True while one more guest (of either kind) still fits. */
    public static boolean canAddGuest(int adults, int children, int totalRoomCapacity) {
        return adults + children < effectiveMax(totalRoomCapacity);
    }

    /**
     * Brings an over-capacity count back inside the limit: children are
     * reduced first, then adults (never below 1). Returns {adults, children}.
     */
    public static int[] clamp(int adults, int children, int totalRoomCapacity) {
        int max = effectiveMax(totalRoomCapacity);
        int a = Math.max(1, adults);
        int c = Math.max(0, children);
        if (a + c > max) {
            c = Math.max(0, max - a);
        }
        if (a + c > max) {
            a = max;
            c = 0;
        }
        return new int[]{a, c};
    }
}
