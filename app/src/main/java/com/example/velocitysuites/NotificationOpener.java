package com.example.velocitysuites;

import android.app.Activity;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What tapping a notification does, wherever it is shown (Dashboard row, bell popup, Notifications card): it is marked read
 * at once - the repository flips its cache before any network round trip, so every badge repaints now - and its target
 * opens: the receipt it announces, else the transaction it is about, else its own detail screen.
 */
final class NotificationOpener {

    private NotificationOpener() {
    }

    /**
     * @param listener         progress / not-found / error callbacks for a transaction that must be fetched; null for the
     *                         default (a not-found dialog and an error toast)
     * @param onReadStateChanged runs after the notification is marked read and again once the save finishes (so a failed
     *                         save repaints the truth); may be null
     */
    static void open(@NonNull Activity activity, @NonNull Notification n, @Nullable TransactionNavigator.Listener listener,
                     @Nullable Runnable onReadStateChanged) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        markRead(activity, n, onReadStateChanged);
        if (n.hasStructuredReceipt()) {
            activity.startActivity(PaymentReceiptActivity.newIntentForReceipt(activity, n.getReceiptNumber()));
        } else if (NotificationPrimaryActionResolver.canViewTransaction(n.getType(), n.getReferenceId())) {
            TransactionNavigator.openFromNotification(activity, n, listener != null ? listener : defaultListener(activity));
        } else {
            activity.startActivity(NotificationDetailsActivity.newIntent(activity, n));
        }
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

    private static TransactionNavigator.Listener defaultListener(@NonNull Activity activity) {
        return new TransactionNavigator.Listener() {
            @Override
            public void onLookupStarted() {
            }

            @Override
            public void onLookupFinished() {
            }

            @Override
            public void onNotFound() {
                if (!activity.isFinishing() && !activity.isDestroyed()) TransactionNavigator.showNotFoundDialog(activity);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(activity.getApplicationContext(), activity.getString(R.string.txn_lookup_error_format, message), Toast.LENGTH_LONG).show();
            }
        };
    }
}
