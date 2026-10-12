package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.R;
import com.example.velocitysuites.ReceiptImageViewerActivity;
import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.UpcomingTransactionsActivity;
import com.example.velocitysuites.network.dto.NotificationDto;
import com.example.velocitysuites.network.dto.PaginatedResponse;
import com.example.velocitysuites.ui.ScreenTestSupport.FakeApi;
import com.example.velocitysuites.ui.ScreenTestSupport.FakeCall;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Field;

/**
 * The header bell's dropdown (latest five, unread dots, empty text, tap marks read, closes on pause) and the
 * full-screen GCash receipt viewer's "no image" path.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NotificationPopupAndReceiptViewerTest {

    private Context app;
    private FakeApi api;
    private RoomRepository repository;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new FakeApi();
        repository = ScreenTestSupport.freshRepository(app, api);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private ActivityController<UpcomingTransactionsActivity> launch() {
        ActivityController<UpcomingTransactionsActivity> controller =
                Robolectric.buildActivity(UpcomingTransactionsActivity.class, new Intent(app, UpcomingTransactionsActivity.class));
        controller.setup();
        idle();
        return controller;
    }

    private void answerNotifications(int unread, NotificationDto... items) {
        FakeCall<PaginatedResponse<NotificationDto>> call = api.last("getNotifications");
        call.succeed(ScreenTestSupport.notificationPage(unread, items));
        idle();
    }

    private static PopupWindow popupOf(Activity activity) throws Exception {
        Field popupField = com.example.velocitysuites.BaseNavigationActivity.class.getDeclaredField("notificationPopup");
        popupField.setAccessible(true);
        Object popup = popupField.get(activity);
        if (popup == null) return null;
        Field windowField = popup.getClass().getDeclaredField("window");
        windowField.setAccessible(true);
        return (PopupWindow) windowField.get(popup);
    }

    private static NotificationDto[] seven() {
        NotificationDto[] items = new NotificationDto[7];
        for (int i = 0; i < 7; i++) {
            items[i] = ScreenTestSupport.notification(i + 1, "Notice " + (i + 1), "booking", 10L + i, i >= 3);
        }
        return items;
    }

    @Test
    public void bellOpensADropdownOfTheLatestFive_withUnreadDots_andViewAll() throws Exception {
        UpcomingTransactionsActivity activity = launch().get();
        activity.findViewById(R.id.headerNotificationContainer).performClick();
        idle();
        answerNotifications(3, seven());

        PopupWindow popup = popupOf(activity);
        assertNotNull("the bell opened a popup", popup);
        assertTrue(popup.isShowing());
        View content = popup.getContentView();
        LinearLayout rows = content.findViewById(R.id.layoutNotifPopupRows);
        assertEquals("only the five most recent", 5, rows.getChildCount());
        assertEquals("Notice 1", ((TextView) rows.getChildAt(0).findViewById(R.id.tvPopupNotifTitle)).getText().toString());
        assertEquals("an unread one has its dot", View.VISIBLE, rows.getChildAt(0).findViewById(R.id.unreadDotPopup).getVisibility());
        assertEquals("a read one does not", View.INVISIBLE, rows.getChildAt(4).findViewById(R.id.unreadDotPopup).getVisibility());
        assertEquals(View.VISIBLE, content.findViewById(R.id.btnNotifPopupViewAll).getVisibility());
        assertEquals(View.GONE, content.findViewById(R.id.tvNotifPopupEmpty).getVisibility());

        activity.findViewById(R.id.headerNotificationContainer).performClick();
        idle();
        assertFalse("tapping the bell again closes it", popupOf(activity) != null && popupOf(activity).isShowing());
    }

    @Test
    public void withNothingToShow_itSaysNoNewNotifications() throws Exception {
        UpcomingTransactionsActivity activity = launch().get();
        activity.findViewById(R.id.headerNotificationContainer).performClick();
        idle();
        answerNotifications(0);

        View content = popupOf(activity).getContentView();
        TextView empty = content.findViewById(R.id.tvNotifPopupEmpty);
        assertEquals(View.VISIBLE, empty.getVisibility());
        assertEquals("No new notifications", empty.getText().toString());
        assertEquals(View.GONE, content.findViewById(R.id.layoutNotifPopupRows).getVisibility());
    }

    @Test
    public void tappingARowMarksItReadAndClosesTheDropdown() throws Exception {
        UpcomingTransactionsActivity activity = launch().get();
        activity.findViewById(R.id.headerNotificationContainer).performClick();
        idle();
        answerNotifications(3, seven());
        assertEquals(3, repository.getUnreadNotificationCount());

        LinearLayout rows = popupOf(activity).getContentView().findViewById(R.id.layoutNotifPopupRows);
        rows.getChildAt(0).performClick();
        idle();

        assertEquals("the unread count dropped at once", 2, repository.getUnreadNotificationCount());
        PopupWindow after = popupOf(activity);
        assertFalse("the dropdown closed", after != null && after.isShowing());
    }

    @Test
    public void theDropdownClosesWhenTheScreenPauses() throws Exception {
        ActivityController<UpcomingTransactionsActivity> controller = launch();
        UpcomingTransactionsActivity activity = controller.get();
        activity.findViewById(R.id.headerNotificationContainer).performClick();
        idle();
        answerNotifications(3, seven());
        assertTrue(popupOf(activity).isShowing());

        controller.pause();
        idle();
        PopupWindow after = popupOf(activity);
        assertFalse("no window may outlive its screen", after != null && after.isShowing());
    }

    @Test
    public void theReceiptViewerWithNoImage_saysSoInsteadOfCrashing() {
        Intent intent = new Intent(app, ReceiptImageViewerActivity.class);
        ReceiptImageViewerActivity activity = Robolectric.buildActivity(ReceiptImageViewerActivity.class, intent).setup().get();
        TextView message = activity.findViewById(R.id.tvReceiptFullMessage);
        assertEquals(View.VISIBLE, message.getVisibility());
        assertEquals(activity.getString(R.string.receipt_image_missing), message.getText().toString());
        assertEquals(View.GONE, activity.findViewById(R.id.progressReceiptFull).getVisibility());

        activity.findViewById(R.id.btnCloseReceiptViewer).performClick();
        assertTrue("the close button finishes the viewer", activity.isFinishing());
    }
}
