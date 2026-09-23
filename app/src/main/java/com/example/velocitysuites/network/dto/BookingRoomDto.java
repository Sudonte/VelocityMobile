package com.example.velocitysuites.network.dto;

/**
 * One room-type line item within a multi-room-type Booking/Reservation
 * transaction - see MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md (root of the
 * repo) for the `booking_rooms`/`reservation_rooms` backend contract this
 * maps. Already returned by the live API on DirectBookingResponseDto#rooms
 * and (pre-conversion) ReservationDto#rooms for a transaction created
 * through this app's own multi-room submission - see each of those fields'
 * own docs for exactly when each is populated vs. still empty. Only
 * BillingDto#rooms (the post-conversion breakdown) remains genuinely
 * unshipped - see that field's own doc. ApiMapper#toBookingRooms() handles
 * a null/empty/missing list as a graceful no-op either way.
 */
public class BookingRoomDto {
    public String room_type_id;
    public String room_type;
    public int quantity;
    public double price_per_night;
    public int nights;
    public double subtotal;
    /**
     * The physical room number(s) actually assigned to this room type at
     * check-in (e.g. ["201", "202"] for "Deluxe x2") - null/empty before
     * check-in, or for a still-unconverted Reservation (which never has
     * physical room assignments of its own). See Booking::
     * getRoomLinesAttribute() (backend) for how this is grouped by room type.
     */
    public java.util.List<String> assigned_room_numbers;
}
