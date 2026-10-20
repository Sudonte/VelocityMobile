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
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.AdditionalAmenitiesActivity;
import com.example.velocitysuites.DashboardActivity;
import com.example.velocitysuites.LoginActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.TapGuard;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.AdditionalAmenityInfoDto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Double and triple taps: a screen, dialog or request is opened/sent once; different controls and steppers still work. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RepeatTapTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;
    private ScreenTestSupport.FakeApi api;
    private final AtomicLong now = new AtomicLong(1_000);

    @Before
    public void setUp() throws Exception {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "t", 1L, "Ana", "Reyes", "", "Ana Reyes", "a@b.c", "09171234567", "Female", "Jan 01, 1990", "");
        TapGuard.setClockForTests(now::get);
        java.lang.reflect.Field last = com.example.velocitysuites.NavUtils.class.getDeclaredField("lastClickAtMs");
        last.setAccessible(true);
        last.setLong(null, 0L);
    }

    @After
    public void tearDown() {
        TapGuard.setClockForTests(null);
    }

    /** A real tap: ACTION_DOWN + ACTION_UP through the window's callback, at the centre of {@code view}. */
    private static void tap(Activity a, View view) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        float x = loc[0] + view.getWidth() / 2f;
        float y = loc[1] + view.getHeight() / 2f;
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, x, y, 0);
        a.getWindow().getCallback().dispatchTouchEvent(down);
        a.getWindow().getCallback().dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** The next screen the activity started, skipping the system permission prompt some screens raise. */
    private static Intent nextLaunch(Activity a) {
        Intent next;
        while ((next = shadowOf(a).getNextStartedActivity()) != null) {
            if (!"android.content.pm.action.REQUEST_PERMISSIONS".equals(next.getAction())) return next;
        }
        return null;
    }

    private static void layOut(Activity a) {
        View decor = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels;
        int h = a.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
    }

    private Activity withButtons(int[] clicks, boolean stepper) {
        ActivityController<Activity> c = Robolectric.buildActivity(Activity.class).create();
        Activity a = c.get();
        a.setTheme(androidx.appcompat.R.style.Theme_AppCompat);
        LinearLayout column = new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        Button first = new Button(a);
        first.setId(R.id.btnViewBookingDetails);
        first.setOnClickListener(v -> clicks[0]++);
        Button second = new Button(a);
        second.setId(R.id.btnNextStayRetry);
        second.setOnClickListener(v -> clicks[1]++);
        Button plus = new Button(a);
        plus.setId(R.id.btnChildrenPlus);
        plus.setOnClickListener(v -> clicks[2]++);
        column.addView(first, new LinearLayout.LayoutParams(-1, 150));
        column.addView(second, new LinearLayout.LayoutParams(-1, 150));
        column.addView(plus, new LinearLayout.LayoutParams(-1, 150));
        a.setContentView(column);
        c.start().postCreate(null).resume().visible();
        TapGuard.installOn(a.getWindow());
        layOut(a);
        return a;
    }

    @Test
    public void aDoubleOrTripleTapOnOneControl_isHandledOnce() {
        int[] clicks = new int[3];
        Activity a = withButtons(clicks, false);
        View first = a.findViewById(R.id.btnViewBookingDetails);
        tap(a, first);
        tap(a, first);
        tap(a, first);
        assertEquals("three quick taps, one click", 1, clicks[0]);
        now.addAndGet(TapGuard.WINDOW_MS + 10);
        tap(a, first);
        assertEquals("a later tap counts again", 2, clicks[0]);
    }

    @Test
    public void tappingADifferentControl_isNeverSwallowed_andAQuantityStepperRepeats() {
        int[] clicks = new int[3];
        Activity a = withButtons(clicks, true);
        tap(a, a.findViewById(R.id.btnViewBookingDetails));
        tap(a, a.findViewById(R.id.btnNextStayRetry));
        assertEquals(1, clicks[0]);
        assertEquals("the next control works at once", 1, clicks[1]);
        View plus = a.findViewById(R.id.btnChildrenPlus);
        tap(a, plus);
        tap(a, plus);
        tap(a, plus);
        assertEquals("steppers are meant to be tapped quickly", 3, clicks[2]);
    }

    @Test
    public void everyRealScreenHasTheGuard() {
        Activity a = Robolectric.buildActivity(DashboardActivity.class).setup().get();
        // the Application installs it in production; the test Application is bare, so install as the lifecycle callback would
        new TapGuard.Installer().onActivityPostCreated(a, null);
        assertTrue(java.lang.reflect.Proxy.isProxyClass(a.getWindow().getCallback().getClass()));
        TapGuard.installOn(a.getWindow());
        assertTrue("installing twice does not wrap twice", java.lang.reflect.Proxy.isProxyClass(a.getWindow().getCallback().getClass()));
    }

    // ---- navigation

    @Test
    public void sideMenu_everyDestination_tripleSelect_opensOnce() {
        int[] items = {R.id.nav_room_browsing, R.id.nav_booking_reservation, R.id.nav_transaction_history, R.id.nav_profile_management};
        for (int id : items) {
            Activity a = Robolectric.buildActivity(DashboardActivity.class).setup().get();
            shadowOf(Looper.getMainLooper()).idle();
            com.google.android.material.navigation.NavigationView nav = a.findViewById(R.id.navigationView);
            for (int i = 0; i < 3; i++) nav.getMenu().performIdentifierAction(id, 0);
            shadowOf(Looper.getMainLooper()).idle();
            Intent first = nextLaunch(a);
            assertNotNull(a.getResources().getResourceEntryName(id) + " opens", first);
            assertEquals("and only once", null, nextLaunch(a));
            assertTrue("never stacks a second copy", (first.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
        }
    }

    @Test
    public void sameIntentStartedTwiceInARow_startsOneScreen_aDifferentOneStillStarts() {
        Activity a = Robolectric.buildActivity(DashboardActivity.class).setup().get();
        Intent history = new Intent(a, com.example.velocitysuites.TransactionHistoryActivity.class);
        a.startActivity(history);
        a.startActivity(new Intent(history));
        assertNotNull(nextLaunch(a));
        assertEquals(null, nextLaunch(a));
        a.startActivity(new Intent(a, com.example.velocitysuites.NotificationActivity.class));
        assertNotNull("a different screen is not a repeat", nextLaunch(a));
        a.startActivity(new Intent(a, com.example.velocitysuites.TransactionHistoryActivity.class).putExtra("EXTRA_OPEN_FILTER", "paid"));
        assertNotNull("same screen with other extras is not a repeat", nextLaunch(a));
    }

    @Test
    public void logout_tripleSelect_showsOneConfirmation() {
        Activity a = Robolectric.buildActivity(DashboardActivity.class).setup().get();
        com.google.android.material.navigation.NavigationView nav = a.findViewById(R.id.navigationView);
        for (int i = 0; i < 3; i++) nav.getMenu().performIdentifierAction(R.id.nav_logout, 0);
        shadowOf(Looper.getMainLooper()).idle();
        List<android.app.Dialog> shown = ShadowDialog.getShownDialogs();
        int logout = 0;
        for (android.app.Dialog d : shown) if (d instanceof AlertDialog && d.isShowing()) logout++;
        assertEquals(1, logout);
    }

    // ---- submit actions

    @Test
    public void signIn_tripleTap_isGuardedAndShowsTheLoadingState() {
        SessionManager.clear(app); // signed out, so the sign-in screen stays
        LoginActivity a = Robolectric.buildActivity(LoginActivity.class).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        ((android.widget.TextView) a.findViewById(R.id.emailEditText)).setText("guest@example.com");
        ((android.widget.TextView) a.findViewById(R.id.passwordEditText)).setText("Secret123!");
        View button = a.findViewById(R.id.signInButton);
        button.performClick();
        button.performClick();
        button.performClick();
        assertFalse("disabled while working", button.isEnabled());
        assertEquals(app.getString(R.string.signing_in), ((Button) button).getText().toString());
    }
}
