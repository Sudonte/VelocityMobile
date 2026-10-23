package com.example.velocitysuites.network.dto;

import java.util.List;

/**
 * One stay of a booking or reservation, as the server lists it under {@code stays} (HasStays::staysPayload()): its own dates and nights,
 * where it stands (upcoming / in_house / checked_out), the rooms booked for it (with the physical room numbers once assigned), its
 * amenities, and its own subtotal. A server without stays simply does not send the field.
 */
public class StayDto {
    public Long id;
    public int position;
    public String check_in;
    public String check_out;
    public int nights;
    public String state;
    public String checked_in_at;
    public String checked_out_at;
    public List<BookingRoomDto> rooms;
    public List<StayAmenityDto> amenities;
    public double rooms_subtotal;
    public double amenities_subtotal;
    public double subtotal;

    public static class StayAmenityDto {
        public String amenity_id;
        public String amenity_name;
        public int quantity;
        public double unit_price;
        public double subtotal;
        public String status;
    }
}
