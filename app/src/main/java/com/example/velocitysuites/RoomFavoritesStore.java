package com.example.velocitysuites;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * On-device-only "favorite this room type" toggle for the landing.xml/
 * roombrowsing.xml room cards - there is no backend/API concept of guest
 * favorites in this app, so this is a real, working, but purely local
 * feature (same "VelocityPrefs" prefs file already used elsewhere - see
 * LocalTransactionState), keyed by Room#getId() (the room TYPE id, shared
 * across both screens since they render the same catalog).
 */
public final class RoomFavoritesStore {

    private static final String PREFS_NAME = "VelocityPrefs";
    private static final String KEY_FAVORITE_ROOM_IDS = "favorite_room_ids";

    private RoomFavoritesStore() { }

    public static boolean isFavorite(Context context, String roomId) {
        if (roomId == null) return false;
        return prefs(context).getStringSet(KEY_FAVORITE_ROOM_IDS, new HashSet<>()).contains(roomId);
    }

    /** Flips the favorite state for this room id and returns the new state. */
    public static boolean toggleFavorite(Context context, String roomId) {
        if (roomId == null) return false;
        SharedPreferences p = prefs(context);
        Set<String> current = new HashSet<>(p.getStringSet(KEY_FAVORITE_ROOM_IDS, new HashSet<>()));
        boolean nowFavorite = !current.contains(roomId);
        if (nowFavorite) {
            current.add(roomId);
        } else {
            current.remove(roomId);
        }
        p.edit().putStringSet(KEY_FAVORITE_ROOM_IDS, current).apply();
        return nowFavorite;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
