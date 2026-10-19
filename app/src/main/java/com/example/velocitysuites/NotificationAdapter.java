package com.example.velocitysuites;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The Notifications screen's cards (item_notification.xml). The whole card opens the notification's detail view and marks it
 * read; an unread card also shows a small "Mark as read" button, and the overflow menu keeps "Mark as read" / "Mark as
 * unread". The description shows its first 2 lines (the detail view has all of it). The icon, title, description, time and
 * dot come from {@link NotificationRowBinder}, the same binder the Dashboard rows and the bell popup use.
 */
public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    /** Lines of the description a card shows. */
    public static final int DESCRIPTION_LINES = 2;

    private final Context context;
    private final OnNotificationClickListener listener;
    private String highlightedNotificationId;
    /** The adapter's own copy of what's displayed - never the same List instance NotificationActivity mutates, so a diff against the PREVIOUS state is always possible. Replaced wholesale only inside submitList(); never mutated in place. */
    private List<Row> rows;

    public interface OnNotificationClickListener {
        /** The whole card was tapped: open its target (receipt / transaction / details) and mark it read. */
        void onNotificationClick(Notification notification);

        /** "Mark as read" / "Mark as unread" was picked in the card's overflow menu. */
        void onToggleReadClick(Notification notification);
    }

    public NotificationAdapter(Context context, List<Notification> notifications, OnNotificationClickListener listener) {
        setHasStableIds(true);
        this.context = context;
        this.listener = listener;
        this.rows = buildRows(notifications);
    }

    /**
     * One row's full display-relevant state. The date-group header (isFirstInGroup/groupLabel) is precomputed HERE, once per
     * submitList() call, rather than derived at bind time by comparing to the adjacent item - a DiffUtil-driven adapter can
     * skip rebinding an item whose OWN content didn't change even when its NEIGHBOR did, so "am I first in my date group"
     * has to be part of this item's own comparable content.
     */
    static class Row {
        final Notification notification;
        /**
         * A SNAPSHOT of notification.isRead() taken when this Row was built - never re-read from the (mutable, shared)
         * Notification afterwards. RoomRepository flips read state IN PLACE on the very same Notification instances the
         * previous Row objects still point at, so a diff that compared the live flags would always see the same value on
         * both sides, report "unchanged", and never rebind the row. Bindings use this too.
         */
        final boolean isRead;
        final boolean isFirstInGroup;
        final String groupLabel;

        Row(Notification notification, boolean isRead, boolean isFirstInGroup, String groupLabel) {
            this.notification = notification;
            this.isRead = isRead;
            this.isFirstInGroup = isFirstInGroup;
            this.groupLabel = groupLabel;
        }
    }

    /** List is already newest-first (from the backend/RoomRepository) - a simple compare-to-previous pass is enough to find each group's first row, no separate sort needed. */
    private List<Row> buildRows(List<Notification> notifications) {
        List<Row> result = new ArrayList<>(notifications.size());
        long now = System.currentTimeMillis();
        String previousGroup = null;
        for (Notification n : notifications) {
            String group = NotificationDateGrouper.groupLabel(context, n.getCreatedAtMillis(), now);
            boolean isFirst = !group.equals(previousGroup);
            result.add(new Row(n, n.isRead(), isFirst, group));
            previousGroup = group;
        }
        return result;
    }

    /**
     * Diffs the new list against what's currently shown (by id, see NotificationDiffCallback) and dispatches only the
     * precise resulting changes - a poll that found nothing new or changed produces a DiffResult with zero operations.
     */
    public void submitList(List<Notification> newList) {
        List<Row> newRows = buildRows(newList);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new NotificationDiffCallback(rows, newRows));
        rows = newRows;
        diffResult.dispatchUpdatesTo(this);
    }

    /** Package-private (not private) only so NotificationAdapterDiffTest can drive the diff on the JVM. */
    static class NotificationDiffCallback extends DiffUtil.Callback {
        private final List<Row> oldRows;
        private final List<Row> newRows;

        NotificationDiffCallback(List<Row> oldRows, List<Row> newRows) {
            this.oldRows = oldRows;
            this.newRows = newRows;
        }

        @Override
        public int getOldListSize() {
            return oldRows.size();
        }

        @Override
        public int getNewListSize() {
            return newRows.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return Objects.equals(oldRows.get(oldItemPosition).notification.getId(), newRows.get(newItemPosition).notification.getId());
        }

        /**
         * Every field onBindViewHolder() below actually renders, plus the date-header state. Deliberately
         * excludes the relative time text - that changes purely from time passing, so comparing it would mean this method
         * almost never reports "unchanged".
         */
        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Row oldRow = oldRows.get(oldItemPosition);
            Row newRow = newRows.get(newItemPosition);
            Notification a = oldRow.notification;
            Notification b = newRow.notification;
            // Row.isRead snapshots, deliberately NOT a.isRead()/b.isRead() - see Row#isRead.
            return oldRow.isRead == newRow.isRead
                    && Objects.equals(a.getTitle(), b.getTitle())
                    && Objects.equals(a.getMessage(), b.getMessage())
                    && Objects.equals(a.getType(), b.getType())
                    && Objects.equals(a.getReferenceId(), b.getReferenceId())
                    && Objects.equals(a.getPublishedAt(), b.getPublishedAt())
                    && Objects.equals(a.getCatalogDetails(), b.getCatalogDetails())
                    && Objects.equals(a.getReceiptNumber(), b.getReceiptNumber())
                    && Objects.equals(a.getReceiptType(), b.getReceiptType())
                    && oldRow.isFirstInGroup == newRow.isFirstInGroup
                    && Objects.equals(oldRow.groupLabel, newRow.groupLabel);
        }
    }

    /** Notification#getId() is already the backend's own unique row id - a real stable key RecyclerView can track a row by across a refresh/load-more. */
    @Override
    public long getItemId(int position) {
        String id = rows.get(position).notification.getId();
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException | NullPointerException e) {
            return id != null ? id.hashCode() : RecyclerView.NO_ID;
        }
    }

    public void setHighlightedNotificationId(String notificationId) {
        String previous = this.highlightedNotificationId;
        this.highlightedNotificationId = notificationId;
        // only the row that lost the highlight and the one that gained it change
        for (int i = 0; i < rows.size(); i++) {
            String id = rows.get(i).notification.getId();
            if (id != null && (id.equals(previous) || id.equals(notificationId))) notifyItemChanged(i);
        }
    }

    /** Payload marker for refreshVisibleTimestamps() below - a time-only partial rebind. */
    private static final Object PAYLOAD_REFRESH_TIME = new Object();

    /**
     * Re-renders ONLY the time text of the given (currently visible) row range, via RecyclerView's partial-bind payload
     * mechanism - everything else is left untouched, so nothing else rebinds or flickers. Called roughly once a minute by
     * NotificationActivity's own timer.
     */
    public void refreshVisibleTimestamps(int firstVisiblePosition, int lastVisiblePosition) {
        if (rows.isEmpty() || lastVisiblePosition < 0 || firstVisiblePosition > lastVisiblePosition) return;
        int from = Math.max(0, firstVisiblePosition);
        int to = Math.min(rows.size() - 1, lastVisiblePosition);
        if (from > to) return;
        notifyItemRangeChanged(from, to - from + 1, PAYLOAD_REFRESH_TIME);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    /** Intercepts refreshVisibleTimestamps()'s payload to do a time-only rebind; anything else is a full bind. */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_REFRESH_TIME)) {
            holder.tvTime.setText(NotificationRowBinder.timeOf(holder.itemView.getContext(), rows.get(position).notification, System.currentTimeMillis()));
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Row row = rows.get(position);
        Notification notification = row.notification;
        // The snapshot, never notification.isRead() - see Row#isRead.
        boolean isRead = row.isRead;
        Context ctx = holder.itemView.getContext();
        holder.boundId = notification.getId();

        holder.tvDateGroup.setVisibility(row.isFirstInGroup ? View.VISIBLE : View.GONE);
        holder.tvDateGroup.setText(row.groupLabel);

        NotificationRowBinder.bind(holder.itemView, notification, isRead, System.currentTimeMillis());
        NotificationCategoryPresenter.Result category = NotificationCategoryPresenter.resolve(notification);
        holder.boundIconRes = category.iconRes;
        holder.tvCategory.setText(category.labelRes);
        String linked = NotificationRowBinder.linkedLabel(ctx, notification);
        holder.tvLinked.setText(linked == null ? "" : linked);
        holder.tvLinked.setVisibility(linked == null ? View.GONE : View.VISIBLE);

        holder.tvMessage.setMaxLines(DESCRIPTION_LINES);

        // An unread card shows its own small "Mark as read"; a read card shows nothing extra (its overflow keeps "Mark as unread").
        holder.btnMarkRead.setVisibility(isRead ? View.GONE : View.VISIBLE);
        holder.btnMarkRead.setOnClickListener(v -> {
            if (listener != null) listener.onToggleReadClick(notification);
        });

        // Unread = a subtle brand-tinted card; read = the normal surface. Beside the bold title and the dot, so the cue is
        // never colour alone.
        holder.card.setCardBackgroundColor(ctx.getColor(isRead ? R.color.velocity_surface_elevated : R.color.velocity_red_bg_start));
        // The whole card opens the notification's target and marks it read.
        holder.card.setOnClickListener(v -> {
            if (listener != null) listener.onNotificationClick(notification);
        });
        holder.card.setContentDescription(null);

        String title = notification.getTitle() != null ? notification.getTitle() : "";
        holder.btnOverflow.setOnClickListener(v -> showOverflow(v, notification, isRead));
        holder.btnOverflow.setContentDescription(ctx.getString(R.string.cd_notification_options_format, title));

        // Deep-link highlight for a specific notification id - a brief flash-then-fade of the card's stroke.
        if (highlightedNotificationId != null && highlightedNotificationId.equals(notification.getId())) {
            startHighlightFade(holder.card, ctx, isRead);
        } else {
            holder.card.setStrokeWidth((int) (1 * ctx.getResources().getDisplayMetrics().density));
            holder.card.setStrokeColor(ctx.getColor(isRead ? R.color.velocity_divider_hairline : R.color.velocity_red_subtle));
        }
    }

    /** The card's overflow: one item, labelled with what it does. */
    private void showOverflow(View anchor, Notification notification, boolean isRead) {
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add(isRead ? R.string.mark_as_unread_action : R.string.mark_as_read_action);
        menu.setOnMenuItemClickListener(item -> {
            if (listener != null) listener.onToggleReadClick(notification);
            return true;
        });
        menu.show();
    }

    /**
     * Bright red stroke -> the card's own normal stroke color, over ~1.5s (a short hold so the guest actually registers it,
     * then a real fade). Purely a stroke animation - never touches the background fill.
     */
    private void startHighlightFade(MaterialCardView card, Context ctx, boolean isRead) {
        float density = ctx.getResources().getDisplayMetrics().density;
        int highlightColor = ctx.getColor(R.color.velocity_red_primary);
        int normalColor = ctx.getColor(isRead ? R.color.velocity_divider_hairline : R.color.velocity_red_subtle);
        int highlightWidth = (int) (2 * density);
        int normalWidth = (int) (1 * density);

        card.setStrokeColor(highlightColor);
        card.setStrokeWidth(highlightWidth);

        android.animation.ValueAnimator colorFade = android.animation.ValueAnimator.ofArgb(highlightColor, normalColor);
        colorFade.setStartDelay(600);
        colorFade.setDuration(900);
        colorFade.addUpdateListener(a -> card.setStrokeColor((int) a.getAnimatedValue()));
        colorFade.start();

        android.animation.ValueAnimator widthFade = android.animation.ValueAnimator.ofInt(highlightWidth, normalWidth);
        widthFade.setStartDelay(600);
        widthFade.setDuration(900);
        widthFade.addUpdateListener(a -> card.setStrokeWidth((int) a.getAnimatedValue()));
        widthFade.start();
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvMessage, tvTime, tvCategory, tvLinked, tvDateGroup;
        View btnOverflow, viewUnreadDot, btnMarkRead;
        MaterialCardView card;
        /** The id of the notification currently bound. */
        String boundId;
        /** The drawable resource currently shown in the icon circle. */
        int boundIconRes;

        /** Test hook: which icon resource this row is showing (a vector drawable has no comparable identity once loaded). */
        @androidx.annotation.VisibleForTesting
        public int getBoundIconRes() {
            return boundIconRes;
        }

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNotifTitle);
            tvMessage = itemView.findViewById(R.id.tvNotifMessage);
            btnMarkRead = itemView.findViewById(R.id.btnNotifMarkRead);
            tvTime = itemView.findViewById(R.id.tvNotifTime);
            tvCategory = itemView.findViewById(R.id.tvNotifCategory);
            tvLinked = itemView.findViewById(R.id.tvNotifLinked);
            tvDateGroup = itemView.findViewById(R.id.tvNotificationDateGroup);
            viewUnreadDot = itemView.findViewById(R.id.viewNotifUnreadDot);
            btnOverflow = itemView.findViewById(R.id.btnNotifOverflow);
            card = itemView.findViewById(R.id.cardNotification);
        }
    }
}
