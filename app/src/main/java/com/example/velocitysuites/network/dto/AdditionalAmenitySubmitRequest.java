package com.example.velocitysuites.network.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * One request for additional amenities: for each room of the stay, the amenities (with quantities) asked for THAT room, so
 * different rooms can get different amenities in the same submission. There is deliberately no free-text note.
 * {@code rooms} is what the server reads; the flat {@code items} list is only for a stay whose server sent no room list.
 * A room is named by {@code room_id} once the front desk assigned it (during the stay), or - before check-in - by the room that was
 * booked: {@code room_type_id} + {@code room_slot} (+ {@code option_key}). Fields that do not apply are left out of the JSON.
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
        /** The assigned room (during the stay); null for a booked room before check-in. */
        public final Long room_id;
        /** The booked room before check-in: its room type, its option ("standard" unless another was booked) and which one of them (1, 2, ...). */
        public final Long room_type_id;
        public final String option_key;
        public final Integer room_slot;
        public final List<Item> items;

        RoomLine(long roomId, List<Item> items) {
            this.room_id = roomId;
            this.room_type_id = null;
            this.option_key = null;
            this.room_slot = null;
            this.items = items;
        }

        RoomLine(long roomTypeId, String optionKey, int roomSlot, List<Item> items) {
            this.room_id = null;
            this.room_type_id = roomTypeId;
            this.option_key = optionKey;
            this.room_slot = roomSlot;
            this.items = items;
        }
    }

    /** Which stay of the booking this request is for (the server needs it when several stays are in house). */
    public Long stay_id;
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

    /** Adds the amenities for one BOOKED room of a stay that has not checked in yet ("Deluxe Room (room 1)"). */
    public void addBookedRoom(long roomTypeId, String optionKey, int roomSlot, List<Item> roomItems) {
        if (rooms == null) rooms = new ArrayList<>();
        rooms.add(new RoomLine(roomTypeId, optionKey, roomSlot, roomItems));
    }

    public boolean isEmpty() {
        return (rooms == null || rooms.isEmpty()) && (items == null || items.isEmpty());
    }
}
