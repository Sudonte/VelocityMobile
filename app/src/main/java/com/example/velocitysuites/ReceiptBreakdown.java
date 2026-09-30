package com.example.velocitysuites;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The itemized Payment Summary a Payment Receipt shows: Selected Rooms (one line
 * per room type - name, rate per night, nights, subtotal) with their Rooms Total,
 * Amenities (one line per amenity - name, quantity, unit price, subtotal) with their
 * Amenities Total, the other charges/deductions, and the Grand Total they add up to.
 * <p>
 * Pure numbers, no Context/Views (mirrors BookingGroupAggregator/PaymentStatusResolver's
 * pattern) so the arithmetic is JVM-unit-testable - see ReceiptBreakdownTest - and so
 * the two receipt layouts (PaymentReceiptActivity's receipt-number mode and its legacy
 * Booking-snapshot mode) render the same figures for the same transaction from one
 * calculation instead of each re-deriving them.
 * <p>
 * <b>The Grand Total is the backend's, never re-computed here</b> whenever one is
 * available (Billing::total_amount, or the booking's own total before billing exists -
 * ReceiptService::grandTotal()): it is what the guest actually owes and was paid against,
 * and a receipt must not quietly show a different number because the itemized lines
 * happen to sum differently. To keep the printed arithmetic honest anyway,
 * {@link #otherCharges} carries whatever the itemized lines don't explain (an additional
 * guest fee the receipt payload doesn't itemize, an extra charge added at checkout, a rate
 * override) so that always
 * <pre>roomsTotal + amenitiesTotal + additionalGuestFee - discount + otherCharges == grandTotal</pre>
 * - zero (and not shown) in the ordinary case where the lines already explain the total.
 */
public final class ReceiptBreakdown {

    /** One selected room type. {@code ratePerNight} is per ROOM per night; {@code subtotal} = rate x quantity x nights. */
    public static final class RoomLine {
        public final String name;
        public final int quantity;
        public final double ratePerNight;
        public final long nights;
        public final double subtotal;

        RoomLine(String name, int quantity, double ratePerNight, long nights, double subtotal) {
            this.name = name;
            this.quantity = quantity;
            this.ratePerNight = ratePerNight;
            this.nights = nights;
            this.subtotal = subtotal;
        }
    }

    /** One selected amenity. {@code subtotal} = unitPrice x quantity. */
    public static final class AmenityLine {
        public final String name;
        public final int quantity;
        public final double unitPrice;
        public final double subtotal;

        AmenityLine(String name, int quantity, double unitPrice, double subtotal) {
            this.name = name;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.subtotal = subtotal;
        }
    }

    /**
     * What is actually known about this transaction's amenities - deliberately more than
     * "list empty or not", because "No amenities selected" is a factual claim that must
     * only ever be made when it is KNOWN to be true, never when the data simply couldn't
     * be obtained.
     */
    public enum AmenityStatus {
        /** Real per-amenity lines are available - show each, then the Amenities Total. */
        ITEMIZED,
        /** No per-amenity lines, but a non-zero amenity charge is on record (an older transaction) - show that one total. */
        AGGREGATE_ONLY,
        /** Known to have none - show "No amenities selected". */
        NONE,
        /** The amenity data could not be obtained - say nothing about amenities rather than claim there are none. */
        UNKNOWN
    }

    private static final double EPSILON = 0.009;

    public final List<RoomLine> roomLines;
    /** Sum of {@link #roomLines}' subtotals, or the aggregate room charge when the transaction has no itemized rooms. */
    public final double roomsTotal;
    public final AmenityStatus amenityStatus;
    public final List<AmenityLine> amenityLines;
    public final double amenitiesTotal;
    /** Known only where the source itemizes it (the legacy Booking snapshot's Billing breakdown) - otherwise 0 and folded into {@link #otherCharges}. */
    public final double additionalGuestFee;
    /** A positive number - shown as a deduction. */
    public final double discount;
    /** Whatever the lines above don't explain of the grand total; may be negative (a deduction). 0 in the ordinary case. */
    public final double otherCharges;
    public final double grandTotal;

    private ReceiptBreakdown(List<RoomLine> roomLines, double roomsTotal, AmenityStatus amenityStatus,
                             List<AmenityLine> amenityLines, double amenitiesTotal, double additionalGuestFee,
                             double discount, double otherCharges, double grandTotal) {
        this.roomLines = Collections.unmodifiableList(roomLines);
        this.roomsTotal = roomsTotal;
        this.amenityStatus = amenityStatus;
        this.amenityLines = Collections.unmodifiableList(amenityLines);
        this.amenitiesTotal = amenitiesTotal;
        this.additionalGuestFee = additionalGuestFee;
        this.discount = discount;
        this.otherCharges = otherCharges;
        this.grandTotal = grandTotal;
    }

