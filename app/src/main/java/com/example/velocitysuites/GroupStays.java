package com.example.velocitysuites;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stays booked together (the server's group, see Api\BookingGroupController): each stay is its own booking or reservation with its own
 * card, details and actions, and this is the one place that knows which ones belong together - their order ("Stay 1 of 2", by the order
 * they were created in), the small label on a card and the list of the other stays.
 */
final class GroupStays {

    private GroupStays() {
    }

    /** Every stay of {@code b}'s group the app has loaded, {@code b} included, in stay order; just {@code b} when it is not part of a group. */
    @NonNull
    static List<Booking> members(@NonNull Context context, @NonNull Booking b) {
        if (!b.isInGroup()) return Collections.singletonList(b);
        List<Booking> out = new ArrayList<>();
        boolean sawSelf = false;
        for (Booking c : RoomRepository.getInstance(context).getAllBookings()) {
            if (c == null || !b.getGroupId().equals(c.getGroupId()) || c.isHasBooking() != b.isHasBooking()) continue;
            if (c.getId().equals(b.getId())) sawSelf = true;
            out.add(c);
        }
        if (!sawSelf) out.add(b);
        Collections.sort(out, (x, y) -> Long.compare(numericId(x), numericId(y)));
        return out;
    }

    /** How many stays the group has, counting the ones that are not loaded: this one plus the ids the server listed. */
    static int size(@NonNull Booking b) {
        return b.isInGroup() ? 1 + b.getGroupSiblingIds().size() : 1;
    }

    /** This stay's place in its group, 1-based: how many of the group's ids are lower than its own, plus one. */
    static int position(@NonNull Booking b) {
        if (!b.isInGroup()) return 1;
        long mine = numericId(b);
        int before = 0;
        for (Long id : b.getGroupSiblingIds()) if (id != null && id < mine) before++;
        return before + 1;
    }

    /** "Group · Stay 1 of 2", or null for a single stay. */
    @Nullable
    static String label(@NonNull Context context, @NonNull Booking b) {
        if (!b.isInGroup() || size(b) < 2) return null;
        return context.getString(R.string.group_stay_label_format, position(b), size(b));
    }

    /** The group's stays that are checked in right now (the ones a guest can request amenities for). */
    @NonNull
    static List<Booking> checkedIn(@NonNull Context context, @NonNull Booking b) {
        List<Booking> out = new ArrayList<>();
        for (Booking m : members(context, b)) if ("Checked-In".equalsIgnoreCase(m.getStatus())) out.add(m);
        return out;
    }

    /** One line per stay for a picker or a list: "Stay 2 · Booking #12 · Nov 10, 2026 - Nov 12, 2026". */
    @NonNull
    static String describe(@NonNull Context context, @NonNull Booking stay, int position) {
        String ref = context.getString(stay.isHasBooking() ? R.string.direct_booking_ref_format : R.string.reservation_ref_format, stay.getDisplayId());
        return context.getString(R.string.group_stay_line_format, position, ref, stay.getCheckInDate(), stay.getCheckOutDate());
    }

    /** A dialog listing the OTHER stays of this booking's group; tapping one opens it. */
    static void showOtherStays(@NonNull Activity activity, @NonNull Booking b) {
        List<Booking> all = members(activity, b);
        List<Booking> others = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(b.getId())) continue;
            others.add(all.get(i));
            lines.add(describe(activity, all.get(i), i + 1));
        }
        if (others.isEmpty()) {
            new SafeDialogs.Material(activity).setTitle(R.string.group_stays_title).setMessage(R.string.group_stays_not_loaded)
                    .setPositiveButton(R.string.close_label, null).show();
            return;
        }
        new SafeDialogs.Material(activity)
                .setTitle(R.string.group_stays_title)
                .setItems(lines.toArray(new String[0]), (d, which) -> activity.startActivity(BookingDetailsActivity.newIntent(activity, others.get(which))))
                .setNegativeButton(R.string.close_label, null)
                .show();
    }

    /**
     * Request Additional Amenities for a stay of a group: when several of its stays are checked in, the guest first picks the stay
     * (dates and booking number), then the room and the amenities as always. One stay (or no group): straight to the picker.
     */
    static void requestAmenities(@NonNull Activity activity, @NonNull Booking b) {
        List<Booking> inHouse = checkedIn(activity, b);
        if (inHouse.size() < 2) {
            activity.startActivity(AdditionalAmenitiesEntry.intent(activity, b));
            return;
        }
        List<String> lines = new ArrayList<>();
        List<Booking> all = members(activity, b);
        for (Booking m : inHouse) lines.add(describe(activity, m, all.indexOf(m) + 1));
        new SafeDialogs.Material(activity)
                .setTitle(R.string.group_pick_stay_title)
                .setItems(lines.toArray(new String[0]), (d, which) -> activity.startActivity(AdditionalAmenitiesEntry.intent(activity, inHouse.get(which))))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static long numericId(@NonNull Booking b) {
        try {
            return Long.parseLong(b.getId());
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }
}
