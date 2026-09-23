package com.example.velocitysuites;

import android.app.Activity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared room-details dialog (dialog_room_details.xml) binder. Two entry
 * points share the same rich layout (hero image/gallery, price chip, bed
 * type/room size strip, description, amenities, hotel policies) but swap the
 * bottom action area for two different purposes:
 *  - show(): read-only room browsing (RoomBrowsingActivity, LandingActivity) -
 *    shows the Book Now/Reserve Now action row (or occupied-range info for an
 *    unavailable room) and navigates away on tap.
 *  - showForStaging(): "tap a room type to view full details and choose a
 *    quantity" from the Available Room List during Step 2 of Booking/
 *    Reservation creation (Step1RoomSelectionFragment, BookingAndReservationActivity) -
 *    shows a quantity stepper + live subtotal instead, and reports every
 *    quantity change back to the caller's staged-selection state immediately
 *    (no separate "confirm" step beyond closing this dialog).
 */
final class RoomDetailsDialog {

    interface ActionListener {
        void onBook(Room room);
        void onReserve(Room room);
    }

    interface QuantityListener {
        void onQuantityChanged(Room room, int newQuantity);
    }

    private RoomDetailsDialog() {}

    static AlertDialog show(Activity activity, Room room, String action,
                             String occupiedRangeText, ActionListener listener) {
        View dialogView = inflateAndBindCommonFields(activity, room);
        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.VelocityDialogTheme)
                .setView(dialogView)
                .create();

        TextView tvStatusBadge = dialogView.findViewById(R.id.detailStatusBadge);
        MaterialButton btnBook = dialogView.findViewById(R.id.btnBookRoom);
        MaterialButton btnReserve = dialogView.findViewById(R.id.btnReserveRoom);
        View layoutActions = dialogView.findViewById(R.id.layoutDetailActions);
        View layoutOccupiedInfo = dialogView.findViewById(R.id.layoutDetailOccupiedInfo);
        TextView tvOccupiedRange = dialogView.findViewById(R.id.tvDetailOccupiedRange);
        View btnClose = dialogView.findViewById(R.id.btnClose);