    /** True when there are real per-room lines to list (a transaction predating room_lines has none - callers then fall back to the plain Subtotal/Discount/Total rows). */
    public boolean hasItemizedRooms() {
        return !roomLines.isEmpty();
    }

    // ---- Factories ----

    /**
     * Receipt-number mode: rooms/nights/discount/grand total straight off the receipt
     * payload (ReceiptDetail - room_lines and payment_summary), amenities from whichever
     * transaction record the caller could resolve for it (the receipt payload itself
     * carries no amenity lines). {@code amenities == null} means "couldn't be resolved" -
     * see {@link AmenityStatus#UNKNOWN}; an empty list means "resolved, and there are none".
     */
    public static ReceiptBreakdown forReceiptDetail(ReceiptDetail detail, @Nullable List<BookingAmenity> amenities) {
        return forReceiptDetail(detail, amenities, 0);
    }

    /**
     * @param aggregateAmenityCharge the transaction record's single amenity-charge total (Booking#getAmenityCharge()),
     *                               used only when {@code amenities} is empty: an amenity charge on record with no
     *                               per-amenity lines must read as an Amenities Total ({@link AmenityStatus#AGGREGATE_ONLY}),
     *                               never as "No amenities selected".
     */
    public static ReceiptBreakdown forReceiptDetail(ReceiptDetail detail, @Nullable List<BookingAmenity> amenities,
                                                    double aggregateAmenityCharge) {
        long stayNights = Math.max(1, detail.getNumberOfNights());
        List<RoomLine> lines = new ArrayList<>();
        for (BookingRoom room : detail.getRoomLines()) {
            lines.add(roomLineOf(room, stayNights));
        }
        Booking.PaymentSummary summary = detail.getPaymentSummary();
        double grandTotal = summary != null ? summary.grandTotal : 0;
        double discount = summary != null ? summary.discount : 0;
        return assemble(lines, 0, amenities, aggregateAmenityCharge, 0, discount, grandTotal);
    }

    /**
     * Legacy Booking-snapshot mode. Rooms follow the same three-tier fallback
     * BookingDetailsActivity#buildRoomInfoSection() uses (itemized room lines, then a
     * legacy grouped multi-room-type transaction - each sibling IS one room type, then the
     * plain single-room case), so the receipt and the Booking Details screen it was
     * opened from can never disagree about the rooms. {@code totalAmount},
     * {@code amenityCharge} and {@code additionalGuestFee} are the caller's already
     * group-aggregated figures (see PaymentReceiptActivity#populateReceipt()).
     */
    public static ReceiptBreakdown forLegacyBooking(Booking booking, @Nullable List<Booking> groupMembers, long stayNights,
                                                    double totalAmount, double amenityCharge, double additionalGuestFee) {
        long nights = Math.max(1, stayNights);
        boolean grouped = groupMembers != null && !groupMembers.isEmpty();

        List<RoomLine> lines = new ArrayList<>();
        if (!booking.getRooms().isEmpty()) {
            for (BookingRoom room : booking.getRooms()) {
                lines.add(roomLineOf(room, nights));
            }
        } else if (grouped) {
            for (Booking member : groupMembers) {
                lines.add(legacyRoomLine(member, nights));
            }
        } else {
            lines.add(legacyRoomLine(booking, nights));
        }

        // Amenities are attached to exactly ONE sibling of a grouped transaction at creation
        // time (PaymentActivity#submitPendingReservationGroups()'s index==0 rule), which is
        // not necessarily the anchor record this receipt was opened from - so gather every
        // sibling's, exactly like the aggregate amenity charge the caller already summed.
        List<BookingAmenity> amenities = new ArrayList<>();
        if (grouped) {
            for (Booking member : groupMembers) {
                amenities.addAll(member.getAmenities());
            }
        } else {
            amenities.addAll(booking.getAmenities());
        }
        return assemble(lines, 0, amenities, amenityCharge, additionalGuestFee, booking.getDiscountAmount(), totalAmount);
    }

    // ---- Assembly ----

