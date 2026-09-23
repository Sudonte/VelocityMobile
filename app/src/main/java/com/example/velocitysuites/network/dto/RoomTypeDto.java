package com.example.velocitysuites.network.dto;

import java.util.List;

/**
 * A bookable room category (guests browse/book by TYPE, never an individual room/unit) - see
 * Api\CatalogController::rooms() and ApiMapper#toRoom(). Decimal-typed backend columns (rate)
 * arrive as JSON strings, same convention as AmenityDto.charge/DiscountDto.value.
 */
public class RoomTypeDto {
    public long id;
    public String name;
    public int capacity;
    public String rate;
    public String description;
    public boolean is_fully_booked;
    /** Rooms of this type still bookable for the guest's requested date range (date-range-aware, see RoomAvailabilityService). */
    public int available_count;
    public String bed_type;
    public String room_size;
    public String policies;
    public String image_url;
    public List<RoomAmenityDto> amenities;
    public List<RoomGalleryImageDto> gallery;

    public double rateAsDouble() {
        try {
            return rate != null ? Double.parseDouble(rate) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Ready-to-display room size (e.g. "35 sqm"), or "" when the backend hasn't set one - see Room#getRoomSize()'s callers. */
    public String formattedRoomSize() {
        if (room_size == null || room_size.trim().isEmpty()) return "";
        return room_size.trim() + " sqm";
    }
}
