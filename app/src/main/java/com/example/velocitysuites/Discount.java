package com.example.velocitysuites;

import androidx.annotation.Nullable;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
    private final String startDate;
    private final String endDate;

    public Discount(String id, String name, String discountType, double value, String description) {
        this(id, name, discountType, value, description, null, null, null);
    }

    public Discount(String id, String name, String discountType, double value, String description,
                    @Nullable String status, @Nullable String createdAt, @Nullable String updatedAt) {
        this(id, name, discountType, value, description, status, createdAt, updatedAt, null, null);
    }

    public Discount(String id, String name, String discountType, double value, String description,
                    @Nullable String status, @Nullable String createdAt, @Nullable String updatedAt,
                    @Nullable String startDate, @Nullable String endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
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
                dto.description, dto.status, dto.created_at, dto.updated_at, dto.start_date, dto.end_date);
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
            if (d != null && d.isActive() && d.isValidOn(CheckInWindow.today()) && d.id != null && d.name != null && !d.name.trim().isEmpty()) {
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

    @Nullable public String getStartDate() { return startDate; }
    @Nullable public String getEndDate() { return endDate; }

    /** True when {@code day} (hotel-local) is inside the optional validity window; an empty side means no limit. Dates are yyyy-MM-dd and inclusive. */
    public boolean isValidOn(LocalDate day) {
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        return (start == null || !day.isBefore(start)) && (end == null || !day.isAfter(end));
    }

    @Nullable
    private static LocalDate parseDate(@Nullable String iso) {
        if (iso == null || iso.length() < 10) return null;
        try {
            return LocalDate.parse(iso.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String pretty(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US));
    }

    /** "No expiry" / "Valid until Dec 31, 2026" / "Valid from Oct 1, 2026" / "Valid Oct 1, 2026 - Dec 31, 2026" - the same wording as the web. */
    public String validityLabel() {
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        if (start != null && end != null) return "Valid " + pretty(start) + " - " + pretty(end);
        if (end != null) return "Valid until " + pretty(end);
        if (start != null) return "Valid from " + pretty(start);
        return "No expiry";
    }

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
