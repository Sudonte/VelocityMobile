package com.example.velocitysuites.network.dto;

import java.util.List;

/** Whether the guest may request more amenities on this stay (and why not), the rooms they can pick amenities for, and the requests so far. */
public class AdditionalAmenityInfoDto {
    /** One room of the stay (assigned at check-in) that an amenity can be requested for. */
    public static class Room {
        /** The stay of the booking this room belongs to (null from an older server). */
        public Long stay_id;
        public long room_id;
        public String room_number;
        public String room_name;
        public long room_type_id;
        public String room_type;
    }

    /** A room that was BOOKED for a stay still to arrive ("Deluxe Room (room 1)"): what an amenity is requested for before check-in. */
    public static class Slot {
        public long room_type_id;
        public String option_key;
        public int room_slot;
        public String label;
    }

    /**
     * A stay of the booking an amenity can be requested for: one in a room right now (its assigned rooms are in the top-level
     * {@code rooms}), or - since 1.74.0 - one still to arrive, which lists its booked rooms as {@code slots}.
     */
    public static class Stay {
        public long stay_id;
        public int position;
        public String check_in;
        public String check_out;
        public int nights;
        /** "upcoming" before check-in, "in_house" during the stay; absent on an older server. */
        public String state;
        /** The booked rooms of a stay that has not checked in yet; empty or absent for one in house. */
        public List<Slot> slots;
    }

    public boolean can_request;
    /** The stays currently checked in; absent on an older server. */
    public List<Stay> stays;
    public String reason;
    /** Absent on an older server: the app then offers one generic "Your room". */
    public List<Room> rooms;
    public List<AdditionalAmenityRequestDto> requests;
    public double total_approved;
}
