package com.example.velocitysuites;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
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
        /** Tapped the card's own "View Transaction" button - distinct from the whole-card tap, which opens the notification detail screen instead. Only ever bound when NotificationPrimaryActionResolver#canViewTransaction() is true for this row (see bindActions()). */
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
    static class Row {
        final Notification notification;
        /**
         * A SNAPSHOT of notification.isRead() taken when this Row was built - never re-read
         * from the (mutable, shared) Notification afterwards. RoomRepository flips read state
         * IN PLACE on the very same Notification instances the previous Row objects still
         * point at, so a diff that compared old.notification.isRead() to new.notification.
         * isRead() would always see the same (already-flipped) value on both sides, report
         * "unchanged", and never rebind the row - leaving a stale button label, unread dot
         * and title weight on screen after a confirmed change. Comparing snapshots taken at
         * two different moments is what makes the change visible to DiffUtil; bindings use
         * this too, so what is drawn always agrees with what was diffed.
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
            // Row.isRead snapshots, deliberately NOT a.isRead()/b.isRead() - see Row#isRead.
            return oldRow.isRead == newRow.isRead
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
     * Time text only - the short, always-one-line form from TimeUtils#formatCompact() ("Just now", "5m
     * ago", "2h ago", then "Sep 30 • 10:09 PM"), computed FRESH from the row's created-at instant on every
     * call (never from the cached, potentially stale Notification#getTimestamp() string), which is what lets
     * the once-a-minute refreshVisibleTimestamps() tick "5m ago" into "6m ago". Only a row with no created-at
     * at all (the oldest shape a backend row can have) falls back to its stored relative/absolute string.
     * Shared by the full bind below and the payload-only partial bind, so both always agree.
     */
    private void bindTime(ViewHolder holder, Notification notification) {
        String text = TimeUtils.formatCompact(notification.getCreatedAtMillis(), System.currentTimeMillis());
        if (text.isEmpty()) {
            String relative = notification.getTimestamp();
            String absolute = notification.getPublishedAt();
            text = relative != null && !relative.isEmpty() ? relative : (absolute != null ? absolute : "");
        }
        holder.tvTime.setText(text);
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
        // The snapshot, never notification.isRead() - see Row#isRead.
        boolean isRead = row.isRead;
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
        holder.tvTitle.setTypeface(null, isRead ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        // The red dot on the category icon - the third, shape-based unread cue (bold
        // title and tinted card being the other two). contentDescription makes it
        // audible too: TalkBack announces "Unread" for an unread card instead of the
        // state being conveyed by color/weight/shape alone.
        holder.viewUnreadDot.setVisibility(isRead ? View.GONE : View.VISIBLE);
        holder.viewUnreadDot.setContentDescription(isRead ? null : ctx.getString(R.string.notif_unread_indicator_desc));
        holder.tvMessage.setText(notification.getMessage());
        bindTime(holder, notification);

        NotificationCategoryPresenter.Result category = NotificationCategoryPresenter.resolve(notification.getType());
        NotificationStatusResolver.Result status = NotificationStatusResolver.resolve(ctx, notification);
        // The circle shows the STATUS icon (amber clock = pending, green check = paid/verified/confirmed,
        // X = cancelled/rejected) whenever the notification announces an outcome - so "Payment Pending
        // Validation" no longer wears the Payment category's green checkmark. A notification with no status
        // keeps its category icon.
        holder.boundIconRes = status != null ? status.iconRes : category.iconRes;
        if (status != null) {
            holder.ivIcon.setImageResource(status.iconRes);
            holder.iconContainer.setCardBackgroundColor(ctx.getColor(status.bgColorRes));
            holder.ivIcon.setColorFilter(ctx.getColor(status.fgColorRes));
        } else {
            holder.ivIcon.setImageResource(category.iconRes);
            holder.iconContainer.setCardBackgroundColor(ctx.getColor(category.bgColorRes));
            holder.ivIcon.setColorFilter(ctx.getColor(category.fgColorRes));
        }

        // Unread = a subtle brand-tinted background (no border/elevation games) - read
        // = the card's normal surface color. Deliberately no alpha-fade on the read
        // state anymore: dimming the whole card (including its text) hurt readability
        // for a guest re-checking an already-read notification, and conflicts with
        // "read cards use the normal background" - the unread-vs-read signal is
        // the tint, the title weight, and the unread dot above, each independently
        // visible.
        holder.card.setCardElevation(0f);
        holder.card.setCardBackgroundColor(ctx.getColor(
                isRead ? R.color.velocity_surface_elevated : R.color.velocity_red_bg_start));
        holder.card.setStrokeColor(ctx.getColor(
                isRead ? R.color.velocity_divider_hairline : R.color.velocity_red_subtle));

        // Set on the card itself, not holder.itemView (the outer wrapper that also
        // contains the shared date-group header above - see item_notification.xml) -
        // otherwise a tap anywhere in the header's row would also fire this.
        holder.card.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNotificationClick(notification);
            }
        });

        bindActions(holder, notification, isRead);

        if (holder.tvCategory != null) {
            holder.tvCategory.setText(category.labelRes);
        }

        if (holder.tvStatusPill != null) {
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
            startHighlightFade(holder.card, ctx, isRead);
        } else {
            holder.card.setStrokeWidth((int) (1 * ctx.getResources().getDisplayMetrics().density));
            holder.card.setStrokeColor(ctx.getColor(isRead ? R.color.velocity_divider_hairline : R.color.velocity_red_subtle));
        }
    }

    /**
     * Footer actions. The read/unread action is ALWAYS present and labeled with
     * what tapping it does - "Mark as read" on an unread notification, "Mark as
     * unread" on a read one - with the matching envelope icon for the state it
     * turns the notification INTO (open envelope = read, closed = unread, same
     * convention as a mail client's own mark-as-read action). Tapping it only
     * reports the tap (onToggleReadClick) - NotificationActivity owns the
     * confirmation dialog, the write, and the success/failure message, so this
     * adapter never mutates read state itself. "View Transaction" only
     * shows for a Booking/Reservation/Payment/Check-in notification with a
     * resolvable reference id - Promotions/Announcements/System have nothing to
     * deep-link to; the button is then GONE and the toggle simply stays on
     * the right of the one-row footer.
     */
    private void bindActions(ViewHolder holder, Notification notification, boolean isRead) {
        Context ctx = holder.itemView.getContext();
        String title = notification.getTitle() != null ? notification.getTitle() : "";

        holder.btnToggleReadState.setText(isRead ? R.string.mark_as_unread_action : R.string.mark_as_read_action);
        setCompoundIcons(holder.btnToggleReadState, isRead ? R.drawable.ic_email : R.drawable.ic_email_open, 0);
        holder.btnToggleReadState.setContentDescription(ctx.getString(
                isRead ? R.string.cd_mark_unread_format : R.string.cd_mark_read_format, title));
        holder.btnToggleReadState.setOnClickListener(v -> {
            if (listener != null) listener.onToggleReadClick(notification);
        });

        boolean canViewTransaction = NotificationPrimaryActionResolver.canViewTransaction(notification.getType(), notification.getReferenceId());
        holder.btnViewTransactionDetails.setVisibility(canViewTransaction ? View.VISIBLE : View.GONE);
        if (canViewTransaction) {
            setCompoundIcons(holder.btnViewTransactionDetails, 0, R.drawable.ic_arrow_forward);
            holder.btnViewTransactionDetails.setContentDescription(ctx.getString(R.string.cd_view_transaction_format, title));
            holder.btnViewTransactionDetails.setOnClickListener(v -> {
                if (listener != null) listener.onViewTransactionDetailsClick(notification);
            });
        } else {
            // Not a stale listener on a recycled, hidden view.
            holder.btnViewTransactionDetails.setOnClickListener(null);
        }
    }

    /**
     * Start/end icons for a footer action, sized to its text (1.3x the text size, so they grow with the
     * guest's system font like the label does) and tinted like the label - see {@link TextIcons}.
     */
    private static void setCompoundIcons(TextView view, @DrawableRes int startRes, @DrawableRes int endRes) {
        TextIcons.setRelative(view, startRes, endRes, 1.3f);
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
        TextView btnToggleReadState, btnViewTransactionDetails;
        View dividerDateGroup, viewUnreadDot;
        MaterialCardView card;
        MaterialCardView iconContainer;
        /** The drawable resource currently shown in the icon circle - the status icon when there is a status, else the category icon. */
        int boundIconRes;

        /** Test hook: which icon resource this row is showing (a vector drawable has no comparable identity once loaded). */
        @androidx.annotation.VisibleForTesting
        public int getBoundIconRes() {
            return boundIconRes;
        }

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNotificationTitle);
            tvMessage = itemView.findViewById(R.id.tvNotificationMessage);
            tvTime = itemView.findViewById(R.id.tvNotificationTime);
            tvCategory = itemView.findViewById(R.id.tvNotificationCategory);
            tvStatusPill = itemView.findViewById(R.id.tvNotificationStatusPill);
            tvDateGroup = itemView.findViewById(R.id.tvNotificationDateGroup);
            dividerDateGroup = itemView.findViewById(R.id.dividerNotificationDateGroup);
            viewUnreadDot = itemView.findViewById(R.id.viewUnreadDot);
            ivIcon = itemView.findViewById(R.id.ivNotificationIcon);
            btnToggleReadState = itemView.findViewById(R.id.btnToggleReadState);
            btnViewTransactionDetails = itemView.findViewById(R.id.btnViewTransactionDetails);
            card = itemView.findViewById(R.id.cardNotification);
            iconContainer = (MaterialCardView) itemView.findViewById(R.id.iconContainer);
        }
    }
}
