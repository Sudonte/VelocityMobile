package com.example.velocitysuites.network;

import static org.junit.Assert.assertEquals;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.network.dto.BookingDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.example.velocitysuites.network.dto.RoomTypeDto;

import org.junit.Test;

/**
 * One transaction, one number: a reservation converted into a booking is Booking #<bookings.id> on the receptionist web,
 * so the guest app must display that id (not the reservation's), while getId() keeps keying API calls and caches.
 */
public class BookingDisplayIdTest {

    private static ReservationDto reservation(long id) {
        ReservationDto dto = new ReservationDto();
        dto.id = id;
        dto.room_type_id = 1;
        dto.room_type = new RoomTypeDto();
        dto.room_type.name = "Deluxe";
        dto.room_type.rate = "2500";
        dto.check_in = "2026-10-12";
        dto.check_out = "2026-10-13";
        dto.rooms_requested = 1;
        dto.adults = 1;
        dto.number_of_guests = 1;
        dto.status = "CONVERTED_TO_BOOKING";
        return dto;
    }

    @Test
    public void convertedReservationDisplaysTheBookingId() {
        ReservationDto dto = reservation(606);
        dto.booking = new BookingDto();
        dto.booking.id = 521;

        Booking booking = ApiMapper.toBooking(dto);

        assertEquals("606", booking.getId());
        assertEquals("521", booking.getDisplayId());
    }

    @Test
    public void unconvertedReservationDisplaysItsOwnId() {
        ReservationDto dto = reservation(606);
        dto.status = "PENDING";

        Booking booking = ApiMapper.toBooking(dto);

        assertEquals("606", booking.getDisplayId());
    }
}
