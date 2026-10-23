package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the guest chose, as review rows - one place, used by the "Review your stays" and amenities sheets and by the final "Review and bill
 * summary", so the same stay always reads the same everywhere. All figures come from {@link BookingWizardState}.
 */
final class WizardReview {

    private WizardReview() {
    }

    /** "Oct 25" */
    private static SimpleDateFormat shortDate() {
        return new SimpleDateFormat("MMM d", Locale.US);
    }

    /** "1 night" / "3 nights" */
    static String nights(Context c, long nights) {
        return c.getResources().getQuantityString(R.plurals.receipt_nights_count, (int) nights, (int) nights);
    }

    /** "Stay 1 · Oct 25 → Oct 26 · 1 night" */
    static String stayHeading(Context c, int index, BookingWizardState.Stay stay) {
        SimpleDateFormat fmt = shortDate();
        return c.getString(R.string.review_stay_heading_format, index + 1, fmt.format(stay.checkIn.getTime()), fmt.format(stay.checkOut.getTime()),
                nights(c, stay.nights()));
    }

    /** Per stay: its dates, every room type x quantity x price per night x nights with the amount, then the stay's subtotal. */
    @NonNull
    static List<ReviewSheet.Section> stays(Context c, BookingWizardState state) {
        List<ReviewSheet.Section> sections = new ArrayList<>();
        List<BookingWizardState.Stay> stays = state.allStays();
        for (int i = 0; i < stays.size(); i++) {
            BookingWizardState.Stay stay = stays.get(i);
            if (stay.checkIn == null || stay.checkOut == null) continue;
            long nights = stay.nights();
            ReviewSheet.Section section = new ReviewSheet.Section(stayHeading(c, i, stay));
            for (List<Room> group : stay.roomsGroupedByType().values()) {
                Room room = group.get(0);
                section.add(ReviewSheet.Row.of(c.getString(R.string.review_room_line_format, group.size(), room.getName(),
                                MoneyFormat.format(room.getPricePerNight()), nights(c, nights)),
                        MoneyFormat.format(room.getPricePerNight() * nights * group.size())));
            }
            section.add(ReviewSheet.Row.bold(c.getString(R.string.review_stay_subtotal_format, i + 1), MoneyFormat.format(stay.roomsTotal())));
            sections.add(section);
        }
        return sections;
    }

    /** Per stay, then per room: the amenities with quantities and amounts. Stays without amenities are left out. */
    @NonNull
    static List<ReviewSheet.Section> amenities(Context c, BookingWizardState state) {
        List<ReviewSheet.Section> sections = new ArrayList<>();
        SimpleDateFormat fmt = shortDate();
        List<BookingWizardState.Stay> stays = state.allStays();
        for (int i = 0; i < stays.size(); i++) {
            BookingWizardState.Stay stay = stays.get(i);
            if (stay.amenities.isEmpty() || stay.checkIn == null || stay.checkOut == null) continue;
            ReviewSheet.Section section = new ReviewSheet.Section(c.getString(R.string.amenity_stay_heading_format, i + 1,
                    fmt.format(stay.checkIn.getTime()), fmt.format(stay.checkOut.getTime())));
            if (stay.roomAmenityQty.isEmpty()) { // seeded without a per-room split (older state): the stay's amenities as they are
                for (AddOnAmenity a : stay.amenities) {
                    section.add(ReviewSheet.Row.indented(c.getString(R.string.summary_amenity_row_qty_format, a.getName(), a.getQuantity()), MoneyFormat.format(a.getSubtotal())));
                }
            }
            for (BookingWizardState.Stay.Slot slot : stay.roomSlots()) {
                Map<String, Integer> forRoom = stay.roomAmenityQty.get(slot.key);
                if (forRoom == null || forRoom.isEmpty()) continue;
                section.add(ReviewSheet.Row.bold(c.getString(R.string.amenity_room_heading_format, slot.room.getName(), slot.number), null));
                for (Map.Entry<String, Integer> e : forRoom.entrySet()) {
                    AddOnAmenity a = stay.amenityCatalog.get(e.getKey());
                    if (a == null) continue;
                    section.add(ReviewSheet.Row.indented(c.getString(R.string.summary_amenity_row_qty_format, a.getName(), e.getValue()),
                            MoneyFormat.format(a.getPrice() * e.getValue())));
                }
            }
            sections.add(section);
        }
        return sections;
    }

    /** Rooms ₱x / Amenities ₱y / Grand total ₱z. */
    @NonNull
    static List<ReviewSheet.Row> totals(Context c, BookingWizardState state) {
        List<ReviewSheet.Row> rows = new ArrayList<>();
        rows.add(ReviewSheet.Row.of(c.getString(R.string.review_rooms_total_label), MoneyFormat.format(state.roomsTotal())));
        rows.add(ReviewSheet.Row.of(c.getString(R.string.review_amenities_total_label), MoneyFormat.format(state.amenitiesTotal())));
        rows.add(ReviewSheet.Row.bold(c.getString(R.string.review_grand_total_label), MoneyFormat.format(state.roomsTotal() + state.amenitiesTotal())));
        return rows;
    }
}
