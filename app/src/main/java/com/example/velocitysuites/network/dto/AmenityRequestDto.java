package com.example.velocitysuites.network.dto;

/**
 * One submitted Additional Amenity Request (App\Models\AmenityRequest),
 * historical snapshot fields - amenity_name/charge reflect the catalog at
 * the moment this request was made, not necessarily its current values.
 */
public class AmenityRequestDto {
    public long id;
    public long reservation_id;
    public Long room_id;
    public long amenity_id;
    public String amenity_name;
    public String category;
    public int quantity;
    public String charge;
    /** pending | approved | in_progress | completed | rejected */
    public String status;
    public String created_at;

    public double chargeAsDouble() {
        try {
            return charge != null ? Double.parseDouble(charge) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public double getSubtotal() {
        return chargeAsDouble() * quantity;
    }
}
