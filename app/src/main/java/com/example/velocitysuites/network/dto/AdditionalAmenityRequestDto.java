package com.example.velocitysuites.network.dto;

/** One of the guest's "additional amenity" requests on a stay (Api\AdditionalAmenityController). */
public class AdditionalAmenityRequestDto {
    public long id;
    public long amenity_id;
    public String name;
    public int quantity;
    public double unit_price;
    public double subtotal;
    /** pending | approved | rejected */
    public String status;
    public String rejection_reason;
    public String note;
    public String requested_at;
    public String decided_at;
}
