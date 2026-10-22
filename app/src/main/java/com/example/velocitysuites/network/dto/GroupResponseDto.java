package com.example.velocitysuites.network.dto;

import java.util.List;

/** POST api/guest/booking-groups: every stay that was created, as bookings or as reservations, tied by one group id. */
public class GroupResponseDto {
    public long group_id;
    /** "booking" or "reservation". */
    public String type;
    public double total;
    public List<DirectBookingResponseDto> bookings;
    public List<ReservationDto> reservations;
}
