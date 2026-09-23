package com.example.velocitysuites;

import com.example.velocitysuites.network.dto.AmenityDto;

import java.io.Serializable;

/**
 * A paid add-on service the guest can opt into during booking/reservation
 * (distinct from Room.getAmenities(), which is the room's free descriptive
 * feature list). Sourced live from the admin-managed, active-only Amenity
 * catalog (GET /api/amenities - see RoomRepository#refreshAmenities) rather
 * than a hardcoded list, so it always reflects whatever the admin currently
 * has active.
 */
public class AddOnAmenity implements Serializable {
    private final String id;
    private final String name;
    private final String category;
    private final String description;
    private final double price;
    private final int stock;

    /** How many the guest wants, set via the quantity stepper once selected - defaults to 1, reset to 1 on deselect. */
    private int quantity = 1;

    public AddOnAmenity(String id, String name, String category, String description, double price, int stock) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.price = price;
        this.stock = stock;
    }

    public static AddOnAmenity fromDto(AmenityDto dto) {
        return new AddOnAmenity(String.valueOf(dto.id), dto.amenity_name, dto.category, dto.description, dto.chargeAsDouble(), dto.quantity);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public double getPrice() { return price; }
    /** Admin-configured available stock, used as the quantity stepper's upper bound (minimum 1, so it's never accidentally uncappable at 0). */
    public int getMaxQuantity() { return Math.max(1, stock); }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getSubtotal() { return price * quantity; }
}
