package com.example.velocitysuites;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Remembers room(s) picked on landing.xml across the Sign In/Sign Up
 * round-trip (SharedPreferences, not in-memory, since auth can restart the
 * process). Supports one or many rooms - the multi-room cart summary bar
 * saves the whole selection; a single per-card Book Now/Reserve Now saves
 * just that one room. Either way, ROOM_IDS carries the full comma-joined id
 * list into RoomBrowsingActivity post-auth; the legacy singular ROOM_ID is
 * also set (to the first id) for older single-room call sites.
 */
final class PendingRoomSelection {
    private static final String PREFS_NAME = "VelocityPrefs";
    private static final String KEY_PENDING_ROOM_IDS = "pendingLandingRoomIds";
    private static final String KEY_PENDING_ROOM_ACTION = "pendingLandingRoomAction";
    private static final String ID_SEPARATOR = ",";

    /** Which button the guest tapped on landing.xml - controls which action button(s) roombrowsing.xml shows afterward. */
    static final String ACTION_VIEW = "VIEW_DETAILS";
    static final String ACTION_BOOK = "BOOK_NOW";
    static final String ACTION_RESERVE = "RESERVE_NOW";

    private PendingRoomSelection() {}

    static void save(Context context, Room room, String action) {
        saveMultiple(context, Collections.singletonList(room), action);
    }

    static void saveMultiple(Context context, List<Room> rooms, String action) {
        List<String> ids = new ArrayList<>();
        for (Room r : rooms) ids.add(r.getId());
        prefs(context).edit()
                .putString(KEY_PENDING_ROOM_IDS, String.join(ID_SEPARATOR, ids))
                .putString(KEY_PENDING_ROOM_ACTION, action)
                .apply();
    }

    static boolean hasPendingRoom(Context context) {
        String ids = prefs(context).getString(KEY_PENDING_ROOM_IDS, null);
        return ids != null && !ids.isEmpty();
    }

    static void clear(Context context) {
        prefs(context).edit().remove(KEY_PENDING_ROOM_IDS).remove(KEY_PENDING_ROOM_ACTION).apply();
    }

    static Intent createPostAuthIntent(Context context, String userName) {
        SharedPreferences preferences = prefs(context);
        String idsCsv = preferences.getString(KEY_PENDING_ROOM_IDS, null);
        String action = preferences.getString(KEY_PENDING_ROOM_ACTION, ACTION_VIEW);

        if (idsCsv != null && !idsCsv.isEmpty()) {
            // Room-selection flow: the guest picked room(s) on the landing
            // screen, so after signing in/up send them straight into
            // RoomBrowsingActivity with that same selection carried over.
            preferences.edit().remove(KEY_PENDING_ROOM_IDS).remove(KEY_PENDING_ROOM_ACTION).apply();
            String[] ids = idsCsv.split(ID_SEPARATOR);
            Intent intent = new Intent(context, RoomBrowsingActivity.class);
            intent.putExtra("ROOM_ID", ids[0]);
            intent.putExtra("ROOM_IDS", idsCsv);
            intent.putExtra("OPEN_ROOM_DETAILS", ids.length == 1);
            intent.putExtra("ROOM_ACTION", action);
            intent.putExtra("USER_NAME", userName);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            return intent;
        }

        // "Next" flow: no room was pre-selected, so after signing in/up the
        // guest lands on their dashboard.
        Intent intent = new Intent(context, DashboardActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}

