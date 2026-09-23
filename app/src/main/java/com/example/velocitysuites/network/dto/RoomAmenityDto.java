package com.example.velocitysuites.network.dto;

/** One amenity inherited from a room's Room Type (display-only pricing info) - see ApiMapper#toRoomAmenities() and RoomAmenity. */
public class RoomAmenityDto {
    public String name;
    public String category;
    public String description;
    /** RoomAmenity.PRICING_COMPLIMENTARY or RoomAmenity.PRICING_PAID. */
    public String pricing_type;
    public String fee;
}
