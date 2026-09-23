package com.example.velocitysuites.network.dto;

/**
 * One Paid/Additional amenity + quantity the guest selected during booking -
 * sent as part of ReservationRequest.amenities. The backend snapshots each
 * selection's current name/price at creation time (see Laravel's
 * ReservationAmenityService) and validates that every amenity_id here is
 * currently active and priced above zero, rejecting the whole submission
 * otherwise - this DTO only carries the guest's choice, never a price.
 */
public class AmenitySelectionDto {
    public long amenity_id;
    public int quantity;

    public AmenitySelectionDto(long amenityId, int quantity) {
        this.amenity_id = amenityId;
        this.quantity = quantity;
    }
}
