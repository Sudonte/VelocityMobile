package com.example.velocitysuites.network.dto;

public class AmenityDto {
    public long id;
    public String amenity_name;
    public String category;
    public String description;
    public String charge;
    public int quantity;
    /** Optional: the catalog endpoint is documented active-only, but if it ever sends a flag or a status the app honours it. */
    public Boolean is_active;
    public String status;

    /** False only when the server says the amenity is inactive / unavailable (absent fields mean "active"). */
    public boolean isActive() {
        if (is_active != null && !is_active) return false;
        if (status == null) return true;
        String s = status.trim().toLowerCase(java.util.Locale.US);
        return !(s.equals("inactive") || s.equals("unavailable") || s.equals("disabled") || s.equals("archived"));
    }

    public double chargeAsDouble() {
        try {
            return charge != null ? Double.parseDouble(charge) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
