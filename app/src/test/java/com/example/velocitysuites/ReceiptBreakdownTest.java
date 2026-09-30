package com.example.velocitysuites;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The receipt's Payment Summary arithmetic: Grand Total must equal Rooms Total +
 * Amenities Total + the other charges/deductions, for a booking WITH amenities and one
 * WITHOUT, on both receipt paths (receipt-number mode and the legacy Booking snapshot).
 * Pure JVM - ReceiptBreakdown has no Context/Views on purpose.
 */
public class ReceiptBreakdownTest {

    private static final double DELTA = 0.001;

    private static BookingRoom room(String name, int qty, double rate, long nights, double subtotal) {
        return new BookingRoom("T-" + name, name, qty, rate, nights, subtotal, null);
    }

    private static BookingAmenity amenity(String name, int qty, double unit, double subtotal) {
        return new BookingAmenity("A-" + name, name, qty, unit, subtotal);
    }

    private static ReceiptDetail receipt(List<BookingRoom> rooms, int nights, double grandTotal, double discount) {
        Booking.PaymentSummary summary = new Booking.PaymentSummary(grandTotal, grandTotal, 0.0, "PAID", 100, true, discount);
        return new ReceiptDetail("OFFICIAL_RECEIPT", "OR-20260930-000001", "250", null,
                "Juan Dela Cruz", "Juan Dela Cruz", "Deluxe Room", rooms,
                "Oct 01, 2026", "Oct 04, 2026", nights, Collections.<String>emptyList(), 2, 0,
                summary, Collections.<Booking.PaymentTransactionRecord>emptyList(), null, "2026-09-30T10:00:00+08:00");
    }

    /** The printed arithmetic itself: every line the receipt shows must add up to the Grand Total it shows. */
    private static void assertAddsUp(ReceiptBreakdown b) {
        double sum = b.roomsTotal + b.amenitiesTotal + b.additionalGuestFee - b.discount + b.otherCharges;
        assertEquals("Rooms + Amenities + fees - discount + other must equal the Grand Total", b.grandTotal, sum, DELTA);
    }

    // ---- A booking WITH amenities ----

    @Test
    public void withAmenities_listsEveryRoomAndAmenity_andTotalsAddUp() {
        List<BookingRoom> rooms = Arrays.asList(
                room("Deluxe Room", 2, 2500.0, 3, 15000.0),
                room("Executive Suite", 1, 4000.0, 3, 12000.0));
        List<BookingAmenity> amenities = Arrays.asList(
                amenity("Breakfast Package", 2, 350.0, 700.0),
                amenity("Airport Transfer", 1, 1200.0, 1200.0),
                amenity("Extra Bed", 3, 500.0, 1500.0));

        ReceiptBreakdown b = ReceiptBreakdown.forReceiptDetail(receipt(rooms, 3, 30400.0, 0), amenities);

        // Selected Rooms: name, rate/night, nights, subtotal per room + Rooms Total
        assertEquals(2, b.roomLines.size());
        ReceiptBreakdown.RoomLine deluxe = b.roomLines.get(0);
        assertEquals("Deluxe Room", deluxe.name);
        assertEquals(2, deluxe.quantity);
        assertEquals(2500.0, deluxe.ratePerNight, DELTA);
        assertEquals(3, deluxe.nights);
        assertEquals(15000.0, deluxe.subtotal, DELTA);
        assertEquals(12000.0, b.roomLines.get(1).subtotal, DELTA);
        assertEquals(27000.0, b.roomsTotal, DELTA);

        // Amenities: name, quantity, unit price, subtotal per amenity + Amenities Total
        assertEquals(ReceiptBreakdown.AmenityStatus.ITEMIZED, b.amenityStatus);
        assertEquals(3, b.amenityLines.size());
        ReceiptBreakdown.AmenityLine breakfast = b.amenityLines.get(0);
        assertEquals("Breakfast Package", breakfast.name);
        assertEquals(2, breakfast.quantity);
        assertEquals(350.0, breakfast.unitPrice, DELTA);
        assertEquals(700.0, breakfast.subtotal, DELTA);
        assertEquals(3400.0, b.amenitiesTotal, DELTA);

        // Grand Total = Rooms Total + Amenities Total, nothing left over.
        assertEquals(30400.0, b.grandTotal, DELTA);
        assertEquals(0.0, b.otherCharges, DELTA);
        assertAddsUp(b);
    }

