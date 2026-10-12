package com.example.velocitysuites;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * The dropdown the header bell opens: the most recent notifications (title, short message, time ago, unread dot),
 * "No new notifications" when there are none, and a "View all notifications" button for the full screen.
 *
 * Tapping a row marks it read (RoomRepository applies it to its cache at once, so the badge drops straight away)
 * and opens its transaction when it has one, else its detail screen. The popup closes on an outside tap and on
 * back (it is focusable), and the owning screen calls {@link #dismiss()} from onPause/onDestroy so a window can
 * never outlive its activity.
 */
public final class NotificationPopup {

    static final int MAX_ROWS = 5;

    private final Activity activity;
    private final Runnable onReadStateChanged;
    private PopupWindow window;

    public NotificationPopup(@NonNull Activity activity, @NonNull Runnable onReadStateChanged) {
        this.activity = activity;
        this.onReadStateChanged = onReadStateChanged;
    }

    public boolean isShowing() {
        return window != null && window.isShowing();
    }

    public void dismiss() {
        if (window != null) {
            try {
                window.dismiss();
            } catch (RuntimeException ignored) {
                // The window token is already gone (activity tearing down) - nothing left to remove.
            }
            window = null;
        }
    }

    /** Shows the popup under {@code anchor}, or closes it if it is already open (the bell toggles it). */
    public void toggle(@NonNull View anchor) {
        if (isShowing()) {
            dismiss();
            return;
        }
        if (activity.isFinishing() || activity.isDestroyed() || !anchor.isAttachedToWindow()) return;

        // A popup window has no parent to inflate against.
        @SuppressLint("InflateParams")
        View content = LayoutInflater.from(activity).inflate(R.layout.popup_notifications, null, false);
        float density = activity.getResources().getDisplayMetrics().density;
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int width = Math.min(metrics.widthPixels - (int) (24 * density), (int) (340 * density));

        PopupWindow popup = new PopupWindow(content, width, LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(8 * density);
        popup.setOnDismissListener(() -> {
            if (window == popup) window = null;
        });

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int margin = (int) (6 * density);
        int x = Math.max(margin, Math.min(loc[0] + anchor.getWidth() - width, metrics.widthPixels - width - margin));
        int y = loc[1] + anchor.getHeight();

        bind(content);
        try {
            popup.showAtLocation(anchor, Gravity.TOP | Gravity.START, x, y);
            window = popup;
        } catch (RuntimeException e) {
            // Token went bad between the checks above and now (screen closing) - skip quietly.
            return;
        }

        RoomRepository repository = RoomRepository.getInstance(activity);
        if (!repository.hasLoadedNotifications()) {
            showLoading(content, true);
            repository.refreshNotifications(new RoomRepository.RepositoryCallback<List<Notification>>() {
                @Override
                public void onSuccess(List<Notification> result) {
                    if (window == popup) {
                        showLoading(content, false);
                        bind(content);
                        onReadStateChanged.run();
                    }
                }

                @Override
                public void onError(String message) {
                    if (window == popup) {
                        showLoading(content, false);
                        bind(content);
                    }
                }
            });
        }
    }

    private void showLoading(View content, boolean loading) {
        content.findViewById(R.id.progressNotifPopup).setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            content.findViewById(R.id.tvNotifPopupEmpty).setVisibility(View.GONE);
        }
    }

    private void bind(View content) {
        RoomRepository repository = RoomRepository.getInstance(activity);
        LinearLayout rows = content.findViewById(R.id.layoutNotifPopupRows);
        TextView empty = content.findViewById(R.id.tvNotifPopupEmpty);
        TextView unreadLabel = content.findViewById(R.id.tvNotifPopupUnread);
        rows.removeAllViews();

        List<Notification> all = repository.getNotifications();
        int shown = 0;
        long now = System.currentTimeMillis();
        for (Notification n : all) {
            if (n == null || shown >= MAX_ROWS) break;
            View row = LayoutInflater.from(activity).inflate(R.layout.item_notification_popup, rows, false);
            ((TextView) row.findViewById(R.id.tvPopupNotifTitle)).setText(n.getTitle());
            ((TextView) row.findViewById(R.id.tvPopupNotifMessage)).setText(n.getMessage());
            TextView time = row.findViewById(R.id.tvPopupNotifTime);
            time.setText(TimeUtils.formatCompact(n.getCreatedAtMillis(), now));
            row.findViewById(R.id.unreadDotPopup).setVisibility(n.isRead() ? View.INVISIBLE : View.VISIBLE);
            TextView title = row.findViewById(R.id.tvPopupNotifTitle);
            title.setTypeface(title.getTypeface(), n.isRead() ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
            row.setOnClickListener(v -> onRowTapped(n));
            rows.addView(row);
            shown++;
        }

        boolean any = shown > 0;
        boolean loadingNow = content.findViewById(R.id.progressNotifPopup).getVisibility() == View.VISIBLE;
        rows.setVisibility(any ? View.VISIBLE : View.GONE);
        empty.setVisibility(any || loadingNow ? View.GONE : View.VISIBLE);

        int unread = repository.getUnreadNotificationCount();
        if (unread > 0) {
            unreadLabel.setText(activity.getString(R.string.notif_popup_unread_format, unread));
            unreadLabel.setVisibility(View.VISIBLE);
        } else {
            unreadLabel.setVisibility(View.GONE);
        }

        content.findViewById(R.id.btnNotifPopupViewAll).setOnClickListener(v -> {
            dismiss();
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                activity.startActivity(new android.content.Intent(activity, NotificationActivity.class));
            }
        });
    }

    private void onRowTapped(Notification n) {
        dismiss();
        if (activity.isFinishing() || activity.isDestroyed()) return;
        RoomRepository repository = RoomRepository.getInstance(activity);
        if (!n.isRead()) {
            repository.setNotificationReadState(n.getId(), true, success -> {
                if (!success) {
                    Toast.makeText(activity.getApplicationContext(), R.string.error_notification_update_failed, Toast.LENGTH_LONG).show();
                }
                onReadStateChanged.run();
            });
            onReadStateChanged.run();
        }
        if (NotificationPrimaryActionResolver.canViewTransaction(n.getType(), n.getReferenceId())) {
            TransactionNavigator.openFromNotification(activity, n, new TransactionNavigator.Listener() {
                @Override
                public void onLookupStarted() {
                }

                @Override
                public void onLookupFinished() {
                }

                @Override
                public void onNotFound() {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        TransactionNavigator.showNotFoundDialog(activity);
                    }
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(activity.getApplicationContext(),
                            activity.getString(R.string.txn_lookup_error_format, message), Toast.LENGTH_LONG).show();
                }
            });
        } else {
            activity.startActivity(NotificationDetailsActivity.newIntent(activity, n));
        }
    }
}
