package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.velocitysuites.network.SessionManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Periodic background poll so alerts still arrive while the app is
 * minimized/backgrounded (not just while it's the active foreground app,
 * which the dashboard's own refreshNotifications() calls already cover).
 * Android's WorkManager enforces a 15-minute minimum interval for periodic
 * work on all OS versions - this is not instant/true push (that needs a
 * server-side push service, e.g. Firebase Cloud Messaging, which this
 * project doesn't have set up), but it's a real, working, no-new-
 * infrastructure way to still surface new notifications without the guest
 * having the app open.
 */
public class NotificationPollWorker extends Worker {

    private static final String UNIQUE_WORK_NAME = "velocity_suites_notification_poll";

    public NotificationPollWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Schedules the periodic poll if it isn't already scheduled - safe to call on every app launch. */
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                NotificationPollWorker.class,
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!SessionManager.isLoggedIn(context)) {
            // Nobody to fetch notifications for right now - not a failure, just nothing to do.
            return Result.success();
        }

        // RoomRepository's own refreshNotifications()/refreshBookings() are callback-based
        // (built for the UI thread); doWork() already runs on a background thread pool, so
        // bridging both to one blocking wait here is the standard WorkManager pattern
        // rather than re-implementing the fetch+mapping logic a second time. Both fetches
        // fire together (not sequentially) so this job doesn't take twice as long, and
        // Transaction History gets the same background freshness Notifications already had -
        // a guest who never reopens the app between visits still sees an up-to-date
        // booking/payment status the next time they check, not just new notifications.
        RoomRepository repository = RoomRepository.getInstance(context);
        CountDownLatch latch = new CountDownLatch(2);
        boolean[] notificationsSucceeded = {false};
        boolean[] bookingsSucceeded = {false};

        repository.refreshNotifications(new RoomRepository.RepositoryCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                // NotificationHelper.maybeAlertNewNotifications() already runs inside
                // refreshNotifications() itself (see RoomRepository) - nothing further
                // to do here beyond releasing the latch.
                notificationsSucceeded[0] = true;
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                latch.countDown();
            }
        });

        repository.refreshBookings(new RoomRepository.RepositoryCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> result) {
                bookingsSucceeded[0] = true;
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                latch.countDown();
            }
        });

        try {
            latch.await(25, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }

        // Retry only if BOTH failed - a partial success plus this job's own 15-minute
        // recurrence (and the foreground 30s polls whenever the guest has the app open,
        // which remain the primary freshness mechanism) is enough; no need to burn a
        // retry attempt over one of the two failing transiently.
        return (notificationsSucceeded[0] || bookingsSucceeded[0]) ? Result.success() : Result.retry();
    }
}
