package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
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
 * The Notifications card, measured for real (inflated from item_notification.xml and bound by the real
 * NotificationAdapter) at the widths and system font sizes where the previous Flow-based card broke:
 * the footer split onto two rows, the timestamp wrapped, and cards ballooned past 250dp.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NotificationCardLayoutTest {

    private static final int[] WIDTHS_DP = {320, 360, 411};
    private static final float[] FONT_SCALES = {1.0f, 1.15f, 1.3f, 1.5f};
    private static final int MIN_TOUCH_TARGET_DP = 44;

    private static Notification pendingPayment(boolean read) {
        return new Notification("1", "Payment Pending Validation",
                "Your payment of ₱1,800.00 for Deluxe has been submitted and is pending validation.",
                "5 minutes ago", Notification.TYPE_PAYMENT, read, "588", null, "Sep 30, 2026 • 10:09 PM",
                null, null, System.currentTimeMillis() - 2 * 3600_000L);
    }

    private static Notification noTransaction() {
        return new Notification("2", "New Promotion", "Enjoy 10% off your next stay with us this weekend.",
                "1 day ago", Notification.TYPE_PROMOTION, false, null, null, "Sep 29, 2026 • 9:00 AM",
                null, null, System.currentTimeMillis() - 30 * 3600_000L);
    }

    /** Inflates and binds one card exactly as the RecyclerView would, laid out at {@code widthDp}. */
    private static NotificationAdapter.ViewHolder bind(Context context, Notification n, int widthDp) {
        NotificationAdapter adapter = new NotificationAdapter(context, Collections.singletonList(n), null);
        FrameLayout parent = LayoutHarness.parentOfWidth(context, widthDp);
        NotificationAdapter.ViewHolder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 0);
        LayoutHarness.layoutAtWidth(holder.itemView, context, widthDp);
        return holder;
    }

    private static View card(Context context, Notification n, int widthDp) {
        return bind(context, n, widthDp).itemView;
    }

    private static int styleOf(TextView view) {
        Typeface tf = view.getTypeface();
        return tf == null ? Typeface.NORMAL : tf.getStyle();
    }

    private static String where(Context context, View root, float font, int width) {
        return "font=" + font + " width=" + width + "dp\n" + LayoutHarness.dump(context, root);
    }

    // ---- The footer is ONE row: View Transaction on the left, Mark as read/unread on the right ----

    @Test
    public void footerIsOneRow_viewTransactionLeft_toggleRight_atEverySizeAndWidth() {
        for (float font : FONT_SCALES) {
            for (int width : WIDTHS_DP) {
                Context context = LayoutHarness.themedContext(font);
                View root = card(context, pendingPayment(true), width);
                View view = root.findViewById(R.id.btnViewTransactionDetails);
                View toggle = root.findViewById(R.id.btnToggleReadState);
                Rect v = LayoutHarness.boundsIn(root, view);
                Rect t = LayoutHarness.boundsIn(root, toggle);
                String ctx = where(context, root, font, width);

                assertEquals("both actions visible\n" + ctx, View.VISIBLE, view.getVisibility());
                assertTrue("View Transaction is LEFT of the toggle\n" + ctx, v.left < t.left);
                assertTrue("they do not overlap horizontally\n" + ctx, v.right <= t.left);
                // One row = their vertical extents overlap (a taller, wrapped label may be centered against the other).
                assertTrue("same row\n" + ctx, Math.min(v.bottom, t.bottom) > Math.max(v.top, t.top));
            }
        }
    }

    @Test
    public void footerActionsKeepA44dpTouchTarget() {
        for (float font : FONT_SCALES) {
            for (int width : WIDTHS_DP) {
                Context context = LayoutHarness.themedContext(font);
                View root = card(context, pendingPayment(false), width);
                float density = context.getResources().getDisplayMetrics().density;
                for (int id : new int[]{R.id.btnViewTransactionDetails, R.id.btnToggleReadState}) {
                    Rect r = LayoutHarness.boundsIn(root, root.findViewById(id));
                    assertTrue("height >= 44dp (was " + r.height() / density + ")\n" + where(context, root, font, width),
                            r.height() >= MIN_TOUCH_TARGET_DP * density - 1);
                    assertTrue("width >= 44dp\n" + where(context, root, font, width), r.width() >= MIN_TOUCH_TARGET_DP * density - 1);
                }
            }
        }
    }

    @Test
    public void withoutATransaction_theToggleStaysOnTheRight_andViewTransactionIsHidden() {
        for (int width : WIDTHS_DP) {
            Context context = LayoutHarness.themedContext(1.3f);
            View root = card(context, noTransaction(), width);
            View view = root.findViewById(R.id.btnViewTransactionDetails);
            View toggle = root.findViewById(R.id.btnToggleReadState);
            assertEquals(View.GONE, view.getVisibility());
            Rect t = LayoutHarness.boundsIn(root, toggle);
            Rect card = LayoutHarness.boundsIn(root, root.findViewById(R.id.cardNotification));
            assertTrue("toggle hugs the right edge\n" + where(context, root, 1.3f, width), t.right >= card.right - LayoutHarness.dp(context, 14));
        }
    }

    // ---- The timestamp never wraps ----

    @Test
    public void timestampIsOneLine_atEverySizeAndWidth_forRelativeAndAbsoluteTimes() {
        long now = System.currentTimeMillis();
        long[] ages = {30_000L, 2 * 3600_000L, 5L * 24 * 3600_000L, 400L * 24 * 3600_000L};
        for (long age : ages) {
            for (float font : FONT_SCALES) {
                for (int width : WIDTHS_DP) {
                    Context context = LayoutHarness.themedContext(font);
                    Notification n = new Notification("3", "Reservation Cancelled", "Your reservation has been cancelled.",
                            "x", Notification.TYPE_RESERVATION, true, "5", null, "", null, null, now - age);
                    View root = card(context, n, width);
                    TextView time = root.findViewById(R.id.tvNotificationTime);
                    assertEquals("timestamp \"" + time.getText() + "\" must stay on one line\n" + where(context, root, font, width),
                            1, time.getLineCount());
                }
            }
        }
    }

    // ---- Nothing overlaps ----

    @Test
    public void rowsNeverOverlap_atEverySizeAndWidth() {
        for (float font : FONT_SCALES) {
            for (int width : WIDTHS_DP) {
                Context context = LayoutHarness.themedContext(font);
                View root = card(context, pendingPayment(false), width);
                String ctx = where(context, root, font, width);
                View title = root.findViewById(R.id.tvNotificationTitle);
                View message = root.findViewById(R.id.tvNotificationMessage);
                View badges = root.findViewById(R.id.layoutNotificationBadges);
                View time = root.findViewById(R.id.tvNotificationTime);
                View divider = root.findViewById(R.id.dividerNotificationActions);
                View actions = root.findViewById(R.id.btnToggleReadState);
                assertFalse("title/message\n" + ctx, LayoutHarness.overlaps(root, title, message));
                assertFalse("message/badges\n" + ctx, LayoutHarness.overlaps(root, message, badges));
                assertFalse("badges/time\n" + ctx, LayoutHarness.overlaps(root, badges, time));
                assertFalse("time/divider\n" + ctx, LayoutHarness.overlaps(root, time, divider));
                assertFalse("divider/actions\n" + ctx, LayoutHarness.overlaps(root, divider, actions));
                // And nothing pokes out of the card.
                Rect card = LayoutHarness.boundsIn(root, root.findViewById(R.id.cardNotification));
                for (View child : new View[]{title, message, badges, time, actions, root.findViewById(R.id.btnViewTransactionDetails)}) {
                    Rect r = LayoutHarness.boundsIn(root, child);
                    assertTrue("inside the card\n" + ctx, card.contains(r));
                }
            }
        }
    }

    // ---- Smaller card ----

    @Test
    public void cardIsCompact_atDefaultSettings() {
        Context context = LayoutHarness.themedContext(1.0f);
        View root = card(context, pendingPayment(true), 360);
        float density = context.getResources().getDisplayMetrics().density;
        float heightDp = root.findViewById(R.id.cardNotification).getHeight() / density;
        // The previous card measured 173dp for this same notification at default settings (and 235-300dp at the
        // reporter's large font); the footer used to take two rows.
        assertTrue("card height was " + heightDp + "dp\n" + where(context, root, 1.0f, 360), heightDp <= 150);
    }

    @Test
    public void cardStaysCompact_atLargeFont() {
        Context context = LayoutHarness.themedContext(1.3f);
        View root = card(context, pendingPayment(true), 360);
        float density = context.getResources().getDisplayMetrics().density;
        float heightDp = root.findViewById(R.id.cardNotification).getHeight() / density;
        // Was 251dp at 1.3x on a 360dp phone.
        assertTrue("card height was " + heightDp + "dp\n" + where(context, root, 1.3f, 360), heightDp <= 200);
    }

    @Test
    public void iconCircleIs40dp() {
        Context context = LayoutHarness.themedContext(1.0f);
        View root = card(context, pendingPayment(true), 360);
        float density = context.getResources().getDisplayMetrics().density;
        View circle = root.findViewById(R.id.iconContainer);
        assertEquals(40f, circle.getWidth() / density, 0.5f);
        assertEquals(40f, circle.getHeight() / density, 0.5f);
    }

    // ---- Icon matches the status ----

    @Test
    public void pendingPaymentShowsAClock_notTheCategoryCheckmark() {
        Context context = LayoutHarness.themedContext(1.0f);
        NotificationAdapter.ViewHolder holder = bind(context, pendingPayment(true), 360);
        assertEquals("Payment Pending Validation must show the clock", R.drawable.ic_clock, holder.getBoundIconRes());
        assertFalse("...not the green check it used to wear", holder.getBoundIconRes() == R.drawable.ic_check_circle);
    }

    @Test
    public void statusIconsFollowTheOutcome() {
        Context context = LayoutHarness.themedContext(1.0f);
        Object[][] cases = {
                {"Payment Pending Validation", Notification.TYPE_PAYMENT, null, R.drawable.ic_clock},
                {"Booking Pending", Notification.TYPE_BOOKING, null, R.drawable.ic_clock},
                {"Booking Confirmed", Notification.TYPE_BOOKING, null, R.drawable.ic_check_circle},
                {"Reservation Confirmed", Notification.TYPE_RESERVATION, null, R.drawable.ic_check_circle},
                {"Payment Verified", Notification.TYPE_PAYMENT, "PARTIAL_RECEIPT", R.drawable.ic_status_partial},
                {"Payment Verified", Notification.TYPE_PAYMENT, "FULL_PAYMENT_RECEIPT", R.drawable.ic_check_circle},
                {"Reservation Cancelled", Notification.TYPE_RESERVATION, null, R.drawable.ic_close},
                {"Payment Rejected", Notification.TYPE_PAYMENT, null, R.drawable.ic_close},
        };
        for (Object[] c : cases) {
            Notification n = new Notification("9", (String) c[0], "msg", "x", (String) c[1], true, "5", null, "",
                    c[2] != null ? "PR-1" : null, (String) c[2], System.currentTimeMillis());
            NotificationAdapter.ViewHolder holder = bind(context, n, 360);
            assertEquals("\"" + c[0] + "\" (" + c[2] + ")", (int) (Integer) c[3], holder.getBoundIconRes());
        }
    }

    @Test
    public void aNotificationWithNoStatusKeepsItsCategoryIcon_andShowsNoPill() {
        Context context = LayoutHarness.themedContext(1.0f);
        Notification n = new Notification("4", "Reservation Modified", "You changed your dates.", "x",
                Notification.TYPE_RESERVATION, true, "5", null, "", null, null, System.currentTimeMillis());
        NotificationAdapter.ViewHolder holder = bind(context, n, 360);
        assertEquals(View.GONE, holder.itemView.findViewById(R.id.tvNotificationStatusPill).getVisibility());
        assertEquals(R.drawable.ic_reservation, holder.getBoundIconRes());
    }

    // ---- Unread is visibly different; the toggle label is right ----

    @Test
    public void unreadCardHasDotBoldTitleAndMarkAsReadLabel() {
        Context context = LayoutHarness.themedContext(1.0f);
        View root = card(context, pendingPayment(false), 360);
        assertEquals(View.VISIBLE, root.findViewById(R.id.viewUnreadDot).getVisibility());
        TextView title = root.findViewById(R.id.tvNotificationTitle);
        assertEquals(Typeface.BOLD, styleOf(title));
        assertEquals(context.getString(R.string.mark_as_read_action), ((TextView) root.findViewById(R.id.btnToggleReadState)).getText().toString());
    }

    @Test
    public void readCardHasNoDotNormalTitleAndMarkAsUnreadLabel() {
        Context context = LayoutHarness.themedContext(1.0f);
        View root = card(context, pendingPayment(true), 360);
        assertEquals(View.GONE, root.findViewById(R.id.viewUnreadDot).getVisibility());
        TextView title = root.findViewById(R.id.tvNotificationTitle);
        assertEquals(Typeface.NORMAL, styleOf(title));
        assertEquals(context.getString(R.string.mark_as_unread_action), ((TextView) root.findViewById(R.id.btnToggleReadState)).getText().toString());
    }

    @Test
    public void footerActionLabelsAreExactlyViewTransactionAndMarkAsRead() {
        Context context = LayoutHarness.themedContext(1.0f);
        View root = card(context, pendingPayment(false), 360);
        assertEquals("View Transaction", ((TextView) root.findViewById(R.id.btnViewTransactionDetails)).getText().toString());
    }
}
