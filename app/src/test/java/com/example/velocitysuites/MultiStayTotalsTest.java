package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * THE calculation, on the two transactions from the device: two ONE-night stays (Oct 26 and Oct 27, an overall span of two nights), each
 * with a Deluxe (3,500) and an Executive (5,800). Every room is paid for ITS OWN stay's night: (3,500 + 5,800) x 1 night x 2 stays = 18,600 -
 * not price x the 2-night span (which made 37,200).
 */
public class MultiStayTotalsTest {

    @Test
    public void rooms_haveTheirNames_andTheirOwnStaysNights() {
        for (Booking b : new Booking[]{MultiStayFixtures.booking(), MultiStayFixtures.reservation("pending")}) {
            assertEquals(4, b.getRooms().size());
            for (BookingRoom r : b.getRooms()) assertNotNull("room type name", r.getRoomTypeName());
            assertEquals(2, b.getStays().size());
            for (Booking.StayRecord stay : b.getStays()) assertEquals(1, BookingTotals.nightsOf(stay));
            assertEquals("Deluxe ×2 • Executive ×2", TransactionRow.roomTextOf(b));
        }
    }

    @Test
    public void booking_roomCharges18600_amenities300_grandTotal18900() {
        Booking b = MultiStayFixtures.booking();
        assertEquals(18600.0, BookingTotals.roomCharges(b), 0.001);
        assertEquals(300.0, BookingTotals.amenities(b), 0.001);
        assertEquals(18900.0, BookingTotals.grandTotal(b), 0.001);
        assertEquals("rooms + amenities = the grand total", BookingTotals.grandTotal(b), BookingTotals.roomCharges(b) + BookingTotals.amenities(b), 0.001);
        assertEquals(2, BookingTotals.roomTypeCount(b));
        assertEquals(4, BookingTotals.roomCount(b));
    }

    @Test
    public void reservation_roomCharges18600_amenities415_grandTotal19015() {
        Booking r = MultiStayFixtures.reservation("pending");
        assertEquals(18600.0, BookingTotals.roomCharges(r), 0.001);
        assertEquals(415.0, BookingTotals.amenities(r), 0.001);
        assertEquals(19015.0, BookingTotals.grandTotal(r), 0.001);
        assertEquals(2, BookingTotals.roomTypeCount(r));
        assertEquals(4, BookingTotals.roomCount(r));
    }

    @Test
    public void eachStaysRoomsAreWorth9300_notTheSpan() {
        Booking b = MultiStayFixtures.booking();
        for (Booking.StayRecord stay : b.getStays()) assertEquals(9300.0, BookingTotals.stayRoomCharges(stay), 0.001);
        BookingRoom deluxe = b.getStays().get(0).rooms.get(0);
        assertEquals(3500.0, BookingTotals.lineTotal(deluxe, b.getStays().get(0)), 0.001);
        assertTrue("never price x 2 nights", BookingTotals.roomCharges(b) < 37200.0);
    }

    @Test
    public void aDiscountWaitingForTheIdCheckIsExpected_notSubtracted() {
        Booking r = MultiStayFixtures.reservation("pending");
        assertFalse(BookingTotals.discountApplied(r));
        assertEquals(0.0, BookingTotals.discountAppliedAmount(r), 0.001);
        assertEquals(3803.0, BookingTotals.expectedDiscount(r), 0.001);
        assertEquals("the grand total stays whole", 19015.0, BookingTotals.grandTotal(r), 0.001);
        assertEquals(15212.0, BookingTotals.estimatedAfterDiscount(r), 0.001);
        assertEquals("PWD", BookingTotals.discountLabel(r));
    }

    @Test
    public void anAppliedDiscountIsInTheBreakdown_andSubtracted() {
        Booking r = MultiStayFixtures.reservation("approved");
        r.setTotalAmount(15212.0); // what the server sends once the discount is applied
        assertTrue(BookingTotals.discountApplied(r));
        assertEquals(3803.0, BookingTotals.discountAppliedAmount(r), 0.001);
        assertEquals(0.0, BookingTotals.expectedDiscount(r), 0.001);
        assertEquals("rooms + amenities - discount = grand total", 15212.0,
                BookingTotals.roomCharges(r) + BookingTotals.amenities(r) - BookingTotals.discountAppliedAmount(r), 0.001);
        assertEquals(15212.0, BookingTotals.grandTotal(r), 0.001);
    }

    @Test
    public void aRoomLineFromTheRawRowShapeStillHasItsName() {
        com.example.velocitysuites.network.dto.BookingRoomDto raw = new com.google.gson.Gson().fromJson(
                "{\"room_type_id\":1,\"room_type_name\":\"Deluxe\",\"quantity\":1,\"price_per_night\":\"3500.00\",\"number_of_nights\":1,\"subtotal\":\"3500.00\"}",
                com.example.velocitysuites.network.dto.BookingRoomDto.class);
        assertEquals("Deluxe", raw.room_type_name);
        assertEquals(Integer.valueOf(1), raw.number_of_nights);
    }
}
