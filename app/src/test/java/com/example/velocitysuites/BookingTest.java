package com.example.velocitysuites;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Pure-JVM coverage for Booking's own multi-room/calculation logic - the
 * parts of the "no legacy-first-room-only" and "consistent totals" audit
 * that don't require an Android runtime (Activity-hosted totals like
 * PaymentActivity's percentage math or BookingDetailsActivity's grouped-sum
 * rendering aren't reachable from a plain unit test - see RoomRepositoryTest
 * for what's testable there).
 */
public class BookingTest {

    private static Booking booking(int guests, double totalAmount, double amountPaid) {
        // The constructor derives amountPaid from status ("Confirmed" defaults
        // it to the full totalAmount) - explicitly override afterwards so
        // tests can set an arbitrary paid amount independent of status.
        Booking b = new Booking("B1", "R1", "Deluxe 101", "Deluxe",
                "May 01, 2025", "May 05, 2025", guests, totalAmount, "Confirmed", "Apr 30, 2025");
        b.setAmountPaid(amountPaid);
        return b;
    }

    private static BookingRoom room(String typeId, String typeName, int quantity, double pricePerNight, long nights) {
        return new BookingRoom(typeId, typeName, quantity, pricePerNight, nights, pricePerNight * quantity * nights, null);
    }

    // ---- Multi-Room ----

    @Test
    public void getAllRoomTypeNames_oneRoom_singleLegacyType() {
        Booking b = booking(2, 2500.0, 0);
        assertEquals(1, b.getAllRoomTypeNames().size());
        assertTrue(b.hasRoomType("Deluxe"));
    }

    @Test
    public void getAllRoomTypeNames_multipleRoomsSameType_oneDistinctName() {
        Booking b = booking(4, 5000.0, 0);
        List<BookingRoom> rooms = new ArrayList<>();
        rooms.add(room("1", "Deluxe", 2, 2500.0, 1));
        b.setRooms(rooms);
        assertEquals(1, b.getAllRoomTypeNames().size());
        assertEquals(2, b.getTotalRoomCount());
    }

    @Test
    public void getAllRoomTypeNames_multipleDifferentTypes_matchesSecondType() {
        Booking b = booking(6, 10000.0, 0);
        List<BookingRoom> rooms = new ArrayList<>();
        rooms.add(room("1", "Deluxe", 2, 2500.0, 1));
        rooms.add(room("2", "Suite", 1, 4000.0, 1));
        b.setRooms(rooms);

        assertEquals(2, b.getAllRoomTypeNames().size());
        assertEquals(3, b.getTotalRoomCount());
        assertEquals(2, b.getTotalRoomTypeCount());
        // The exact scenario the multi-room search/filter audit depends on -
        // a "Deluxe + Suite" transaction must match a query for the SECOND
        // type, not just the first.
        assertTrue(b.hasRoomType("Suite"));
        assertTrue(b.hasRoomType("Deluxe"));
        assertFalse(b.hasRoomType("Family Room"));
        assertTrue(b.anyRoomTypeContains("suit"));
    }

    // ---- Calculations ----

    @Test
    public void roomLineTotal_isRatePerNightTimesQuantityTimesNights() {
        // Room Line Total = Price Per Room/Night x Quantity x Nights
        BookingRoom line = room("1", "Deluxe", 2, 2500.0, 4);
        assertEquals(2500.0 * 2 * 4, line.getSubtotal(), 0.001);
    }

    @Test
    public void roomTotal_isSumOfEveryRoomLineTotal() {
        List<BookingRoom> rooms = new ArrayList<>();
        rooms.add(room("1", "Deluxe", 2, 2500.0, 4)); // 20,000
        rooms.add(room("2", "Suite", 1, 4000.0, 4));  // 16,000
        double roomTotal = 0;
        for (BookingRoom r : rooms) roomTotal += r.getSubtotal();
        assertEquals(36000.0, roomTotal, 0.001);
    }

    @Test
    public void remainingBalance_isTotalMinusAmountPaid() {
        Booking b = booking(2, 8650.0, 1730.0);
        assertEquals(8650.0 - 1730.0, b.getRemainingBalance(), 0.001);
    }

    @Test
    public void remainingBalance_neverNegativeAmountPaid() {
        Booking b = booking(2, 8650.0, 0);
        b.setAmountPaid(-500.0);
        assertEquals(0.0, b.getAmountPaid(), 0.001);
        assertEquals(8650.0, b.getRemainingBalance(), 0.001);
    }

    // ---- Null/Legacy Data: the real room collection must win ----

    @Test
    public void getAllRoomTypeNames_emptyRooms_fallsBackToLegacyRoomType() {
        Booking b = booking(2, 2500.0, 0); // legacy roomType="Deluxe", getRooms() empty
        assertEquals(1, b.getAllRoomTypeNames().size());
        assertEquals("Deluxe", b.getAllRoomTypeNames().get(0));
    }

