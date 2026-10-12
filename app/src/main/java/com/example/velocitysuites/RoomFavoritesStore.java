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

    /**
     * Shows the heart for this room and wires its tap - the one place both the room card and the room-details
     * dialog use, so they can never disagree. The icon flips instantly; the choice is persisted by toggleFavorite().
     */
    public static void bindHeart(android.widget.ImageView heart, Context context, String roomId, String roomName) {
        if (heart == null) return;
        String name = roomName == null ? "" : roomName;
        applyIcon(heart, context, isFavorite(context, roomId), name);
        heart.setOnClickListener(v -> applyIcon(heart, context, toggleFavorite(context, roomId), name));
    }

    private static void applyIcon(android.widget.ImageView heart, Context context, boolean favorite, String roomName) {
        heart.setImageResource(favorite ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_border);
        heart.setContentDescription(context.getString(
                favorite ? R.string.cd_remove_from_favorites_format : R.string.cd_add_to_favorites_format, roomName));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
