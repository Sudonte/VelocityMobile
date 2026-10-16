package com.example.velocitysuites.network.dto;

import java.util.List;

/** Whether the guest may request more amenities on this stay (and why not), the rooms they can pick amenities for, and the requests so far. */
public class AdditionalAmenityInfoDto {
    /** One room of the stay (assigned at check-in) that an amenity can be requested for. */
    public static class Room {
        public long room_id;
        public String room_number;
        public String room_name;
        public long room_type_id;
        public String room_type;
    }

    public boolean can_request;
    public String reason;
    /** Absent on an older server: the app then offers one generic "Your room". */
    public List<Room> rooms;
    public List<AdditionalAmenityRequestDto> requests;
    public double total_approved;
}
