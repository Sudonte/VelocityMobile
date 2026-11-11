package com.example.velocitysuites.network.dto;

/** One of the guest's "additional amenity" requests on a stay (Api\AdditionalAmenityController). */
public class AdditionalAmenityRequestDto {
    public long id;
    public long amenity_id;
    /** The room this line was requested for (null on requests made before rooms were tracked). */
    public Long room_id;
    public String room_number;
    /** The booked room a request made before check-in was for ("Deluxe Room (room 1)"); null from an older server or once a room is assigned. */
    public String room_label;
    public String room_type;
    public String batch_ref;
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
