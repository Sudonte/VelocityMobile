package com.example.velocitysuites;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * An ongoing promotion (amenity-bundle package, e.g. "Book a Deluxe, get a
 * free Breakfast Buffet") shown in landing.xml's Ongoing Promotions &
 * Special Offers section - sourced from the exact same centralized
 * App\Models\Promotion rows (Api\CatalogController::promotions(), already
 * filtered to active + currently within its date range) that also drive
 * the web public Home page's own Promotions & Discounts section, so there
 * is only ever one record per promotion, never a separate mobile-only copy.
 * Guests can only ever view these - creating/editing/deleting promotions
 * stays exclusively a System Administrator (web) capability.
 */
public class Promotion implements Serializable {
    private static final SimpleDateFormat API_DATETIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private static final SimpleDateFormat DISPLAY_DATE_SHORT = new SimpleDateFormat("MMM dd", Locale.US);
    private static final SimpleDateFormat DISPLAY_DATE_FULL = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    private final String id;
    private final String name;
    private final String description;
    private final String imageUrl;
    private final String roomTypeName;
    private final String validityRange;
    private final List<String> includedAmenities;

    public Promotion(String id, String name, String description, String imageUrl, String roomTypeName,
                      String validityRange, List<String> includedAmenities) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.roomTypeName = roomTypeName;
        this.validityRange = validityRange;
        this.includedAmenities = includedAmenities;
    }

    public static Promotion fromDto(com.example.velocitysuites.network.dto.PromotionDto dto) {
        List<String> amenities = new ArrayList<>();
        if (dto.amenities != null) {
            for (com.example.velocitysuites.network.dto.PromotionDto.PromotionAmenityDto a : dto.amenities) {
                int qty = a.pivot != null ? a.pivot.quantity : 1;
                amenities.add(qty + "× " + a.amenity_name);
            }
        }
        String roomTypeName = dto.room_type != null ? dto.room_type.name : null;
        return new Promotion(
                String.valueOf(dto.id),
                dto.promo_name,
                dto.description,
                dto.image_url,
                roomTypeName,
                formatValidityRange(dto.start_date, dto.end_date),
                amenities
        );
    }

    private static String formatValidityRange(String isoStart, String isoEnd) {
        Date start = parseIso(isoStart);
        Date end = parseIso(isoEnd);
        if (start == null || end == null) return "";
        return DISPLAY_DATE_SHORT.format(start) + " - " + DISPLAY_DATE_FULL.format(end);
    }

    private static Date parseIso(String isoTimestamp) {
        if (isoTimestamp == null) return null;
        try {
            String cleaned = isoTimestamp.replace("Z", "");
            if (cleaned.length() > 19) cleaned = cleaned.substring(0, 19);
            return API_DATETIME.parse(cleaned);
        } catch (ParseException e) {
            return null;
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    /** Null means the promotion applies to every room type. */
    public String getRoomTypeName() { return roomTypeName; }
    public String getValidityRange() { return validityRange; }
    public List<String> getIncludedAmenities() { return includedAmenities; }
}
