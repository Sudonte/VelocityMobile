package com.example.velocitysuites;

import java.io.Serializable;

/**
 * One paid-amenity line item within a Booking/Reservation transaction (e.g.
 * "Breakfast Package ×2" is one BookingAmenity with quantity=2) - see
 * MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md (root of the repo). Populated on
 * {@link Booking#getAmenities()} only once the backend actually returns an
 * `amenities` array (network/dto/BookingAmenityDto) - empty on every
 * transaction today, since that endpoint doesn't exist yet. Until then,
 * Booking#getAmenityCharge() (a single dollar total, no item breakdown)
 * remains the only amenity data available.
 */
public class BookingAmenity implements Serializable {
    private final String amenityId;
    private final String amenityName;
    private final int quantity;
    private final double unitPrice;
    private final double subtotal;
    /** The booked room it was chosen for ("Deluxe (room 2)"); null for one booked for the whole stay and for every older row. */
    private final String roomLabel;
    /** The booked room's type and which room of it (1-based) - what an edit sends back; null with the label. */
    private final String roomTypeId;
    private final int roomSlot;

    public BookingAmenity(String amenityId, String amenityName, int quantity, double unitPrice, double subtotal) {
        this(amenityId, amenityName, quantity, unitPrice, subtotal, null);
    }

    public BookingAmenity(String amenityId, String amenityName, int quantity, double unitPrice, double subtotal, String roomLabel) {
        this(amenityId, amenityName, quantity, unitPrice, subtotal, roomLabel, null, 0);
    }

    public BookingAmenity(String amenityId, String amenityName, int quantity, double unitPrice, double subtotal, String roomLabel,
                          String roomTypeId, int roomSlot) {
        this.roomTypeId = roomTypeId;
        this.roomSlot = roomSlot;
        this.roomLabel = roomLabel != null && !roomLabel.trim().isEmpty() ? roomLabel.trim() : null;
        this.amenityId = amenityId;
        this.amenityName = amenityName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    public String getAmenityId() { return amenityId; }
    public String getAmenityName() { return amenityName; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public double getSubtotal() { return subtotal; }
    /** "Deluxe (room 2)" or null (booked for the whole stay). */
    public String getRoomLabel() { return roomLabel; }
    public String getRoomTypeId() { return roomTypeId; }
    /** Which room of that type (1-based); 0 when the amenity is for the whole stay. */
    public int getRoomSlot() { return roomSlot; }
}
