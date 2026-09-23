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

        // RoomRepository's own refreshNotifications() is callback-based (built for the
        // UI thread); doWork() already runs on a background thread pool, so bridging it
        // to a blocking call here is the standard WorkManager pattern rather than
        // re-implementing the fetch+mapping logic a second time.
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] succeeded = {false};

        RoomRepository.getInstance(context).refreshNotifications(new RoomRepository.RepositoryCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                // NotificationHelper.maybeAlertNewNotifications() already runs inside
                // refreshNotifications() itself (see RoomRepository) - nothing further
                // to do here beyond releasing the latch.
                succeeded[0] = true;
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

        return succeeded[0] ? Result.success() : Result.retry();
    }
}
