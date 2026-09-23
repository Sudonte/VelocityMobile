package com.example.velocitysuites.network.dto;

/**
 * An individual physical room/unit, assigned by staff at booking confirmation/check-in time - see
 * ApiMapper#toBooking(), which reads this off ReservationDto.booking.room / DirectBookingResponseDto.room.
 * Distinct from RoomTypeDto, which is the bookable catalog entry a guest actually selects.
 */
public class RoomDto {
    public long id;
    public String room_number;
    public String room_name;
}
