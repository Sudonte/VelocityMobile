package com.example.velocitysuites;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private final List<Notification> notifications;
    private final OnNotificationClickListener listener;
    private String highlightedNotificationId;

    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
    }

    public NotificationAdapter(List<Notification> notifications, OnNotificationClickListener listener) {
        this.notifications = notifications;
        this.listener = listener;
    }

    public void setHighlightedNotificationId(String notificationId) {
        this.highlightedNotificationId = notificationId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Notification notification = notifications.get(position);
        holder.tvTitle.setText(notification.getTitle());
        holder.tvMessage.setText(notification.getMessage());
        // Absolute "Sep 9, 2026 • 2:37 AM" (Asia/Manila, from the notification's
        // real created_at) rather than a purely relative "2 hours ago" - the
        // exact date/time must always be visible on the card itself, not just
        // in the detail screen. Falls back to the relative string only for the
        // rare case an older cached notification has no publishedAt yet.
        String absoluteTime = notification.getPublishedAt();
        holder.tvTime.setText(absoluteTime != null && !absoluteTime.isEmpty() ? absoluteTime : notification.getTimestamp());

        // Set icon and colors based on type
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
            case Notification.TYPE_SMS:
                iconRes = R.drawable.ic_info;
                bgColor = R.color.velocity_red_soft;
                iconColor = R.color.velocity_red_dark;
                break;
            case Notification.TYPE_ANNOUNCEMENT:
                iconRes = R.drawable.ic_info;
                bgColor = R.color.velocity_blue_soft;
                iconColor = R.color.velocity_blue_primary;
                break;
            default:
                iconRes = R.drawable.ic_notifications;
                bgColor = R.color.velocity_red_soft;
                iconColor = R.color.velocity_red_primary;
                break;
        }
        
        holder.ivIcon.setImageResource(iconRes);
        holder.iconContainer.setCardBackgroundColor(holder.itemView.getContext().getColor(bgColor));
        holder.ivIcon.setColorFilter(holder.itemView.getContext().getColor(iconColor));

        if (notification.isRead()) {
            holder.unreadDot.setVisibility(View.GONE);
            holder.card.setCardElevation(0f);
            holder.card.setStrokeColor(holder.itemView.getContext().getColor(R.color.velocity_red_subtle));
            holder.card.setCardBackgroundColor(holder.itemView.getContext().getColor(R.color.velocity_surface_elevated));
            holder.itemView.setAlpha(0.7f);
        } else {
            holder.unreadDot.setVisibility(View.VISIBLE);
            holder.card.setCardElevation(4f);
            holder.card.setStrokeColor(holder.itemView.getContext().getColor(R.color.velocity_red_primary));
            holder.card.setCardBackgroundColor(holder.itemView.getContext().getColor(R.color.velocity_surface_elevated));
            holder.itemView.setAlpha(1.0f);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNotificationClick(notification);
            }
        });

        if (holder.tvCategory != null) {
            holder.tvCategory.setText(notification.getType());
        }

        if (holder.tvStatusPill != null) {
            android.content.Context ctx = holder.itemView.getContext();
            NotificationStatusResolver.Result status = NotificationStatusResolver.resolve(ctx, notification);
            if (status != null) {
                holder.tvStatusPill.setVisibility(View.VISIBLE);
                holder.tvStatusPill.setText(status.label);
                holder.tvStatusPill.setBackgroundTintList(ctx.getColorStateList(status.bgColorRes));
                holder.tvStatusPill.setTextColor(ctx.getColor(status.fgColorRes));
            } else {
                holder.tvStatusPill.setVisibility(View.GONE);
            }
        }

        // Deep-link highlight for a specific notification id (e.g. tapped from the
        // dashboard's Booking, Reservation, Payment & Hotel Updates section) - outlines
        // the exact row so it's unambiguous which update the guest tapped.
        if (highlightedNotificationId != null && highlightedNotificationId.equals(notification.getId())) {
            android.content.Context ctx = holder.itemView.getContext();
            holder.card.setStrokeColor(ctx.getColor(R.color.velocity_red_primary));
            holder.card.setStrokeWidth((int) (2 * ctx.getResources().getDisplayMetrics().density));
        }
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvMessage, tvTime, tvCategory, tvStatusPill;
        ImageView ivIcon;
        View unreadDot;
        MaterialCardView card;
        MaterialCardView iconContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNotificationTitle);
            tvMessage = itemView.findViewById(R.id.tvNotificationMessage);
            tvTime = itemView.findViewById(R.id.tvNotificationTime);
            tvCategory = itemView.findViewById(R.id.tvNotificationCategory);
            tvStatusPill = itemView.findViewById(R.id.tvNotificationStatusPill);
            ivIcon = itemView.findViewById(R.id.ivNotificationIcon);
            unreadDot = itemView.findViewById(R.id.unreadDot);
            card = itemView.findViewById(R.id.cardNotification);
            iconContainer = (MaterialCardView) itemView.findViewById(R.id.iconContainer);
        }
    }
}
