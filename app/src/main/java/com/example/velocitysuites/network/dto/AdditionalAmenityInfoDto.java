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

    /** A stay of the booking that is in a room right now - the only kind an amenity can be requested for. */
    public static class Stay {
        public long stay_id;
        public int position;
        public String check_in;
        public String check_out;
        public int nights;
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
