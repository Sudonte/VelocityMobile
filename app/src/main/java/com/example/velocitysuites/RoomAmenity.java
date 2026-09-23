package com.example.velocitysuites;

import java.io.Serializable;

/**
 * A single amenity a room displays - always inherited from its Room Type
 * (amenities are managed only at the Room Type level by the System
 * Administrator; an individual room can never carry its own independent
 * assignment - see the backend's Room::getAmenitiesAttribute(), a pure
 * passthrough to its RoomType's amenities). Complimentary amenities are
 * included with the room at no extra charge; paid amenities carry a fee
 * and are requested as optional extras through the existing add-on
 * Amenities flow during booking (this class is display-only pricing info,
 * not itself a bookable/requestable entity - that remains AddOnAmenity's
 * role).
 */
public class RoomAmenity implements Serializable {
    public static final String PRICING_COMPLIMENTARY = "complimentary";
    public static final String PRICING_PAID = "paid";

    private final String name;
    private final String category;
    private final String description;
    private final String pricingType;
    private final String fee;

    public RoomAmenity(String name, String category, String description, String pricingType, String fee) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.pricingType = pricingType;
        this.fee = fee;
    }

    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getPricingType() { return pricingType; }
    public String getFee() { return fee; }

    public boolean isPaid() {
        return PRICING_PAID.equals(pricingType);
    }

    public double getFeeAsDouble() {
        try {
            return fee != null ? Double.parseDouble(fee) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
