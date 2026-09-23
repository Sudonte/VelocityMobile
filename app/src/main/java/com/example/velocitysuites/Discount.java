package com.example.velocitysuites;

import java.io.Serializable;
import java.util.Locale;

/**
 * A standing, non-expiring discount category (Senior Citizen, PWD, Student,
 * etc.) that a receptionist applies manually at billing time after
 * verifying a guest's ID - genuinely separate from Promotion (no image, no
 * validity dates, no room type), but shown to guests in the same "Ongoing
 * Promotions & Special Offers" section as an informational card, exactly
 * matching how the web public Home page renders Promotion and Discount
 * cards side by side in one unified grid (Api\CatalogController::discounts(),
 * same active-only rows the web side uses - never a separate mobile copy).
 */
public class Discount implements Serializable {
    private final String id;
    private final String name;
    private final String discountType;
    private final double value;
    private final String description;

    public Discount(String id, String name, String discountType, double value, String description) {
        this.id = id;
        this.name = name;
        this.discountType = discountType;
        this.value = value;
        this.description = description;
    }

    public static Discount fromDto(com.example.velocitysuites.network.dto.DiscountDto dto) {
        return new Discount(String.valueOf(dto.id), dto.name, dto.discount_type, dto.valueAsDouble(), dto.description);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }

    /** Mirrors welcome.blade.php's exact badge formatting: percentage values drop
     *  trailing zeros ("20.00" -> "20% OFF", "12.50" -> "12.5% OFF"); fixed-amount
     *  values always keep 2 decimals ("500.00" -> "₱500.00 OFF"). */
    public String getBadgeLabel() {
        if ("percentage".equals(discountType)) {
            String formatted = String.format(Locale.US, "%.2f", value);
            if (formatted.contains(".")) {
                formatted = formatted.replaceAll("0+$", "");
                formatted = formatted.replaceAll("\\.$", "");
            }
            return formatted + "% OFF";
        }
        return "₱" + String.format(Locale.US, "%,.2f", value) + " OFF";
    }
}
