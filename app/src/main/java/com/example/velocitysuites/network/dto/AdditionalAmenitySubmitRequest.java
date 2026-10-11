package com.example.velocitysuites.network.dto;

import java.util.ArrayList;
import java.util.List;

public class AdditionalAmenitySubmitRequest {
    public static class Item {
        public final long amenity_id;
        public final int quantity;

        public Item(long amenityId, int quantity) {
            this.amenity_id = amenityId;
            this.quantity = quantity;
        }
    }

    public final List<Item> items = new ArrayList<>();
    public final String note;

    public AdditionalAmenitySubmitRequest(String note) {
        this.note = note;
    }
}
