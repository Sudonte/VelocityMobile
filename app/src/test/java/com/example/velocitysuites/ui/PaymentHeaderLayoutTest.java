package com.example.velocitysuites.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.PaymentActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.dto.RequestableAmenityDto;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.shape.MaterialShapeDrawable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The guest header on the Payment screen - its two states are "Review Billing" and "GCash Payment" - must
 * stay on top and fully visible while the guest scrolls: the scrolling content goes UNDER it.
 * <p>
 * What went wrong (root cause): payment.xml's scroller was a direct child of a CoordinatorLayout with
 * {@code clipChildren="false"} AND had {@code clipToPadding="false"} itself, so nothing clipped the scrolled
 * cards to the scroller's viewport - they painted above its top edge, into the header's strip. The AppBar's
 * 8dp elevation used to paint the header over that overflow; the flat/shadowless pass set it to 0, so the
 * AppBar and the scroller tied on Z and the scroller (declared later) painted over the header.
 * <p>
 * Two kinds of check, per state: the DRAWN result (the header strip's pixels must not change when the
 * content scrolls - this is the check that actually caught the bug) and the STRUCTURE that guarantees it
 * (Z order, opaque background, no shadow, the scroller starting exactly at the header's bottom edge).
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PaymentHeaderLayoutTest {

    private static final int WIDTH_PX = 1080;
    private static final int HEIGHT_PX = 2400;

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
    }

    // ---- helpers ----

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Lets the screen's entry animation (alpha/translation, 280ms) finish. */
    private static void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600));
    }

    private static void layoutAgain(View decor) {
        decor.measure(View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, WIDTH_PX, HEIGHT_PX);
    }

    private static int[] topStripPixels(View decor, int stripHeightPx) {
        layoutAgain(decor);
        Bitmap bitmap = Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        int[] pixels = new int[WIDTH_PX * stripHeightPx];
        bitmap.getPixels(pixels, 0, WIDTH_PX, 0, 0, WIDTH_PX, stripHeightPx);
        return pixels;
    }

    /** A real finger drag, so the AppBar's nested-scroll collapse runs exactly as it does on a phone. */
    private static void drag(View decor, float fromY, float toY) {
        long t = SystemClock.uptimeMillis();
        decor.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 540f, fromY, 0));
        int steps = 12;
        for (int i = 1; i <= steps; i++) {
            float y = fromY + (toY - fromY) * i / steps;
            decor.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 16L, MotionEvent.ACTION_MOVE, 540f, y, 0));
        }
        decor.dispatchTouchEvent(MotionEvent.obtain(t, t + 400, MotionEvent.ACTION_UP, 540f, toY, 0));
    }

    /** Payment screen for an existing reservation, bookings loaded: shows the "Review Billing" step. */
    private PaymentActivity launchReviewBilling() {
        Intent intent = new Intent(app, PaymentActivity.class).putExtra("BOOKING_ID", "588");
        ActivityController<PaymentActivity> controller = Robolectric.buildActivity(PaymentActivity.class, intent);
        controller.setup();
        idle();
        ScreenTestSupport.answerBookingLoad(api, 0,
                ScreenTestSupport.reservations(ScreenTestSupport.reservation(588, 1800)),
                ScreenTestSupport.directBookings());
        idle();
        if (api.count("getRequestableAmenities") > 0) {
            ScreenTestSupport.FakeCall<List<RequestableAmenityDto>> amenities = api.call("getRequestableAmenities", 0);
            amenities.succeed(new ArrayList<>());
            idle();
        }
        settle();
        return controller.get();
    }

    /** Same, then through "Show GCash QR Code" -> confirm: shows the "GCash Payment" step. */
    private PaymentActivity launchGcashPayment() {
        PaymentActivity activity = launchReviewBilling();
        activity.findViewById(R.id.proceedToGcashButton).performClick();
        idle();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("the amount confirmation dialog should be showing", dialog);
        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        settle();
        assertEquals("the GCash step is the visible one", View.VISIBLE, activity.findViewById(R.id.gcashPortalSection).getVisibility());
        return activity;
    }

    /** Collapses the app bar to its pinned header - the position where the header is all that sits above the content. */
    private static int collapseHeader(PaymentActivity activity, String state) {
        View decor = activity.getWindow().getDecorView();
        AppBarLayout appBar = activity.findViewById(R.id.appBarLayout);
        NestedScrollView scroll = activity.findViewById(R.id.screenContent);
        layoutAgain(decor);
        appBar.setExpanded(false, false);
        idle();
        layoutAgain(decor);
        assertTrue(state + ": the header has a real height", appBar.getBottom() > 0);
        assertTrue(state + ": the content must be taller than the viewport for scrolling to mean anything",
                scroll.getChildAt(0).getHeight() > scroll.getHeight());
        return appBar.getBottom();
    }

    /** The drawn result: the strip the header occupies must not change at all while the content scrolls under it. */
    private static void assertContentNeverPaintsIntoTheHeader(PaymentActivity activity, String state) {
        View decor = activity.getWindow().getDecorView();
        NestedScrollView scroll = activity.findViewById(R.id.screenContent);
        int headerBottom = collapseHeader(activity, state);

        int[] atRest = topStripPixels(decor, headerBottom);

        scroll.scrollTo(0, 500);
        idle();
        assertArrayEquals(state + ": scrolling the content must not paint into the header",
                atRest, topStripPixels(decor, headerBottom));

        drag(decor, 1800f, 1100f);
        idle();
        AppBarLayout appBar = activity.findViewById(R.id.appBarLayout);
        assertTrue(state + ": the drag actually scrolled the content", scroll.getScrollY() > 500);
        assertArrayEquals(state + ": a real finger drag must not paint content into the header",
                atRest, topStripPixels(decor, appBar.getBottom()));
    }

    /** The structure that guarantees it. */
    private static void assertHeaderStructure(PaymentActivity activity, String state) {
        AppBarLayout appBar = activity.findViewById(R.id.appBarLayout);
        NestedScrollView scroll = activity.findViewById(R.id.screenContent);
        int headerBottom = collapseHeader(activity, state);

        assertEquals(state + ": the scrolling content starts exactly at the header's bottom edge", headerBottom, scroll.getTop());
        assertTrue(state + ": the header must paint above the content (header Z " + appBar.getZ() + " vs content Z " + scroll.getZ() + ")",
                appBar.getZ() > scroll.getZ());
        assertTrue(state + ": the header background must be opaque - content may never show through it", isOpaque(appBar.getBackground()));
        assertFalse(state + ": raising the header's Z must not bring a shadow back", castsShadow(appBar));
        assertNull(state + ": no lift animator may raise the header's elevation later", appBar.getStateListAnimator());

        // The root cause itself: the scroller must not be allowed to paint outside its own viewport.
        ViewGroup parent = (ViewGroup) scroll.getParent();
        assertTrue(state + ": the scroller's parent must clip its children (clipChildren=false lets content overflow)",
                parent.getClipChildren());
        assertTrue(state + ": the scroller must clip to its own viewport (clipToPadding=false lets content overflow)",
                scroll.getClipToPadding());
    }

    /**
     * AppBarLayout swaps a plain colour background for a MaterialShapeDrawable (which always reports
     * TRANSLUCENT from getOpacity()), so look at what it actually fills with.
     */
    private static boolean isOpaque(Drawable background) {
        if (background == null) return false;
        if (background instanceof MaterialShapeDrawable) {
            MaterialShapeDrawable shape = (MaterialShapeDrawable) background;
            return shape.getFillColor() != null && Color.alpha(shape.getFillColor().getDefaultColor()) == 255 && shape.getAlpha() == 255;
        }
        return background.getOpacity() == PixelFormat.OPAQUE;
    }

    /** A shadow needs elevation above zero AND an outline with some opacity - take either away and there is none. */
    private static boolean castsShadow(View view) {
        if (view.getZ() <= 0f || view.getOutlineProvider() == null) return false;
        Outline outline = new Outline();
        view.getOutlineProvider().getOutline(view, outline);
        return !outline.isEmpty() && outline.getAlpha() > 0f;
    }

    // ---- tests ----

    @Test
    public void reviewBilling_contentNeverPaintsIntoTheHeader() {
        assertContentNeverPaintsIntoTheHeader(launchReviewBilling(), "Review Billing");
    }

    @Test
    public void reviewBilling_headerIsOpaqueShadowlessAndAboveTheContent() {
        assertHeaderStructure(launchReviewBilling(), "Review Billing");
    }

    @Test
    public void gcashPayment_contentNeverPaintsIntoTheHeader() {
        assertContentNeverPaintsIntoTheHeader(launchGcashPayment(), "GCash Payment");
    }

    @Test
    public void gcashPayment_headerIsOpaqueShadowlessAndAboveTheContent() {
        assertHeaderStructure(launchGcashPayment(), "GCash Payment");
    }
}
