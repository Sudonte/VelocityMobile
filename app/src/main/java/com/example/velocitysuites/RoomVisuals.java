package com.example.velocitysuites;

import android.content.Context;

import java.util.Locale;

/**
 * Maps room/amenity data to presentation resources (icons, accent colors).
 * Kept separate from {@link Room} so the model stays a plain data holder.
 */
public final class RoomVisuals {

    private RoomVisuals() {}

    public static int getAmenityIcon(String amenity) {
        String a = amenity.toLowerCase(Locale.ROOT);
        if (a.contains("wifi") || a.contains("wi-fi")) return R.drawable.ic_amenity_wifi;
        if (a.contains("air condition") || a.equals("ac") || a.contains(" ac") || a.startsWith("ac")) return R.drawable.ic_amenity_ac;
        if (a.contains("tv")) return R.drawable.ic_amenity_tv;
        if (a.contains("parking")) return R.drawable.ic_amenity_parking;
        if (a.contains("breakfast")) return R.drawable.ic_amenity_breakfast;
        if (a.contains("pool")) return R.drawable.ic_amenity_pool;
        if (a.contains("lounge")) return R.drawable.ic_star;
        if (a.contains("bar") || a.contains("fridge") || a.contains("kitchenette")) return R.drawable.ic_services;
        if (a.contains("living")) return R.drawable.ic_dashboard;
        if (a.contains("restaurant") || a.contains("dining") || a.contains("buffet")) return R.drawable.ic_amenity_restaurant;
        if (a.contains("front desk") || a.contains("concierge") || a.contains("room service")) return R.drawable.ic_amenity_concierge;
        if (a.contains("laundry")) return R.drawable.ic_amenity_concierge;
        if (a.contains("security") || a.contains("guard")) return R.drawable.ic_amenity_security;
        if (a.contains("shuttle") || a.contains("transfer")) return R.drawable.ic_amenity_shuttle;
        return R.drawable.ic_check_circle;
    }

    /**
     * Type-specific room illustration used whenever the backend has no
     * photo for a room type (and as the Glide placeholder while one loads),
     * so every card in All Rooms shows a distinct picture instead of the
     * same flat gradient.
     */
    public static int getRoomImage(String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.contains("deluxe")) return R.drawable.img_room_deluxe;
        if (t.contains("superior")) return R.drawable.img_room_superior;
        if (t.contains("family")) return R.drawable.img_room_family;
        if (t.contains("executive") || t.contains("suite")) return R.drawable.img_room_executive;
        return R.drawable.img_room_standard;
    }

    public static int getTypeIcon(String type) {
        String t = type.toLowerCase(Locale.ROOT);
        if (t.contains("deluxe") || t.contains("superior")) return R.drawable.ic_star;
        if (t.contains("family")) return R.drawable.ic_people;
        if (t.contains("executive")) return R.drawable.ic_bank;
        return R.drawable.ic_booking;
    }

    public static int getTypeAccentColor(String type) {
        String t = type.toLowerCase(Locale.ROOT);
        if (t.contains("deluxe")) return R.color.velocity_red_medium;
        if (t.contains("superior")) return R.color.velocity_red_rose;
        if (t.contains("family")) return R.color.velocity_red_dark;
        if (t.contains("executive")) return R.color.velocity_red_deep;
        return R.color.velocity_red_primary;
    }

    /**
     * The backend doesn't send bed configuration yet, so every room falls
     * back to a sensible default derived from its type/capacity rather than
     * showing a blank "Not specified" in the UI.
     */
    public static String getBedType(Context context, String type, int capacity) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.contains("family") || t.contains("grand") || capacity >= 4) {
            return context.getString(R.string.twin_beds);
        }
        if (t.contains("executive") || t.contains("deluxe") || t.contains("suite")) {
            return context.getString(R.string.king_bed);
        }
        return context.getString(R.string.queen_bed);
    }

}
