package com.example.velocitysuites.network.dto;

import java.util.List;

/**
 * One promo from the admin Promotional module as the server shows it to a guest (App\Models\Promotion::snapshotRow() plus what it does to
 * the guest's rooms): the rooms' room-type rate promo only. Money fields are plain numbers.
 */
public class PromoOfferDto {
    public long id;
    public String title;
    public String description;
    /** "percentage" or "fixed"; null for a promo with no discount value (it only gives amenities / is display only). */
    public String discount_type;
    public Double discount_value;
    /** "8% off the room rate" / "₱500.00 off per night" - ready to show. */
    public String label;
    public Long room_type_id;
    public String room_type_name;
    /** yyyy-MM-dd the promo ends. */
    public String valid_until;
    public boolean has_money_effect;
    /** What it would take off these rooms (an estimate; subtracted only once the front desk verifies it). */
    public double estimated_saving;
    /** A room list only: the nightly rate of THIS room type with the promo applied. */
    public Double rate_after;
    public boolean applied;
    public List<String> amenities;
}
