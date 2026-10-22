package com.example.velocitysuites;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
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
public final class RoomDetailsDialog {

    public interface ActionListener {
        void onBook(Room room);
        void onReserve(Room room);
    }

    interface QuantityListener {
        void onQuantityChanged(Room room, int newQuantity);
    }

    private RoomDetailsDialog() {}

    public static AlertDialog show(Activity activity, Room room, String action,
                             String occupiedRangeText, ActionListener listener) {
        return show(activity, room, action, occupiedRangeText, null, listener);
    }

    /** As above; {@code datesSummary} ("Oct 21 → Oct 22 · 1 night") is shown in the sticky bar beside the price when the guest searched for dates. */
    public static AlertDialog show(Activity activity, Room room, String action,
                             String occupiedRangeText, @Nullable String datesSummary, ActionListener listener) {
        View dialogView = inflateAndBindCommonFields(activity, room);
        TextView barDates = dialogView.findViewById(R.id.detailBarDates);
        if (barDates != null && datesSummary != null && !datesSummary.trim().isEmpty()) {
            barDates.setText(datesSummary.trim());
            barDates.setVisibility(View.VISIBLE);
        }
        AlertDialog dialog = new SafeDialogs.Plain(activity, R.style.VelocityDialogTheme)
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
            // dark text on the pale success fill (white on it was about 1.1:1 - unreadable)
            tvStatusBadge.setTextColor(ContextCompat.getColor(activity, R.color.velocity_green_primary));
            if (layoutActions != null) layoutActions.setVisibility(View.VISIBLE);
            if (layoutOccupiedInfo != null) layoutOccupiedInfo.setVisibility(View.GONE);
            configureDetailActionButtons(btnBook, btnReserve, action);
        } else {
            tvStatusBadge.setText(R.string.unavailable_label);
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral);
            tvStatusBadge.setTextColor(ContextCompat.getColor(activity, R.color.velocity_red_dark));
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
        AlertDialog dialog = new SafeDialogs.Plain(activity, R.style.VelocityDialogTheme)
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
                : ContextCompat.getColor(activity, R.color.velocity_red_dark));

        if (layoutActions != null) layoutActions.setVisibility(View.GONE);
        if (layoutOccupiedInfo != null) layoutOccupiedInfo.setVisibility(View.GONE);
        layoutQuantitySection.setVisibility(View.VISIBLE);
        dialogView.findViewById(R.id.layoutDetailBarSummary).setVisibility(View.GONE);

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
                String selectedText = activity.getResources().getQuantityString(R.plurals.rooms_staged_total_count, qty[0], qty[0]);
                // at the limit the message is right under the quantity, not in a toast
                tvSelectedIndicator.setText(qty[0] >= maxQty ? selectedText + "
" + activity.getString(R.string.rooms_limit_message, room.getName(), maxQty) : selectedText);
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
                updateQtyUi.run(); // the limit message is already shown under the quantity
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

    /** Binds every field the two entry points above share (gallery, name, price, type, refund, overview, facts, amenities, policies) - only the sticky bar's actions differ between them. */
    @SuppressLint("InflateParams") // a dialog's content view has no parent to inflate into
    private static View inflateAndBindCommonFields(Activity activity, Room room) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_room_details, null);

        TextView tvName = dialogView.findViewById(R.id.detailRoomName);
        TextView tvType = dialogView.findViewById(R.id.detailRoomType);
        TextView tvPrice = dialogView.findViewById(R.id.detailRoomPrice);
        TextView tvDesc = dialogView.findViewById(R.id.detailRoomDescription);
        ImageView ivRoomImage = dialogView.findViewById(R.id.detailRoomImage);
        ViewPager2 pagerRoomImages = dialogView.findViewById(R.id.detailRoomImagePager);
        View galleryCountCard = dialogView.findViewById(R.id.detailGalleryCountCard);
        TextView tvGalleryCount = dialogView.findViewById(R.id.detailGalleryCount);

        // The name once; the type is a small chip only when it says something the name does not.
        String name = room.getName() == null ? "" : room.getName().trim();
        String type = room.getType() == null ? "" : room.getType().trim();
        tvName.setText(name.isEmpty() ? type : name);
        tvType.setText(type);
        tvType.setVisibility(!type.isEmpty() && !name.isEmpty() && !type.equalsIgnoreCase(name) ? View.VISIBLE : View.GONE);
        String price = activity.getString(R.string.price_format_per_night, room.getPricePerNight());
        tvPrice.setText(price);
        ((TextView) dialogView.findViewById(R.id.detailBarPrice)).setText(price);

        // Overview: the full description (the section is left out when there is none).
        String description = room.getDescription() == null ? "" : room.getDescription().trim();
        tvDesc.setText(description);
        int descVisibility = description.isEmpty() ? View.GONE : View.VISIBLE;
        tvDesc.setVisibility(descVisibility);
        dialogView.findViewById(R.id.headOverview).setVisibility(descVisibility);

        // Room facts: label left, value right.
        LinearLayout facts = dialogView.findViewById(R.id.layoutDetailFacts);
        int capacity = Math.max(1, room.getCapacity());
        addLabelValue(facts, activity.getString(R.string.room_fact_guests),
                activity.getResources().getQuantityString(R.plurals.guests_count_lower, capacity, capacity));
        addLabelValue(facts, activity.getString(R.string.room_fact_beds), room.getBedType() == null || room.getBedType().isEmpty()
                ? RoomVisuals.getBedType(activity, room.getType(), room.getCapacity()) : room.getBedType());
        addLabelValue(facts, activity.getString(R.string.room_fact_size), room.getRoomSize() == null || room.getRoomSize().isEmpty()
                ? activity.getString(R.string.not_specified) : room.getRoomSize());

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

        bindAmenityGrid(activity, dialogView.findViewById(R.id.detailRoomAmenities), room);

        // Policies: the hotel-wide refund policy first (the posted times and refund policy come from HotelPolicy), then the full hotel policies (folded) with the
        // admin's per-room-type notes (if any) under them.
        LinearLayout policies = dialogView.findViewById(R.id.layoutDetailPolicies);
        addLabelValue(policies, activity.getString(R.string.room_policy_refund_label), HotelPolicy.refundValue(activity));
        addLabelValue(policies, activity.getString(R.string.room_policy_checkin_label), HotelPolicy.checkInFrom(activity));
        addLabelValue(policies, activity.getString(R.string.room_policy_checkout_label), HotelPolicy.checkOutUntil(activity));
        TextView policiesBody = dialogView.findViewById(R.id.detailPoliciesBody);
        policiesBody.setText(room.getPolicies() != null && !room.getPolicies().trim().isEmpty()
                ? HotelPolicy.policiesText(activity) + "\n\n" + room.getPolicies().trim() : HotelPolicy.policiesText(activity));
        dialogView.findViewById(R.id.detailRefundChip).setVisibility(HotelPolicy.NON_REFUNDABLE ? View.VISIBLE : View.GONE);
        ((TextView) dialogView.findViewById(R.id.detailRefundChip)).setText(HotelPolicy.refundChip(activity));
        makeCollapsible(dialogView.findViewById(R.id.headMorePolicies), policiesBody, false);

        // Cap the scrollable sections so the sticky bar below them always stays on screen, even on a small phone - see
        // MaxHeightNestedScrollView's doc comment for why a plain android:maxHeight attribute never worked.
        MaxHeightNestedScrollView scroll = dialogView.findViewById(R.id.scrollRoomDetails);
        scroll.setMaxHeightPx((int) (activity.getResources().getDisplayMetrics().heightPixels * 0.42f));

        return dialogView;
    }

    /** One row: the label on the left (secondary), the value on the right (bold). */
    private static void addLabelValue(LinearLayout container, String label, String value) {
        Context context = container.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Math.round(36 * density));
        TextView l = new TextView(context);
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(context.getColor(R.color.velocity_text_secondary));
        row.addView(l, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(context);
        v.setText(value);
        v.setTextSize(13);
        v.setGravity(Gravity.END);
        v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        v.setTextColor(context.getColor(R.color.velocity_text_primary));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f);
        vp.setMarginStart(Math.round(12 * density));
        row.addView(v, vp);
        container.addView(row);
    }

    /** The amenities as a two-column list, each with its icon (or one line saying there are none). */
    private static void bindAmenityGrid(Activity activity, GridLayout grid, Room room) {
        grid.removeAllViews();
        List<RoomAmenity> amenities = room.getAmenities();
        float density = activity.getResources().getDisplayMetrics().density;
        if (amenities == null || amenities.isEmpty()) {
            grid.setColumnCount(1);
            TextView none = new TextView(activity);
            none.setText(R.string.no_amenities_available);
            none.setTextSize(13);
            none.setTextColor(activity.getColor(R.color.velocity_text_secondary));
            grid.addView(none);
            return;
        }
        grid.setColumnCount(2);
        for (RoomAmenity amenity : amenities) {
            TextView item = new TextView(activity);
            item.setText(amenity.getName());
            item.setTextSize(13);
            item.setTextColor(activity.getColor(R.color.velocity_text_primary));
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setMinHeight(Math.round(36 * density));
            item.setMaxLines(2);
            item.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.setCompoundDrawablePadding(Math.round(8 * density));
            int icon = RoomVisuals.getAmenityIcon(amenity.getName());
            if (icon != 0) {
                android.graphics.drawable.Drawable d = ContextCompat.getDrawable(activity, icon);
                if (d != null) {
                    d = d.mutate();
                    int size = Math.round(18 * density);
                    d.setBounds(0, 0, size, size);
                    d.setTint(activity.getColor(R.color.velocity_red_primary));
                    item.setCompoundDrawablesRelative(d, null, null, null);
                }
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            lp.setMarginEnd(Math.round(8 * density));
            grid.addView(item, lp);
        }
    }

    /**
     * Tap a section heading to fold/unfold its body; a chevron on the heading shows the current state.
     * Description and Amenities start open, the long Policies list starts folded.
     */
    private static void makeCollapsible(View heading, View body, boolean startOpen) {
        TextView label = heading instanceof TextView ? (TextView) heading
                : (TextView) ((android.view.ViewGroup) heading).getChildAt(0);
        boolean[] open = {startOpen};
        Runnable apply = () -> {
            body.setVisibility(open[0] ? View.VISIBLE : View.GONE);
            label.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,
                    open[0] ? R.drawable.ic_expand_less : R.drawable.ic_expand_more, 0);
            label.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(
                    label.getContext().getColor(R.color.velocity_text_secondary)));
            heading.setContentDescription(label.getText() + ", " + (open[0] ? "expanded" : "collapsed"));
        };
        apply.run();
        heading.setOnClickListener(v -> { open[0] = !open[0]; apply.run(); });
    }

    private static AlertDialog showDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        SafeDialogs.show(dialog);
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
