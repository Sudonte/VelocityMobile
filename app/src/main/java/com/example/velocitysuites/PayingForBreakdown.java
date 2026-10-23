package com.example.velocitysuites;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "What you're paying for": one breakdown for a booking or reservation, built the same way for a saved transaction ({@link Booking}) and
 * for a new one that is still the reviewed wizard state ({@link BookingWizardState}). Per stay: its heading, its rooms, then that stay's
 * amenities (under the room they were chosen for, else under "Amenities for this stay") and, with several stays, the stay's subtotal.
 * After the stays: Room charges, Amenities, the Grand total (rooms + amenities, before any discount) and the expected-discount note.
 * Every row is label left, amount right.
 */
final class PayingForBreakdown {

    /** One priced line: "Deluxe × 1" and its amount. */
    static final class Line {
        final String label;
        final double amount;

        Line(String label, double amount) {
            this.label = label;
            this.amount = amount;
        }
    }

    /** Amenities of one room ({@code room} = "Deluxe (room 1)") or of the stay as a whole ({@code room} = null). */
    static final class AmenityGroup {
        @Nullable final String room;
        final List<Line> lines = new ArrayList<>();

        AmenityGroup(@Nullable String room) {
            this.room = room;
        }
    }

    static final class StayBlock {
        @Nullable String heading;
        final List<Line> rooms = new ArrayList<>();
        final List<AmenityGroup> amenities = new ArrayList<>();
        double subtotal;
        int position;

        double roomCharges() {
            double sum = 0;
            for (Line l : rooms) sum += l.amount;
            return sum;
        }

        double amenityCharges() {
            double sum = 0;
            for (AmenityGroup g : amenities) for (Line l : g.lines) sum += l.amount;
            return sum;
        }

        boolean hasAmenities() {
            for (AmenityGroup g : amenities) if (!g.lines.isEmpty()) return true;
            return false;
        }
    }

    final List<StayBlock> stays = new ArrayList<>();
    /** The Grand total the guest pays against (rooms + amenities; after an applied discount, the server's net). */
    double grandTotal;
    /** The discount an ID check is still expected to take off (a note, never a subtraction); 0 for none. */
    double expectedDiscount;
    @Nullable String discountName;
    /** A discount the front desk already applied (a subtraction); 0 for none. */
    double appliedDiscount;

    double roomCharges() {
        double sum = 0;
        for (StayBlock s : stays) sum += s.roomCharges();
        return sum;
    }

    double amenityCharges() {
        double sum = 0;
        for (StayBlock s : stays) sum += s.amenityCharges();
        return sum;
    }

    boolean hasAmenities() {
        for (StayBlock s : stays) if (s.hasAmenities()) return true;
        return false;
    }

    boolean isEmpty() {
        return stays.isEmpty();
    }

    // ---- building ----

    /** A saved booking or reservation. {@code grandTotal} is what the screen charges against. */
    static PayingForBreakdown of(Context c, @NonNull Booking booking, double grandTotal) {
        PayingForBreakdown out = new PayingForBreakdown();
        out.grandTotal = grandTotal;
        boolean several = booking.hasSeveralStays();
        if (!booking.getStays().isEmpty()) {
            for (Booking.StayRecord stay : booking.getStays()) {
                if (stay == null) continue;
                StayBlock block = new StayBlock();
                block.position = stay.position;
                long n = BookingTotals.nightsOf(stay);
                String nights = c.getResources().getQuantityString(R.plurals.receipt_nights_count, (int) n, (int) n);
                String dates = StayCardText.stayDates(stay.checkIn, stay.checkOut);
                block.heading = several ? c.getString(R.string.details_stay_heading_format, stay.position, dates, nights)
                        : c.getString(R.string.details_single_stay_heading_format, dates, nights);
                for (BookingRoom room : stay.rooms) {
                    if (room != null) block.rooms.add(new Line(roomLine(c, room.getRoomTypeName(), Math.max(1, room.getQuantity())), BookingTotals.lineTotal(room, stay)));
                }
                addBookedAmenities(c, block, stay.amenities);
                block.subtotal = block.roomCharges() + block.amenityCharges();
                out.stays.add(block);
            }
        } else {
            // an older record with no stays: one block from its own rooms and amenities
            StayBlock block = new StayBlock();
            for (BookingRoom room : booking.getRooms()) {
                if (room != null) block.rooms.add(new Line(roomLine(c, room.getRoomTypeName(), Math.max(1, room.getQuantity())), room.getSubtotal()));
            }
            addBookedAmenities(c, block, booking.getAmenities());
            if (block.rooms.isEmpty() && grandTotal > 0.009) {
                block.rooms.add(new Line(roomLine(c, booking.getRoomName(), Math.max(1, booking.getRoomsRequested())), Math.max(0, grandTotal - block.amenityCharges())));
            }
            block.subtotal = block.roomCharges() + block.amenityCharges();
            if (!block.rooms.isEmpty()) out.stays.add(block);
        }
        out.expectedDiscount = BookingTotals.expectedDiscount(booking);
        out.appliedDiscount = BookingTotals.discountAppliedAmount(booking);
        out.discountName = BookingTotals.discountLabel(booking);
        return out;
    }