    /**
     * @param fallbackRoomCharge   used as the Rooms Total only when {@code roomLines} is empty
     * @param amenities            null = unknown, empty = known to have none, else the lines
     * @param fallbackAmenityCharge an aggregate amenity charge on record without lines (older data)
     * @param grandTotal           the authoritative figure; values at/below zero mean "not available" and the total is summed from the parts instead
     */
    static ReceiptBreakdown assemble(List<RoomLine> roomLines, double fallbackRoomCharge,
                                     @Nullable List<BookingAmenity> amenities, double fallbackAmenityCharge,
                                     double additionalGuestFee, double discount, double grandTotal) {
        double roomsTotal = 0;
        for (RoomLine line : roomLines) roomsTotal += line.subtotal;
        if (roomLines.isEmpty()) roomsTotal = Math.max(0, fallbackRoomCharge);

        List<AmenityLine> amenityLines = new ArrayList<>();
        double amenitiesTotal = 0;
        AmenityStatus status;
        if (amenities == null) {
            status = AmenityStatus.UNKNOWN;
            amenitiesTotal = Math.max(0, fallbackAmenityCharge);
        } else if (!amenities.isEmpty()) {
            for (BookingAmenity amenity : amenities) {
                AmenityLine line = amenityLineOf(amenity);
                amenityLines.add(line);
                amenitiesTotal += line.subtotal;
            }
            status = AmenityStatus.ITEMIZED;
        } else if (fallbackAmenityCharge > EPSILON) {
            status = AmenityStatus.AGGREGATE_ONLY;
            amenitiesTotal = fallbackAmenityCharge;
        } else {
            status = AmenityStatus.NONE;
        }

        double fee = Math.max(0, additionalGuestFee);
        double deduction = Math.max(0, discount);
        double sumOfParts = round2(roomsTotal + amenitiesTotal + fee - deduction);
        double total = grandTotal > EPSILON ? round2(grandTotal) : sumOfParts;
        double other = round2(total - sumOfParts);
        if (Math.abs(other) < 0.005) other = 0;

        return new ReceiptBreakdown(roomLines, round2(roomsTotal), status, amenityLines, round2(amenitiesTotal),
                round2(fee), round2(deduction), other, total);
    }

    /** Package-private (not private) only so ReceiptBreakdownTest can build a line directly. */
    static RoomLine roomLineOf(BookingRoom room, long fallbackNights) {
        int quantity = Math.max(1, room.getQuantity());
        long nights = room.getNights() > 0 ? room.getNights() : Math.max(1, fallbackNights);
        double subtotal = room.getSubtotal() > EPSILON
                ? room.getSubtotal()
                : room.getPricePerNight() * quantity * nights;
        double rate = room.getPricePerNight() > EPSILON
                ? room.getPricePerNight()
                : subtotal / (quantity * nights);
        String name = room.getRoomTypeName() != null && !room.getRoomTypeName().trim().isEmpty() ? room.getRoomTypeName() : "Room";
        return new RoomLine(name, quantity, round2(rate), nights, round2(subtotal));
    }

    /** One legacy Booking record treated as one room-type line - the same per-record fallback BookingDetailsActivity uses: the real Billing room split when there is one, else its own total minus its amenity charge (never double-counting amenities). */
    private static RoomLine legacyRoomLine(Booking record, long nights) {
        int quantity = Math.max(1, record.getRoomsRequested());
        double subtotal = record.getRoomCharge() > EPSILON
                ? record.getRoomCharge()
                : Math.max(0, record.getTotalAmount() - record.getAmenityCharge());
        String name = record.getRoomType() != null && !record.getRoomType().trim().isEmpty()
                ? record.getRoomType()
                : (record.getRoomName() != null ? record.getRoomName() : "Room");
        return new RoomLine(name, quantity, round2(subtotal / (quantity * nights)), nights, round2(subtotal));
    }

    private static AmenityLine amenityLineOf(BookingAmenity amenity) {
        int quantity = Math.max(1, amenity.getQuantity());
        double subtotal = amenity.getSubtotal() > EPSILON ? amenity.getSubtotal() : amenity.getUnitPrice() * quantity;
        // Derived from the subtotal (as BookingDetailsActivity does) rather than trusting the
        // separate unit_price field, so "quantity x unit price = subtotal" holds on screen by
        // construction whatever the backend sent.
        double unitPrice = subtotal > EPSILON ? subtotal / quantity : amenity.getUnitPrice();
        String name = amenity.getAmenityName() != null && !amenity.getAmenityName().trim().isEmpty() ? amenity.getAmenityName() : "Amenity";
        return new AmenityLine(name, quantity, round2(unitPrice), round2(subtotal));
    }

    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
