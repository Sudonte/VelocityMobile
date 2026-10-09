package com.example.velocitysuites.ui;

import com.example.velocitysuites.network.dto.AmenityDto;
import com.example.velocitysuites.network.dto.AnnouncementDto;
import com.example.velocitysuites.network.dto.DiscountDto;
import com.example.velocitysuites.network.dto.PromotionDto;
import com.example.velocitysuites.network.dto.RoomTypeDto;
import com.example.velocitysuites.network.dto.RoomsResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The server's answers for the landing page, as the DTOs Retrofit would hand back. */
final class LandingFixtures {

    private LandingFixtures() {
    }

    static RoomTypeDto roomType(long id, String name, String rate, int available) {
        RoomTypeDto type = new RoomTypeDto();
        type.id = id;
        type.name = name;
        type.capacity = 2;
        type.rate = rate;
        type.description = "About " + name;
        type.bed_type = "Queen";
        type.room_size = "24";
        type.available_count = available;
        type.is_fully_booked = available <= 0;
        type.amenities = new ArrayList<>();
        return type;
    }

    static RoomsResponse rooms(RoomTypeDto... types) {
        RoomsResponse response = new RoomsResponse();
        response.room_types = ScreenTestSupport.page(Arrays.asList(types));
        return response;
    }

    static AnnouncementDto announcement(long id, String title) {
        AnnouncementDto a = new AnnouncementDto();
        a.id = id;
        a.title = title;
        a.content = "Details of " + title;
        a.published_at = "2026-10-01T08:00:00Z";
        a.target_audience = Collections.singletonList("guest");
        a.images = new ArrayList<>();
        return a;
    }

    static PromotionDto promotion(long id, String name) {
        PromotionDto p = new PromotionDto();
        p.id = id;
        p.promo_name = name;
        p.description = "About " + name;
        p.start_date = "2026-10-01";
        p.end_date = "2026-12-31";
        p.amenities = new ArrayList<>();
        return p;
    }

    static DiscountDto discount(long id, String name) {
        DiscountDto d = new DiscountDto();
        d.id = id;
        d.name = name;
        d.discount_type = "percentage";
        d.value = "20.00";
        d.description = "About " + name;
        d.status = "active";
        return d;
    }

    static AmenityDto amenity(long id, String name, String category) {
        AmenityDto a = new AmenityDto();
        a.id = id;
        a.amenity_name = name;
        a.category = category;
        a.description = "About " + name;
        a.charge = "0.00";
        a.quantity = 1;
        return a;
    }

    static List<AmenityDto> amenities(AmenityDto... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    static List<AnnouncementDto> announcements(AnnouncementDto... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    static List<PromotionDto> promotions(PromotionDto... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    static List<DiscountDto> discounts(DiscountDto... items) {
        return new ArrayList<>(Arrays.asList(items));
    }
}
