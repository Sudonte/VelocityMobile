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
    /** Optional: the booked room this amenity is for - its room type and which room of that type (1-based). Omitted when null. */
    public Long room_type_id;
    public Integer room_slot;

    public AmenitySelectionDto(long amenityId, int quantity) {
        this.amenity_id = amenityId;
        this.quantity = quantity;
    }
}
