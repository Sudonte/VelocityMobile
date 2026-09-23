package com.example.velocitysuites;

import android.app.Activity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared room-details dialog (dialog_room_details.xml) binder, used by both
 * RoomBrowsingActivity (with search dates, so occupied rooms show the searched
 * date range) and LandingActivity (no search dates, so an unavailable room
 * just shows the generic "Unavailable" label instead of a date range).
 */
final class RoomDetailsDialog {

    interface ActionListener {
        void onBook(Room room);
        void onReserve(Room room);
    }

    private RoomDetailsDialog() {}

    static AlertDialog show(Activity activity, Room room, String action,
                             String occupiedRangeText, ActionListener listener) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_room_details, null);
        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.VelocityDialogTheme)
                .setView(dialogView)
                .create();

        TextView tvName = dialogView.findViewById(R.id.detailRoomName);
        TextView tvType = dialogView.findViewById(R.id.detailRoomType);
        TextView tvPrice = dialogView.findViewById(R.id.detailRoomPrice);
        TextView tvDesc = dialogView.findViewById(R.id.detailRoomDescription);
        TextView tvCapacity = dialogView.findViewById(R.id.detailRoomCapacity);
        TextView tvBedType = dialogView.findViewById(R.id.detailRoomBedType);
        TextView tvRoomSize = dialogView.findViewById(R.id.detailRoomSize);
        TextView tvAmenities = dialogView.findViewById(R.id.detailRoomAmenities);
        TextView tvStatusBadge = dialogView.findViewById(R.id.detailStatusBadge);
        ImageView ivRoomImage = dialogView.findViewById(R.id.detailRoomImage);
        ViewPager2 pagerRoomImages = dialogView.findViewById(R.id.detailRoomImagePager);
        View galleryCountCard = dialogView.findViewById(R.id.detailGalleryCountCard);
        TextView tvGalleryCount = dialogView.findViewById(R.id.detailGalleryCount);
        ImageView ivTypeIcon = dialogView.findViewById(R.id.detailRoomTypeIcon);
        MaterialButton btnBook = dialogView.findViewById(R.id.btnBookRoom);
        MaterialButton btnReserve = dialogView.findViewById(R.id.btnReserveRoom);
        View layoutActions = dialogView.findViewById(R.id.layoutDetailActions);
        View layoutOccupiedInfo = dialogView.findViewById(R.id.layoutDetailOccupiedInfo);
        TextView tvOccupiedRange = dialogView.findViewById(R.id.tvDetailOccupiedRange);
        View btnClose = dialogView.findViewById(R.id.btnClose);

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
