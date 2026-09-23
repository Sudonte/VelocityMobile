package com.example.velocitysuites.network.dto;

import java.util.List;

/** GET /promotions response item - see Api\CatalogController::promotions() and Promotion#fromDto(). */
public class PromotionDto {
    public long id;
    public String promo_name;
    public String description;
    public String image_url;
    public String start_date;
    public String end_date;
    /** Null means the promotion applies to every room type. */
    public PromotionRoomTypeDto room_type;
    public List<PromotionAmenityDto> amenities;

    public static class PromotionRoomTypeDto {
        public long id;
        public String name;
    }

    public static class PromotionAmenityDto {
        public long id;
        public String amenity_name;
        /** Pivot row on the promotion_amenity table - carries the bundled quantity for this amenity. */
        public PromotionAmenityPivotDto pivot;
    }

    public static class PromotionAmenityPivotDto {
        public int quantity;
    }
}
