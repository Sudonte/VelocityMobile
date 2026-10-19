package com.example.velocitysuites;

import android.app.Activity;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What tapping a notification does, wherever it is shown (Dashboard row, bell popup, Notifications card): it opens the notification's
 * DETAIL sheet (the full message first) and marks it read at once - the repository flips its cache before any network round trip, so
 * every badge repaints now. The linked transaction opens only from the sheet's action button.
 */
final class NotificationOpener {

    private NotificationOpener() {
    }

    /** @param onReadStateChanged runs after the notification is marked read, and whenever the sheet toggles it; may be null */
    static void open(@NonNull Activity activity, @NonNull Notification n, @Nullable Runnable onReadStateChanged) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        NotificationDetailSheet.show(activity, n, onReadStateChanged);
    }

    /** Marks an unread notification read (a read one is left alone, so re-opening it never fires a pointless request). */
    static void markRead(@NonNull Activity activity, @NonNull Notification n, @Nullable Runnable onReadStateChanged) {
        if (n.isRead()) return;
        RoomRepository.getInstance(activity).setNotificationReadState(n.getId(), true, success -> {
            if (!success) {
                Toast.makeText(activity.getApplicationContext(), R.string.error_notification_update_failed, Toast.LENGTH_LONG).show();
            }
            if (onReadStateChanged != null) onReadStateChanged.run();
        });
        if (onReadStateChanged != null) onReadStateChanged.run();
    }
}