    /** A new booking or reservation that is still the reviewed wizard state. */
    static PayingForBreakdown of(Context c, @NonNull BookingWizardState state) {
        PayingForBreakdown out = new PayingForBreakdown();
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        List<BookingWizardState.Stay> all = state.allStays();
        boolean several = all.size() > 1;
        for (int i = 0; i < all.size(); i++) {
            BookingWizardState.Stay stay = all.get(i);
            if (stay.checkIn == null || stay.checkOut == null) continue;
            StayBlock block = new StayBlock();
            block.position = i + 1;
            long n = stay.nights();
            String nights = c.getResources().getQuantityString(R.plurals.receipt_nights_count, (int) n, (int) n);
            String dates = StayCardText.stayDates(iso.format(stay.checkIn.getTime()), iso.format(stay.checkOut.getTime()));
            block.heading = several ? c.getString(R.string.details_stay_heading_format, i + 1, dates, nights)
                    : c.getString(R.string.details_single_stay_heading_format, dates, nights);
            for (List<Room> group : stay.roomsGroupedByType().values()) {
                Room room = group.get(0);
                block.rooms.add(new Line(roomLine(c, room.getName(), group.size()), room.getPricePerNight() * n * group.size()));
            }
            if (stay.roomAmenityQty.isEmpty()) {
                // chosen without a per-room split: the stay's own list
                AmenityGroup g = new AmenityGroup(null);
                for (AddOnAmenity a : stay.amenities) g.lines.add(new Line(c.getString(R.string.summary_amenity_row_qty_format, a.getName(), a.getQuantity()), a.getSubtotal()));
                if (!g.lines.isEmpty()) block.amenities.add(g);
            } else {
                addChosenAmenities(c, block, stay, stay.anyRoomSlot(), null);
                for (BookingWizardState.Stay.Slot slot : stay.roomSlots()) {
                    addChosenAmenities(c, block, stay, slot, c.getString(R.string.amenity_room_heading_format, slot.room.getName(), slot.number));
                }
            }
            block.subtotal = block.roomCharges() + block.amenityCharges();
            out.stays.add(block);
        }
        out.grandTotal = state.roomsTotal() + state.amenitiesTotal();
        if (state.discount != null) {
            out.expectedDiscount = state.discount.estimateOff(out.grandTotal);
            out.discountName = state.discount.getName();
        }
        return out;
    }

    private static void addChosenAmenities(Context c, StayBlock block, BookingWizardState.Stay stay, BookingWizardState.Stay.Slot slot, @Nullable String roomLabel) {
        java.util.Map<String, Integer> chosen = stay.roomAmenityQty.get(slot.key);
        if (chosen == null || chosen.isEmpty()) return;
        AmenityGroup g = new AmenityGroup(roomLabel);
        for (java.util.Map.Entry<String, Integer> e : chosen.entrySet()) {
            AddOnAmenity a = stay.amenityCatalog.get(e.getKey());
            if (a == null || e.getValue() <= 0) continue;
            g.lines.add(new Line(c.getString(R.string.summary_amenity_row_qty_format, a.getName(), e.getValue()), a.getPrice() * e.getValue()));
        }
        if (!g.lines.isEmpty()) block.amenities.add(g);
    }

