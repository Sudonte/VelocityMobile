package com.example.velocitysuites;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private final Context context;
    private final OnNotificationClickListener listener;
    private String highlightedNotificationId;
    /** The adapter's own copy of what's displayed - never the same List instance NotificationActivity mutates, so a diff against the PREVIOUS state is always possible. Replaced wholesale only inside submitList(); never mutated in place. */
    private List<Row> rows;

    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
        /** Tapped the per-row mark-as-read/unread toggle - distinct from the whole-card tap above. */
        void onToggleReadClick(Notification notification);
        /** Tapped the card's own "View Transaction Details" button - distinct from the whole-card tap, which opens the notification detail screen instead. Only ever bound when NotificationPrimaryActionResolver#canViewTransaction() is true for this row (see bindActions()). */
        void onViewTransactionDetailsClick(Notification notification);
    }

    public NotificationAdapter(Context context, List<Notification> notifications, OnNotificationClickListener listener) {
        setHasStableIds(true);
        this.context = context;
        this.listener = listener;
        this.rows = buildRows(notifications);
    }

    /**
     * One row's full display-relevant state. The date-group header
     * (isFirstInGroup/groupLabel) is precomputed HERE, once per submitList()
     * call, rather than derived at bind time by comparing to the adjacent
     * item in the list (the previous approach) - a DiffUtil-driven adapter
     * can skip rebinding an item whose OWN content didn't change even when
     * its NEIGHBOR did (that's the whole point of diffing), so "am I first
     * in my date group" has to be part of this item's own comparable
     * content, not a side-effect of whatever happened to be adjacent to it
     * the last time onBindViewHolder() actually ran. Without this, inserting
     * a new notification at the front could leave a stale "Today" header
     * sitting on what used to be the first row, now duplicated under the
     * new row's own "Today" header, because the old row's unchanged content
     * never triggered a rebind.
     */
    private static class Row {
        final Notification notification;
        final boolean isFirstInGroup;
        final String groupLabel;

        Row(Notification notification, boolean isFirstInGroup, String groupLabel) {
            this.notification = notification;
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
            result.add(new Row(n, isFirst, group));
            previousGroup = group;
        }
        return result;
    }

    /**
     * Diffs the new list against what's currently shown (by id, see
     * NotificationDiffCallback) and dispatches only the precise resulting
     * changes - a poll that found nothing new or changed produces a
     * DiffResult with zero operations, so this never calls any notify*()
     * method at all in that case; an update to one existing row (e.g.
     * is_read flipped on another device) rebinds only that row instead of
     * the whole visible list; a genuinely new row is inserted at its real
     * position instead of the whole list being torn down and rebuilt.
     */
    public void submitList(List<Notification> newList) {
        List<Row> newRows = buildRows(newList);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new NotificationDiffCallback(rows, newRows));
        rows = newRows;
        diffResult.dispatchUpdatesTo(this);
    }

    private static class NotificationDiffCallback extends DiffUtil.Callback {
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
         * Every field onBindViewHolder() below actually renders, plus
         * isFirstInGroup/groupLabel (the date-header state - see Row's own
         * doc for why that counts as content, not just position).
         * Deliberately excludes getTimestamp() (the "X minutes ago" relative
         * string) - that changes purely from time passing, so comparing it
         * would mean this method almost never reports "unchanged" even when
         * nothing real about the notification is different, defeating the
         * entire point of diffing.
         */
        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Row oldRow = oldRows.get(oldItemPosition);
            Row newRow = newRows.get(newItemPosition);
            Notification a = oldRow.notification;
            Notification b = newRow.notification;
            return a.isRead() == b.isRead()
                    && Objects.equals(a.getTitle(), b.getTitle())
                    && Objects.equals(a.getMessage(), b.getMessage())
                    && Objects.equals(a.getType(), b.getType())
                    && Objects.equals(a.getReferenceId(), b.getReferenceId())
                    && Objects.equals(a.getPublishedAt(), b.getPublishedAt())
                    && Objects.equals(a.getReceiptNumber(), b.getReceiptNumber())
                    && Objects.equals(a.getReceiptType(), b.getReceiptType())
                    && oldRow.isFirstInGroup == newRow.isFirstInGroup
                    && Objects.equals(oldRow.groupLabel, newRow.groupLabel);
        }
    }

    /** Notification#getId() is already the backend's own unique row id - a real stable key RecyclerView can track a row by across a refresh/load-more, instead of treating every position as a brand-new view every time this adapter updates. */
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
        this.highlightedNotificationId = notificationId;
        notifyDataSetChanged();
    }

    /** Payload marker for refreshVisibleTimestamps() below - onBindViewHolder(holder, position, payloads) checks for this to do a time-only partial rebind instead of the full bind every other notify path triggers. */
    private static final Object PAYLOAD_REFRESH_TIME = new Object();

    /**
     * Re-renders ONLY the time text of the given (currently visible) row
     * range, via RecyclerView's partial-bind payload mechanism - title,
     * message, icon, read state, status pill, and everything else are left
     * completely untouched, so nothing else rebinds or flickers. Called
     * roughly once a minute by NotificationActivity's own timer, restricted
     * to whatever's actually visible right now (there is no point
     * refreshing a row the guest can't see). Absolute-time rows re-render
     * the exact same text (no visible change, harmless) - only a row
     * genuinely showing relative time (see bindTime()'s own doc) actually
     * changes.
     */
    public void refreshVisibleTimestamps(int firstVisiblePosition, int lastVisiblePosition) {
        if (rows.isEmpty() || lastVisiblePosition < 0 || firstVisiblePosition > lastVisiblePosition) return;
        int from = Math.max(0, firstVisiblePosition);
        int to = Math.min(rows.size() - 1, lastVisiblePosition);
        if (from > to) return;
        notifyItemRangeChanged(from, to - from + 1, PAYLOAD_REFRESH_TIME);
    }

    /**
     * Time text only - always the absolute "Sep 9, 2026 • 2:37 AM" when the
     * backend provided one, since that never goes stale; falls back to a
     * FRESHLY computed relative string (never the cached, potentially
     * minutes/hours-stale Notification#getTimestamp() field - see
     * TimeUtils#formatRelative(long)'s own doc) for the rare older
     * notification with no publishedAt at all. Shared by the full bind
     * below and the payload-only partial bind, so both always agree.
     */
    private void bindTime(ViewHolder holder, Notification notification) {
        String absoluteTime = notification.getPublishedAt();
        holder.tvTime.setText(absoluteTime != null && !absoluteTime.isEmpty()
                ? absoluteTime
                : TimeUtils.formatRelative(notification.getCreatedAtMillis()));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    /**
     * Intercepts refreshVisibleTimestamps()'s payload to do a time-only
     * rebind; any other call (payloads empty - a real data change via
     * submitList(), the initial bind, a recycled-view rebind, etc.) falls
     * through to the normal full onBindViewHolder(holder, position) via the
     * default RecyclerView.Adapter implementation.
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_REFRESH_TIME)) {
            bindTime(holder, rows.get(position).notification);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Row row = rows.get(position);
        Notification notification = row.notification;
        Context ctx = holder.itemView.getContext();

        // Date-group header ("Today"/"Yesterday"/"Earlier") - precomputed in
        // buildRows()/Row, see that class's own doc for why this can't be
        // recomputed here by comparing to the adjacent item anymore.
        if (holder.tvDateGroup != null) {
            // Deliberately keyed only on row.isFirstInGroup (precomputed content,
            // never `position` directly) - a DiffUtil-driven adapter can skip
            // rebinding a row whose own compared content didn't change, so any
            // visibility rule that depended on this row's absolute position
            // instead could go stale the moment an insertion shifted it without
            // otherwise changing it (the exact bug Row's own doc already covers
            // for the header text itself). A harmless side effect: the divider
            // also shows above the very first card in the whole list, not just
            // above later groups - visually fine, and avoids that trap entirely.
            int visibility = row.isFirstInGroup ? View.VISIBLE : View.GONE;
            holder.tvDateGroup.setVisibility(visibility);
            holder.tvDateGroup.setText(row.groupLabel);
            if (holder.dividerDateGroup != null) {
                holder.dividerDateGroup.setVisibility(visibility);
            }
        }

        holder.tvTitle.setText(notification.getTitle());
        // Bold when unread, regular when read - alongside the unread dot/tinted
        // background below, a second, text-level cue that doesn't rely on color
        // alone (helps in bright sunlight or for a guest who has trouble
        // distinguishing the background tint).
        holder.tvTitle.setTypeface(null, notification.isRead() ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        holder.tvMessage.setText(notification.getMessage());
        bindTime(holder, notification);

        NotificationCategoryPresenter.Result category = NotificationCategoryPresenter.resolve(notification.getType());
        holder.ivIcon.setImageResource(category.iconRes);
        holder.iconContainer.setCardBackgroundColor(ctx.getColor(category.bgColorRes));
        holder.ivIcon.setColorFilter(ctx.getColor(category.fgColorRes));

        // Unread = a subtle brand-tinted background (no border/elevation games) - read
        // = the card's normal surface color. Deliberately no alpha-fade on the read
        // state anymore: dimming the whole card (including its text) hurt readability
        // for a guest re-checking an already-read notification, and conflicts with
        // "read cards use the normal background" - the ONLY unread-vs-read signal now
        // is the dot, the tint, and the title weight above, each independently visible.
        holder.unreadDot.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);
        holder.card.setCardElevation(notification.isRead() ? 0f : 2f);
        holder.card.setCardBackgroundColor(ctx.getColor(
                notification.isRead() ? R.color.velocity_surface_elevated : R.color.velocity_red_bg_start));
        holder.card.setStrokeColor(ctx.getColor(
                notification.isRead() ? R.color.velocity_divider_hairline : R.color.velocity_red_subtle));

        // Set on the card itself, not holder.itemView (the outer wrapper that also
        // contains the shared date-group header above - see item_notification.xml) -
        // otherwise a tap anywhere in the header's row would also fire this.
        holder.card.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNotificationClick(notification);
            }
        });

        bindActions(holder, notification, ctx);

        if (holder.tvCategory != null) {
            holder.tvCategory.setText(notification.getType());
        }

        if (holder.tvStatusPill != null) {
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
        // dashboard's Booking, Reservation, Payment & Hotel Updates section) - a
        // brief, smooth flash-then-fade (see startHighlightFade()) rather than a
        // static border that stayed until something else changed. Re-plays if the
        // guest scrolls this exact row off-screen and back while it's still the
        // selected one - a harmless, arguably helpful reinforcement, not a bug.
        if (highlightedNotificationId != null && highlightedNotificationId.equals(notification.getId())) {
            startHighlightFade(holder.card, ctx, notification.isRead());
        } else {
            holder.card.setStrokeWidth((int) (1 * ctx.getResources().getDisplayMetrics().density));
            holder.card.setStrokeColor(ctx.getColor(notification.isRead() ? R.color.velocity_divider_hairline : R.color.velocity_red_subtle));
        }
    }

    /**
     * Read/Unread toggle (always shown) and "View Transaction Details" (only for a
     * Booking/Reservation/Payment/Check-in notification with a resolvable reference
     * id - Promotions/Announcements/System have nothing to deep-link to). Both are
     * real MaterialButtons now (48dp tall, matching the app-wide minimum touch
     * target) rather than the previous bare 36dp icon-only toggle.
     */
    private void bindActions(ViewHolder holder, Notification notification, Context ctx) {
        boolean isRead = notification.isRead();
        holder.btnToggleReadState.setText(isRead ? R.string.mark_as_unread_action : R.string.mark_as_read_action);
        holder.btnToggleReadState.setIconResource(isRead ? R.drawable.ic_eye : R.drawable.ic_check_circle);
        holder.btnToggleReadState.setOnClickListener(v -> {
            if (listener != null) listener.onToggleReadClick(notification);
        });

        boolean canViewTransaction = NotificationPrimaryActionResolver.canViewTransaction(notification.getType(), notification.getReferenceId());
        holder.btnViewTransactionDetails.setVisibility(canViewTransaction ? View.VISIBLE : View.GONE);
        if (canViewTransaction) {
            holder.btnViewTransactionDetails.setOnClickListener(v -> {
                if (listener != null) listener.onViewTransactionDetailsClick(notification);
            });
        }
    }

    /**
     * Bright red stroke -> the card's own normal stroke color, over ~1.5s (a
     * short hold so the guest actually registers it, then a real fade, not an
     * instant snap). Purely a stroke-color/width animation - never touches the
     * background fill, so it layers cleanly on top of the unread/read background
     * already set above regardless of which one this row has.
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
        TextView tvTitle, tvMessage, tvTime, tvCategory, tvStatusPill, tvDateGroup;
        ImageView ivIcon;
        com.google.android.material.button.MaterialButton btnToggleReadState, btnViewTransactionDetails;
        View unreadDot, dividerDateGroup;
        MaterialCardView card;
        MaterialCardView iconContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNotificationTitle);
            tvMessage = itemView.findViewById(R.id.tvNotificationMessage);
            tvTime = itemView.findViewById(R.id.tvNotificationTime);
            tvCategory = itemView.findViewById(R.id.tvNotificationCategory);
            tvStatusPill = itemView.findViewById(R.id.tvNotificationStatusPill);
            tvDateGroup = itemView.findViewById(R.id.tvNotificationDateGroup);
            dividerDateGroup = itemView.findViewById(R.id.dividerNotificationDateGroup);
            ivIcon = itemView.findViewById(R.id.ivNotificationIcon);
            btnToggleReadState = itemView.findViewById(R.id.btnToggleReadState);
            btnViewTransactionDetails = itemView.findViewById(R.id.btnViewTransactionDetails);
            unreadDot = itemView.findViewById(R.id.unreadDot);
            card = itemView.findViewById(R.id.cardNotification);
            iconContainer = (MaterialCardView) itemView.findViewById(R.id.iconContainer);
        }
    }
}