    // ---- A booking WITHOUT amenities ----

    @Test
    public void withoutAmenities_isKnownToHaveNone_andGrandTotalIsRoomsOnly() {
        List<BookingRoom> rooms = Collections.singletonList(room("Deluxe Room", 1, 2500.0, 2, 5000.0));

        ReceiptBreakdown b = ReceiptBreakdown.forReceiptDetail(receipt(rooms, 2, 5000.0, 0), Collections.<BookingAmenity>emptyList());

        assertEquals(ReceiptBreakdown.AmenityStatus.NONE, b.amenityStatus);
        assertTrue(b.amenityLines.isEmpty());
        assertEquals(0.0, b.amenitiesTotal, DELTA);
        assertEquals(5000.0, b.roomsTotal, DELTA);
        assertEquals(5000.0, b.grandTotal, DELTA);
        assertEquals(0.0, b.otherCharges, DELTA);
        assertAddsUp(b);
    }

    // ---- Existing deductions/charges are kept, and still add up ----

    @Test
    public void discount_isDeductedFromRoomsAndAmenities_toReachTheGrandTotal() {
        List<BookingRoom> rooms = Collections.singletonList(room("Suite", 1, 5000.0, 2, 10000.0));
        List<BookingAmenity> amenities = Collections.singletonList(amenity("Spa Package", 1, 500.0, 500.0));

        // 10,000 + 500 - 1,050 (10% senior discount) = 9,450
        ReceiptBreakdown b = ReceiptBreakdown.forReceiptDetail(receipt(rooms, 2, 9450.0, 1050.0), amenities);

        assertEquals(1050.0, b.discount, DELTA);
        assertEquals(9450.0, b.grandTotal, DELTA);
        assertEquals(0.0, b.otherCharges, DELTA);
        assertAddsUp(b);
    }

    @Test
    public void aGrandTotalTheLinesDontExplain_isShownAsOtherCharges_soTheArithmeticStillAddsUp() {
        // e.g. an additional guest fee the receipt payload doesn't itemize: total 10,500 vs rooms 10,000.
        List<BookingRoom> rooms = Collections.singletonList(room("Deluxe Room", 1, 2500.0, 4, 10000.0));

        ReceiptBreakdown b = ReceiptBreakdown.forReceiptDetail(receipt(rooms, 4, 10500.0, 0), Collections.<BookingAmenity>emptyList());

        assertEquals("the backend's total is never overridden", 10500.0, b.grandTotal, DELTA);
        assertEquals(500.0, b.otherCharges, DELTA);
        assertAddsUp(b);
    }

    @Test
    public void aGrandTotalBelowTheLines_becomesANegativeAdjustment() {
        List<BookingRoom> rooms = Collections.singletonList(room("Deluxe Room", 1, 2500.0, 4, 10000.0));

        ReceiptBreakdown b = ReceiptBreakdown.forReceiptDetail(receipt(rooms, 4, 9800.0, 0), Collections.<BookingAmenity>emptyList());

        assertEquals(-200.0, b.otherCharges, DELTA);
        assertAddsUp(b);
    }

    // ---- Honesty: unknown is not "none" ----

    @Test
    public void amenitiesThatCouldNotBeResolved_areUnknown_neverClaimedToBeNone() {
        List<BookingRoom> rooms = Collections.singletonList(room("Deluxe Room", 1, 2500.0, 4, 10000.0));

        ReceiptBreakdown b = ReceiptBreakdown.forReceiptDetail(receipt(rooms, 4, 11000.0, 0), null);

        assertEquals(ReceiptBreakdown.AmenityStatus.UNKNOWN, b.amenityStatus);
        assertTrue(b.amenityLines.isEmpty());
        // The unexplained 1,000 (most likely the amenities) stays visible as other charges rather than vanishing.
        assertEquals(1000.0, b.otherCharges, DELTA);
        assertAddsUp(b);
    }

