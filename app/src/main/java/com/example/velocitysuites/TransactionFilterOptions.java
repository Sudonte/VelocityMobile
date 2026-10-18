package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The choices Transaction History's Filters sheet offers. Pure, so they are unit-testable.
 */
final class TransactionFilterOptions {

    private TransactionFilterOptions() {
    }

    /** One choice: the value the filter stores and the words the chip shows. */
    static final class Option {
        final String value;
        final int labelRes;

        Option(String value, int labelRes) {
            this.value = value;
            this.labelRes = labelRes;
        }
    }

    /**
     * Room types to offer: every room type in the live catalog Browse Rooms uses ({@code catalog}, from
     * RoomRepository.getAllRooms()), never a hard-coded list; when that catalog is not available (empty - the request
     * failed or has not answered) the room types found in the guest's own transactions. Sorted, no duplicates.
     */
    @NonNull
    static List<String> roomTypes(@Nullable List<Room> catalog, @NonNull List<TransactionRow> rows) {
        List<String> out = new ArrayList<>();
        if (catalog != null) {
            for (Room room : catalog) {
                if (room != null) addDistinct(out, room.getName());
            }
        }
        if (out.isEmpty()) {
            for (TransactionRow row : rows) {
                for (String type : row.roomTypes) addDistinct(out, type);
            }
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static void addDistinct(List<String> out, @Nullable String name) {
        if (name == null || name.trim().isEmpty()) return;
        String trimmed = name.trim();
        for (String existing : out) if (existing.toLowerCase(Locale.US).equals(trimmed.toLowerCase(Locale.US))) return;
        out.add(trimmed);
    }

    /** Payment status choices (the first, "All", is the default). */
    static List<Option> paymentStatuses() {
        List<Option> list = new ArrayList<>();
        list.add(new Option("", R.string.filters_all_option));
        list.add(new Option("PENDING", R.string.txn_status_pending));
        list.add(new Option("PAID", R.string.txn_status_paid));
        list.add(new Option("PARTIALLY_PAID", R.string.txn_status_partially_paid));
        return list;
    }

    /** Booking status choices: the lifecycle statuses a booking or reservation can be in. */
    static List<Option> bookingStatuses() {
        List<Option> list = new ArrayList<>();
        list.add(new Option("", R.string.filters_all_option));
        list.add(new Option("Pending", R.string.upcoming_status_pending));
        list.add(new Option("Confirmed", R.string.upcoming_status_confirmed));
        list.add(new Option("Checked-In", R.string.upcoming_status_checked_in));
        list.add(new Option("Checked-Out", R.string.upcoming_status_checked_out));
        list.add(new Option("Cancelled", R.string.upcoming_status_cancelled));
        list.add(new Option("Rejected", R.string.upcoming_status_rejected));
        return list;
    }
}
