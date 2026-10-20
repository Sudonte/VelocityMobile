package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

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

    /** Ten notifications, the first three unread - more than the popup shows. */
    private static NotificationDto[] seven() {
        NotificationDto[] items = new NotificationDto[10];
        for (int i = 0; i < 10; i++) {
            items[i] = ScreenTestSupport.notification(i + 1, "Notice " + (i + 1), "booking", 10L + i, i >= 3);
        }
        return items;
    }

    @Test
    public void bellOpensADropdownOfTheLatestEight_withUnreadDots_andViewAll() throws Exception {
        UpcomingTransactionsActivity activity = launch().get();
        activity.findViewById(R.id.headerNotificationContainer).performClick();
        idle();
        answerNotifications(3, seven());

        PopupWindow popup = popupOf(activity);
        assertNotNull("the bell opened a popup", popup);
        assertTrue(popup.isShowing());
        View content = popup.getContentView();
        LinearLayout rows = content.findViewById(R.id.layoutNotifPopupRows);
        assertEquals("only the eight most recent", 8, rows.getChildCount());
        assertEquals("Notice 1", ((TextView) rows.getChildAt(0).findViewById(R.id.tvNotifTitle)).getText().toString());
        assertEquals("an unread one has its dot", View.VISIBLE, rows.getChildAt(0).findViewById(R.id.viewNotifUnreadDot).getVisibility());
        assertEquals("a read one does not", View.GONE, rows.getChildAt(4).findViewById(R.id.viewNotifUnreadDot).getVisibility());
        assertEquals("the header counts the unread", "3 unread", ((TextView) content.findViewById(R.id.tvNotifPopupUnread)).getText().toString());
        assertEquals(View.VISIBLE, content.findViewById(R.id.btnNotifPopupViewAll).getVisibility());
        assertEquals(View.GONE, content.findViewById(R.id.tvNotifPopupEmpty).getVisibility());

        activity.findViewById(R.id.headerNotificationContainer).performClick();
        idle();
        assertFalse("tapping the bell again closes it", popupOf(activity) != null && popupOf(activity).isShowing());
    }

    @Test
    public void withNothingToShow_itSaysAllCaughtUp() throws Exception {
        UpcomingTransactionsActivity activity = launch().get();
        activity.findViewById(R.id.headerNotificationContainer).performClick();
        idle();
        answerNotifications(0);

        View content = popupOf(activity).getContentView();
        TextView empty = content.findViewById(R.id.tvNotifPopupEmpty);
        assertEquals(View.VISIBLE, empty.getVisibility());
        assertEquals("You're all caught up", empty.getText().toString());
        assertEquals(View.GONE, content.findViewById(R.id.scrollNotifPopupRows).getVisibility());
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
    public void popupRows_shareTheRowDesign_roundIcon_boldUnreadTitle_dot_twoLinePreview_noLeftIndent() throws Exception {
        UpcomingTransactionsActivity activity = launch().get();
        activity.findViewById(R.id.headerNotificationContainer).performClick();
        idle();
        answerNotifications(3, seven());

        LinearLayout rows = popupOf(activity).getContentView().findViewById(R.id.layoutNotifPopupRows);
        View unread = rows.getChildAt(0);
        View read = rows.getChildAt(4);
        assertEquals("an unread title is bold", android.graphics.Typeface.BOLD, ((TextView) unread.findViewById(R.id.tvNotifTitle)).getTypeface().getStyle());
        assertEquals("a read title is regular", android.graphics.Typeface.NORMAL,
                ((TextView) read.findViewById(R.id.tvNotifTitle)).getTypeface() == null ? android.graphics.Typeface.NORMAL
                        : ((TextView) read.findViewById(R.id.tvNotifTitle)).getTypeface().getStyle());
        assertNotNull("a category icon circle on the left", unread.findViewById(R.id.frameNotifIcon));
        assertEquals("a title may wrap to two lines here", 2, ((TextView) unread.findViewById(R.id.tvNotifTitle)).getMaxLines());
        assertEquals("a two-line message preview", 2, ((TextView) unread.findViewById(R.id.tvNotifMessage)).getMaxLines());
        assertNull("no per-row Mark as read (the header has Mark all as read)", unread.findViewById(R.id.btnNotifOverflow));
    }

    @Test
    public void markAllAsRead_clearsEveryUnread_andHidesItself() throws Exception {
        UpcomingTransactionsActivity activity = launch().get();
        activity.findViewById(R.id.headerNotificationContainer).performClick();
        idle();
        answerNotifications(3, seven());

        View content = popupOf(activity).getContentView();
        assertEquals(View.VISIBLE, content.findViewById(R.id.btnNotifPopupMarkAll).getVisibility());
        content.findViewById(R.id.btnNotifPopupMarkAll).performClick();
        idle();

        assertEquals(0, repository.getUnreadNotificationCount());
        assertTrue("still open", popupOf(activity).isShowing());
        content = popupOf(activity).getContentView();
        assertEquals(View.GONE, content.findViewById(R.id.btnNotifPopupMarkAll).getVisibility());
        assertEquals(View.GONE, content.findViewById(R.id.tvNotifPopupUnread).getVisibility());
        LinearLayout rows = content.findViewById(R.id.layoutNotifPopupRows);
        for (int i = 0; i < rows.getChildCount(); i++) {
            assertEquals("every row repainted as read", View.GONE, rows.getChildAt(i).findViewById(R.id.viewNotifUnreadDot).getVisibility());
        }
    }

    @Test
    public void theCloseButtonClosesThePopup() throws Exception {
        UpcomingTransactionsActivity activity = launch().get();
        activity.findViewById(R.id.headerNotificationContainer).performClick();
        idle();
        answerNotifications(3, seven());

        popupOf(activity).getContentView().findViewById(R.id.btnNotifPopupClose).performClick();
        idle();
        PopupWindow after = popupOf(activity);
        assertFalse(after != null && after.isShowing());
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
