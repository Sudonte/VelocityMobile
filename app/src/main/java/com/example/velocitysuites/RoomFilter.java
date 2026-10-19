package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Which rooms the Room Browsing filters keep, as pure rules (so the Filters sheet's live "Show N rooms" count and the list use the
 * very same ones):
 * <ul>
 *   <li><b>Room type</b> - "All" or a type name (Deluxe, Suite ...), matched on the room's type;</li>
 *   <li><b>Guests</b> - a bucket: 0 any, 1 = rooms for 1-2 guests, 2 = 3-4, 3 = 5 or more (the room's capacity falls in the range);</li>
 *   <li><b>Price per night</b> - an optional minimum and maximum ({@link #UNSET} = no limit on that side);</li>
 *   <li><b>Availability</b> - "All", "Available", "Limited" (bookable but nearly full) or "Unavailable".</li>
 * </ul>
 * The old "Room status" dropdown (its all-rooms entry read "All Rooms") filtered on availability; the "All Types" dropdown on room type.
 * They are different things, so both stay, now labelled "Room type" and "Availability".
 */
final class RoomFilter {

    /** "No limit" for a price side. */
    static final float UNSET = -1f;

    private RoomFilter() {
    }

    static boolean capacityMatches(int bucket, int capacity) {
        switch (bucket) {
            case 1:
                return capacity >= 1 && capacity <= 2;
            case 2:
                return capacity >= 3 && capacity <= 4;
            case 3:
                return capacity >= 5;
            default:
                return true;
        }
    }

    static boolean typeMatches(@Nullable String filter, @Nullable String roomType) {
        if (filter == null || filter.isEmpty() || "All".equalsIgnoreCase(filter)) return true;
        return roomType != null && roomType.toLowerCase(Locale.US).contains(filter.toLowerCase(Locale.US));
    }

    static boolean priceMatches(float min, float max, double price) {
        if (min != UNSET && price < min) return false;
        return max == UNSET || price <= max;
    }

    static boolean statusMatches(@Nullable String status, @NonNull Room room) {
        if (status == null || "All".equals(status)) return true;
        switch (status) {
            case "Available":
                return room.isAvailable();
            case "Limited":
                return room.isAvailable() && RoomAvailabilityStatus.of(room) == RoomAvailabilityStatus.LIMITED;
            case "Unavailable":
                return !room.isAvailable();
            default:
                return true;
        }
    }

    static boolean matches(@Nullable Room room, @Nullable String type, int guestBucket, float minPrice, float maxPrice, @Nullable String status) {
        if (room == null) return false;
        return typeMatches(type, room.getType())
                && capacityMatches(guestBucket, room.getCapacity())
                && priceMatches(minPrice, maxPrice, room.getPricePerNight())
                && statusMatches(status, room);
    }

    static int count(@Nullable List<Room> rooms, @Nullable String type, int guestBucket, float minPrice, float maxPrice, @Nullable String status) {
        if (rooms == null) return 0;
        int n = 0;
        for (Room r : rooms) if (matches(r, type, guestBucket, minPrice, maxPrice, status)) n++;
        return n;
    }

    /**
     * The slider's bounds from the real prices: the lowest rounded DOWN and the highest rounded UP to the next 100 (never equal - a
     * range slider needs room to move). With no priced room, 0 to 1,000.
     */
    @NonNull
    static float[] priceBounds(@Nullable List<Room> rooms) {
        double lo = Double.MAX_VALUE;
        double hi = 0;
        boolean any = false;
        if (rooms != null) {
            for (Room r : rooms) {
                if (r == null || r.getPricePerNight() <= 0) continue;
                any = true;
                lo = Math.min(lo, r.getPricePerNight());
                hi = Math.max(hi, r.getPricePerNight());
            }
        }
        if (!any) return new float[]{0f, 1000f};
        float floor = (float) (Math.floor(lo / 100.0) * 100.0);
        float ceil = (float) (Math.ceil(hi / 100.0) * 100.0);
        if (ceil <= floor) ceil = floor + 100f;
        return new float[]{floor, ceil};
    }
}
