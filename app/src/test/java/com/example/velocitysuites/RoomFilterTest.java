package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The Room Browsing filter rules: type, guest buckets, price range, availability, and the slider's bounds. */
public class RoomFilterTest {

    private static Room room(String id, String type, int capacity, double price, int available) {
        Room r = new Room(id, type + " " + id, type, capacity, price, "d", 0, available > 0, Collections.<RoomAmenity>emptyList(), "Queen", "24", "");
        r.setAvailableCount(available);
        return r;
    }

    private final List<Room> rooms = Arrays.asList(
            room("1", "Deluxe", 2, 1850, 5), room("2", "Suite", 4, 3000, 2), room("3", "Family", 6, 3999, 0), room("4", "Deluxe", 3, 2500, 1));

    @Test
    public void guestBuckets_matchTheRoomsCapacityRange() {
        assertEquals(4, RoomFilter.count(rooms, "All", 0, RoomFilter.UNSET, RoomFilter.UNSET, "All"));
        assertEquals("1-2 guests", 1, RoomFilter.count(rooms, "All", 1, RoomFilter.UNSET, RoomFilter.UNSET, "All"));
        assertEquals("3-4 guests", 2, RoomFilter.count(rooms, "All", 2, RoomFilter.UNSET, RoomFilter.UNSET, "All"));
        assertEquals("5+ guests", 1, RoomFilter.count(rooms, "All", 3, RoomFilter.UNSET, RoomFilter.UNSET, "All"));
    }

    @Test
    public void priceRange_hasAnOptionalMinimumAndMaximum() {
        assertEquals(3, RoomFilter.count(rooms, "All", 0, 2500f, RoomFilter.UNSET, "All"));
        assertEquals(2, RoomFilter.count(rooms, "All", 0, RoomFilter.UNSET, 2500f, "All"));
        assertEquals(2, RoomFilter.count(rooms, "All", 0, 2500f, 3000f, "All"));
    }

    @Test
    public void typeAndAvailability_andEverythingCombines() {
        assertEquals(2, RoomFilter.count(rooms, "Deluxe", 0, RoomFilter.UNSET, RoomFilter.UNSET, "All"));
        assertEquals("Deluxe, 3-4 guests", 1, RoomFilter.count(rooms, "Deluxe", 2, RoomFilter.UNSET, RoomFilter.UNSET, "All"));
        assertEquals("limited = bookable with 1-2 left", 2, RoomFilter.count(rooms, "All", 0, RoomFilter.UNSET, RoomFilter.UNSET, "Limited"));
        assertEquals(1, RoomFilter.count(rooms, "All", 0, RoomFilter.UNSET, RoomFilter.UNSET, "Unavailable"));
        assertEquals(3, RoomFilter.count(rooms, "All", 0, RoomFilter.UNSET, RoomFilter.UNSET, "Available"));
        assertEquals(1, RoomFilter.count(rooms, "Deluxe", 2, 2000f, 3000f, "Limited"));
    }

    @Test
    public void aRoomWithMissingFields_neverCrashesTheFilter() {
        Room odd = new Room("9", null, null, 0, 0, null, 0, true, null, null, null, null);
        assertFalse(RoomFilter.matches(null, "All", 0, RoomFilter.UNSET, RoomFilter.UNSET, "All"));
        assertTrue(RoomFilter.matches(odd, "All", 0, RoomFilter.UNSET, RoomFilter.UNSET, "All"));
        assertFalse(RoomFilter.matches(odd, "Deluxe", 0, RoomFilter.UNSET, RoomFilter.UNSET, "All"));
        assertEquals(0, RoomFilter.count(null, "All", 0, RoomFilter.UNSET, RoomFilter.UNSET, "All"));
    }

    @Test
    public void sliderBounds_comeFromTheRealPrices() {
        float[] bounds = RoomFilter.priceBounds(rooms);
        assertEquals(1800f, bounds[0], 0.01f);
        assertEquals(4000f, bounds[1], 0.01f);
        float[] single = RoomFilter.priceBounds(Collections.singletonList(room("1", "Deluxe", 2, 2000, 1)));
        assertEquals(2000f, single[0], 0.01f);
        assertEquals("never an empty range", 2100f, single[1], 0.01f);
        float[] none = RoomFilter.priceBounds(new ArrayList<>());
        assertEquals(0f, none[0], 0.01f);
        assertEquals(1000f, none[1], 0.01f);
    }
}
