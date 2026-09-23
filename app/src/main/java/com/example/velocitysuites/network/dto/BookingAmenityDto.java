package com.example.velocitysuites.network.dto;

/**
 * One paid-amenity line item within a Booking/Reservation transaction - see
 * MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md (root of the repo) for the proposed
 * `booking_amenities`/`reservation_amenities` backend contract this maps.
 * Not returned by the live API today - every field here stays null/unused
 * until the backend ships that contract.
 */
public class BookingAmenityDto {
    public String amenity_id;
    public String amenity_name;
    public int quantity;
    public double unit_price;
    public double subtotal;
}
