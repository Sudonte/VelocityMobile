package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;

import androidx.core.widget.NestedScrollView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.LandingActivity;
import com.example.velocitysuites.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.Duration;

/**
 * The first view of the landing page is the hero only: Available Rooms starts below the fold on a small phone (360x640)
 * and a tall one (412x915), and Explore Now scrolls down to it.
 */
@RunWith(AndroidJUnit4.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LandingHeroFoldTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    @Before
    public void setUp() {
        Context app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        ScreenTestSupport.goOnline(app);
    }

    /** Opens the landing page, lays it out like a real window of the given size and returns the pieces. */
    private ActivityController<LandingActivity> open(int widthPx, int heightPx) {
        Context app = ApplicationProvider.getApplicationContext();
        ActivityController<LandingActivity> c = Robolectric.buildActivity(LandingActivity.class, new Intent(app, LandingActivity.class)).setup();
        shadowOf(Looper.getMainLooper()).idle();
        layout(c.get(), widthPx, heightPx);
        layout(c.get(), widthPx, heightPx); // the hero sizes itself from the first pass's scroller height
        return c;
    }

    private static void layout(LandingActivity a, int w, int h) {
        View decor = a.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
    }

    /** Top of a view inside the scroller's content, measured from the top of the scrolled content. */
    private static int topInScroller(View v, View scroller) {
        int top = 0;
        View cur = v;
        while (cur != null && cur != scroller) {
            top += cur.getTop();
            cur = (View) cur.getParent();
        }
        return top;
    }

    private void assertRoomsBelowTheFirstView(int widthDp, int heightDp) {
        float density = ApplicationProvider.getApplicationContext().getResources().getDisplayMetrics().density;
        ActivityController<LandingActivity> c = open(Math.round(widthDp * density), Math.round(heightDp * density));
        LandingActivity a = c.get();
        View scroller = a.findViewById(R.id.landingScroll);
        View rooms = a.findViewById(R.id.availableRoomsSection);
        int viewport = scroller.getHeight();
        assertTrue("the scroller has a height", viewport > 0);
        int roomsTop = topInScroller(rooms, scroller);
        assertTrue(widthDp + "x" + heightDp + ": rooms start at " + roomsTop + "px, first view ends at " + viewport + "px",
                roomsTop >= viewport - 2);
        c.pause().stop().destroy();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w360dp-h640dp-xxhdpi", application = TestApplication.class)
    public void onASmallPhone_theRoomsAreNotInTheFirstView() {
        assertRoomsBelowTheFirstView(360, 640);
    }

    @Test
    @Config(sdk = 35, qualifiers = "w412dp-h915dp-xxhdpi", application = TestApplication.class)
    public void onATallPhone_theRoomsAreNotInTheFirstView() {
        assertRoomsBelowTheFirstView(412, 915);
    }

    @Test
    @Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = TestApplication.class)
    public void onAMediumPhone_theRoomsAreNotInTheFirstView() {
        assertRoomsBelowTheFirstView(411, 891);
    }

    @Test
    @Config(sdk = 35, qualifiers = "w480dp-h1000dp-xxhdpi", application = TestApplication.class)
    public void onALargePhone_theRoomsAreNotInTheFirstView_andTheHeroFillsTheWindow() {
        assertRoomsBelowTheFirstView(480, 1000);
    }

    @Test
    @Config(sdk = 35, qualifiers = "w360dp-h640dp-xxhdpi", application = TestApplication.class)
    public void exploreNowScrollsDownToTheRooms() {
        float density = ApplicationProvider.getApplicationContext().getResources().getDisplayMetrics().density;
        ActivityController<LandingActivity> c = open(Math.round(360 * density), Math.round(640 * density));
        LandingActivity a = c.get();
        NestedScrollView scroller = a.findViewById(R.id.landingScroll);
        assertEquals("starts at the top", 0, scroller.getScrollY());

        a.findViewById(R.id.btnExploreNow).performClick();
        assertTrue("the page can scroll down to the rooms", scroller.canScrollVertically(1));
        // the smooth scroll is a frame animation: step the clock and the scroller's own computeScroll()
        for (int i = 0; i < 40; i++) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
            scroller.computeScroll();
        }
        assertTrue("it scrolled down (scrollY=" + scroller.getScrollY() + ")", scroller.getScrollY() > 0);
        c.pause().stop().destroy();
    }
}
