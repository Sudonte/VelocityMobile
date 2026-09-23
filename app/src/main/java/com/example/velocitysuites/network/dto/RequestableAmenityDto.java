package com.example.velocitysuites.network.dto;

/**
 * A Paid/Additional amenity originally selected for a reservation, with how much has already been
 * requested since - the only amenities a guest may submit a further request for. See
 * Api\AmenityRequestController::requestable(), RequestAmenityActivity and BillingSummaryActivity.
 */
public class RequestableAmenityDto {
    public long amenity_id;
    public String amenity_name;
    public String category;
    public double price;
    /** Quantity originally selected during booking. */
    public int original_quantity;
    /** Quantity already requested (and granted) since. */
    public int already_requested_quantity;
}
