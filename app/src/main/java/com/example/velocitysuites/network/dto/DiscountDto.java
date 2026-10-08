package com.example.velocitysuites.network.dto;

public class DiscountDto {
    public long id;
    public String name;
    public String discount_type;
    public String value;
    public String description;
    public String status;
    /** Optional validity window (yyyy-MM-dd, hotel-local days, inclusive); null = no limit on that side. */
    public String start_date;
    public String end_date;
    public String created_at;
    public String updated_at;

    public double valueAsDouble() {
        try {
            return value != null ? Double.parseDouble(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
