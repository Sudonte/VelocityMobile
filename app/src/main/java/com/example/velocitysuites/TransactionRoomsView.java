package com.example.velocitysuites;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;


/**
 * The ONE view of "Your rooms" (see {@link TransactionRooms}): each room as its own small card with its approved amenities and
 * subtotal, the amenities that are not linked to a room under "All rooms", requests still waiting for approval (never counted),
 * and the amenities total. Label left, amount right, the same spacing on every screen that shows it (Your Next Stay full details,
 * Booking Details, Transaction History, the Upcoming details sheet).
 */
final class TransactionRoomsView {

    private TransactionRoomsView() {
    }

    @NonNull
    static LinearLayout build(@NonNull Context c, @NonNull Booking booking) {
        return build(c, TransactionRooms.of(booking));
    }

    @NonNull
    static LinearLayout build(@NonNull Context c, @NonNull TransactionRooms model) {
        float density = c.getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setId(R.id.yourRoomsSection);

        TextView title = new TextView(c);
        title.setTextAppearance(R.style.TextAppearance_Velocity_Section_Title);
        title.setText(R.string.your_rooms_title);
        title.setPadding(0, Math.round(8 * density), 0, Math.round(4 * density));
        root.addView(title);

        boolean anyAmenities = !model.groups.isEmpty();
        for (TransactionRooms.RoomItem room : model.rooms) {
            LinearLayout body = card(c, root);
            body.addView(header(c, roomHeader(c, room)));
            if (model.linkedToRooms()) {
                TransactionRooms.Group g = model.groupFor(room.number);
                if (g != null) {
                    amenityLines(c, body, g, R.string.room_amenities_subtotal);
                } else {
                    body.addView(secondary(c, c.getString(R.string.no_amenities_added)));
                }
            } else if (!anyAmenities) {
                body.addView(secondary(c, c.getString(R.string.no_amenities_added)));
            }
        }
        TransactionRooms.Group all = model.allRoomsGroup();
        if (all != null) {
            LinearLayout body = card(c, root);
            body.addView(header(c, c.getString(R.string.all_rooms_label)));
            amenityLines(c, body, all, R.string.amenities_subtotal_label);
        }
        if (!model.waiting.isEmpty()) {
            LinearLayout body = card(c, root);
            body.addView(header(c, c.getString(R.string.waiting_for_approval)));
            for (TransactionRooms.AmenityItem item : model.waiting) {
                LinearLayout line = new LinearLayout(c);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(0, Math.round(3 * density), 0, Math.round(3 * density));
                TextView name = text(c, c.getString(R.string.next_stay_detail_item_format, item.name, item.quantity), 13, false);
                line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView chip = new TextView(c);
                chip.setText(R.string.pending_chip_label);
                chip.setTextSize(12);
                chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
                chip.setTextColor(c.getColor(R.color.status_pending_fg));
                chip.setBackgroundResource(R.drawable.shape_intro_pill);
                chip.setBackgroundTintList(c.getColorStateList(R.color.status_pending_bg));
                chip.setPadding(Math.round(8 * density), Math.round(2 * density), Math.round(8 * density), Math.round(2 * density));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cp.setMarginStart(Math.round(8 * density));
                line.addView(chip, cp);
                body.addView(line);
            }
        }
        if (anyAmenities) {
            LinearLayout total = row(c, c.getString(R.string.next_stay_amenities_total), MoneyFormat.format(model.amenitiesTotal), true);
            total.setId(R.id.yourRoomsAmenitiesTotal);
            total.setPadding(0, Math.round(6 * density), 0, 0);
            root.addView(total);
        }
        return root;
    }

    private static String roomHeader(Context c, TransactionRooms.RoomItem room) {
        if (room.number != null) {
            return room.type.isEmpty() ? c.getString(R.string.room_header_number_only_format, room.number)
                    : c.getString(R.string.room_header_format, room.number, room.type);
        }
        return room.type.isEmpty() ? c.getString(R.string.room_header_unassigned_only)
                : c.getString(R.string.room_header_unassigned_format, room.type);
    }

    private static void amenityLines(Context c, LinearLayout body, TransactionRooms.Group g, int subtotalLabel) {
        TextView sub = text(c, c.getString(R.string.next_stay_approved_amenities), 13, true);
        sub.setPadding(0, Math.round(6 * c.getResources().getDisplayMetrics().density), 0, 0);
        body.addView(sub);
        for (TransactionRooms.AmenityItem item : g.items) {
            body.addView(row(c, c.getString(R.string.next_stay_detail_item_format, item.name, item.quantity), MoneyFormat.format(item.subtotal), false));
        }
        body.addView(row(c, c.getString(subtotalLabel), MoneyFormat.format(g.subtotal), true));
    }

    private static LinearLayout card(Context c, LinearLayout root) {
        View card = android.view.LayoutInflater.from(c).inflate(R.layout.item_room_group_card, root, false);
        root.addView(card);
        return card.findViewById(R.id.roomGroupBody);
    }

    private static TextView header(Context c, String text) {
        return text(c, text, 14, true);
    }

    private static TextView secondary(Context c, String text) {
        TextView t = text(c, text, 13, false);
        t.setPadding(0, Math.round(4 * c.getResources().getDisplayMetrics().density), 0, 0);
        return t;
    }

    private static TextView text(Context c, String text, int sp, boolean bold) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(c.getColor(bold ? R.color.velocity_text_primary : R.color.velocity_text_secondary));
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    /** Label left, amount right-aligned. */
    private static LinearLayout row(Context c, String label, String value, boolean strong) {
        float density = c.getResources().getDisplayMetrics().density;
        LinearLayout line = new LinearLayout(c);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(0, Math.round(3 * density), 0, Math.round(3 * density));
        TextView l = text(c, label, 13, strong);
        if (!strong) l.setTextColor(c.getColor(R.color.velocity_text_secondary));
        line.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text(c, value, 13, strong);
        v.setGravity(Gravity.END);
        v.setTextColor(c.getColor(R.color.velocity_text_primary));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.setMarginStart(Math.round(10 * density));
        line.addView(v, vp);
        return line;
    }
}
