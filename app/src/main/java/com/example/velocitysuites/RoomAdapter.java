package com.example.velocitysuites;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
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
            ImageFreshness.apply(Glide.with(context)
                    .load(room.getImageUrl())
                    .placeholder(fallbackImage)
                    .error(fallbackImage))
                    .into(holder.roomImage);
        } else if (room.getImageResId() != 0) {
            holder.roomImage.setImageResource(room.getImageResId());
        } else {
            holder.roomImage.setImageResource(fallbackImage);
        }

        boolean available = room.isAvailable();
        RoomAvailabilityStatus status = RoomAvailabilityStatus.of(room);
        switch (status) {
            // The badge fills are fixed pale colours (they do not change in dark mode), so the text colours must be
            // fixed dark ones too: the "available" badge used to be white text on a pale pink fill (about 1.1:1),
            // and the other two were under 4.5:1.
            case LIMITED:
                holder.roomStatusBadge.setText(R.string.limited_label);
                holder.roomStatusBadge.setBackgroundResource(R.drawable.bg_badge_warning);
                holder.roomStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.velocity_green_primary));
                break;
            case FULLY_BOOKED:
                holder.roomStatusBadge.setText(R.string.unavailable_label);
                holder.roomStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral);
                holder.roomStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.velocity_red_dark));
                break;
            case AVAILABLE:
            default:
                holder.roomStatusBadge.setText(R.string.available_label);
                holder.roomStatusBadge.setBackgroundResource(R.drawable.bg_badge_success);
                holder.roomStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.velocity_green_primary));
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

        // The three action buttons are 48dp tall in item_room_card.xml, so they are full-size touch targets on
        // their own (an earlier version widened their tap areas with a TouchDelegate because they were 40dp).

        animateItemEntrance(holder.itemView, position);
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
        // 12sp (it was 8sp) and square like every other control in the app (it was a 10dp rounded corner).
        chip.setTextSize(12f);
        chip.setTextColor(ContextCompat.getColor(context, R.color.velocity_text_secondary));
        chip.setChipBackgroundColorResource(R.color.velocity_surface_elevated);
        chip.setChipStrokeColorResource(R.color.velocity_red_subtle);
        chip.setChipStrokeWidth(dpToPx(context, 1));
        chip.setChipCornerRadius(0f);
        chip.setChipMinHeight(dpToPx(context, 28));
        chip.setChipStartPadding(dpToPx(context, 6));
        chip.setChipEndPadding(dpToPx(context, 6));
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
            chip.setChipIconSize(dpToPx(context, 14));
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

    /** The system font scale from which the card is restacked (see {@link #needsRoomyLayout}). */
    static final float ROOMY_FONT_SCALE = 1.3f;
    /** Below this screen width (dp) the card is restacked, whatever the font size. */
    static final int ROOMY_BELOW_WIDTH_DP = 340;

    /**
     * Whether this phone is too narrow, or its font too large, for the compact side-by-side card.
     * The card puts a 140dp photo next to the room's details; on a 320dp screen, or with a large font, that
     * leaves the details a column so thin that words break in the middle ("Exec/utive", "Quee/n") and the
     * half-width Reserve / Book buttons cut their labels off. Those phones get the roomy arrangement instead:
     * photo across the top, details below it, every action full width.
     */
    public static boolean needsRoomyLayout(android.content.res.Configuration configuration) {
        return configuration.fontScale >= ROOMY_FONT_SCALE
                || (configuration.screenWidthDp > 0 && configuration.screenWidthDp < ROOMY_BELOW_WIDTH_DP);
    }

    /**
     * Restacks a freshly inflated item_room_card for {@link #needsRoomyLayout}: the photo goes across the top, the
     * details sit below it at full width, and Reserve Now / Book Now / View Details are three full-width rows.
     * Only layout parameters change - every view, id and click behaviour is the same as in the compact card.
     * A copy of the layout that lacks any of these parts is left exactly as it is.
     */
    static void applyRoomyLayout(View card) {
        View topRowView = card.findViewById(R.id.roomCardTopRow);
        View imageCard = card.findViewById(R.id.roomImageCard);
        View infoColumn = card.findViewById(R.id.roomInfoColumn);
        View actionsRowView = card.findViewById(R.id.layoutRoomActionsPrimary);
        View reserve = card.findViewById(R.id.btnReserveNow);
        View book = card.findViewById(R.id.btnQuickBook);
        if (!(topRowView instanceof LinearLayout) || !(actionsRowView instanceof LinearLayout)
                || imageCard == null || infoColumn == null || reserve == null || book == null) {
            return;
        }
        int gap = dpToPx(card.getContext(), 8);

        LinearLayout topRow = (LinearLayout) topRowView;
        topRow.setOrientation(LinearLayout.VERTICAL);
        topRow.setGravity(android.view.Gravity.START);
        fullWidthRow(imageCard, 0, 0);
        fullWidthRow(infoColumn, gap, 0);

        LinearLayout actionsRow = (LinearLayout) actionsRowView;
        actionsRow.setOrientation(LinearLayout.VERTICAL);
        fullWidthRow(reserve, 0, gap);
        fullWidthRow(book, 0, 0);
    }

    /** Makes a view of a horizontal row fill the width of a vertical one: no weight, no side margins. */
    private static void fullWidthRow(View view, int topMargin, int bottomMargin) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.weight = 0f;
        params.setMarginStart(0);
        params.setMarginEnd(0);
        params.topMargin = topMargin;
        params.bottomMargin = bottomMargin;
        view.setLayoutParams(params);
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
            if (needsRoomyLayout(itemView.getResources().getConfiguration())) {
                applyRoomyLayout(itemView);
            }
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
            return sameRoom(o.room, n.room) && Objects.equals(o.occupiedRangeText, n.occupiedRangeText);
        }
    }

    /**
     * True when the two copies of a room are the same in everything the card - or what its buttons open - uses.
     * This used to compare only availability and price, so after a refresh a visible card kept showing the old
     * name, capacity, bed type, picture, "rooms left" count or amenities (RecyclerView only re-draws rows the
     * diff calls changed) and its buttons kept opening the old copy's description and gallery. Every field of
     * the model is compared; a refresh that changed nothing still re-draws nothing.
     */
    static boolean sameRoom(Room a, Room b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.isAvailable() == b.isAvailable()
                && a.getAvailableCount() == b.getAvailableCount()
                && a.getCapacity() == b.getCapacity()
                && a.getImageResId() == b.getImageResId()
                && a.getRoomTypeId() == b.getRoomTypeId()
                && Double.compare(a.getPricePerNight(), b.getPricePerNight()) == 0
                && Objects.equals(a.getId(), b.getId())
                && Objects.equals(a.getName(), b.getName())
                && Objects.equals(a.getType(), b.getType())
                && Objects.equals(a.getDescription(), b.getDescription())
                && Objects.equals(a.getBedType(), b.getBedType())
                && Objects.equals(a.getRoomSize(), b.getRoomSize())
                && Objects.equals(a.getPolicies(), b.getPolicies())
                && Objects.equals(a.getImageUrl(), b.getImageUrl())
                && Objects.equals(a.getImageUrls(), b.getImageUrls())
                && Objects.equals(a.getImageLabels(), b.getImageLabels())
                && sameAmenities(a.getAmenities(), b.getAmenities());
    }

    private static boolean sameAmenities(List<RoomAmenity> a, List<RoomAmenity> b) {
        int sizeA = a == null ? 0 : a.size();
        int sizeB = b == null ? 0 : b.size();
        if (sizeA != sizeB) return false;
        for (int i = 0; i < sizeA; i++) {
            RoomAmenity x = a.get(i);
            RoomAmenity y = b.get(i);
            if (!Objects.equals(x.getName(), y.getName())
                    || !Objects.equals(x.getCategory(), y.getCategory())
                    || !Objects.equals(x.getDescription(), y.getDescription())
                    || !Objects.equals(x.getPricingType(), y.getPricingType())
                    || !Objects.equals(x.getFee(), y.getFee())) {
                return false;
            }
        }
        return true;
    }
}
