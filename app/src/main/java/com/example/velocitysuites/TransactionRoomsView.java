package com.example.velocitysuites;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * The ONE view of "Your rooms" (see {@link TransactionRooms}): a card per room - "Room 1-001" with its type chip - listing the
 * approved amenities requested for that room with a "Room amenities" footer, then what is waiting for approval (amber chip) and what
 * was not approved (red chip), neither ever counted. Lines no source could place in a room go under "Other amenities". Below the
 * cards, the "Amenities total". Label left, amount right, the same spacing on every screen that shows it (Your Next Stay full
 * details, Booking Details, Transaction History, the Upcoming details sheet).
 */
final class TransactionRoomsView {

    private TransactionRoomsView() {
    }

    /** The view from what is known right now (cached request lists included); it does not touch the network. */
    @NonNull
    static LinearLayout build(@NonNull Context c, @NonNull Booking booking) {
        AmenityRoomSources.attach(c);
        return build(c, TransactionRooms.of(booking));
    }

    /**
     * Adds the live view to {@code parent}: built now from what is known, then rebuilt in place once the server's amenity requests
     * (the source that names each room) arrive.
     */
    static void addLive(@NonNull Context c, @NonNull ViewGroup parent, @NonNull Booking booking) {
        addLive(c, parent, booking, true);
    }

    /** {@code fetch} false when the screen already asks for the same list itself (Booking Details' button check) and publishes it. */
    static void addLive(@NonNull Context c, @NonNull ViewGroup parent, @NonNull Booking booking, boolean fetch) {
        LinearLayout view = build(c, booking);
        parent.addView(view);
        Runnable[] self = new Runnable[1];
        self[0] = () -> {
            if (view.getParent() == null) {
                AmenityRoomSources.unlisten(booking, self[0]);
                return;
            }
            fill(c, view, TransactionRooms.of(booking));
        };
        AmenityRoomSources.listen(booking, self[0]);
        if (fetch) AmenityRoomSources.fetch(c, booking);
    }

    @NonNull
    static LinearLayout build(@NonNull Context c, @NonNull TransactionRooms model) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setId(R.id.yourRoomsSection);
        fill(c, root, model);
        return root;
    }

    private static void fill(Context c, LinearLayout root, TransactionRooms model) {
        root.removeAllViews();
        float density = c.getResources().getDisplayMetrics().density;

        TextView title = new TextView(c);
        title.setTextAppearance(R.style.TextAppearance_Velocity_Section_Title);
        title.setText(R.string.your_rooms_title);
        title.setPadding(0, Math.round(8 * density), 0, Math.round(4 * density));
        root.addView(title);

        for (TransactionRooms.Card card : model.cards) {
            addCard(c, root, card, roomHeader(c, card.room), roomChip(card.room), R.string.room_amenities_footer, true);
        }
        if (model.other != null) {
            addCard(c, root, model.other, c.getString(R.string.other_amenities_label), null, R.string.other_amenities_footer, false);
        }

        if (model.hasAnyApproved()) {
            LinearLayout total = row(c, c.getString(R.string.next_stay_amenities_total), MoneyFormat.format(model.amenitiesTotal), true);
            total.setId(R.id.yourRoomsAmenitiesTotal);
            total.setPadding(0, Math.round(6 * density), 0, 0);
            root.addView(total);
        }
    }

    private static void addCard(Context c, LinearLayout root, TransactionRooms.Card card, String header, @Nullable String chip,
                                int footerLabel, boolean saysWhenEmpty) {
        float density = c.getResources().getDisplayMetrics().density;
        LinearLayout body = card(c, root);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(c, header, 14, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (chip != null && !chip.isEmpty()) {
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMarginStart(Math.round(8 * density));
            head.addView(chip(c, chip, R.color.status_cancelled_bg, R.color.status_cancelled_fg), cp);
        }
        body.addView(head);

        if (card.isEmpty() && saysWhenEmpty) {
            body.addView(secondary(c, c.getString(R.string.no_amenities_for_room)));
            return;
        }
        if (!card.approved.isEmpty()) {
            body.addView(subheader(c, R.string.next_stay_approved_amenities));
            for (TransactionRooms.AmenityItem item : card.approved) {
                body.addView(row(c, c.getString(R.string.next_stay_detail_item_format, item.name, item.quantity), MoneyFormat.format(item.subtotal), false));
            }
        }
        addState(c, body, card.waiting, R.string.waiting_for_approval, R.string.pending_chip_label, R.color.status_pending_bg, R.color.status_pending_fg);
        addState(c, body, card.rejected, R.string.not_approved_label, R.string.rejected_chip_label, R.color.velocity_red_status_soft, R.color.velocity_red_status_dark);
        if (!card.approved.isEmpty()) body.addView(row(c, c.getString(footerLabel), MoneyFormat.format(card.subtotal), true));
    }

    /** "Waiting for approval" / "Not approved": the item, its amount in secondary colour and a status chip - never counted. */
    private static void addState(Context c, LinearLayout body, List<TransactionRooms.AmenityItem> items, int titleRes, int chipRes, int bg, int fg) {
        if (items.isEmpty()) return;
        float density = c.getResources().getDisplayMetrics().density;
        body.addView(subheader(c, titleRes));
        for (TransactionRooms.AmenityItem item : items) {
            LinearLayout line = new LinearLayout(c);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(0, Math.round(3 * density), 0, Math.round(3 * density));
            line.addView(text(c, c.getString(R.string.next_stay_detail_item_format, item.name, item.quantity), 13, false),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (item.subtotal > 0) {
                TextView amount = text(c, MoneyFormat.format(item.subtotal), 13, false);
                amount.setGravity(Gravity.END);
                LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ap.setMarginStart(Math.round(8 * density));
                line.addView(amount, ap);
            }
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMarginStart(Math.round(8 * density));
            line.addView(chip(c, c.getString(chipRes), bg, fg), cp);
            body.addView(line);
        }
    }

    private static TextView chip(Context c, String label, int bg, int fg) {
        float density = c.getResources().getDisplayMetrics().density;
        TextView chip = new TextView(c);
        chip.setText(label);
        chip.setTextSize(12);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        chip.setTextColor(c.getColor(fg));
        chip.setBackgroundResource(R.drawable.shape_intro_pill);
        chip.setBackgroundTintList(c.getColorStateList(bg));
        chip.setPadding(Math.round(8 * density), Math.round(2 * density), Math.round(8 * density), Math.round(2 * density));
        return chip;
    }

    private static TextView subheader(Context c, int res) {
        TextView sub = text(c, c.getString(res), 13, true);
        sub.setPadding(0, Math.round(6 * c.getResources().getDisplayMetrics().density), 0, 0);
        return sub;
    }

    /** "Room 1-001", or "Room (Deluxe)" while no number is assigned yet. */
    private static String roomHeader(Context c, TransactionRooms.RoomItem room) {
        if (room.number != null) return c.getString(R.string.room_card_title_format, room.number);
        return room.type.isEmpty() ? c.getString(R.string.room_card_title_unassigned) : c.getString(R.string.room_card_title_type_format, room.type);
    }

    /** The room type chip; none while the header already names the type. */
    @Nullable
    private static String roomChip(TransactionRooms.RoomItem room) {
        return room.number != null && !room.type.isEmpty() ? room.type : null;
    }

    private static LinearLayout card(Context c, LinearLayout root) {
        View card = android.view.LayoutInflater.from(c).inflate(R.layout.item_room_group_card, root, false);
        root.addView(card);
        return card.findViewById(R.id.roomGroupBody);
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