        if (room.isAvailable()) {
            tvStatusBadge.setText(R.string.available_label);
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_success);
            tvStatusBadge.setTextColor(ContextCompat.getColor(activity, R.color.white));
            if (layoutActions != null) layoutActions.setVisibility(View.VISIBLE);
            if (layoutOccupiedInfo != null) layoutOccupiedInfo.setVisibility(View.GONE);
            configureDetailActionButtons(btnBook, btnReserve, action);
        } else {
            tvStatusBadge.setText(R.string.unavailable_label);
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral);
            tvStatusBadge.setTextColor(activity.getResources().getColor(R.color.velocity_inactive_gray, activity.getTheme()));
            // Unavailable rooms may still be viewed in full, but must not be
            // bookable/reservable here, and only the occupancy window is shown -
            // no guest/booking identity.
            if (layoutActions != null) layoutActions.setVisibility(View.GONE);
            if (layoutOccupiedInfo != null && tvOccupiedRange != null) {
                layoutOccupiedInfo.setVisibility(View.VISIBLE);
                tvOccupiedRange.setText(occupiedRangeText != null
                        ? occupiedRangeText : activity.getString(R.string.unavailable_label));
            }
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnBook.setOnClickListener(v -> {
            dialog.dismiss();
            listener.onBook(room);
        });
        if (btnReserve != null) {
            btnReserve.setOnClickListener(v -> {
                dialog.dismiss();
                listener.onReserve(room);
            });
        }

        return showDialog(dialog);
    }

    /**
     * @param nights          BookingWizardState#nights() / the equivalent
     *                        selectedNights() - needed to show the same
     *                        price x quantity x nights subtotal formula used
     *                        everywhere else in the wizard.
     * @param availableCount  remaining units of this room type the guest can
     *                        still add (already excludes whatever's already
     *                        committed to the transaction elsewhere - same
     *                        "remainingBase" the caller's own card badge
     *                        shows), i.e. the quantity stepper's ceiling.
     * @param initialQuantity the quantity already staged for this room type
     *                        in the caller's in-progress selection, or 0 -
     *                        the stepper starts here, never at 1, so
     *                        reopening this dialog never silently re-adds a
     *                        room the guest hadn't actually chosen yet.
     * @param onQuantityChanged fired on every +/- tap, immediately (not just
     *                        on Done/close) - the caller updates its staged
     *                        selection map and refreshes its own footer/card
     *                        state right away.
     * @param onDismissed     fired once, when the dialog closes by any means
     *                        (Done, X, back press, outside tap) - lets the
     *                        caller do a final refresh of the room-type card
     *                        that opened this dialog.
     */
    static AlertDialog showForStaging(Activity activity, Room room, long nights, int availableCount,
                                       int initialQuantity, QuantityListener onQuantityChanged,
                                       @Nullable Runnable onDismissed) {
        View dialogView = inflateAndBindCommonFields(activity, room);
        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.VelocityDialogTheme)
                .setView(dialogView)
                .create();

        TextView tvStatusBadge = dialogView.findViewById(R.id.detailStatusBadge);
        View layoutActions = dialogView.findViewById(R.id.layoutDetailActions);
        View layoutOccupiedInfo = dialogView.findViewById(R.id.layoutDetailOccupiedInfo);
        View layoutQuantitySection = dialogView.findViewById(R.id.layoutDetailQuantitySection);
        TextView tvQty = dialogView.findViewById(R.id.tvDetailQty);
        TextView tvSelectedIndicator = dialogView.findViewById(R.id.tvDetailSelectedIndicator);
        View layoutSubtotalRow = dialogView.findViewById(R.id.layoutDetailSubtotalRow);
        TextView tvSubtotalAmount = dialogView.findViewById(R.id.tvDetailSubtotalAmount);
        MaterialButton btnQtyMinus = dialogView.findViewById(R.id.btnDetailQtyMinus);
        MaterialButton btnQtyPlus = dialogView.findViewById(R.id.btnDetailQtyPlus);
        MaterialButton btnDone = dialogView.findViewById(R.id.btnDetailDone);
        View btnClose = dialogView.findViewById(R.id.btnClose);

        tvStatusBadge.setText(activity.getString(R.string.available_qty_format, availableCount));
        tvStatusBadge.setBackgroundResource(availableCount > 0 ? R.drawable.bg_badge_success : R.drawable.bg_badge_neutral);
        tvStatusBadge.setTextColor(availableCount > 0
                ? ContextCompat.getColor(activity, R.color.velocity_green_primary)
                : activity.getResources().getColor(R.color.velocity_inactive_gray, activity.getTheme()));

        if (layoutActions != null) layoutActions.setVisibility(View.GONE);
        if (layoutOccupiedInfo != null) layoutOccupiedInfo.setVisibility(View.GONE);
        layoutQuantitySection.setVisibility(View.VISIBLE);

        int maxQty = Math.max(1, availableCount);
        int[] qty = {Math.max(0, Math.min(initialQuantity, maxQty))};

        Runnable updateQtyUi = () -> {
            tvQty.setText(String.valueOf(qty[0]));
            btnQtyMinus.setEnabled(qty[0] > 0);
            btnQtyPlus.setEnabled(qty[0] < maxQty);
            if (qty[0] <= 0) {
                tvSelectedIndicator.setText(R.string.not_selected_yet_label);
                layoutSubtotalRow.setVisibility(View.GONE);
            } else {
                tvSelectedIndicator.setText(activity.getResources().getQuantityString(
                        R.plurals.rooms_staged_total_count, qty[0], qty[0]));
                layoutSubtotalRow.setVisibility(View.VISIBLE);
                tvSubtotalAmount.setText(String.format(Locale.US, activity.getString(R.string.price_format),
                        room.getPricePerNight() * nights * qty[0]));
            }
        };
        updateQtyUi.run();

        btnQtyMinus.setOnClickListener(v -> {
            if (qty[0] <= 0) return;
            qty[0]--;
            updateQtyUi.run();
            onQuantityChanged.onQuantityChanged(room, qty[0]);
        });
        btnQtyPlus.setOnClickListener(v -> {
            if (qty[0] >= maxQty) {
                Toast.makeText(activity, activity.getString(R.string.no_more_rooms_available_toast, room.getName()), Toast.LENGTH_SHORT).show();
                return;
            }
            qty[0]++;
            updateQtyUi.run();
            onQuantityChanged.onQuantityChanged(room, qty[0]);
        });
        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnDone.setOnClickListener(v -> dialog.dismiss());
        if (onDismissed != null) {
            dialog.setOnDismissListener(d -> onDismissed.run());
        }

        return showDialog(dialog);
    }

    /** Binds every field the two entry points above share (image/gallery, name, type, price, capacity, bed type, room size, description, amenities) - only the bottom action area differs between them. */
    private static View inflateAndBindCommonFields(Activity activity, Room room) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_room_details, null);

        TextView tvName = dialogView.findViewById(R.id.detailRoomName);
        TextView tvType = dialogView.findViewById(R.id.detailRoomType);
        TextView tvPrice = dialogView.findViewById(R.id.detailRoomPrice);
        TextView tvDesc = dialogView.findViewById(R.id.detailRoomDescription);
        TextView tvCapacity = dialogView.findViewById(R.id.detailRoomCapacity);
        TextView tvBedType = dialogView.findViewById(R.id.detailRoomBedType);
        TextView tvRoomSize = dialogView.findViewById(R.id.detailRoomSize);
        TextView tvAmenities = dialogView.findViewById(R.id.detailRoomAmenities);
        ImageView ivRoomImage = dialogView.findViewById(R.id.detailRoomImage);
        ViewPager2 pagerRoomImages = dialogView.findViewById(R.id.detailRoomImagePager);
        View galleryCountCard = dialogView.findViewById(R.id.detailGalleryCountCard);
        TextView tvGalleryCount = dialogView.findViewById(R.id.detailGalleryCount);
        ImageView ivTypeIcon = dialogView.findViewById(R.id.detailRoomTypeIcon);

        tvName.setText(room.getName());
        tvType.setText(room.getType());
        tvPrice.setText(activity.getString(R.string.price_format_night, room.getPricePerNight()));
        tvDesc.setText(room.getDescription());
        tvCapacity.setText(activity.getString(R.string.capacity_persons_format, room.getCapacity()));
        tvBedType.setText(room.getBedType() == null || room.getBedType().isEmpty()
                ? RoomVisuals.getBedType(activity, room.getType(), room.getCapacity())
                : room.getBedType());
        tvRoomSize.setText(room.getRoomSize() == null || room.getRoomSize().isEmpty()
                ? activity.getString(R.string.not_specified)
                : room.getRoomSize());

        int fallbackImage = RoomVisuals.getRoomImage(room.getType());
        List<String> galleryUrls = room.getImageUrls();
        List<String> galleryLabels = room.getImageLabels();
        if (galleryUrls != null && !galleryUrls.isEmpty()) {
            // Real gallery data available - show the swipeable pager instead of the
            // static hero image, and let a tap open the full-screen, autoplaying
            // RoomGalleryActivity (same behavior as the web room-gallery component).
            ivRoomImage.setVisibility(View.GONE);
            pagerRoomImages.setVisibility(View.VISIBLE);

            int galleryCount = galleryUrls.size();
            galleryCountCard.setVisibility(View.VISIBLE);
            tvGalleryCount.setText(activity.getString(R.string.gallery_count_tap_format, 1, galleryCount));
            pagerRoomImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    tvGalleryCount.setText(activity.getString(
                            R.string.gallery_count_tap_format, position + 1, galleryCount));
                }
            });

            pagerRoomImages.setAdapter(new RoomGalleryPagerAdapter(galleryUrls, galleryLabels, fallbackImage,
                    position -> activity.startActivity(RoomGalleryActivity.createIntent(
                            activity, new ArrayList<>(galleryUrls), new ArrayList<>(galleryLabels), position, room.getName()))));
            galleryCountCard.setOnClickListener(v -> activity.startActivity(RoomGalleryActivity.createIntent(
                    activity, new ArrayList<>(galleryUrls), new ArrayList<>(galleryLabels), pagerRoomImages.getCurrentItem(), room.getName())));
        } else if (room.getImageUrl() != null && !room.getImageUrl().isEmpty()) {
            Glide.with(activity)
                    .load(room.getImageUrl())
                    .placeholder(fallbackImage)
                    .error(fallbackImage)
                    .into(ivRoomImage);
        } else if (room.getImageResId() != 0) {
            ivRoomImage.setImageResource(room.getImageResId());
        } else {
            ivRoomImage.setImageResource(fallbackImage);
        }
        ivTypeIcon.setImageResource(RoomVisuals.getTypeIcon(room.getType()));

        if (room.getAmenities().isEmpty()) {
            tvAmenities.setText(R.string.no_amenities_available);
        } else {
            // Name only - no description, price, category, or status here;
            // shared by both landing.xml and roombrowsing.xml since both
            // open this same dialog.
            StringBuilder amenities = new StringBuilder();
            for (RoomAmenity amenity : room.getAmenities()) {
                amenities.append("• ").append(amenity.getName()).append("\n");
            }
            tvAmenities.setText(amenities.toString().trim());
        }

        // Cap the scrollable content area so a long description/amenities/policies
        // stack can never push the quantity selector or action row below the
        // dialog's visible bounds on a small screen - see
        // MaxHeightNestedScrollView's doc comment for why a plain android:maxHeight
        // attribute (the original approach here) never actually worked.
        MaxHeightNestedScrollView scroll = dialogView.findViewById(R.id.scrollRoomDetails);
        scroll.setMaxHeightPx((int) (activity.getResources().getDisplayMetrics().heightPixels * 0.6f));

        return dialogView;
    }

    private static AlertDialog showDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
        return dialog;
    }

    /**
     * Shows only the button matching the action the guest originally chose
     * (Book Now or Reserve Now), aligned to the bottom-right corner instead of
     * stretching full width. When the guest arrived via View Details (or a
     * plain room-card tap), both buttons stay visible, evenly split, as before.
     */
    private static void configureDetailActionButtons(MaterialButton btnBook, MaterialButton btnReserve, String action) {
        if (btnBook == null || btnReserve == null) return;

        boolean showBook = !PendingRoomSelection.ACTION_RESERVE.equals(action);
        boolean showReserve = !PendingRoomSelection.ACTION_BOOK.equals(action);
        boolean singleButton = showBook != showReserve;

        btnBook.setVisibility(showBook ? View.VISIBLE : View.GONE);
        btnReserve.setVisibility(showReserve ? View.VISIBLE : View.GONE);

        LinearLayout.LayoutParams bookParams = (LinearLayout.LayoutParams) btnBook.getLayoutParams();
        LinearLayout.LayoutParams reserveParams = (LinearLayout.LayoutParams) btnReserve.getLayoutParams();

        if (singleButton) {
            LinearLayout.LayoutParams visibleParams = showBook ? bookParams : reserveParams;
            visibleParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            visibleParams.weight = 0f;
            visibleParams.leftMargin = 0;
            visibleParams.rightMargin = 0;
        } else {
            bookParams.width = 0;
            bookParams.weight = 1f;
            reserveParams.width = 0;
            reserveParams.weight = 1f;
        }
        btnBook.setLayoutParams(bookParams);
        btnReserve.setLayoutParams(reserveParams);

        Object parent = btnBook.getParent();
        if (parent instanceof LinearLayout) {
            ((LinearLayout) parent).setGravity(singleButton ? Gravity.END : Gravity.START);
        }
    }
}
