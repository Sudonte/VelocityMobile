package com.example.velocitysuites.network.dto;

public class DiscountDto {
    public long id;
    public String name;
    public String discount_type;
    public String value;
    public String description;

    public double valueAsDouble() {
        try {
            return value != null ? Double.parseDouble(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
