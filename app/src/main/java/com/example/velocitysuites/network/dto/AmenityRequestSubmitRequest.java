package com.example.velocitysuites.network.dto;

public class AmenityRequestSubmitRequest {
    public long amenity_id;
    public int quantity;

    public AmenityRequestSubmitRequest(long amenityId, int quantity) {
        this.amenity_id = amenityId;
        this.quantity = quantity;
    }
}
