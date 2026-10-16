package com.example.velocitysuites.network.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * One request for additional amenities: for each room of the stay, the amenities (with quantities) asked for THAT room, so
 * different rooms can get different amenities in the same submission. There is deliberately no free-text note.
 * {@code rooms} is what the server reads; the flat {@code items} list is only for a stay whose server sent no room list.
 */
public class AdditionalAmenitySubmitRequest {
    public static class Item {
        public final long amenity_id;
        public final int quantity;

        public Item(long amenityId, int quantity) {
            this.amenity_id = amenityId;
            this.quantity = quantity;
        }
    }

    public static class RoomLine {
        public final long room_id;
        public final List<Item> items;

        RoomLine(long roomId, List<Item> items) {
            this.room_id = roomId;
            this.items = items;
        }
    }

    /** Per-room form. Null (so omitted from the JSON) when only the flat form is used. */
    public List<RoomLine> rooms;
    /** Flat form - amenities for "the room", used only when there is no real room id (room_id 0). */
    public List<Item> items;

    /** Adds one room's amenities. room_id 0 means the stay had no room list, so the flat form is used instead. */
    public void addRoom(long roomId, List<Item> roomItems) {
        if (roomId == 0) {
            if (items == null) items = new ArrayList<>();
            items.addAll(roomItems);
            return;
        }
        if (rooms == null) rooms = new ArrayList<>();
        rooms.add(new RoomLine(roomId, roomItems));
    }

    public boolean isEmpty() {
        return (rooms == null || rooms.isEmpty()) && (items == null || items.isEmpty());
    }
}
