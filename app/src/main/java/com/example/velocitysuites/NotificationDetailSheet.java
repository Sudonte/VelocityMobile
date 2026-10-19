package com.example.velocitysuites;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What tapping a notification shows: a bottom sheet with its category, the FULL title and message (scrolls when long), when it came,
 * a details block (linked ID, room and total of the linked stay, a promotion's validity and inclusions) and the action that takes the
 * guest on - "View Booking #567", "View payment", "View receipt" or "Browse rooms". The linked transaction opens ONLY from that button,
 * never straight from the list card. Opening marks the notification read; "Mark as unread" puts it back.
 * <p>
 * One sheet at a time (a rapid second tap does nothing), dismissed with its screen, and a missing linked transaction says
 * "This item is no longer available" in the sheet instead of failing.
 */
final class NotificationDetailSheet {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US);

    /** The sheet on screen right now, if any. */
    private static java.lang.ref.WeakReference<BottomSheetDialog> currentRef = new java.lang.ref.WeakReference<>(null);
    /** The screen that sheet belongs to: a sheet whose screen is gone no longer counts as showing. */
    private static java.lang.ref.WeakReference<Activity> currentOwnerRef = new java.lang.ref.WeakReference<>(null);

    private NotificationDetailSheet() {
    }

    static boolean isShowing() {
        BottomSheetDialog current = currentRef.get();
        if (current == null || !current.isShowing()) return false;
        Activity owner = currentOwnerRef.get();
        return owner != null && !owner.isFinishing() && !owner.isDestroyed();
    }

    /** The title of the sheet's action button for this notification, or null when there is nothing to open (a plain announcement). */
    @Nullable
    static String actionLabel(@NonNull Context context, @NonNull Notification n) {
        if (n.hasStructuredReceipt()) return context.getString(R.string.notif_detail_view_receipt);
        if (NotificationPrimaryActionResolver.canViewTransaction(n.getType(), n.getReferenceId())) {
            if (Notification.TYPE_PAYMENT.equals(n.getType())) return context.getString(R.string.notif_detail_view_payment);
            String linked = NotificationRowBinder.linkedLabel(context, n);
            return linked == null ? context.getString(R.string.notif_detail_view_payment) : context.getString(R.string.notif_detail_view_format, linked);
        }
        if (Notification.TYPE_PROMOTION.equals(n.getType())) return context.getString(R.string.notif_detail_browse_rooms);
        return null;
    }

    @android.annotation.SuppressLint("InflateParams") // a dialog has no parent to inflate against
    static void show(@NonNull Activity activity, @NonNull Notification n, @Nullable Runnable onReadStateChanged) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (isShowing() && currentOwnerRef.get() == activity) return; // one sheet per screen: a rapid second tap does nothing
        BottomSheetDialog stale = currentRef.get();
        if (stale != null) {
            // A sheet left over from another (finished) screen.
            try {
                stale.dismiss();
            } catch (RuntimeException ignored) {
                // already gone
            }
            currentRef = new java.lang.ref.WeakReference<>(null);
            currentOwnerRef = new java.lang.ref.WeakReference<>(null);
        }
        NotificationOpener.markRead(activity, n, onReadStateChanged);

        View view = LayoutInflater.from(activity).inflate(R.layout.sheet_notification_detail, null, false);
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(view);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        currentRef = new java.lang.ref.WeakReference<>(dialog);
        currentOwnerRef = new java.lang.ref.WeakReference<>(activity);
        dialog.setOnDismissListener(d -> {
            if (currentRef.get() == dialog) {
                currentRef = new java.lang.ref.WeakReference<>(null);
                currentOwnerRef = new java.lang.ref.WeakReference<>(null);
            }
        });
        if (activity instanceof LifecycleOwner) {
            ((LifecycleOwner) activity).getLifecycle().addObserver(new DefaultLifecycleObserver() {
                @Override
                public void onDestroy(@NonNull LifecycleOwner owner) {
                    try {
                        dialog.dismiss();
                    } catch (RuntimeException ignored) {
                        // already gone
                    }
                    owner.getLifecycle().removeObserver(this);
                }
            });
        }

        MaxHeightNestedScrollViewLimiter.limit(view.findViewById(R.id.scrollNotifDetail), activity);
        bind(activity, dialog, view, n, onReadStateChanged);
        SafeDialogs.show(dialog);
    }

    private static void bind(Activity activity, BottomSheetDialog dialog, View view, Notification n, @Nullable Runnable onReadStateChanged) {
        NotificationCategoryPresenter.Result category = NotificationCategoryPresenter.resolve(n);
        view.findViewById(R.id.frameNotifIcon).setBackgroundTintList(ContextCompat.getColorStateList(activity, category.bgColorRes));
        ImageView icon = view.findViewById(R.id.ivNotifIcon);
        icon.setImageResource(category.iconRes);
        icon.setColorFilter(ContextCompat.getColor(activity, category.fgColorRes));
        ((TextView) view.findViewById(R.id.tvNotifDetailCategory)).setText(category.labelRes);
        ((TextView) view.findViewById(R.id.tvNotifDetailTitle)).setText(n.getTitle() == null ? "" : n.getTitle());
        TextView message = view.findViewById(R.id.tvNotifDetailMessage);
        message.setText(n.getMessage() == null ? "" : n.getMessage());
        message.setVisibility(n.getMessage() == null || n.getMessage().trim().isEmpty() ? View.GONE : View.VISIBLE);
        String when = whenText(n);
        TextView time = view.findViewById(R.id.tvNotifDetailTime);
        time.setText(when);
        time.setVisibility(when.isEmpty() ? View.GONE : View.VISIBLE);

        bindRows(activity, view, n);

        view.findViewById(R.id.btnNotifDetailClose).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnNotifDetailDone).setOnClickListener(v -> dialog.dismiss());

        // Mark as unread / read.
        MaterialButton toggle = view.findViewById(R.id.btnNotifDetailToggleRead);
        final boolean[] read = {true}; // opening marked it read
        toggle.setText(R.string.mark_as_unread_action);
        toggle.setOnClickListener(v -> {
            read[0] = !read[0];
            RoomRepository.getInstance(activity).setNotificationReadState(n.getId(), read[0], success -> {
                if (onReadStateChanged != null) onReadStateChanged.run();
            });
            toggle.setText(read[0] ? R.string.mark_as_unread_action : R.string.mark_as_read_action);
            if (onReadStateChanged != null) onReadStateChanged.run();
        });

        // The action.
        MaterialButton action = view.findViewById(R.id.btnNotifDetailAction);
        String label = actionLabel(activity, n);
        if (label == null) {
            action.setVisibility(View.GONE);
            return;
        }
        action.setVisibility(View.VISIBLE);
        action.setText(label);
        action.setOnClickListener(v -> runAction(activity, dialog, view, action, n));
    }

    private static void runAction(Activity activity, BottomSheetDialog dialog, View view, MaterialButton action, Notification n) {
        if (!action.isEnabled()) return; // one lookup at a time; a rapid second tap does nothing
        TextView error = view.findViewById(R.id.tvNotifDetailError);
        error.setVisibility(View.GONE);
        if (n.hasStructuredReceipt()) {
            activity.startActivity(PaymentReceiptActivity.newIntentForReceipt(activity, n.getReceiptNumber()));
            dialog.dismiss();
            return;
        }
        if (Notification.TYPE_PROMOTION.equals(n.getType()) && !NotificationPrimaryActionResolver.canViewTransaction(n.getType(), n.getReferenceId())) {
            activity.startActivity(new Intent(activity, RoomBrowsingActivity.class));
            dialog.dismiss();
            return;
        }
        final boolean[] errorShown = {false};
        final boolean[] lookup = {false};
        action.setEnabled(false);
        TransactionNavigator.openFromNotification(activity, n, new TransactionNavigator.Listener() {
            @Override
            public void onLookupStarted() {
                lookup[0] = true;
            }

            @Override
            public void onLookupFinished() {
                action.setEnabled(true);
                // onNotFound / onError (if any) follow straight after this call: decide once they have had their say.
                view.post(() -> {
                    if (!errorShown[0]) dialog.dismiss();
                });
            }

            @Override
            public void onNotFound() {
                errorShown[0] = true;
                action.setEnabled(true);
                error.setText(R.string.notif_detail_unavailable);
                error.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                errorShown[0] = true;
                action.setEnabled(true);
                error.setText(R.string.notif_detail_lookup_error);
                error.setVisibility(View.VISIBLE);
            }
        });
        if (!lookup[0] && !errorShown[0]) dialog.dismiss(); // opened straight from the cache
        else if (!lookup[0]) action.setEnabled(true);
    }

    private static String whenText(Notification n) {
        if (n.getCreatedAtMillis() > 1) {
            java.time.ZonedDateTime t = Instant.ofEpochMilli(n.getCreatedAtMillis()).atZone(MANILA);
            // A promotion is dated by its start DAY, with no time of day.
            if (n.getType() != null && Notification.TYPE_PROMOTION.equals(n.getType())) {
                return t.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US));
            }
            return t.format(WHEN);
        }
        return n.getPublishedAt() == null ? "" : n.getPublishedAt();
    }

    private static void bindRows(Context context, View view, Notification n) {
        LinearLayout rows = view.findViewById(R.id.layoutNotifDetailRows);
        List<String[]> pairs = new ArrayList<>();
        String linked = NotificationRowBinder.linkedLabel(context, n);
        if (linked != null) {
            pairs.add(new String[]{context.getString(R.string.notif_detail_linked), linked});
            Booking b = TransactionNavigator.pick(RoomRepository.getInstance(context).getAllBookings(), n.getReferenceId(), null, n.getType(), n.getReceiptNumber());
            if (b != null) {
                String room = TransactionRow.roomTextOf(b);
                if (!room.isEmpty()) pairs.add(new String[]{context.getString(R.string.notif_detail_room), room});
                double total = TransactionStatusHelper.grandTotalOf(b);
                if (total > 0) pairs.add(new String[]{context.getString(R.string.notif_detail_total), MoneyFormat.format(total)});
            }
        }
        for (String line : n.getCatalogDetails()) {
            int bar = line.indexOf('|');
            if (bar > 0) pairs.add(new String[]{line.substring(0, bar), line.substring(bar + 1)});
        }
        rows.removeAllViews();
        for (String[] pair : pairs) {
            View row = LayoutInflater.from(context).inflate(R.layout.item_notif_detail_row, rows, false);
            ((TextView) row.findViewById(R.id.tvDetailRowLabel)).setText(pair[0]);
            ((TextView) row.findViewById(R.id.tvDetailRowValue)).setText(pair[1]);
            rows.addView(row);
        }
        rows.setVisibility(pairs.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Keeps the sheet's body from growing past about 55% of the screen: a long message scrolls inside it. */
    private static final class MaxHeightNestedScrollViewLimiter {
        static void limit(View scroll, Activity activity) {
            if (scroll instanceof MaxHeightNestedScrollView) {
                ((MaxHeightNestedScrollView) scroll).setMaxHeightPx((int) (activity.getResources().getDisplayMetrics().heightPixels * 0.55f));
            }
        }
    }
}
