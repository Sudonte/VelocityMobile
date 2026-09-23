package com.example.velocitysuites;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;

/**
 * "View Details" popup for the multi-room selection cart on landing.xml and
 * roombrowsing.xml - lists every currently selected room TYPE (name,
 * quantity, capacity, price, subtotal, image) with a per-type remove action,
 * shared by both screens' cart bars rather than duplicating the list-building
 * logic in each Activity. The incoming list carries one Room entry per
 * selected unit (the app's "duplicate entries ARE the quantity" convention -
 * see BookingWizardState); Room itself has no quantity field, so this dialog
 * derives each group's count by counting occurrences, purely for display.
 */
final class SelectedRoomsDialog {

    interface RemoveListener {
        /** Called when the guest removes a room type from the cart via this dialog (all units of it); the caller owns updating its own selection map/adapter/summary bar. */
        void onRemove(Room room);
    }

    private SelectedRoomsDialog() {}

    static void show(Activity activity, List<Room> rooms, RemoveListener listener) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_selected_rooms, null);
        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.VelocityDialogTheme)
                .setView(dialogView)
                .create();

        LinearLayout list = dialogView.findViewById(R.id.layoutSelectedRoomsList);
        TextView tvCount = dialogView.findViewById(R.id.tvSelectedRoomsDialogCount);
        TextView tvTotal = dialogView.findViewById(R.id.tvSelectedRoomsDialogTotal);
        renderRows(activity, dialog, list, tvCount, tvTotal, new ArrayList<>(rooms), listener);

        dialogView.findViewById(R.id.btnCloseSelectedRoomsDialog).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnDoneSelectedRooms).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /** Room id -> unit count, and room id -> a representative Room instance - both in first-seen order. */
    private static Map<String, Room> representativesById(List<Room> rooms) {
        Map<String, Room> byId = new LinkedHashMap<>();
        for (Room room : rooms) {
            byId.putIfAbsent(room.getId(), room);
        }
        return byId;
    }

    private static int countOf(List<Room> rooms, String id) {
        int count = 0;
        for (Room room : rooms) {
            if (room.getId().equals(id)) count++;
        }
        return count;
    }

    private static void renderRows(Activity activity, AlertDialog dialog, LinearLayout list,
                                    TextView tvCount, TextView tvTotal,
                                    List<Room> rooms, RemoveListener listener) {
        list.removeAllViews();
        if (rooms.isEmpty()) {
            dialog.dismiss();
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(activity);
        Map<String, Room> representatives = representativesById(rooms);
        double combinedTotal = 0;
        int totalUnits = 0;
        for (Room room : representatives.values()) {
            int qty = countOf(rooms, room.getId());
            double subtotal = room.getPricePerNight() * qty;
            combinedTotal += subtotal;
            totalUnits += qty;

            View row = inflater.inflate(R.layout.item_cart_room_row, list, false);
            ImageView ivImage = row.findViewById(R.id.ivCartRoomImage);
            TextView tvName = row.findViewById(R.id.tvCartRoomName);
            TextView tvType = row.findViewById(R.id.tvCartRoomType);
            TextView tvDesc = row.findViewById(R.id.tvCartRoomDesc);
            TextView tvMeta = row.findViewById(R.id.tvCartRoomMeta);

            tvName.setText(room.getName());
            String type = room.getType();
            if (type != null && !type.trim().isEmpty()) {
                tvType.setText(type);
                tvType.setVisibility(View.VISIBLE);
            } else {
                tvType.setVisibility(View.GONE);
            }
            String description = room.getDescription();
            if (description != null && !description.trim().isEmpty()) {
                tvDesc.setText(description);
                tvDesc.setVisibility(View.VISIBLE);
            } else {
                tvDesc.setVisibility(View.GONE);
            }
            // Quantity is always shown (not just when >1) - a hidden "1" reads
            // as if the room type has no explicit quantity at all, which is
            // exactly the ambiguity this dialog exists to remove.
            String qtyLabel = activity.getResources().getQuantityString(R.plurals.room_qty_format, qty, qty);
            tvMeta.setText(qty > 1
                    ? String.format(Locale.US, "Up to %d guests · ₱%,.2f / night · %s · Subtotal: ₱%,.2f",
                            room.getCapacity(), room.getPricePerNight(), qtyLabel, subtotal)
                    : String.format(Locale.US, "Up to %d guests · ₱%,.2f / night · %s",
                            room.getCapacity(), room.getPricePerNight(), qtyLabel));
            String imageUrl = room.getImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(activity).load(imageUrl).placeholder(R.drawable.ic_bed).into(ivImage);
            } else {
                ivImage.setImageResource(R.drawable.ic_bed);
            }

            row.findViewById(R.id.btnRemoveCartRoom).setOnClickListener(v -> {
                listener.onRemove(room);
                List<Room> remaining = new ArrayList<>();
                for (Room r : rooms) {
                    if (!r.getId().equals(room.getId())) remaining.add(r);
                }
                renderRows(activity, dialog, list, tvCount, tvTotal, remaining, listener);
            });
            list.addView(row);
        }

        if (tvCount != null) {
            tvCount.setText(activity.getString(R.string.selected_rooms_dialog_count_format, totalUnits));
        }
        if (tvTotal != null) {
            tvTotal.setText(String.format(Locale.US, "₱%,.2f", combinedTotal));
        }
    }
}
