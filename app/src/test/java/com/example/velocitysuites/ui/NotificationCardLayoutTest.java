package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Notification;
import com.example.velocitysuites.NotificationAdapter;
import com.example.velocitysuites.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Collections;

/**
 * The Notifications card (item_notification.xml), the Dashboard row and the bell popup row (item_notification_row.xml),
 * inflated and bound by the real adapter / binder and measured for real at 360dp and 393dp - in light and in dark mode, and
 * at large system fonts: every label is where it should be, nothing overlaps, nothing pokes out of its card, and the touch
 * targets are big enough.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NotificationCardLayoutTest {

    @org.junit.Before
    public void freshRepository() {
        ScreenTestSupport.freshRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext(), new ScreenTestSupport.FakeApi());
    }

    private static final int[] WIDTHS_DP = {320, 360, 393, 411};
    private static final float[] FONT_SCALES = {1.0f, 1.3f, 1.5f};

    private static Notification amenity(boolean read) {
        return new Notification("1", "Amenity Request Approved",
                "Your request for 1 x Additional Pillow (Room 1-001) (₱50.00) was approved and added to your bill.",
                "x", Notification.TYPE_BOOKING, read, "567", null, "Oct 20, 2026 • 1:20 AM", null, null,
                System.currentTimeMillis() - 24 * 60_000L);
    }

    private static Notification promotion() {
        return new Notification("2", "New Promotion", "Enjoy 10% off your next stay with us this weekend.",
                "x", Notification.TYPE_PROMOTION, false, null, null, "", null, null, System.currentTimeMillis() - 30 * 3600_000L);
    }

    private static Notification longTitleAndMessage() {
        return new Notification("3", "You Can Now Request Additional Amenities For Your Stay At Velocity Suites",
                "You are checked in. Open your booking and tap Request Additional Amenities to ask for extras for any of your rooms; "
                        + "each request is reviewed by the front desk and, once approved, is added to your bill so you pay everything at checkout.",
                "x", Notification.TYPE_CHECK_IN, false, "567", null, "", null, null, System.currentTimeMillis() - 5 * 60_000L);
    }

    private static NotificationAdapter.ViewHolder bind(Context context, Notification n, int widthDp) {
        NotificationAdapter adapter = new NotificationAdapter(context, Collections.singletonList(n), null);
        FrameLayout parent = LayoutHarness.parentOfWidth(context, widthDp);
        NotificationAdapter.ViewHolder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 0);
        LayoutHarness.layoutAtWidth(holder.itemView, context, widthDp);
        shadowOf(Looper.getMainLooper()).idle();
        LayoutHarness.layoutAtWidth(holder.itemView, context, widthDp);
        return holder;
    }

    private static int styleOf(TextView view) {
        Typeface tf = view.getTypeface();
        return tf == null ? Typeface.NORMAL : tf.getStyle();
    }

    private static String where(Context context, View root, float font, int width) {
        return "font=" + font + " width=" + width + "dp\n" + LayoutHarness.dump(context, root);
    }

    /** One card, measured: nothing overlaps, nothing leaves the card, the right things stay on one line. */
    private void assertCardFits(Notification n, int width, float font) {
        Context context = LayoutHarness.themedContext(font);
        NotificationAdapter.ViewHolder holder = bind(context, n, width);
        View root = holder.itemView;
        String ctx = where(context, root, font, width);
        float density = context.getResources().getDisplayMetrics().density;

        TextView title = root.findViewById(R.id.tvNotifTitle);
        TextView message = root.findViewById(R.id.tvNotifMessage);
        TextView time = root.findViewById(R.id.tvNotifTime);
        TextView category = root.findViewById(R.id.tvNotifCategory);
        View overflow = root.findViewById(R.id.btnNotifOverflow);
        View icon = root.findViewById(R.id.frameNotifIcon);
        View card = root.findViewById(R.id.cardNotification);

        assertEquals("time on one line: \"" + time.getText() + "\"\n" + ctx, 1, time.getLineCount());
        assertEquals("category chip on one line\n" + ctx, 1, category.getLineCount());
        assertTrue("title at most 2 lines\n" + ctx, title.getLineCount() <= 2);
        assertTrue("message at most 3 lines\n" + ctx, message.getLineCount() <= NotificationAdapter.COLLAPSED_MESSAGE_LINES);
        assertTrue("a non-empty title\n" + ctx, title.getText().length() > 0);

        assertFalse("title/time\n" + ctx, LayoutHarness.overlaps(root, title, time));
        assertFalse("message/time\n" + ctx, LayoutHarness.overlaps(root, message, time));
        assertFalse("title/icon\n" + ctx, LayoutHarness.overlaps(root, title, icon));
        assertFalse("message/overflow\n" + ctx, LayoutHarness.overlaps(root, message, overflow));
        assertFalse("time/overflow\n" + ctx, LayoutHarness.overlaps(root, time, overflow));
        assertFalse("title/message\n" + ctx, LayoutHarness.overlaps(root, title, message));
        assertFalse("message/chip\n" + ctx, LayoutHarness.overlaps(root, message, category));

        Rect cardBox = LayoutHarness.boundsIn(root, card);
        for (View child : new View[]{title, message, time, category, overflow, icon, root.findViewById(R.id.tvNotifLinked)}) {
            if (child.getVisibility() != View.VISIBLE) continue;
            Rect r = LayoutHarness.boundsIn(root, child);
            assertTrue("inside the card: " + child.getClass().getSimpleName() + " " + r + " vs " + cardBox + "\n" + ctx, cardBox.contains(r));
        }
        Rect o = LayoutHarness.boundsIn(root, overflow);
        assertTrue("overflow touch target >= 48dp\n" + ctx, o.width() >= 48 * density - 1 && o.height() >= 48 * density - 1);
        assertEquals("icon circle is 36dp", 36f, icon.getWidth() / density, 0.5f);
        assertEquals(36f, icon.getHeight() / density, 0.5f);
    }

    @Test
    public void cardFits_lightMode_atEveryWidthAndFont() {
        for (float font : FONT_SCALES) {
            for (int width : WIDTHS_DP) {
                assertCardFits(amenity(false), width, font);
                assertCardFits(amenity(true), width, font);
                assertCardFits(promotion(), width, font);
                assertCardFits(longTitleAndMessage(), width, font);
            }
        }
    }

    @Test
    @Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xxhdpi", application = TestApplication.class)
    public void cardFits_darkMode_atEveryWidthAndFont() {
        for (float font : FONT_SCALES) {
            for (int width : WIDTHS_DP) {
                assertCardFits(amenity(false), width, font);
                assertCardFits(promotion(), width, font);
                assertCardFits(longTitleAndMessage(), width, font);
            }
        }
    }

    @Test
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-xxhdpi", application = TestApplication.class)
    public void cardFits_at393dpDevice() {
        assertCardFits(amenity(false), 393, 1.0f);
        assertCardFits(longTitleAndMessage(), 393, 1.0f);
    }

    // ---- the time ----

    @Test
    public void timeIsShortAndInNormalCase() {
        Context context = LayoutHarness.themedContext(1.0f);
        TextView time = bind(context, amenity(false), 360).itemView.findViewById(R.id.tvNotifTime);
        assertEquals("24m ago", time.getText().toString());
        assertFalse("not ALL CAPS", time.isAllCaps());
    }

    // ---- icon and chip follow the type ----

    @Test
    public void iconAndChipFollowTheNotificationType() {
        Context context = LayoutHarness.themedContext(1.0f);
        Object[][] cases = {
                {"Amenity Request Approved", Notification.TYPE_BOOKING, R.drawable.ic_amenity_concierge, "Amenities"},
                {"You Can Now Request Additional Amenities", Notification.TYPE_CHECK_IN, R.drawable.ic_amenity_concierge, "Amenities"},
                {"Payment Verified", Notification.TYPE_PAYMENT, R.drawable.ic_payment_card, "Payment"},
                {"Booking Confirmed", Notification.TYPE_BOOKING, R.drawable.ic_booking, "Booking"},
                {"Reservation Confirmed", Notification.TYPE_RESERVATION, R.drawable.ic_reservation, "Reservation"},
                {"Checked In", Notification.TYPE_CHECK_IN, R.drawable.ic_clock, "Check-in"},
                {"New Promotion", Notification.TYPE_PROMOTION, R.drawable.ic_star, "Promotion"},
                {"Maintenance", Notification.TYPE_SYSTEM, R.drawable.ic_notifications, "System"},
        };
        for (Object[] c : cases) {
            Notification n = new Notification("9", (String) c[0], "msg", "x", (String) c[1], true, null, null, "", null, null, System.currentTimeMillis());
            NotificationAdapter.ViewHolder holder = bind(context, n, 360);
            assertEquals("\"" + c[0] + "\" icon", (int) (Integer) c[2], holder.getBoundIconRes());
            assertEquals("\"" + c[0] + "\" chip", c[3], ((TextView) holder.itemView.findViewById(R.id.tvNotifCategory)).getText().toString());
        }
    }

    @Test
    public void everyCategoryUsesTheSameRoundSoftCircle() {
        for (String type : new String[]{Notification.TYPE_BOOKING, Notification.TYPE_RESERVATION, Notification.TYPE_PAYMENT, Notification.TYPE_CHECK_IN,
                Notification.TYPE_PROMOTION, Notification.TYPE_SYSTEM, Notification.TYPE_SMS, Notification.TYPE_ANNOUNCEMENT}) {
            com.example.velocitysuites.NotificationCategoryPresenter.Result r = com.example.velocitysuites.NotificationCategoryPresenter.resolve(type);
            assertEquals(type + " circle", R.color.velocity_red_soft, r.bgColorRes);
            assertEquals(type + " icon", R.color.velocity_red_primary, r.fgColorRes);
        }
    }

    @Test
    public void theLinkedIdShowsOnlyForANotificationAboutATransaction() {
        Context context = LayoutHarness.themedContext(1.0f);
        View withLink = bind(context, amenity(false), 360).itemView;
        assertEquals("Booking #567", ((TextView) withLink.findViewById(R.id.tvNotifLinked)).getText().toString());
        View without = bind(context, promotion(), 360).itemView;
        assertEquals(View.GONE, without.findViewById(R.id.tvNotifLinked).getVisibility());
    }

    // ---- unread vs read ----

    @Test
    public void unreadCardHasDotAndBoldTitle_readCardHasNeither() {
        Context context = LayoutHarness.themedContext(1.0f);
        View unread = bind(context, amenity(false), 360).itemView;
        assertEquals(View.VISIBLE, unread.findViewById(R.id.viewNotifUnreadDot).getVisibility());
        assertEquals(Typeface.BOLD, styleOf(unread.findViewById(R.id.tvNotifTitle)));
        View read = bind(context, amenity(true), 360).itemView;
        assertEquals(View.GONE, read.findViewById(R.id.viewNotifUnreadDot).getVisibility());
        assertEquals(Typeface.NORMAL, styleOf(read.findViewById(R.id.tvNotifTitle)));
    }

    // ---- a long message: 3 lines and "More" ----

    @Test
    public void aLongMessageShowsThreeLinesAndMore_aShortOneHasNoMore() {
        Context context = LayoutHarness.themedContext(1.0f);
        NotificationAdapter.ViewHolder longOne = bind(context, longTitleAndMessage(), 360);
        assertEquals(NotificationAdapter.COLLAPSED_MESSAGE_LINES, ((TextView) longOne.itemView.findViewById(R.id.tvNotifMessage)).getLineCount());
        assertEquals("More", ((TextView) longOne.itemView.findViewById(R.id.tvNotifMore)).getText().toString());
        assertEquals(View.VISIBLE, longOne.itemView.findViewById(R.id.tvNotifMore).getVisibility());

        View shortOne = bind(context, promotion(), 360).itemView;
        assertEquals(View.GONE, shortOne.findViewById(R.id.tvNotifMore).getVisibility());
    }

    @Test
    public void groupHeadersAreTitleCase_notAllCaps() {
        Context context = LayoutHarness.themedContext(1.0f);
        TextView header = bind(context, amenity(false), 360).itemView.findViewById(R.id.tvNotificationDateGroup);
        assertEquals("Today", header.getText().toString());
        assertFalse(header.isAllCaps());
        assertNotNull(header);
    }
}
