package com.example.velocitysuites;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * On-device-only lifecycle flags for bookings/reservations that the live API
 * has no field for (one-time-modify). Backed by the same "VelocityPrefs"
 * prefs file already used for profile prefill, so it survives app restarts
 * but not reinstalls/other devices - there is no server-side equivalent to
 * sync this to. Deletion is NOT tracked here - it's a real, permanent server-
 * side row deletion (see RoomRepository#deleteReservationPermanently()/
 * #deleteBookingPermanently()), so there is nothing local left to sync once
 * the guest confirms it. No-Show is likewise no longer
 * tracked here - it's now a real, server-authoritative field (see
 * Booking#isNoShow()) instead of an on-device simulation.
 */
public final class LocalTransactionState {

    private static final String PREFS_NAME = "VelocityPrefs";
    private static final String KEY_MODIFIED_ONCE_IDS = "modified_once_reservation_ids";

    private LocalTransactionState() { }

    public static boolean hasModifiedOnce(Context context, String id) {
        return contains(context, KEY_MODIFIED_ONCE_IDS, id);
    }

    public static void markModifiedOnce(Context context, String id) {
        add(context, KEY_MODIFIED_ONCE_IDS, id);
    }

    private static boolean contains(Context context, String key, String id) {
        if (id == null) return false;
        return prefs(context).getStringSet(key, new HashSet<>()).contains(id);
    }

    private static void add(Context context, String key, String id) {
        if (id == null) return;
        SharedPreferences p = prefs(context);
        Set<String> current = new HashSet<>(p.getStringSet(key, new HashSet<>()));
        current.add(id);
        p.edit().putStringSet(key, current).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
