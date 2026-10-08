package com.example.velocitysuites;

import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A standing discount category created by the System Administrator in the
 * Discount module (Senior Citizen, PWD, VIP, ...) that a guest can claim on
 * the ID-verification step and a receptionist verifies and applies at
 * billing. Genuinely separate from Promotion (no image, no room type). The
 * Discount module stores exactly: name, type (percentage/fixed), value,
 * description and status - this model mirrors those fields and nothing else.
 */
public class Discount implements Serializable {
    private final String id;
    private final String name;
    private final String discountType;
    private final double value;
    private final String description;
    private final String status;
    private final String createdAt;
    private final String updatedAt;

    public Discount(String id, String name, String discountType, double value, String description) {
        this(id, name, discountType, value, description, null, null, null);
    }

    public Discount(String id, String name, String discountType, double value, String description,
                    @Nullable String status, @Nullable String createdAt, @Nullable String updatedAt) {
        this.id = id;
        this.name = name;
        this.discountType = discountType;
        this.value = value;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Discount fromDto(com.example.velocitysuites.network.dto.DiscountDto dto) {
        return new Discount(String.valueOf(dto.id), dto.name, dto.discount_type, dto.valueAsDouble(),
                dto.description, dto.status, dto.created_at, dto.updated_at);
    }

    /**
     * Only discounts the admin currently offers. The API already filters on
     * status = active, but a stale/odd row (explicitly inactive, or no id/name)
     * must never reach the guest's list.
     */
    public static List<Discount> activeOnly(@Nullable List<Discount> all) {
        List<Discount> result = new ArrayList<>();
        if (all == null) return result;
        for (Discount d : all) {
            if (d != null && d.isActive() && d.id != null && d.name != null && !d.name.trim().isEmpty()) {
                result.add(d);
            }
        }
        return result;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getDiscountType() { return discountType; }
    public double getValue() { return value; }
    @Nullable public String getStatus() { return status; }
    @Nullable public String getCreatedAt() { return createdAt; }
    @Nullable public String getUpdatedAt() { return updatedAt; }

    /** A missing status (older payloads) counts as active - the endpoint only ever returns active rows. */
    public boolean isActive() {
        return status == null || "active".equalsIgnoreCase(status.trim());
    }

    public boolean isPercentage() {
        return "percentage".equals(discountType);
    }

    /** Peso amount this discount would take off {@code base} (never more than base) - the same rule as the backend's Discount::amountOff(). 0 when its type/value are unknown (e.g. a discount only remembered by name). */
    public double estimateOff(double base) {
        if (discountType == null || base <= 0) return 0;
        double amount = isPercentage() ? Math.round(base * value) / 100.0 : value;
        return Math.max(0, Math.min(amount, base));
    }

    /** "20%" / "12.5%" / "₱500.00" - the value alone, without "OFF". */
    public String getValueLabel() {
        if (isPercentage()) {
            String formatted = String.format(Locale.US, "%.2f", value);
            if (formatted.contains(".")) {
                formatted = formatted.replaceAll("0+$", "");
                formatted = formatted.replaceAll("\\.$", "");
            }
            return formatted + "%";
        }
        return "₱" + String.format(Locale.US, "%,.2f", value);
    }

    /** Mirrors welcome.blade.php's exact badge formatting: percentage values drop
     *  trailing zeros ("20.00" -> "20% OFF", "12.50" -> "12.5% OFF"); fixed-amount
     *  values always keep 2 decimals ("500.00" -> "₱500.00 OFF"). */
    public String getBadgeLabel() {
        return getValueLabel() + " OFF";
    }
}