    private static void addBookedAmenities(Context c, StayBlock block, List<BookingAmenity> amenities) {
        java.util.Map<String, AmenityGroup> byRoom = new java.util.LinkedHashMap<>();
        for (BookingAmenity a : amenities) {
            if (a == null) continue;
            String room = a.getRoomLabel();
            AmenityGroup g = byRoom.get(room == null ? "" : room);
            if (g == null) {
                g = new AmenityGroup(room);
                byRoom.put(room == null ? "" : room, g);
            }
            g.lines.add(new Line(c.getString(R.string.summary_amenity_row_qty_format, a.getAmenityName(), a.getQuantity()), a.getSubtotal()));
        }
        block.amenities.addAll(byRoom.values());
    }

    private static String roomLine(Context c, @Nullable String name, int count) {
        return c.getString(R.string.gcash_room_line_format, name != null && !name.trim().isEmpty() ? name : c.getString(R.string.label_not_available), count);
    }

    // ---- drawing ----

    /** Fills {@code into} (cleared first) with the breakdown. */
    void render(Context c, ViewGroup into) {
        into.removeAllViews();
        boolean several = stays.size() > 1;
        for (StayBlock stay : stays) renderStay(c, into, stay, several);
        if (!hasAmenities()) into.addView(noAmenities(c));
        if (stays.isEmpty()) return;
        View rule = new View(c);
        rule.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))));
        rule.setBackgroundColor(ContextCompat.getColor(c, R.color.velocity_divider_hairline));
        LinearLayout.LayoutParams ruleLp = (LinearLayout.LayoutParams) rule.getLayoutParams();
        ruleLp.topMargin = dp(c, 8);
        ruleLp.bottomMargin = dp(c, 4);
        into.addView(rule);
        into.addView(row(c, c.getString(R.string.review_rooms_total_label), roomCharges(), false));
        into.addView(row(c, c.getString(R.string.breakdown_amenities_total_label), amenityCharges(), false));
        if (appliedDiscount > 0.009) {
            into.addView(row(c, c.getString(R.string.details_label_discount), -appliedDiscount, false));
        }
        into.addView(row(c, c.getString(R.string.review_grand_total_label), grandTotal, true));
        if (expectedDiscount > 0.009) {
            TextView note = small(c, c.getString(R.string.details_expected_discount_format,
                    discountName == null || discountName.isEmpty() ? c.getString(R.string.details_label_discount) : discountName, MoneyFormat.format(expectedDiscount)), 6);
            into.addView(note);
        }
    }

    /** "No amenities added", in the secondary colour. */
    static TextView noAmenities(Context c) {
        return small(c, c.getString(R.string.breakdown_no_amenities), 6);
    }

    /** One stay: heading, rooms, its amenities and (with several stays) its subtotal. */
    static void renderStay(Context c, ViewGroup into, StayBlock stay, boolean withSubtotal) {
        if (stay.heading != null) into.addView(heading(c, stay.heading));
        for (Line l : stay.rooms) into.addView(row(c, l.label, l.amount, false));
        for (AmenityGroup g : stay.amenities) {
            if (g.lines.isEmpty()) continue;
            if (g.room == null) into.addView(small(c, c.getString(R.string.breakdown_stay_amenities_heading), 4));
            for (Line l : g.lines) {
                into.addView(row(c, g.room == null ? l.label : c.getString(R.string.breakdown_room_amenity_format, g.room, l.label), l.amount, false));
            }
        }
        if (withSubtotal) into.addView(row(c, c.getString(R.string.breakdown_stay_subtotal_format, stay.position), stay.subtotal, true));
    }

    private static View row(Context c, String label, double amount, boolean bold) {
        return ReviewSheet.row(c, bold ? ReviewSheet.Row.bold(label, MoneyFormat.format(amount)) : ReviewSheet.Row.of(label, MoneyFormat.format(amount)));
    }

    private static TextView heading(Context c, String text) {
        TextView tv = new TextView(c);
        tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setTextColor(ContextCompat.getColor(c, R.color.velocity_text_primary));
        tv.setPadding(0, dp(c, 8), 0, dp(c, 2));
        return tv;
    }

    /** A small secondary-colour line ("No amenities added", the discount note). */
    private static TextView small(Context c, String text, int topDp) {
        TextView tv = new TextView(c);
        tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(ContextCompat.getColor(c, R.color.velocity_text_secondary));
        tv.setPadding(0, dp(c, topDp), 0, dp(c, 2));
        return tv;
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
