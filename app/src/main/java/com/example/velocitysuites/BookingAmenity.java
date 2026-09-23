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

    public BookingAmenity(String amenityId, String amenityName, int quantity, double unitPrice, double subtotal) {
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
}
