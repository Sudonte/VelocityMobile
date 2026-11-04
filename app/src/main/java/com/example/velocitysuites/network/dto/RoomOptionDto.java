package com.example.velocitysuites.network.dto;

import java.util.List;

/** One OTHER option of a room type: its rooms have their own price and/or capacity (GET /rooms -> room_types[].other_options). */
public class RoomOptionDto {
    public String option_key;
    public double price;
    public int capacity;
    public String capacity_label;
    public int total_rooms;
    public int available_count;
    public boolean is_fully_booked;
    public List<PromoOfferDto> promos;
}
