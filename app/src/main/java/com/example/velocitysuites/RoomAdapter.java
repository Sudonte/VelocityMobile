package com.example.velocitysuites;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RoomAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROOM = 1;
    private static final int TYPE_MESSAGE = 2;

    /**
     * A row in the results list: either a section header ("Available Rooms (n)")
     * or a room. Kept internal to the adapter so callers can keep passing plain
     * Room lists (legacy, e.g. LandingActivity) or a sectioned Available/
     * Unavailable breakdown (RoomBrowsingActivity search results).
     */
    private static final class RoomListItem {
        final int viewType;
        final Room room;
        final String headerText;
        final String occupiedRangeText;

        private RoomListItem(int viewType, Room room, String headerText, String occupiedRangeText) {
            this.viewType = viewType;
            this.room = room;
            this.headerText = headerText;
            this.occupiedRangeText = occupiedRangeText;
        }

        static RoomListItem header(String text) {
            return new RoomListItem(TYPE_HEADER, null, text, null);
        }

        static RoomListItem forRoom(Room room) {
            return new RoomListItem(TYPE_ROOM, room, null, null);
        }

        static RoomListItem forRoom(Room room, String occupiedRangeText) {
            return new RoomListItem(TYPE_ROOM, room, null, occupiedRangeText);
        }

        static RoomListItem message(String text) {
            return new RoomListItem(TYPE_MESSAGE, null, text, null);
        }
    }

    private List<RoomListItem> items;
    private final OnRoomClickListener listener;
    private final boolean detailsOnlyMode;
    private int lastAnimatedPosition = -1;

    /**
     * Multi-room cart selection: owned by the caller (LandingActivity/
     * RoomBrowsingActivity), mutated in place here on stepper +/- so both
     * sides always see the same map with no separate sync step. Room id ->
     * selected quantity (0 = not in the cart; entries are never left at 0,
     * they're removed instead - see bindQuantityStepper()). Null disables
     * the quantity stepper entirely (e.g. any future details-only usage).
     */
    private final Map<String, Integer> selectedQuantities;

    /**
     * True only when populated via {@link #updateSectionedResults}: enables the
     * privacy-safe Occupied From/Until row (replacing the action buttons) for
     * rooms that are unavailable for the searched date range.
     */
    private boolean sectionedResultsMode = false;

    /** Debounce window for the action buttons - blocks a second tap that lands
     *  before navigation/the previous request has had a chance to take effect,
     *  which otherwise risks a duplicate reservation/booking submission. */
    private static final long CLICK_DEBOUNCE_MS = 800L;
    private long lastActionClickAtMs = 0L;

    private boolean isDebouncedClick() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastActionClickAtMs < CLICK_DEBOUNCE_MS) {
            return true;
        }
        lastActionClickAtMs = now;
        return false;
    }

    public interface OnRoomClickListener {
        void onRoomClick(Room room);
        void onViewDetailsClick(Room room);
        void onBookNowClick(Room room);

        /** Default no-op so existing implementers (e.g. LandingActivity) don't need changes. */
        default void onReserveNowClick(Room room) {
        }

        /** Fired after the shared selectedRoomIds set changes, so the caller can refresh its cart summary bar. Default no-op for callers that don't pass a selection set. */
        default void onRoomSelectionChanged() {
        }
    }

    public RoomAdapter(List<Room> rooms, OnRoomClickListener listener) {
        this(rooms, listener, false, null);
    }

    public RoomAdapter(List<Room> rooms, OnRoomClickListener listener, boolean detailsOnlyMode) {
        this(rooms, listener, detailsOnlyMode, null);
    }

    /** @param selectedQuantities shared, mutable id-to-quantity cart map - pass null to hide the quantity stepper entirely. */
    public RoomAdapter(List<Room> rooms, OnRoomClickListener listener, boolean detailsOnlyMode, Map<String, Integer> selectedQuantities) {
        this.items = toRoomItems(rooms);
        this.listener = listener;
        this.detailsOnlyMode = detailsOnlyMode;
        this.selectedQuantities = selectedQuantities;
    }

    private static List<RoomListItem> toRoomItems(List<Room> rooms) {
        List<RoomListItem> result = new ArrayList<>();
        if (rooms != null) {
            for (Room r : rooms) {
                result.add(RoomListItem.forRoom(r));
            }
        }
        return result;
    }

    /** Legacy path: a flat room list with no section headers, Reserve Now, or occupancy info. */
    public void updateList(List<Room> newList) {
        sectionedResultsMode = false;
        replaceItems(toRoomItems(newList));
    }

    /**
     * Date-search results path: renders Available rooms (with Book Now + Reserve
     * Now) under one header and Unavailable rooms (Occupied From/Until only, no
     * guest info, no booking actions) under another. Either group may be empty,
     * in which case its header is omitted.
     */
    public void updateSectionedResults(List<Room> availableRooms, List<Room> unavailableRooms,
                                        String availableHeaderText, String unavailableHeaderText,
                                        String occupiedRangeText, String noAvailableRoomsMessage) {
        sectionedResultsMode = true;

        List<RoomListItem> newItems = new ArrayList<>();
        if (noAvailableRoomsMessage != null && !noAvailableRoomsMessage.isEmpty()) {
            newItems.add(RoomListItem.message(noAvailableRoomsMessage));
        }
        if (availableRooms != null && !availableRooms.isEmpty()) {
            newItems.add(RoomListItem.header(availableHeaderText));
            for (Room r : availableRooms) newItems.add(RoomListItem.forRoom(r));
        }
        if (unavailableRooms != null && !unavailableRooms.isEmpty()) {
            newItems.add(RoomListItem.header(unavailableHeaderText));
            for (Room r : unavailableRooms) newItems.add(RoomListItem.forRoom(r, occupiedRangeText));
        }
        replaceItems(newItems);
    }

    private void replaceItems(List<RoomListItem> newItems) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new RoomDiffCallback(this.items, newItems));
        this.items = newItems;
        lastAnimatedPosition = -1;
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).viewType;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room_section_header, parent, false);
            return new HeaderViewHolder(view);
        }
        if (viewType == TYPE_MESSAGE) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room_message_banner, parent, false);
            return new MessageViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room_card, parent, false);
        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        RoomListItem item = items.get(position);
        if (item.viewType == TYPE_HEADER) {
            ((HeaderViewHolder) viewHolder).tvSectionHeader.setText(item.headerText);
            return;
        }
        if (item.viewType == TYPE_MESSAGE) {
            ((MessageViewHolder) viewHolder).tvMessageBanner.setText(item.headerText);
            return;
        }
        bindRoom((RoomViewHolder) viewHolder, item.room, item.occupiedRangeText, position);
    }

    private void bindRoom(RoomViewHolder holder, Room room, String occupiedRangeText, int position) {
        Context context = holder.itemView.getContext();

        holder.roomName.setText(room.getName());
        holder.roomGuestsText.setText(context.getResources().getQuantityString(
                R.plurals.room_card_guests_format, room.getCapacity(), room.getCapacity()));
        holder.roomSizeText.setText(room.getRoomSize() == null || room.getRoomSize().isEmpty()
                ? context.getString(R.string.not_specified)
                : room.getRoomSize());
        holder.roomPrice.setText(context.getString(R.string.price_format_per_night, room.getPricePerNight()));
        holder.roomBedType.setText(room.getBedType() == null || room.getBedType().isEmpty()
                ? RoomVisuals.getBedType(context, room.getType(), room.getCapacity())
                : room.getBedType());

        int fallbackImage = RoomVisuals.getRoomImage(room.getType());
        if (room.getImageUrl() != null && !room.getImageUrl().isEmpty()) {
            Glide.with(context)
                    .load(room.getImageUrl())
                    .placeholder(fallbackImage)
                    .error(fallbackImage)
                    .into(holder.roomImage);
        } else if (room.getImageResId() != 0) {
            holder.roomImage.setImageResource(room.getImageResId());
        } else {
            holder.roomImage.setImageResource(fallbackImage);
        }

        boolean available = room.isAvailable();
        RoomAvailabilityStatus status = RoomAvailabilityStatus.of(room);
        switch (status) {
            case LIMITED:
                holder.roomStatusBadge.setText(R.string.limited_label);
                holder.roomStatusBadge.setBackgroundResource(R.drawable.bg_badge_warning);
                holder.roomStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.velocity_orange_primary));
                break;
            case FULLY_BOOKED:
                holder.roomStatusBadge.setText(R.string.unavailable_label);
                holder.roomStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral);
                holder.roomStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.velocity_inactive_gray));
                break;
            case AVAILABLE:
            default:
                holder.roomStatusBadge.setText(R.string.available_label);
                holder.roomStatusBadge.setBackgroundResource(R.drawable.bg_badge_success);
                holder.roomStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.white));
                break;
        }

        if (holder.tvRoomsLeftHint != null) {
            if (status == RoomAvailabilityStatus.LIMITED) {
                holder.tvRoomsLeftHint.setVisibility(View.VISIBLE);
                holder.tvRoomsLeftHint.setText(context.getString(R.string.rooms_left_format, room.getAvailableCount()));
            } else {
                holder.tvRoomsLeftHint.setVisibility(View.GONE);
            }
        }

        if (detailsOnlyMode) {
            holder.layoutRoomActions.setVisibility(View.GONE);
            holder.layoutOccupiedInfo.setVisibility(View.GONE);
        } else {
            // Every room card (landing.xml and roombrowsing.xml alike) always
            // offers all three actions: View Details, Book Now, Reserve Now -
            // a fully-booked room keeps them visible but disabled (subtle
            // disabled state) rather than hiding them outright. The Occupied
            // From/Until row (date-search results only, privacy-safe - no
            // guest info) is now purely additive alongside the buttons, not
            // a replacement for them.
            holder.layoutRoomActions.setVisibility(View.VISIBLE);
            holder.btnReserveNow.setVisibility(View.VISIBLE);
            holder.btnQuickBook.setVisibility(View.VISIBLE);
            holder.btnQuickBook.setEnabled(available);
            holder.btnQuickBook.setAlpha(available ? 1.0f : 0.5f);
            holder.btnReserveNow.setEnabled(available);
            holder.btnReserveNow.setAlpha(available ? 1.0f : 0.5f);

            boolean showOccupiedInfo = sectionedResultsMode && !available;
            holder.layoutOccupiedInfo.setVisibility(showOccupiedInfo ? View.VISIBLE : View.GONE);
            if (showOccupiedInfo) {
                holder.tvOccupiedRange.setText(occupiedRangeText != null ? occupiedRangeText : context.getString(R.string.unavailable_label));
            }
        }

        bindAmenities(holder, room, context);
        bindQuantityStepper(holder, room, available, context);
        bindFavorite(holder, room, context);

        holder.btnViewDetails.setOnClickListener(v -> listener.onViewDetailsClick(room));
        holder.btnQuickBook.setOnClickListener(v -> {
            if (isDebouncedClick()) return;
            listener.onBookNowClick(room);
        });
        holder.btnReserveNow.setOnClickListener(v -> {
            if (isDebouncedClick()) return;
            listener.onReserveNowClick(room);
        });
        holder.itemView.setOnClickListener(v -> listener.onRoomClick(room));

        holder.roomImage.setContentDescription(room.getName());
        holder.btnViewDetails.setContentDescription(context.getString(R.string.cd_view_details_format, room.getName()));
        holder.btnReserveNow.setContentDescription(context.getString(R.string.cd_reserve_now_format, room.getName()));
        holder.btnQuickBook.setContentDescription(context.getString(R.string.cd_book_now_format, room.getName()));

        expandActionButtonTouchTargets(holder);

        animateItemEntrance(holder.itemView, position);
    }

    private static final int TOUCH_TARGET_EXPANSION_DP = 6;

    /**
     * Grows the effective tap area of the three 40dp-tall action buttons
     * toward Android's ~48dp minimum touch target guidance, without growing
     * the buttons themselves - reclaims the dead space already present
     * around them (the divider gap above the row, the card's own padding
     * below it, and the small gaps between buttons) via a composite
     * TouchDelegate on the card, instead of enlarging the visible card.
     */
    private void expandActionButtonTouchTargets(RoomViewHolder holder) {
        View card = holder.itemView;
        View[] buttons = {holder.btnViewDetails, holder.btnReserveNow, holder.btnQuickBook};
        card.post(() -> {
            if (card.getWidth() == 0 || card.getHeight() == 0) return;
            int extra = dpToPx(card.getContext(), TOUCH_TARGET_EXPANSION_DP);
            List<View> targets = new ArrayList<>();
            List<Rect> rects = new ArrayList<>();
            for (View button : buttons) {
                if (button == null || button.getWidth() == 0 || button.getHeight() == 0) continue;
                Rect rect = new Rect();
                offsetRectToAncestor(button, card, rect);
                rect.inset(-extra, -extra);
                targets.add(button);
                rects.add(rect);
            }
            if (targets.isEmpty()) return;
            Rect cardBounds = new Rect(0, 0, card.getWidth(), card.getHeight());
            card.setTouchDelegate(new CompositeTouchDelegate(cardBounds, rects, targets));
        });
    }

    private static void offsetRectToAncestor(View view, View ancestor, Rect outRect) {
        outRect.set(0, 0, view.getWidth(), view.getHeight());
        View current = view;
        while (current != ancestor && current.getParent() instanceof View) {
            outRect.offset(current.getLeft(), current.getTop());
            current = (View) current.getParent();
        }
    }

    /**
     * A TouchDelegate that can forward to one of several candidate views
     * depending on where ACTION_DOWN lands, unlike the platform TouchDelegate
     * which only ever supports a single delegate target. Mirrors the
     * platform implementation's own trick of relocating each forwarded event
     * to the delegate's center rather than translating exact coordinates,
     * since only click/ripple firing correctly matters here.
     */
    private static final class CompositeTouchDelegate extends TouchDelegate {
        private final List<Rect> rects;
        private final List<View> targets;
        private View activeTarget;

        CompositeTouchDelegate(Rect bounds, List<Rect> rects, List<View> targets) {
            super(bounds, targets.get(0));
            this.rects = rects;
            this.targets = targets;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                activeTarget = null;
                for (int i = 0; i < rects.size(); i++) {
                    if (rects.get(i).contains(x, y)) {
                        activeTarget = targets.get(i);
                        break;
                    }
                }
            }
            if (activeTarget == null) {
                return false;
            }
            event.setLocation(activeTarget.getWidth() / 2f, activeTarget.getHeight() / 2f);
            boolean handled = activeTarget.dispatchTouchEvent(event);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                activeTarget = null;
            }
            return handled;
        }
    }

    /**
     * On-device-only favorite toggle - see RoomFavoritesStore. Purely a
     * local UI preference (no backend concept of favorites exists), but a
     * real, working, persisted one rather than decorative - the icon swaps
     * between outline/filled and survives app restarts.
     */
    private void bindFavorite(RoomViewHolder holder, Room room, Context context) {
        if (holder.btnFavorite == null) return;
        applyFavoriteIcon(holder.btnFavorite, context, RoomFavoritesStore.isFavorite(context, room.getId()), room.getName());
        holder.btnFavorite.setOnClickListener(v -> {
            boolean nowFavorite = RoomFavoritesStore.toggleFavorite(context, room.getId());
            applyFavoriteIcon(holder.btnFavorite, context, nowFavorite, room.getName());
        });
    }

    private void applyFavoriteIcon(ImageView button, Context context, boolean favorite, String roomName) {
        button.setImageResource(favorite ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_border);
        button.setContentDescription(context.getString(
                favorite ? R.string.cd_remove_from_favorites_format : R.string.cd_add_to_favorites_format, roomName));
    }

    /**
     * Multi-room cart quantity stepper: hidden entirely when no selection map
     * was given to this adapter instance, when the room is unavailable
     * (can't be carted), or in details-only mode. Reads/writes directly
     * through the caller-owned map (keyed by room id) so a recycled
     * ViewHolder never needs its own quantity state - see the class docblock
     * on selectedQuantities.
     */
    private void bindQuantityStepper(RoomViewHolder holder, Room room, boolean available, Context context) {
        if (holder.cardRoomQtyStepper == null || holder.tvRoomQty == null) return;
        if (selectedQuantities == null || detailsOnlyMode || !available) {
            holder.cardRoomQtyStepper.setVisibility(View.GONE);
            return;
        }
        holder.cardRoomQtyStepper.setVisibility(View.VISIBLE);

        int maxQty = Math.max(0, room.getAvailableCount());
        int qty = selectedQuantities.getOrDefault(room.getId(), 0);
        holder.tvRoomQty.setText(String.valueOf(qty));
        holder.btnRoomQtyMinus.setEnabled(qty > 0);
        holder.btnRoomQtyPlus.setEnabled(qty < maxQty);

        holder.btnRoomQtyMinus.setOnClickListener(v -> {
            int current = selectedQuantities.getOrDefault(room.getId(), 0);
            if (current <= 0) return;
            if (current == 1) {
                selectedQuantities.remove(room.getId());
            } else {
                selectedQuantities.put(room.getId(), current - 1);
            }
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) notifyItemChanged(position);
            listener.onRoomSelectionChanged();
        });
        holder.btnRoomQtyPlus.setOnClickListener(v -> {
            int current = selectedQuantities.getOrDefault(room.getId(), 0);
            if (current >= maxQty) {
                Toast.makeText(context, context.getString(R.string.no_more_rooms_available_toast, room.getName()), Toast.LENGTH_SHORT).show();
                return;
            }
            selectedQuantities.put(room.getId(), current + 1);
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) notifyItemChanged(position);
            listener.onRoomSelectionChanged();
        });
    }

    /**
     * Renders every amenity assigned to this room type as a small read-only
     * chip in the full-width section below the image/info column - none are
     * hidden behind a "+X more" cap and none are truncated. The ChipGroup
     * wraps naturally (no singleLine, no HorizontalScrollView - see
     * item_room_card.xml) so once a row fills, the remaining amenities flow
     * onto the next row instead of requiring a scroll or being hidden.
     */
    private void bindAmenities(RoomViewHolder holder, Room room, Context context) {
        holder.amenitiesChipGroup.removeAllViews();
        List<RoomAmenity> amenities = room.getAmenities();

        if (amenities == null || amenities.isEmpty()) {
            holder.amenitiesChipGroup.addView(newAmenityChip(context, context.getString(R.string.no_amenities_available), 0));
            return;
        }

        for (RoomAmenity amenity : amenities) {
            holder.amenitiesChipGroup.addView(
                    newAmenityChip(context, amenity.getName(), RoomVisuals.getAmenityIcon(amenity.getName())));
        }
    }

    private com.google.android.material.chip.Chip newAmenityChip(Context context, String text, int iconRes) {
        com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(context);
        chip.setText(text);
        chip.setTextSize(8f);
        chip.setTextColor(ContextCompat.getColor(context, R.color.velocity_text_secondary));
        chip.setChipBackgroundColorResource(R.color.velocity_surface_elevated);
        chip.setChipStrokeColorResource(R.color.velocity_red_subtle);
        chip.setChipStrokeWidth(dpToPx(context, 1));
        chip.setChipCornerRadius(dpToPx(context, 10));
        chip.setChipMinHeight(dpToPx(context, 24));
        chip.setChipStartPadding(dpToPx(context, 4));
        chip.setChipEndPadding(dpToPx(context, 4));
        chip.setTextStartPadding(dpToPx(context, 2));
        chip.setTextEndPadding(dpToPx(context, 2));
        chip.setEnsureMinTouchTargetSize(false);
        chip.setClickable(false);
        chip.setCheckable(false);
        chip.setFocusable(false);
        if (iconRes != 0) {
            chip.setChipIconResource(iconRes);
            chip.setChipIconTint(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.velocity_red_primary)));
            chip.setChipIconVisible(true);
            chip.setChipIconSize(dpToPx(context, 9));
            chip.setIconStartPadding(dpToPx(context, 2));
            chip.setIconEndPadding(dpToPx(context, 2));
        } else {
            chip.setChipIconVisible(false);
        }
        return chip;
    }

    private void animateItemEntrance(View itemView, int position) {
        if (position > lastAnimatedPosition) {
            itemView.setAlpha(0f);
            itemView.setTranslationY(40f);
            itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(Math.min(position, 6) * 40L)
                    .setDuration(260)
                    .setInterpolator(AnimationUtils.loadInterpolator(itemView.getContext(), android.R.interpolator.decelerate_cubic))
                    .start();
            lastAnimatedPosition = position;
        } else {
            itemView.animate().cancel();
            itemView.setAlpha(1f);
            itemView.setTranslationY(0f);
        }
    }

    private static int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class RoomViewHolder extends RecyclerView.ViewHolder {
        ImageView roomImage;
        ImageButton btnFavorite;
        TextView roomStatusBadge, roomPrice, roomName, roomGuestsText, roomSizeText, roomBedType, tvRoomsLeftHint;
        com.google.android.material.chip.ChipGroup amenitiesChipGroup;
        MaterialButton btnViewDetails, btnQuickBook, btnReserveNow;
        View layoutRoomActions, layoutOccupiedInfo;
        TextView tvOccupiedRange;
        View cardRoomQtyStepper;
        TextView tvRoomQty;
        MaterialButton btnRoomQtyMinus, btnRoomQtyPlus;

        RoomViewHolder(@NonNull View itemView) {
            super(itemView);
            btnFavorite = itemView.findViewById(R.id.btnFavorite);
            cardRoomQtyStepper = itemView.findViewById(R.id.cardRoomQtyStepper);
            tvRoomQty = itemView.findViewById(R.id.tvRoomQty);
            btnRoomQtyMinus = itemView.findViewById(R.id.btnRoomQtyMinus);
            btnRoomQtyPlus = itemView.findViewById(R.id.btnRoomQtyPlus);
            roomImage = itemView.findViewById(R.id.roomImage);
            roomStatusBadge = itemView.findViewById(R.id.roomStatusBadge);
            roomPrice = itemView.findViewById(R.id.roomPrice);
            roomName = itemView.findViewById(R.id.roomName);
            roomGuestsText = itemView.findViewById(R.id.roomGuestsText);
            roomSizeText = itemView.findViewById(R.id.roomSizeText);
            roomBedType = itemView.findViewById(R.id.roomBedType);
            tvRoomsLeftHint = itemView.findViewById(R.id.tvRoomsLeftHint);
            amenitiesChipGroup = itemView.findViewById(R.id.amenitiesChipGroup);
            btnViewDetails = itemView.findViewById(R.id.btnViewDetails);
            btnQuickBook = itemView.findViewById(R.id.btnQuickBook);
            btnReserveNow = itemView.findViewById(R.id.btnReserveNow);
            layoutRoomActions = itemView.findViewById(R.id.layoutRoomActions);
            layoutOccupiedInfo = itemView.findViewById(R.id.layoutOccupiedInfo);
            tvOccupiedRange = itemView.findViewById(R.id.tvOccupiedRange);
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvSectionHeader;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSectionHeader = itemView.findViewById(R.id.tvSectionHeader);
        }
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessageBanner;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessageBanner = itemView.findViewById(R.id.tvMessageBanner);
        }
    }

    private static class RoomDiffCallback extends DiffUtil.Callback {
        private final List<RoomListItem> oldList;
        private final List<RoomListItem> newList;

        RoomDiffCallback(List<RoomListItem> oldList, List<RoomListItem> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            RoomListItem o = oldList.get(oldItemPosition);
            RoomListItem n = newList.get(newItemPosition);
            if (o.viewType != n.viewType) return false;
            if (o.viewType == TYPE_HEADER || o.viewType == TYPE_MESSAGE) return Objects.equals(o.headerText, n.headerText);
            return o.room.getId().equals(n.room.getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            RoomListItem o = oldList.get(oldItemPosition);
            RoomListItem n = newList.get(newItemPosition);
            if (o.viewType == TYPE_HEADER || o.viewType == TYPE_MESSAGE) return Objects.equals(o.headerText, n.headerText);
            return o.room.isAvailable() == n.room.isAvailable()
                    && o.room.getPricePerNight() == n.room.getPricePerNight()
                    && Objects.equals(o.occupiedRangeText, n.occupiedRangeText);
        }
    }
}
