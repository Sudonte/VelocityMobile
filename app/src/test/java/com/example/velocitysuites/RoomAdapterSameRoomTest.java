package com.example.velocitysuites;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * RecyclerView only re-draws a row the diff calls "changed". The room list used to compare just availability and
 * price, so after a refresh a visible card kept its old name, capacity, picture, "rooms left" count or amenities.
 * sameRoom() is that comparison now: any difference in what the card (or the details its buttons open) uses
 * counts as a change; an identical copy still counts as unchanged, so a refresh with no news re-draws nothing.
 */
public class RoomAdapterSameRoomTest {

    private static List<RoomAmenity> amenities(String... names) {
        List<RoomAmenity> list = new ArrayList<>();
        for (String name : names) list.add(new RoomAmenity(name, "General", "About " + name, RoomAmenity.PRICING_COMPLIMENTARY, "0"));
        return list;
    }

    /** The reference room; each test changes exactly one thing about a second copy. */
    private static Room base() {
        Room room = new Room("7", "Deluxe", "Deluxe", 2, 1800.0, "A nice room", 0, true,
                amenities("Wi-Fi", "Parking"), "Queen", "24 sqm", "No smoking");
        room.setImageUrl("https://example.test/a.jpg");
        room.setImageUrls(Arrays.asList("https://example.test/a.jpg", "https://example.test/b.jpg"));
        room.setImageLabels(Arrays.asList("Room 1", "Room 2"));
        room.setRoomTypeId(7);
        room.setAvailableCount(3);
        return room;
    }

    @Test
    public void twoIdenticalCopies_areTheSame() {
        assertTrue(RoomAdapter.sameRoom(base(), base()));
    }

    @Test
    public void theVeryObject_isTheSame() {
        Room room = base();
        assertTrue(RoomAdapter.sameRoom(room, room));
        assertTrue(RoomAdapter.sameRoom(null, null));
    }

    @Test
    public void nullAgainstARoom_isAChange() {
        assertFalse(RoomAdapter.sameRoom(base(), null));
        assertFalse(RoomAdapter.sameRoom(null, base()));
    }

    @Test
    public void aDifferentName_isAChange() {
        Room changed = new Room("7", "Deluxe Plus", "Deluxe", 2, 1800.0, "A nice room", 0, true,
                amenities("Wi-Fi", "Parking"), "Queen", "24 sqm", "No smoking");
        copyExtras(changed);
        assertFalse(RoomAdapter.sameRoom(base(), changed));
    }

    private static void copyExtras(Room target) {
        target.setImageUrl("https://example.test/a.jpg");
        target.setImageUrls(Arrays.asList("https://example.test/a.jpg", "https://example.test/b.jpg"));
        target.setImageLabels(Arrays.asList("Room 1", "Room 2"));
        target.setRoomTypeId(7);
        target.setAvailableCount(3);
    }

    @Test
    public void aDifferentCapacity_isAChange() {
        Room changed = new Room("7", "Deluxe", "Deluxe", 4, 1800.0, "A nice room", 0, true,
                amenities("Wi-Fi", "Parking"), "Queen", "24 sqm", "No smoking");
        copyExtras(changed);
        assertFalse(RoomAdapter.sameRoom(base(), changed));
    }

    @Test
    public void aDifferentPrice_isAChange() {
        Room changed = new Room("7", "Deluxe", "Deluxe", 2, 2100.0, "A nice room", 0, true,
                amenities("Wi-Fi", "Parking"), "Queen", "24 sqm", "No smoking");
        copyExtras(changed);
        assertFalse(RoomAdapter.sameRoom(base(), changed));
    }

    @Test
    public void availabilityFlippingAndTheRoomsLeftCount_areChanges() {
        Room soldOut = new Room("7", "Deluxe", "Deluxe", 2, 1800.0, "A nice room", 0, false,
                amenities("Wi-Fi", "Parking"), "Queen", "24 sqm", "No smoking");
        copyExtras(soldOut);
        assertFalse(RoomAdapter.sameRoom(base(), soldOut));

        Room fewerLeft = base();
        fewerLeft.setAvailableCount(1);
        assertFalse("3 rooms left -> 1 left must re-draw the 'rooms left' hint and the stepper's limit",
                RoomAdapter.sameRoom(base(), fewerLeft));
    }

    @Test
    public void aDifferentBedTypeSizeOrDescriptionOrPolicies_areChanges() {
        Room bed = new Room("7", "Deluxe", "Deluxe", 2, 1800.0, "A nice room", 0, true, amenities("Wi-Fi", "Parking"), "King", "24 sqm", "No smoking");
        Room size = new Room("7", "Deluxe", "Deluxe", 2, 1800.0, "A nice room", 0, true, amenities("Wi-Fi", "Parking"), "Queen", "30 sqm", "No smoking");
        Room description = new Room("7", "Deluxe", "Deluxe", 2, 1800.0, "Renovated", 0, true, amenities("Wi-Fi", "Parking"), "Queen", "24 sqm", "No smoking");
        Room policies = new Room("7", "Deluxe", "Deluxe", 2, 1800.0, "A nice room", 0, true, amenities("Wi-Fi", "Parking"), "Queen", "24 sqm", "Smoking area only");
        for (Room changed : new Room[]{bed, size, description, policies}) {
            copyExtras(changed);
            assertFalse(RoomAdapter.sameRoom(base(), changed));
        }
    }

    @Test
    public void aDifferentTypeLabel_isAChange() {
        Room changed = new Room("7", "Deluxe", "Suite", 2, 1800.0, "A nice room", 0, true,
                amenities("Wi-Fi", "Parking"), "Queen", "24 sqm", "No smoking");
        copyExtras(changed);
        assertFalse(RoomAdapter.sameRoom(base(), changed));
    }

    @Test
    public void aReplacedPictureOrGallery_isAChange() {
        Room newPicture = base();
        newPicture.setImageUrl("https://example.test/new.jpg");
        assertFalse(RoomAdapter.sameRoom(base(), newPicture));

        Room newGallery = base();
        newGallery.setImageUrls(Arrays.asList("https://example.test/a.jpg"));
        assertFalse(RoomAdapter.sameRoom(base(), newGallery));

        Room newLabels = base();
        newLabels.setImageLabels(Arrays.asList("Room 1", "Room 9"));
        assertFalse(RoomAdapter.sameRoom(base(), newLabels));
    }

    @Test
    public void addedRemovedOrRenamedAmenities_areChanges() {
        Room added = new Room("7", "Deluxe", "Deluxe", 2, 1800.0, "A nice room", 0, true,
                amenities("Wi-Fi", "Parking", "Pool"), "Queen", "24 sqm", "No smoking");
        Room removed = new Room("7", "Deluxe", "Deluxe", 2, 1800.0, "A nice room", 0, true,
                amenities("Wi-Fi"), "Queen", "24 sqm", "No smoking");
        Room renamed = new Room("7", "Deluxe", "Deluxe", 2, 1800.0, "A nice room", 0, true,
                amenities("Wi-Fi", "Free parking"), "Queen", "24 sqm", "No smoking");
        for (Room changed : new Room[]{added, removed, renamed}) {
            copyExtras(changed);
            assertFalse(RoomAdapter.sameRoom(base(), changed));
        }
    }

    @Test
    public void aDifferentRoomType_isAChange() {
        Room other = base();
        other.setRoomTypeId(8);
        assertFalse(RoomAdapter.sameRoom(base(), other));
    }
}