    @Test
    public void receiptMode_anAmenityChargeOnRecordWithNoLines_isAnAmenitiesTotal_neverNoAmenities() {
        List<BookingRoom> rooms = Collections.singletonList(room("Deluxe Room", 1, 2500.0, 2, 5000.0));

        // Rooms 5,000 + 300 of amenities on record (but no per-amenity lines) = 5,300.
        ReceiptBreakdown b = ReceiptBreakdown.forReceiptDetail(receipt(rooms, 2, 5300.0, 0),
                Collections.<BookingAmenity>emptyList(), 300.0);

        assertEquals(ReceiptBreakdown.AmenityStatus.AGGREGATE_ONLY, b.amenityStatus);
        assertTrue(b.amenityLines.isEmpty());
        assertEquals(300.0, b.amenitiesTotal, DELTA);
        assertEquals("nothing left unexplained", 0.0, b.otherCharges, DELTA);
        assertAddsUp(b);
    }

    @Test
    public void receiptMode_noLinesAndNoAggregateCharge_isKnownToHaveNoAmenities() {
        List<BookingRoom> rooms = Collections.singletonList(room("Deluxe Room", 1, 2500.0, 2, 5000.0));

        ReceiptBreakdown b = ReceiptBreakdown.forReceiptDetail(receipt(rooms, 2, 5000.0, 0),
                Collections.<BookingAmenity>emptyList(), 0.0);

        assertEquals(ReceiptBreakdown.AmenityStatus.NONE, b.amenityStatus);
    }

    @Test
    public void receiptMode_realLinesWinOverTheAggregate() {
        List<BookingRoom> rooms = Collections.singletonList(room("Deluxe Room", 1, 2500.0, 2, 5000.0));
        List<BookingAmenity> lines = Collections.singletonList(amenity("Breakfast", 2, 250.0, 500.0));

        // The aggregate (500) is the same money as the lines - it must not be counted a second time.
        ReceiptBreakdown b = ReceiptBreakdown.forReceiptDetail(receipt(rooms, 2, 5500.0, 0), lines, 500.0);

        assertEquals(ReceiptBreakdown.AmenityStatus.ITEMIZED, b.amenityStatus);
        assertEquals(500.0, b.amenitiesTotal, DELTA);
        assertEquals(5500.0, b.grandTotal, DELTA);
        assertEquals(0.0, b.otherCharges, DELTA);
        assertAddsUp(b);
    }

    @Test
    public void anAmenityChargeOnRecordWithoutLines_isAggregateOnly_notNone() {
        List<BookingRoom> rooms = Collections.singletonList(room("Deluxe Room", 1, 2500.0, 2, 5000.0));

        ReceiptBreakdown b = ReceiptBreakdown.assemble(
                Collections.singletonList(ReceiptBreakdown.roomLineOf(rooms.get(0), 2)), 0,
                Collections.<BookingAmenity>emptyList(), 300.0, 0, 0, 5300.0);

        assertEquals(ReceiptBreakdown.AmenityStatus.AGGREGATE_ONLY, b.amenityStatus);
        assertEquals(300.0, b.amenitiesTotal, DELTA);
        assertAddsUp(b);
    }

    // ---- Missing/partial line data is filled in from what IS known, never invented ----

    @Test
    public void roomLineMissingNightsAndSubtotal_usesTheStaysNights_andComputesTheSubtotal() {
        List<BookingRoom> rooms = Collections.singletonList(room("Family Room", 2, 1500.0, 0, 0.0));

        ReceiptBreakdown b = ReceiptBreakdown.forReceiptDetail(receipt(rooms, 4, 12000.0, 0), Collections.<BookingAmenity>emptyList());

        ReceiptBreakdown.RoomLine line = b.roomLines.get(0);
        assertEquals(4, line.nights);
        assertEquals(12000.0, line.subtotal, DELTA); // 1,500 x 2 rooms x 4 nights
        assertAddsUp(b);
    }

