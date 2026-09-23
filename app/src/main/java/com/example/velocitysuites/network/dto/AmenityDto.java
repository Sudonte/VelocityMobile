package com.example.velocitysuites.network.dto;

public class AmenityDto {
    public long id;
    public String amenity_name;
    public String category;
    public String description;
    public String charge;
    public int quantity;

    public double chargeAsDouble() {
        try {
            return charge != null ? Double.parseDouble(charge) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
