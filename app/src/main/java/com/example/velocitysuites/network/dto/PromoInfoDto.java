package com.example.velocitysuites.network.dto;

import java.util.List;

/** The promos a booking or reservation can use and the one the desk applied (App\Models\Promotion::infoFor()). */
public class PromoInfoDto {
    public List<PromoOfferDto> available;
    public Long applied_id;
    public String applied_title;
    /** Pesos the applied promo takes off the room rate (0 until the desk verifies it). */
    public double promo_discount;
    /** "applied" or "to_be_verified". */
    public String status;
}