    @Test
    public void amenityUnitPrice_isDerivedFromTheSubtotal_soQuantityTimesPriceAlwaysEqualsTheSubtotal() {
        List<BookingAmenity> amenities = Collections.singletonList(amenity("Late Checkout", 3, 0.0, 900.0));
        List<BookingRoom> rooms = Collections.singletonList(room("Deluxe Room", 1, 2500.0, 2, 5000.0));

        ReceiptBreakdown b = ReceiptBreakdown.forReceiptDetail(receipt(rooms, 2, 5900.0, 0), amenities);

        ReceiptBreakdown.AmenityLine line = b.amenityLines.get(0);
        assertEquals(300.0, line.unitPrice, DELTA);
        assertEquals(line.subtotal, line.unitPrice * line.quantity, DELTA);
    }

    @Test
    public void noAuthoritativeGrandTotal_fallsBackToTheSumOfTheParts() {
        List<BookingRoom> rooms = Collections.singletonList(room("Deluxe Room", 1, 2500.0, 2, 5000.0));
        List<BookingAmenity> amenities = Collections.singletonList(amenity("Breakfast", 2, 250.0, 500.0));

        ReceiptBreakdown b = ReceiptBreakdown.forReceiptDetail(receipt(rooms, 2, 0.0, 0), amenities);

        assertEquals(5500.0, b.grandTotal, DELTA);
        assertEquals(0.0, b.otherCharges, DELTA);
        assertAddsUp(b);
    }

    @Test
    public void aReceiptPredatingRoomLines_hasNoItemizedRooms_soCallersKeepTheSimpleSummary() {
        ReceiptBreakdown b = ReceiptBreakdown.forReceiptDetail(
                receipt(Collections.<BookingRoom>emptyList(), 3, 10000.0, 0), Collections.<BookingAmenity>emptyList());

        assertFalse(b.hasItemizedRooms());
        assertEquals(10000.0, b.grandTotal, DELTA);
    }

    @Test
    public void centsAreRoundedSoFloatingPointNoiseNeverReachesTheReceipt() {
        List<BookingRoom> rooms = Collections.singletonList(room("Deluxe Room", 1, 1999.99, 3, 5999.97));
        List<BookingAmenity> amenities = Collections.singletonList(amenity("Water", 3, 0.1, 0.30000000000000004));

        ReceiptBreakdown b = ReceiptBreakdown.forReceiptDetail(receipt(rooms, 3, 6000.27, 0), amenities);

        assertEquals(0.30, b.amenitiesTotal, 0.0000001);
        assertEquals(0.0, b.otherCharges, 0.0000001);
        assertEquals(6000.27, b.grandTotal, 0.0000001);
    }

    // ---- Legacy Booking-snapshot path ----

    private static Booking legacyBooking(String type, int roomsRequested, double total) {
        Booking b = new Booking("B1", "R1", type + " 101", type,
                "Oct 01, 2026", "Oct 03, 2026", 2, total, "Confirmed", "Sep 20, 2026");
        b.setRoomsRequested(roomsRequested);
        return b;
    }

    @Test
    public void legacyBooking_withAmenities_singleRoom() {
        Booking booking = legacyBooking("Deluxe", 1, 8650.0);
        booking.setRoomCharge(8500.0);
        booking.setAmenityCharge(150.0);
        booking.setAmenities(Collections.singletonList(amenity("Extra Towels", 3, 50.0, 150.0)));

        ReceiptBreakdown b = ReceiptBreakdown.forLegacyBooking(booking, null, 2, 8650.0, 150.0, 0);

        assertEquals(1, b.roomLines.size());
        assertEquals("Deluxe", b.roomLines.get(0).name);
        assertEquals(4250.0, b.roomLines.get(0).ratePerNight, DELTA); // 8,500 / (1 room x 2 nights)
        assertEquals(8500.0, b.roomsTotal, DELTA);
        assertEquals(ReceiptBreakdown.AmenityStatus.ITEMIZED, b.amenityStatus);
        assertEquals(150.0, b.amenitiesTotal, DELTA);
        assertEquals(8650.0, b.grandTotal, DELTA);
        assertAddsUp(b);
    }