    @Test
    public void getAllRoomTypeNames_realRoomsPresent_winsOverLegacyRoomType() {
        // Legacy roomType is "Deluxe" (single-room constructor field), but the
        // real itemized collection says Deluxe + Suite - the real collection
        // must be the source of truth, never silently overridden by the
        // legacy single-value field.
        Booking b = booking(6, 10000.0, 0);
        List<BookingRoom> rooms = new ArrayList<>();
        rooms.add(room("1", "Deluxe", 2, 2500.0, 1));
        rooms.add(room("2", "Suite", 1, 4000.0, 1));
        b.setRooms(rooms);

        List<String> names = b.getAllRoomTypeNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("Deluxe"));
        assertTrue(names.contains("Suite"));
    }

    @Test
    public void formatRoomSelectionSummary_singleType_noMultiplierSuffix() {
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        counts.put("Deluxe", 1);
        assertEquals("Deluxe", Booking.formatRoomSelectionSummary(counts));
    }

    @Test
    public void formatRoomSelectionSummary_multipleTypes_joinsWithQuantities() {
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        counts.put("Deluxe", 2);
        counts.put("Suite", 1);
        assertEquals("Deluxe ×2 • Suite", Booking.formatRoomSelectionSummary(counts));
    }

    // ---- buildRoomSelectionSummaryText(): the shared three-tier summary
    // BookingDetailsActivity's header and TransactionListActivity's
    // read-only detail dialog both now use, so a grouped multi-room-type
    // transaction's text can never drift between the two screens. ----

    @Test
    public void buildRoomSelectionSummaryText_itemizedRoomsPresent_winsOverGroupMembersAndLegacy() {
        Booking b = booking(6, 10000.0, 0);
        List<BookingRoom> rooms = new ArrayList<>();
        rooms.add(room("1", "Deluxe", 2, 2500.0, 1));
        rooms.add(room("2", "Suite", 1, 4000.0, 1));
        b.setRooms(rooms);

        // groupMembers present too - itemized getRooms() must still win, per
        // the same priority order BookingDetailsActivity's own doc specifies.
        List<Booking> groupMembers = new ArrayList<>();
        groupMembers.add(booking(6, 10000.0, 0));

        assertEquals("Deluxe ×2 • Suite", Booking.buildRoomSelectionSummaryText(b, groupMembers));
    }

    @Test
    public void buildRoomSelectionSummaryText_groupMembers_oneDistinctTypePerSibling_noDuplicates() {
        // Reproduces the real BookingGroupState shape: N separate Booking
        // records, each its own single room type, tied together locally -
        // "each sibling record IS one distinct room type" (see
        // BookingDetailsActivity's own doc on this invariant). Merges by
        // getRoomName() (a type-level descriptive label, e.g. "Deluxe Room" -
        // identical for every physical unit of that type, per the real API
        // shape - see Room#room_name), not getRoomType(), matching the
        // pre-existing behavior this method was extracted from verbatim.
        Booking anchor = new Booking("B1", "R1", "Deluxe", "Deluxe",
                "May 01, 2025", "May 05, 2025", 4, 5000.0, "Confirmed", "Apr 30, 2025");
        Booking sibling = new Booking("B2", "R2", "Suite", "Suite",
                "May 01, 2025", "May 05, 2025", 4, 4000.0, "Confirmed", "Apr 30, 2025");
        List<Booking> groupMembers = new ArrayList<>();
        groupMembers.add(anchor);
        groupMembers.add(sibling);

        String summary = Booking.buildRoomSelectionSummaryText(anchor, groupMembers);

        assertEquals("Deluxe • Suite", summary);
    }

    @Test
    public void buildRoomSelectionSummaryText_groupMembers_multipleSameTypeSiblings_quantityMerged() {
        // Two sibling records both "Deluxe" (e.g. the guest added 2 Deluxe
        // rooms as two separate wizard entries) - must merge into one
        // "Deluxe ×2" entry, never two separate "Deluxe" lines.
        Booking anchor = new Booking("B1", "R1", "Deluxe", "Deluxe",
                "May 01, 2025", "May 05, 2025", 4, 5000.0, "Confirmed", "Apr 30, 2025");
        Booking sibling = new Booking("B2", "R2", "Deluxe", "Deluxe",
                "May 01, 2025", "May 05, 2025", 4, 5000.0, "Confirmed", "Apr 30, 2025");
        List<Booking> groupMembers = new ArrayList<>();
        groupMembers.add(anchor);
        groupMembers.add(sibling);

        assertEquals("Deluxe ×2", Booking.buildRoomSelectionSummaryText(anchor, groupMembers));
    }

    @Test
    public void buildRoomSelectionSummaryText_noItemizedNoGroupMembers_fallsBackToLegacySingleRoom() {
        // The legacy fallback tier reads getRoomName() (the "Deluxe 101"
        // constructor field, matching bindHeader()'s pre-existing behavior
        // this method was extracted from), not getRoomType().
        Booking b = booking(2, 2500.0, 0);
        assertEquals("Deluxe 101", Booking.buildRoomSelectionSummaryText(b, null));
    }

    @Test
    public void buildRoomSelectionSummaryText_noItemizedEmptyGroupMembers_fallsBackToLegacySingleRoom() {
        Booking b = booking(2, 2500.0, 0);
        assertEquals("Deluxe 101", Booking.buildRoomSelectionSummaryText(b, new ArrayList<>()));
    }
}
