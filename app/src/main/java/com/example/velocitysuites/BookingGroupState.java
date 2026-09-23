package com.example.velocitysuites;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * On-device-only grouping of bookings/reservations created together in a
 * single multi-room transaction. The live API only accepts one room per
 * reservation call (see RoomRepository#createReservation), so a multi-room
 * checkout is submitted as N separate server-side reservations; this class
 * ties their IDs back together under one client-generated group reference so
 * the guest can see "these rooms were booked together." Backed by the same
 * "VelocityPrefs" prefs file already used for other local-only state - there
 * is no server-side equivalent to sync this to.
 */
public final class BookingGroupState {

    private static final String PREFS_NAME = "VelocityPrefs";
    private static final String KEY_GROUP_PREFIX = "booking_group_ref_";
    private static final String KEY_MEMBERS_PREFIX = "booking_group_members_";

    private BookingGroupState() { }

    /**
     * Records that {@code bookingIds} were all created together and should
     * share {@code groupRef}. Safe to call with a single-element list (a
     * plain single-room booking simply never gets tagged).
     */
    public static void saveGroup(Context context, String groupRef, List<String> bookingIds) {
        if (groupRef == null || bookingIds == null || bookingIds.size() < 2) {
            return;
        }
        Set<String> members = new LinkedHashSet<>(bookingIds);
        SharedPreferences.Editor editor = prefs(context).edit();
        String membersCsv = String.join(",", members);
        for (String id : members) {
            if (id == null) continue;
            editor.putString(KEY_GROUP_PREFIX + id, groupRef);
            editor.putString(KEY_MEMBERS_PREFIX + id, membersCsv);
        }
        editor.apply();
    }

    /** The shared group reference for {@code bookingId}, or null if it isn't part of a group. */
    public static String getGroupRef(Context context, String bookingId) {
        if (bookingId == null) return null;
        return prefs(context).getString(KEY_GROUP_PREFIX + bookingId, null);
    }

    /** All booking IDs sharing a group with {@code bookingId}, including itself; empty if ungrouped. */
    public static List<String> getGroupMembers(Context context, String bookingId) {
        List<String> result = new ArrayList<>();
        if (bookingId == null) return result;
        String csv = prefs(context).getString(KEY_MEMBERS_PREFIX + bookingId, null);
        if (csv == null || csv.trim().isEmpty()) return result;
        for (String id : csv.split(",")) {
            if (!id.trim().isEmpty()) result.add(id.trim());
        }
        return result;
    }

    /** Convenience: how many rooms (including this one) were booked in the same transaction. */
    public static int getGroupSize(Context context, String bookingId) {
        List<String> members = getGroupMembers(context, bookingId);
        return members.isEmpty() ? 1 : members.size();
    }

    /**
     * Resolves {@code bookingId}'s full sibling group (this record included)
     * into their actual {@link Booking} records from the repository's shared
     * cache - null if this booking isn't grouped at all. Single source of
     * truth for this resolution so every screen that needs a grouped
     * transaction's full room/total picture (BookingDetailsActivity,
     * TransactionListActivity's read-only detail dialog) reads the exact
     * same sibling list rather than each re-implementing its own lookup - a
     * sibling ID present in the saved group but not found in
     * {@code repository.getBookings()} (e.g. it hasn't been fetched into the
     * cache for some reason) is simply omitted rather than crashing.
     */
    public static List<Booking> resolveGroupMembers(Context context, RoomRepository repository, String bookingId) {
        String groupRef = getGroupRef(context, bookingId);
        if (groupRef == null) {
            return null;
        }
        List<String> memberIds = getGroupMembers(context, bookingId);
        List<Booking> allBookings = repository.getBookings();
        List<Booking> resolved = new ArrayList<>();
        for (String memberId : memberIds) {
            for (Booking candidate : allBookings) {
                if (candidate.getId().equals(memberId)) {
                    resolved.add(candidate);
                    break;
                }
            }
        }
        return resolved;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
