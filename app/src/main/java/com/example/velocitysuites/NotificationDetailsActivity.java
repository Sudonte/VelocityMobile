package com.example.velocitysuites;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/**
 * Full-detail screen for a single Notification, replacing
 * NotificationActivity.showNotificationDetails()'s dialog. Adds a real,
 * additive capability the old dialog never had: when the notification is
 * linked to a Booking/Reservation still resident in RoomRepository's
 * already-loaded cache, an inline "Reservation/Booking Information" panel
 * is rendered so the guest doesn't have to navigate away just to see it.
 *
 * Deliberately omits the old dialog's target-audience ("Sent to: ...")
 * pill - this screen is guest-only (NotificationActivity extends
 * BaseNavigationActivity, which has no staff variant), and surfacing
 * internal role/audience routing to a guest leaks admin metadata that has
 * no guest-facing purpose.
 */
public class NotificationDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_NOTIFICATION = "EXTRA_NOTIFICATION";

    private Notification notification;
    /** Resolved once in bindRelatedRecord() and reused by bindPrimaryAction() for the "View Payment Receipt" gate - null when the notification has no linked record still resident in RoomRepository's cache. */
    @Nullable
    private Booking relatedBooking;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_details);

        notification = (Notification) getIntent().getSerializableExtra(EXTRA_NOTIFICATION);
        if (notification == null) {
            finish();
            return;
        }

        ImageButton btnBack = findViewById(R.id.btnNotificationDetailsBack);
        btnBack.setOnClickListener(v -> finish());

        bindHeader();
        bindRelatedRecord();
        bindPrimaryAction();
    }

    private void bindHeader() {
        TextView tvTitle = findViewById(R.id.tvNotifDetailTitle);
        tvTitle.setText(notification.getTitle());

        // Full date/time must always be available here (not just for
        // announcements) - relative time is shown alongside it, not instead
        // of it, matching the "2 minutes ago / Sep 9, 2026 • 2:37 AM" spec.
        TextView tvTime = findViewById(R.id.tvNotifDetailTime);
        String absoluteTime = notification.getPublishedAt();
        String relativeTime = notification.getTimestamp();
        if (absoluteTime != null && !absoluteTime.isEmpty()) {
            tvTime.setText(!TextUtils.isEmpty(relativeTime) ? (relativeTime + "  •  " + absoluteTime) : absoluteTime);
        } else {
            tvTime.setText(relativeTime);
        }

        TextView tvMessage = findViewById(R.id.tvNotifDetailMessage);
        tvMessage.setText(notification.getMessage());

        int iconRes = R.drawable.ic_notifications;
        int bgColor = R.color.velocity_red_soft;
        int iconColor = R.color.velocity_red_primary;
        switch (notification.getType()) {
            case Notification.TYPE_PAYMENT:
                iconRes = R.drawable.ic_check_circle;
                bgColor = R.color.velocity_green_primary;
                iconColor = R.color.white;
                break;
            case Notification.TYPE_BOOKING:
                iconRes = R.drawable.ic_booking;
                bgColor = R.color.velocity_red_bg_start;
                iconColor = R.color.velocity_red_primary;
                break;
            case Notification.TYPE_CHECK_IN:
                iconRes = R.drawable.ic_clock;
                bgColor = R.color.velocity_orange_primary;
                iconColor = R.color.white;
                break;
            case Notification.TYPE_PROMOTION:
                iconRes = R.drawable.ic_star;
                bgColor = R.color.velocity_red_dark;
                iconColor = R.color.white;
                break;
            case Notification.TYPE_ANNOUNCEMENT:
                iconRes = R.drawable.ic_info;
                bgColor = R.color.velocity_blue_soft;
                iconColor = R.color.velocity_blue_primary;
                break;
        }

        MaterialCardView iconContainer = findViewById(R.id.iconContainerNotifDetail);
        ImageView ivIcon = findViewById(R.id.ivNotifDetailIcon);
        iconContainer.setCardBackgroundColor(getColor(bgColor));
        ivIcon.setImageResource(iconRes);
        ivIcon.setColorFilter(getColor(iconColor));
    }

    private void bindRelatedRecord() {
        MaterialCardView cardRelated = findViewById(R.id.cardRelatedRecord);
        String referenceId = notification.getReferenceId();
        if (referenceId == null) {
            cardRelated.setVisibility(View.GONE);
            return;
        }

        Booking booking = null;
        List<Booking> allBookings = RoomRepository.getInstance(this).getBookings();
        if (allBookings != null) {
            for (Booking candidate : allBookings) {
                if (referenceId.equals(candidate.getId())) {
                    booking = candidate;
                    break;
                }
            }
        }

        relatedBooking = booking;
        if (booking == null) {
            cardRelated.setVisibility(View.GONE);
            return;
        }

        cardRelated.setVisibility(View.VISIBLE);
        TextView tvTitle = findViewById(R.id.tvRelatedRecordTitle);
        tvTitle.setText(booking.isHasBooking() ? R.string.booking_information_label : R.string.reservation_information_label);

        LinearLayout container = findViewById(R.id.sectionRelatedRecordInfo);
        container.removeAllViews();

        String ref = booking.isHasBooking()
                ? getString(R.string.direct_booking_ref_format, booking.getId())
                : getString(R.string.reservation_ref_format, booking.getId());
        addInfoRow(container, getString(R.string.details_label_transaction_ref), ref);
        addInfoRow(container, getString(R.string.details_label_room_type),
                android.text.TextUtils.join(", ", booking.getAllRoomTypeNames()));
        addInfoRow(container, getString(R.string.details_label_check_in), booking.getCheckInDate());
        addInfoRow(container, getString(R.string.details_label_check_out), booking.getCheckOutDate());
        addInfoRow(container, getString(R.string.details_label_status), BookingStatusPresenter.computeStatusLabel(this, booking));

        boolean isCancelledOrRejected = "Cancelled".equalsIgnoreCase(booking.getStatus()) || "Rejected".equalsIgnoreCase(booking.getStatus());
        if (isCancelledOrRejected && !TextUtils.isEmpty(booking.getTransactionRejectionReason())) {
            addInfoRow(container, getString(R.string.details_label_rejection_reason), booking.getTransactionRejectionReason());
        }
    }

    private void bindPrimaryAction() {
        String referenceId = notification.getReferenceId();
        String type = notification.getType();
        // Booking/Check-in notifications go straight to the richer Booking/Reservation
        // Details screen when the linked record is still resolvable (relatedBooking, set
        // by bindRelatedRecord() above) - a more direct destination than the Transaction
        // History filter below, which stays the destination for Payment notifications
        // (paired with the separate View Receipt button) and as the fallback for a
        // Booking/Check-in notification whose record has since fallen out of cache.
        boolean canViewBookingDetails = relatedBooking != null
                && (Notification.TYPE_BOOKING.equals(type) || Notification.TYPE_CHECK_IN.equals(type));
        boolean canViewTransaction = referenceId != null
                && (Notification.TYPE_BOOKING.equals(type) || Notification.TYPE_PAYMENT.equals(type) || Notification.TYPE_CHECK_IN.equals(type));

        MaterialButton btnPrimary = findViewById(R.id.btnNotifPrimaryAction);
        if (canViewBookingDetails) {
            btnPrimary.setVisibility(View.VISIBLE);
            btnPrimary.setOnClickListener(v -> startActivity(BookingDetailsActivity.newIntent(this, relatedBooking)));
        } else if (canViewTransaction) {
            btnPrimary.setVisibility(View.VISIBLE);
            btnPrimary.setOnClickListener(v -> {
                Intent intent = new Intent(this, TransactionHistoryActivity.class);
                intent.putExtra(TransactionHistoryActivity.EXTRA_OPEN_FILTER, TransactionHistoryActivity.FILTER_PAYMENTS);
                intent.putExtra(TransactionHistoryActivity.EXTRA_SELECTED_BOOKING_ID, referenceId);
                startActivity(intent);
            });
        } else {
            btnPrimary.setVisibility(View.GONE);
        }

        // Same gate as every other Payment Receipt entry point in the app
        // (BookingDetailsActivity/TransactionDetailsActivity) - only a
        // notification whose related record already has a staff-verified
        // payment gets this button at all, matching "Do not send the
        // official payment receipt link before successful verification".
        MaterialButton btnReceipt = findViewById(R.id.btnNotifViewReceipt);
        boolean canViewReceipt = relatedBooking != null
                && relatedBooking.isStaffVerified() && relatedBooking.getAmountPaid() > 0.009;
        if (canViewReceipt) {
            btnReceipt.setVisibility(View.VISIBLE);
            btnReceipt.setOnClickListener(v -> startActivity(PaymentReceiptActivity.newIntent(this, relatedBooking, false)));
        } else {
            btnReceipt.setVisibility(View.GONE);
        }
    }

    private void addInfoRow(LinearLayout container, String label, @Nullable String value) {
        if (TextUtils.isEmpty(value)) return;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(rowParams);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(getResources().getColor(R.color.velocity_text_secondary));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvLabel.setLayoutParams(labelParams);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(13);
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
        tvValue.setTextColor(getResources().getColor(R.color.velocity_text_primary));
        tvValue.setGravity(Gravity.END);

        row.addView(tvLabel);
        row.addView(tvValue);
        container.addView(row);
    }

    public static Intent newIntent(Context context, Notification notification) {
        Intent intent = new Intent(context, NotificationDetailsActivity.class);
        intent.putExtra(EXTRA_NOTIFICATION, notification);
        return intent;
    }
}