    @Test
    public void legacyBooking_withoutAmenities_singleRoom() {
        Booking booking = legacyBooking("Deluxe", 1, 5000.0);
        booking.setRoomCharge(5000.0);

        ReceiptBreakdown b = ReceiptBreakdown.forLegacyBooking(booking, null, 2, 5000.0, 0.0, 0);

        assertEquals(ReceiptBreakdown.AmenityStatus.NONE, b.amenityStatus);
        assertEquals(5000.0, b.roomsTotal, DELTA);
        assertEquals(5000.0, b.grandTotal, DELTA);
        assertAddsUp(b);
    }

    @Test
    public void legacyBooking_beforeBillingExists_derivesTheRoomSplitFromTotalMinusAmenities() {
        // A still-pending reservation: no Billing breakdown yet (roomCharge = 0), total = rooms + amenities.
        Booking booking = legacyBooking("Suite", 2, 20500.0);
        booking.setAmenityCharge(500.0);
        booking.setAmenities(Collections.singletonList(amenity("Breakfast", 2, 250.0, 500.0)));

        ReceiptBreakdown b = ReceiptBreakdown.forLegacyBooking(booking, null, 2, 20500.0, 500.0, 0);

        assertEquals(2, b.roomLines.get(0).quantity);
        assertEquals(20000.0, b.roomsTotal, DELTA);
        assertEquals(5000.0, b.roomLines.get(0).ratePerNight, DELTA); // 20,000 / (2 rooms x 2 nights)
        assertAddsUp(b);
    }

    @Test
    public void legacyGroupedTransaction_listsEverySiblingAsARoomLine_andGathersAmenitiesFromWhicheverHoldsThem() {
        Booking deluxe = legacyBooking("Deluxe", 1, 5000.0);
        deluxe.setRoomCharge(5000.0);
        Booking suite = legacyBooking("Suite", 1, 10700.0);
        suite.setRoomCharge(10000.0);
        suite.setAmenityCharge(700.0);
        // Amenities live on exactly ONE sibling - here NOT the anchor (deluxe) the receipt was opened from.
        suite.setAmenities(Collections.singletonList(amenity("Spa", 1, 700.0, 700.0)));

        ReceiptBreakdown b = ReceiptBreakdown.forLegacyBooking(deluxe, Arrays.asList(deluxe, suite), 2, 15700.0, 700.0, 0);

        assertEquals(2, b.roomLines.size());
        assertEquals(15000.0, b.roomsTotal, DELTA);
        assertEquals(ReceiptBreakdown.AmenityStatus.ITEMIZED, b.amenityStatus);
        assertEquals(700.0, b.amenitiesTotal, DELTA);
        assertEquals(15700.0, b.grandTotal, DELTA);
        assertAddsUp(b);
    }

    @Test
    public void legacyBooking_additionalGuestFeeAndDiscount_areKeptAndReconcile() {
        Booking booking = legacyBooking("Deluxe", 1, 5400.0);
        booking.setRoomCharge(5000.0);
        booking.setDiscountAmount(100.0);

        // 5,000 rooms + 500 additional guest fee - 100 discount = 5,400
        ReceiptBreakdown b = ReceiptBreakdown.forLegacyBooking(booking, null, 2, 5400.0, 0.0, 500.0);

        assertEquals(500.0, b.additionalGuestFee, DELTA);
        assertEquals(100.0, b.discount, DELTA);
        assertEquals(0.0, b.otherCharges, DELTA);
        assertAddsUp(b);
    }
}
