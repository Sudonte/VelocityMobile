package com.example.velocitysuites;

import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class RoomRepositoryTest {

    private RoomRepository repository;
    private Room room;

    @Before
    public void setUp() {
        repository = RoomRepository.getInstance(null);
        repository.clearBookings();
        room = new Room("R1", "Deluxe 101", "Deluxe", 2, 2500.0, "A deluxe room",
                0, true, Collections.<RoomAmenity>emptyList(), "Queen", "24 sqm", "No smoking");
        repository.setRoomsForTesting(Collections.singletonList(room));
    }

    @Test
    public void testIsAvailableForDates_NoBookings() {
        Calendar checkIn = Calendar.getInstance();
        checkIn.set(2025, Calendar.MAY, 1);
        Calendar checkOut = Calendar.getInstance();
        checkOut.set(2025, Calendar.MAY, 5);

        assertTrue("Room should be available when there are no bookings",
                repository.isAvailableForDates(room, checkIn, checkOut, null));
    }

    @Test
    public void testIsAvailableForDates_WithConflict() {
        Booking booking = new Booking(
                "B1", room.getId(), room.getName(), room.getType(),
                "May 01, 2025", "May 05, 2025", 2, 500.0, "Confirmed", "Apr 30, 2025"
        );
        repository.addBookingForTesting(booking);

        // Try to book overlapping dates
        Calendar checkIn = Calendar.getInstance();
        checkIn.set(2025, Calendar.MAY, 3);
        Calendar checkOut = Calendar.getInstance();
        checkOut.set(2025, Calendar.MAY, 7);

        assertFalse("Room should not be available due to overlap",
                repository.isAvailableForDates(room, checkIn, checkOut, null));
    }

    @Test
    public void testIsAvailableForDates_ExcludeCurrentBooking() {
        String bookingId = "B2";
        Booking booking = new Booking(
                bookingId, room.getId(), room.getName(), room.getType(),
                "May 10, 2025", "May 15, 2025", 2, 500.0, "Confirmed", "Apr 30, 2025"
        );
        repository.addBookingForTesting(booking);

        Calendar checkIn = Calendar.getInstance();
        checkIn.set(2025, Calendar.MAY, 10);
        Calendar checkOut = Calendar.getInstance();
        checkOut.set(2025, Calendar.MAY, 15);

        assertTrue("Should be available when excluding the current booking ID",
                repository.isAvailableForDates(room, checkIn, checkOut, bookingId));
    }

    @Test
    public void testIsAvailableForDates_Adjacency() {
        // Existing booking May 1-5
        repository.addBookingForTesting(new Booking(
                "B1", room.getId(), room.getName(), room.getType(),
                "May 01, 2025", "May 05, 2025", 2, 500.0, "Confirmed", "Apr 30, 2025"
        ));

        // New booking starting May 5
        Calendar checkIn = Calendar.getInstance();
        checkIn.set(2025, Calendar.MAY, 5);
        Calendar checkOut = Calendar.getInstance();
        checkOut.set(2025, Calendar.MAY, 10);

        assertTrue("Should be available if new check-in is on old check-out date",
                repository.isAvailableForDates(room, checkIn, checkOut, null));

        // New booking ending May 1
        checkIn.set(2025, Calendar.APRIL, 25);
        checkOut.set(2025, Calendar.MAY, 1);
        assertTrue("Should be available if new check-out is on old check-in date",
                repository.isAvailableForDates(room, checkIn, checkOut, null));
    }

    @Test
    public void testIsAvailableForDates_IgnoresCancelledBooking() {
        repository.addBookingForTesting(new Booking(
                "B1", room.getId(), room.getName(), room.getType(),
                "May 01, 2025", "May 05, 2025", 2, 500.0, "Cancelled", "Apr 30, 2025"
        ));

        Calendar checkIn = Calendar.getInstance();
        checkIn.set(2025, Calendar.MAY, 2);
        Calendar checkOut = Calendar.getInstance();
        checkOut.set(2025, Calendar.MAY, 4);

        assertTrue("A Cancelled booking must never block a room's availability",
                repository.isAvailableForDates(room, checkIn, checkOut, null));
    }

    @Test
    public void clearAccountSpecificCache_bumpsAccountGeneration() {
        // The exact mechanism refreshBookings()/refreshNotifications() use to detect
        // and discard a stale, still-in-flight previous account's response after a
        // logout (see accountGeneration's own field doc) - a real end-to-end race
        // (request started in session A, session B logs in, session A's response
        // lands late) isn't reachable from a plain JVM test without faking the whole
        // Retrofit Call stack, so this verifies the one deterministic, pure unit the
        // fix actually depends on: logout must change the generation at all.
        int before = repository.getAccountGenerationForTesting();
        repository.clearAccountSpecificCache();
        assertEquals(before + 1, repository.getAccountGenerationForTesting());
    }

    @Test
    public void clearAccountSpecificCache_alsoClearsBookings() {
        Booking booking = new Booking(
                "B1", room.getId(), room.getName(), room.getType(),
                "May 01, 2025", "May 05, 2025", 2, 500.0, "Confirmed", "Apr 30, 2025"
        );
        repository.addBookingForTesting(booking);
        assertFalse(repository.getBookings().isEmpty());

        repository.clearAccountSpecificCache();
        assertTrue(repository.getBookings().isEmpty());
    }

    @Test
    public void testFindOverlappingBooking_MatchesAnyRoomTypeInMultiTypeTransaction() {
        // A multi-room-type transaction (e.g. "Deluxe + Suite") must still be
        // found as an overlap when only its second room type is queried -
        // see Booking#hasRoomType()/getAllRoomTypeNames().
        Booking multiType = new Booking(
                "B1", room.getId(), room.getName(), "Executive",
                "May 01, 2025", "May 05, 2025", 2, 500.0, "Confirmed", "Apr 30, 2025"
        );
        List<BookingRoom> rooms = new java.util.ArrayList<>();
        rooms.add(new BookingRoom("1", "Executive", 1, 3000.0, 4, 12000.0, null));
        rooms.add(new BookingRoom("2", "Deluxe", 2, 2500.0, 4, 20000.0, null));
        multiType.setRooms(rooms);
        repository.addBookingForTesting(multiType);

        Calendar checkIn = Calendar.getInstance();
        checkIn.set(2025, Calendar.MAY, 2);
        Calendar checkOut = Calendar.getInstance();
        checkOut.set(2025, Calendar.MAY, 3);

        assertNotNull("Querying the second room type of a multi-type booking must still find the overlap",
                repository.findOverlappingBooking("Deluxe", checkIn, checkOut, null));
    }
}
