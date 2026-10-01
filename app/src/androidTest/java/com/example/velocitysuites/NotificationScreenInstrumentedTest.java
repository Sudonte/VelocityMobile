package com.example.velocitysuites;

import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Regression coverage for the crash reported after the combo-box/card-redesign/
 * envelope-toggle/TouchDelegate commits: opening the Notifications screen crashed
 * immediately (bell icon -> app closes to the home screen). Plain JUnit tests never
 * open the screen at all (when this was written the project had no Robolectric; src/test/.../ui now covers the same ground on the JVM), so that regression
 * shipped with a fully green test suite - these instrumented tests actually inflate
 * item_notification.xml and drive NotificationAdapter/NotificationActivity on a
 * device/emulator, the same class of check that would have caught it.
 */
@RunWith(AndroidJUnit4.class)
public class NotificationScreenInstrumentedTest {

    /**
     * Exercises NotificationAdapter directly - onCreateViewHolder (inflates
     * item_notification.xml, including its unread dot and the one-row footer's
     * View Transaction + Mark as read/unread actions), and onBindViewHolder
     * for one representative notification per category plus every read/unread and
     * with/without-referenceId combination - all with zero login/network
     * dependency, so this fails the same way whether or not a real backend session
     * is available.
     */
    @Test
    public void notificationAdapter_bindsEveryCategoryAndReadState_withoutCrashing() {
        try (ActivityScenario<NotificationActivity> scenario = ActivityScenario.launch(NotificationActivity.class)) {
            scenario.onActivity(activity -> {
                List<Notification> notifications = buildRepresentativeNotifications();
                RecyclerView recyclerView = new RecyclerView(activity);
                recyclerView.setLayoutManager(new LinearLayoutManager(activity));

                NotificationAdapter adapter = new NotificationAdapter(activity, new ArrayList<>(notifications),
                        new NotificationAdapter.OnNotificationClickListener() {
                            @Override public void onNotificationClick(Notification notification) { }
                            @Override public void onToggleReadClick(Notification notification) { }
                            @Override public void onViewTransactionDetailsClick(Notification notification) { }
                        });
                recyclerView.setAdapter(adapter);
                adapter.submitList(notifications);

                int widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
                int heightSpec = View.MeasureSpec.makeMeasureSpec(20000, View.MeasureSpec.AT_MOST);
                recyclerView.setLayoutParams(new ViewGroup.LayoutParams(1080, ViewGroup.LayoutParams.WRAP_CONTENT));
                recyclerView.measure(widthSpec, heightSpec);
                recyclerView.layout(0, 0, 1080, 20000);

                // Scrolling to the end forces every remaining row through
                // onCreateViewHolder/onBindViewHolder too, not just whatever fit in
                // the first measure/layout pass.
                recyclerView.scrollToPosition(notifications.size() - 1);
                recyclerView.measure(widthSpec, heightSpec);
                recyclerView.layout(0, 0, 1080, 20000);
            });
            // Lets anything the binds above posted to the main thread actually run, so a
            // failure it would cause surfaces inside this test instead of after it returns.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }

    /**
     * Launches the real screen exactly as the bell icon/drawer/push-notification
     * entry points do. ActivityScenario.launch() re-throws any exception the
     * Activity raises during its own lifecycle, so a crash in onCreate() itself -
     * regardless of whether a backend session is available - fails this test
     * instead of silently passing.
     */
    @Test
    public void notificationActivity_launches_withoutCrashing() {
        try (ActivityScenario<NotificationActivity> scenario = ActivityScenario.launch(NotificationActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }

    private static List<Notification> buildRepresentativeNotifications() {
        String[] types = {
                Notification.TYPE_BOOKING, Notification.TYPE_RESERVATION, Notification.TYPE_PAYMENT,
                Notification.TYPE_CHECK_IN, Notification.TYPE_PROMOTION, Notification.TYPE_SMS,
                Notification.TYPE_SYSTEM, Notification.TYPE_ANNOUNCEMENT
        };
        List<Notification> notifications = new ArrayList<>();
        int i = 0;
        for (String type : types) {
            // Alternates read/unread and with/without a referenceId (drives
            // NotificationPrimaryActionResolver.canViewTransaction() both ways, so
            // "View Transaction" is exercised both visible and GONE) and
            // titles chosen to also exercise NotificationStatusResolver's pill.
            boolean isRead = i % 2 == 0;
            String referenceId = i % 3 == 0 ? null : "REF-" + i;
            String title = i % 2 == 0 ? "Booking Confirmed" : "Payment Pending Validation";
            notifications.add(new Notification(
                    "notif-" + i, title, "Representative message body for a " + type + " notification.",
                    "2026-09-30T10:00:00Z", type, isRead, referenceId, null, "Sep 30, 2026 at 10:00 AM",
                    null, null, System.currentTimeMillis() - i * 3600_000L));
            i++;
        }
        // No publishedAt/createdAtMillis at all - the oldest, least-populated shape a
        // real backend row can still have (bindTime()'s relative-time fallback path).
        notifications.add(new Notification("notif-legacy", "System Notice", "Legacy row with minimal fields.",
                "2026-01-01T00:00:00Z", Notification.TYPE_SYSTEM, false));
        return notifications;
    }
}
