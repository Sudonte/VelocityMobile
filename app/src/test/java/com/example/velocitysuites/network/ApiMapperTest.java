package com.example.velocitysuites.network;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.network.dto.BillingDto;
import com.example.velocitysuites.network.dto.BookingDto;
import com.example.velocitysuites.network.dto.BookingRoomDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.example.velocitysuites.network.dto.RoomTypeDto;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Pure-JVM coverage for ApiMapper's rooms[] handling - the exact question this
 * investigation pass exists to answer: does Booking#getRooms() actually end up
 * populated when the DTO carries itemized room lines, and does it fall back safely
 * when it doesn't? ApiMapper has zero Android imports, so this exercises the real
 * production mapping code directly, not a reimplementation of it.
 */
public class ApiMapperTest {

    private static ReservationDto baseDto() {
        ReservationDto dto = new ReservationDto();
        dto.id = 501;
        dto.room_type_id = 1;
        dto.room_type = new RoomTypeDto();
        dto.room_type.name = "Deluxe";
        dto.room_type.rate = "2500";
        dto.check_in = "2025-05-01";
        dto.check_out = "2025-05-05";
        dto.rooms_requested = 1;
        dto.adults = 2;
        dto.children = 0;
        dto.number_of_guests = 2;
        dto.status = "TO_BE_CONVERTED";
        dto.total_amount_due = 10000.0;
        return dto;
    }

    private static BookingRoomDto roomLine(String typeId, String typeName, int quantity, double pricePerNight, int nights) {
        return roomLine(typeId, typeName, quantity, pricePerNight, nights, null);
    }

    private static BookingRoomDto roomLine(String typeId, String typeName, int quantity, double pricePerNight, int nights, List<String> assignedRoomNumbers) {
        BookingRoomDto line = new BookingRoomDto();
        line.room_type_id = typeId;
        line.room_type = typeName;
        line.quantity = quantity;
        line.price_per_night = pricePerNight;
        line.nights = nights;
        line.subtotal = pricePerNight * quantity * nights;
        line.assigned_room_numbers = assignedRoomNumbers;
        return line;
    }

    // ---- Pre-conversion Reservation: dto.room_lines populated (confirmed live 2026-09-18 - see ReservationDto#room_lines's own doc) ----

    @Test
    public void toBooking_populatedRoomLines_preservesAllRoomTypes() {
        ReservationDto dto = baseDto();
        List<BookingRoomDto> rooms = new ArrayList<>();
        rooms.add(roomLine("1", "Deluxe", 2, 2500.0, 4));
        rooms.add(roomLine("2", "Suite", 1, 4000.0, 4));
        dto.room_lines = rooms;

        Booking booking = ApiMapper.toBooking(dto);

        assertEquals(2, booking.getRooms().size());
        List<String> types = booking.getAllRoomTypeNames();
        assertEquals(2, types.size());
        assertTrue(booking.hasRoomType("Deluxe"));
        assertTrue(booking.hasRoomType("Suite"));
        assertEquals(3, booking.getTotalRoomCount());
    }

    @Test
    public void toBooking_multiRoomDto_allThreeTypesPreserved() {
        ReservationDto dto = baseDto();
        List<BookingRoomDto> rooms = new ArrayList<>();
        rooms.add(roomLine("1", "Deluxe", 2, 2500.0, 4));
        rooms.add(roomLine("2", "Suite", 1, 4000.0, 4));
        rooms.add(roomLine("3", "Family Room", 1, 3200.0, 4));
        dto.room_lines = rooms;

        Booking booking = ApiMapper.toBooking(dto);

        assertEquals(3, booking.getRooms().size());
        assertTrue(booking.hasRoomType("Deluxe"));
        assertTrue(booking.hasRoomType("Suite"));
        assertTrue(booking.hasRoomType("Family Room"));
    }

    @Test
    public void toBooking_populatedRoomLines_carriesAssignedRoomNumbersPerType() {
        // Confirmed live 2026-09-18: BookingRoomDto#assigned_room_numbers, grouped
        // server-side by room_type_id from the booking's real physical assignments.
        ReservationDto dto = baseDto();
        List<BookingRoomDto> rooms = new ArrayList<>();
        rooms.add(roomLine("1", "Deluxe", 2, 2500.0, 4, java.util.Arrays.asList("201", "202")));
        rooms.add(roomLine("2", "Suite", 1, 4000.0, 4, java.util.Collections.emptyList()));
        dto.room_lines = rooms;

        Booking booking = ApiMapper.toBooking(dto);

        assertEquals(java.util.Arrays.asList("201", "202"), booking.getRooms().get(0).getAssignedRoomNumbers());
        assertTrue("Suite has no physical assignment yet (pre-check-in) - must be empty, not null",
                booking.getRooms().get(1).getAssignedRoomNumbers().isEmpty());
    }

