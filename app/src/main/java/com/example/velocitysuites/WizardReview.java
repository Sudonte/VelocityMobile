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
        return c.getString(R.string.review_stay_heading_format, fmt.format(stay.checkIn.getTime()), fmt.format(stay.checkOut.getTime()),
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
            section.add(ReviewSheet.Row.bold(c.getString(R.string.review_stay_subtotal), MoneyFormat.format(stay.roomsTotal())));
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
            ReviewSheet.Section section = new ReviewSheet.Section(c.getString(R.string.amenity_stay_heading_format,
                    fmt.format(stay.checkIn.getTime()), fmt.format(stay.checkOut.getTime())));
            if (stay.roomAmenityQty.isEmpty()) { // seeded without a per-room split (older state): the stay's amenities as they are
                for (AddOnAmenity a : stay.amenities) {
                    section.add(ReviewSheet.Row.indented(c.getString(R.string.summary_amenity_row_qty_format, a.getName(), a.getQuantity()), MoneyFormat.format(a.getSubtotal())));
                }
            }
            Map<String, Integer> anyRoom = stay.roomAmenityQty.get(BookingWizardState.Stay.ANY_ROOM);
            if (anyRoom != null && !anyRoom.isEmpty()) {
                section.add(ReviewSheet.Row.bold(c.getString(R.string.amenity_any_room_heading), null));
                for (Map.Entry<String, Integer> e : anyRoom.entrySet()) {
                    AddOnAmenity a = stay.amenityCatalog.get(e.getKey());
                    if (a != null) section.add(ReviewSheet.Row.indented(c.getString(R.string.summary_amenity_row_qty_format, a.getName(), e.getValue()), MoneyFormat.format(a.getPrice() * e.getValue())));
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

    /**
     * "Promos for your rooms (verified at check-in)": every promo running for the room types chosen, with the estimated saving on those
     * rooms as a note. Never subtracted - the front desk verifies one promo at check-in (like the expected ID discount). Empty without any.
     */
    @NonNull
    static List<ReviewSheet.Row> promoRows(Context c, BookingWizardState state) {
        List<ReviewSheet.Row> rows = new ArrayList<>();
        List<PromoOffer> promos = PayingForBreakdown.promosFor(state);
        if (promos.isEmpty()) return rows;
        rows.add(ReviewSheet.Row.bold(c.getString(R.string.promos_for_your_rooms), null));
        for (PromoOffer p : promos) {
            String where = p.roomTypeName == null ? c.getString(R.string.promo_all_room_types) : p.roomTypeName;
            rows.add(ReviewSheet.Row.of(p.title, p.label.isEmpty() ? where : p.label + " · " + where));
            if (p.estimatedSaving > 0.009) {
                rows.add(ReviewSheet.Row.indented(c.getString(R.string.promo_saving_note_format, String.format(java.util.Locale.US, "%,.2f", p.estimatedSaving)), null));
            }
        }
        rows.add(ReviewSheet.Row.indented(c.getString(R.string.promo_to_be_verified_note), null));
        return rows;
    }

    /** Rooms ₱x / Amenities ₱y / Grand total ₱z. */
    @NonNull
    static List<ReviewSheet.Row> totals(Context c, BookingWizardState state) {
        List<ReviewSheet.Row> rows = new ArrayList<>();
        rows.add(ReviewSheet.Row.of(c.getString(R.string.review_rooms_total_label), MoneyFormat.format(state.roomsTotal())));
        rows.add(ReviewSheet.Row.of(c.getString(R.string.review_amenities_total_label), MoneyFormat.format(state.amenitiesTotal())));
        rows.add(ReviewSheet.Row.bold(c.getString(R.string.review_grand_total_label), MoneyFormat.format(state.roomsTotal() + state.amenitiesTotal())));
        rows.addAll(promoRows(c, state));
        return rows;
    }
}
