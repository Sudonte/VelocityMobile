package com.example.velocitysuites;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Posts real Android system (heads-up) notifications for genuinely new
 * server-fetched {@link Notification}s - never a static/hardcoded alert.
 * Called from exactly one place, {@link RoomRepository#refreshNotifications},
 * so every code path that fetches notifications (dashboard load/pull-to-
 * refresh, the Notification Module, the background poll worker) benefits
 * automatically without per-screen wiring, and the dedup store below is the
 * single source of truth for "have we already alerted this one" regardless
 * of which of those paths triggered the fetch.
 */
public final class NotificationHelper {

    public static final String CHANNEL_ID = "velocity_suites_alerts";
    private static final String PREFS_NAME = "VelocityPrefs";
    private static final String KEY_ALERTED_IDS = "alertedNotificationIds";
    /** Bounds the dedup store so a long-lived install doesn't grow this SharedPreferences value unboundedly. */
    private static final int MAX_TRACKED_IDS = 300;
    private static final String GROUP_KEY = "velocity_suites_alerts_group";
    private static final int SUMMARY_NOTIFICATION_ID = 0;

    private NotificationHelper() {}

    /** Creates the high-importance (heads-up) channel - safe to call repeatedly, Android no-ops on an existing channel id. */
    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        channel.enableVibration(true);
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
    }

    /**
     * Posts a heads-up alert for every notification in {@code freshList} that hasn't
     * already been alerted on this device, then records those ids so a later fetch of
     * the same server rows (e.g. the next pull-to-refresh) never re-alerts them - this
     * is what "do not create duplicate alerts for the same transaction/event" means in
     * practice, since the server list itself is re-fetched wholesale on every refresh.
     */
    public static void maybeAlertNewNotifications(Context context, List<Notification> freshList) {
        if (context == null || freshList == null || freshList.isEmpty()) return;

        Context appContext = context.getApplicationContext();
        Set<String> alerted = loadAlertedIds(appContext);
        int postedThisRound = 0;

        for (Notification n : freshList) {
            if (n == null || n.getId() == null) continue;
            if (n.isRead()) continue; // already-read rows (e.g. read on another device) never need a fresh alert
            if (alerted.contains(n.getId())) continue;

            postSingle(appContext, n);
            alerted.add(n.getId());
            postedThisRound++;
        }

        if (postedThisRound > 1) {
            postGroupSummary(appContext, postedThisRound);
        }

        if (postedThisRound > 0) {
            saveAlertedIds(appContext, alerted);
        }
    }

    /**
     * A single floating alert: title (bold, via NotificationCompat's own styling),
     * message (BigTextStyle so long messages wrap across multiple lines instead of
     * being cut off), a relative timestamp (setWhen + setShowWhen - Android's own
     * system UI renders this as "now"/"5 min ago" the same way it does for every
     * other app), grouped so several arriving close together collapse under one
     * summary instead of covering the screen, and a tap action that deep-links to
     * the right screen for this notification's type.
     */
    private static void postSingle(Context context, Notification n) {
        if (!canPost(context)) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setColor(context.getColor(R.color.velocity_red_primary))
                .setContentTitle(n.getTitle())
                .setContentText(n.getMessage())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(n.getMessage()))
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setGroup(GROUP_KEY)
                .setContentIntent(buildDeepLinkIntent(context, n))
                .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE);

        try {
            NotificationManagerCompat.from(context).notify(n.getId().hashCode(), builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS denied at the OS level (Android 13+) - fail silently,
            // the in-app Notification Module is still fully populated regardless.
        }
    }

    /** Collapses several near-simultaneous alerts under one summary line instead of stacking N separate heads-up cards. */
    private static void postGroupSummary(Context context, int count) {
        if (!canPost(context)) return;

        NotificationCompat.Builder summary = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setColor(context.getColor(R.color.velocity_red_primary))
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notification_group_summary_format, count))
                .setStyle(new NotificationCompat.InboxStyle()
                        .setSummaryText(context.getString(R.string.notification_group_summary_format, count)))
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(buildOpenAppIntent(context));

        try {
            NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, summary.build());
        } catch (SecurityException ignored) {
            // Same permission guard as postSingle().
        }
    }

    /**
     * Never crashes on missing/unavailable notification data or a denied permission -
     * both are treated as "just don't post", not an error condition.
     */
    private static boolean canPost(Context context) {
        try {
            return NotificationManagerCompat.from(context).areNotificationsEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Booking/Reservation/Payment/Cancellation/Check-in alerts all carry a
     * reference_id (see the server-side Notification-to-booking linkage) and
     * open straight into Transaction History with that exact transaction
     * selected - the same proven deep-link TransactionHistoryActivity already
     * uses from the dashboard and from NotificationActivity's own "View
     * Transaction Details" button. A FILTER_PAYMENTS default is safe
     * regardless of whether the underlying record is a Booking or a
     * Reservation: TransactionHistoryActivity already falls back to the "All"
     * chip whenever the selected id isn't present under the requested filter,
     * so the exact transaction is always reachable either way. Promotion/
     * System notifications (no reference_id) open the Notification Module
     * itself instead, scrolled to and highlighting this exact entry.
     */
    private static PendingIntent buildDeepLinkIntent(Context context, Notification n) {
        Intent intent;
        if (n.getReferenceId() != null && !n.getReferenceId().isEmpty()) {
            intent = new Intent(context, TransactionHistoryActivity.class);
            intent.putExtra(TransactionHistoryActivity.EXTRA_OPEN_FILTER, TransactionHistoryActivity.FILTER_PAYMENTS);
            intent.putExtra(TransactionHistoryActivity.EXTRA_SELECTED_BOOKING_ID, n.getReferenceId());
        } else {
            intent = new Intent(context, NotificationActivity.class);
            intent.putExtra(NotificationActivity.EXTRA_NOTIFICATION_ID, n.getId());
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int requestCode = n.getId() != null ? n.getId().hashCode() : 0;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }

    private static PendingIntent buildOpenAppIntent(Context context) {
        Intent intent = new Intent(context, NotificationActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getActivity(context, 0, intent, flags);
    }

    private static Set<String> loadAlertedIds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_ALERTED_IDS, "");
        Set<String> ids = new LinkedHashSet<>();
        if (!stored.isEmpty()) {
            for (String id : stored.split(",")) {
                if (!id.isEmpty()) ids.add(id);
            }
        }
        return ids;
    }

    private static void saveAlertedIds(Context context, Set<String> ids) {
        // Trim from the front (oldest first, LinkedHashSet preserves insertion order)
        // once the store grows past the cap, rather than letting it grow forever.
        while (ids.size() > MAX_TRACKED_IDS) {
            java.util.Iterator<String> it = ids.iterator();
            if (!it.hasNext()) break;
            it.next();
            it.remove();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ALERTED_IDS, String.join(",", ids)).apply();
    }
}