    // ---- Empty/null room_lines: legacy single-field fallback must remain available ----

    @Test
    public void toBooking_emptyRoomLines_fallsBackToLegacyRoomType() {
        ReservationDto dto = baseDto();
        dto.room_lines = new ArrayList<>(); // present but empty - a genuinely different JSON shape than null

        Booking booking = ApiMapper.toBooking(dto);

        assertTrue(booking.getRooms().isEmpty());
        assertEquals("Deluxe", booking.getRoomType());
        assertEquals(1, booking.getAllRoomTypeNames().size());
        assertEquals("Deluxe", booking.getAllRoomTypeNames().get(0));
        assertTrue(booking.hasRoomType("Deluxe"));
    }

    @Test
    public void toBooking_nullRoomLines_doesNotCrash_fallsBackToLegacyRoomType() {
        ReservationDto dto = baseDto();
        dto.room_lines = null; // a transaction predating this feature

        Booking booking = ApiMapper.toBooking(dto);

        assertNotNull(booking.getRooms());
        assertTrue(booking.getRooms().isEmpty());
        assertEquals("Deluxe", booking.getRoomType());
        assertEquals("Deluxe", booking.getAllRoomTypeNames().get(0));
    }

    @Test
    public void toBooking_legacyOnlyDto_missingRoomLinesFieldEntirely_stillReadable() {
        // A ReservationDto built the way Gson would for an old cached response that
        // predates the room_lines[] field existing at all - the field is simply never
        // assigned (stays at its default null), same runtime shape as the explicit
        // null case above, but written to mirror "old transaction" rather than
        // "explicit null" intent.
        ReservationDto dto = baseDto();

        Booking booking = ApiMapper.toBooking(dto);

        assertTrue(booking.getRooms().isEmpty());
        assertEquals("Deluxe", booking.getRoomType());
        assertEquals(1, booking.getRoomsRequested());
    }

    // ---- Post-conversion: BillingDto#room_lines (confirmed live 2026-09-18) ----

    @Test
    public void toBooking_postConversion_billingRoomLinesPopulated_usesItemizedBreakdown() {
        // Corrects an earlier version of this test, which documented a real gap that
        // existed until 2026-09-18: BillingDto#rooms (now #room_lines) was never
        // actually returned by the live backend post-conversion, so a reservation
        // that itemized correctly before conversion (dto.room_lines populated)
        // regressed to the single legacy field the moment it became a paid Booking.
        // Fixed server-side (Billing::getRoomLinesAttribute() delegates to the owning
        // Booking's own accessor) - this now asserts the itemization survives
        // conversion instead of asserting the old regression.
        ReservationDto dto = baseDto();
        List<BookingRoomDto> preConversionRooms = new ArrayList<>();
        preConversionRooms.add(roomLine("1", "Deluxe", 2, 2500.0, 4));
        preConversionRooms.add(roomLine("2", "Suite", 1, 4000.0, 4));
        dto.room_lines = preConversionRooms;

        dto.booking = new BookingDto();
        dto.booking.booking_status = "Confirmed";
        dto.booking.billing = new BillingDto();
        dto.booking.billing.total_amount = "26150.00";
        dto.booking.billing.billing_status = "paid";
        List<BookingRoomDto> postConversionRooms = new ArrayList<>();
        postConversionRooms.add(roomLine("1", "Deluxe", 2, 2500.0, 4, java.util.Arrays.asList("201", "202")));
        postConversionRooms.add(roomLine("2", "Suite", 1, 4000.0, 4, java.util.Arrays.asList("305")));
        dto.booking.billing.room_lines = postConversionRooms;

        Booking booking = ApiMapper.toBooking(dto);

        assertEquals("Itemization must survive conversion, sourced from booking.billing.room_lines, not the pre-conversion snapshot",
                2, booking.getRooms().size());
        assertTrue(booking.hasRoomType("Deluxe"));
        assertTrue(booking.hasRoomType("Suite"));
        assertEquals(java.util.Arrays.asList("201", "202"), booking.getRooms().get(0).getAssignedRoomNumbers());
        assertTrue(booking.isHasBooking());
    }

    @Test
    public void toBooking_postConversion_billingRoomLinesNull_fallsBackToLegacyRoomType() {
        // Still a real, valid case - a Booking created before this feature shipped.
        ReservationDto dto = baseDto();
        dto.booking = new BookingDto();
        dto.booking.booking_status = "Confirmed";
        dto.booking.billing = new BillingDto();
        dto.booking.billing.total_amount = "10000.00";
        dto.booking.billing.billing_status = "paid";
        dto.booking.billing.room_lines = null;

        Booking booking = ApiMapper.toBooking(dto);

        assertTrue(booking.getRooms().isEmpty());
        assertEquals("Deluxe", booking.getRoomType());
        assertTrue(booking.isHasBooking());
    }
}
