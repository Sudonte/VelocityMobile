package com.example.velocitysuites;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * What every notification row says and how it looks, in one place, so the Dashboard section, the bell popup and the
 * Notifications screen can never drift apart: the round category icon, the title (bold while unread, regular once read), the
 * message, the short time and the unread dot. The views are found by the shared ids of item_notification_row.xml and
 * item_notification.xml.
 */
final class NotificationRowBinder {

    private NotificationRowBinder() {
    }

    /** The unread count as shown on a badge: the number, or "99+" above 99. */
    @NonNull
    static String badgeText(@NonNull Context context, int count) {
        return count > 99 ? context.getString(R.string.unread_badge_overflow) : String.valueOf(count);
    }

    /**
     * Fills the icon circle, title, message, time and unread dot of {@code root}. {@code isRead} is passed in (a snapshot
     * taken when the row was built) rather than read from the shared, mutable Notification.
     */
    static void bind(@NonNull View root, @NonNull Notification n, boolean isRead, long nowMillis) {
        Context context = root.getContext();
        NotificationCategoryPresenter.Result category = NotificationCategoryPresenter.resolve(n);

        View circle = root.findViewById(R.id.frameNotifIcon);
        circle.setBackgroundTintList(ContextCompat.getColorStateList(context, category.bgColorRes));
        ImageView icon = root.findViewById(R.id.ivNotifIcon);
        icon.setImageResource(category.iconRes);
        icon.setColorFilter(ContextCompat.getColor(context, category.fgColorRes));

        TextView title = root.findViewById(R.id.tvNotifTitle);
        title.setText(n.getTitle());
        // Bold while unread, regular once read: a text-level cue that does not rely on colour, beside the dot.
        title.setTypeface(null, isRead ? Typeface.NORMAL : Typeface.BOLD);
        ((TextView) root.findViewById(R.id.tvNotifMessage)).setText(n.getMessage());
        ((TextView) root.findViewById(R.id.tvNotifTime)).setText(timeOf(context, n, nowMillis));

        View dot = root.findViewById(R.id.viewNotifUnreadDot);
        dot.setVisibility(isRead ? View.GONE : View.VISIBLE);
        dot.setContentDescription(isRead ? null : context.getString(R.string.notif_unread_indicator_desc));
    }

    /** The short time, falling back to the stored relative / absolute text for a row with no instant. */
    @NonNull
    static String timeOf(@NonNull Context context, @NonNull Notification n, long nowMillis) {
        String relative = n.getTimestamp();
        String absolute = n.getPublishedAt();
        String fallback = relative != null && !relative.isEmpty() ? relative : absolute;
        return NotificationTimeText.of(context, n.getCreatedAtMillis(), nowMillis, fallback);
    }

    /**
     * "Booking #567" / "Reservation #12" for a notification linked to a transaction, or null when it links to none. The
     * transaction's own kind wins when it is loaded (a payment or check-in notification says which it is about); otherwise
     * the notification's category decides.
     */
    @Nullable
    static String linkedLabel(@NonNull Context context, @NonNull Notification n) {
        String ref = n.getReferenceId();
        if (!NotificationPrimaryActionResolver.canViewTransaction(n.getType(), ref)) return null;
        Booking linked = TransactionNavigator.pick(RoomRepository.getInstance(context).getAllBookings(), ref, null, n.getType(), n.getReceiptNumber());
        if (linked != null) return TransactionText.reference(context, linked.isHasBooking(), linked.getDisplayId());
        return TransactionText.reference(context, !Notification.TYPE_RESERVATION.equals(n.getType()), ref.trim());
    }
}
