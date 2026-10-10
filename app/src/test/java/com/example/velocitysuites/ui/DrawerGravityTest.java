package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.DashboardActivity;
import com.example.velocitysuites.R;
import com.google.android.material.navigation.NavigationView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The hamburger sits on the LEFT of the toolbar, so the navigation drawer must slide in from the LEFT (start) edge,
 * and close again on back press.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DrawerGravityTest {

    private DashboardActivity activity;

    @Before
    public void setUp() {
        Context app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        ScreenTestSupport.goOnline(app);
        com.example.velocitysuites.network.SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes",
                "ana@example.com", "09171234567", "Female", "Jan 01, 1990", "");
        ActivityController<DashboardActivity> controller = Robolectric.buildActivity(DashboardActivity.class);
        controller.setup();
        shadowOf(Looper.getMainLooper()).idle();
        activity = controller.get();
    }

    /** A DrawerLayout only advances its slide while it is being drawn, so draw it frame by frame. */
    private static void drawFrames(View decor, android.util.DisplayMetrics dm, int frames) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        for (int i = 0; i < frames; i++) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));
            decor.draw(canvas);
        }
    }

    @Test
    public void theNavigationViewIsAnchoredToTheStartEdge() {
        NavigationView nav = activity.findViewById(R.id.navigationView);
        DrawerLayout.LayoutParams lp = (DrawerLayout.LayoutParams) nav.getLayoutParams();
        assertEquals(GravityCompat.START, lp.gravity);
    }

    @Test
    public void theMenuButtonOpensTheDrawerFromTheLeft_andBackClosesIt() {
        DrawerLayout drawer = activity.findViewById(R.id.drawerLayout);
        View menu = activity.findViewById(R.id.menuButtonControl);
        assertNotNull(menu);
        assertFalse(drawer.isDrawerOpen(GravityCompat.START));

        // Lay the window out first: a DrawerLayout that was never measured cannot slide anything in.
        View decor = activity.getWindow().getDecorView();
        android.util.DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        decor.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, dm.widthPixels, dm.heightPixels);

        menu.performClick();
        drawFrames(decor, dm, 30); // the drawer slides in
        assertTrue("the drawer opened on the start (left) edge", drawer.isDrawerOpen(GravityCompat.START));
        assertFalse("and not on the end (right) edge", drawer.isDrawerOpen(GravityCompat.END));

        activity.getOnBackPressedDispatcher().onBackPressed();
        drawFrames(decor, dm, 30); // the close slides rather than snapping
        assertFalse("back closes it", drawer.isDrawerOpen(GravityCompat.START));
    }
}
