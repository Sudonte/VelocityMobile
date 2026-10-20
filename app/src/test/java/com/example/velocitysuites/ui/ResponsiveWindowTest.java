package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ResponsiveWindow;
import com.example.velocitysuites.TransactionHistoryActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.nio.file.Files;

/** System bars, cut-outs and the keyboard are kept clear; the app's own dark theme is not recoloured by the OEM; no orientation lock. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w393dp-h851dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ResponsiveWindowTest {

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), "UTF-8");
    }

    @Test
    public void theBottomBarAndKeyboard_pushTheContentUp_andTheSidesStayClearOfCutouts() {
        Activity a = Robolectric.buildActivity(TransactionHistoryActivity.class).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        ResponsiveWindow.apply(a);
        FrameLayout content = a.findViewById(android.R.id.content);

        WindowInsetsCompat nav = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 80, 0, 144)).build();
        ViewCompat.dispatchApplyWindowInsets(content, nav);
        assertEquals("above the gesture / navigation bar", 144, content.getPaddingBottom());
        assertEquals("the top is left to the screen's own header", 0, content.getPaddingTop());

        WindowInsetsCompat keyboard = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 80, 0, 144))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 900)).setVisible(WindowInsetsCompat.Type.ime(), true).build();
        ViewCompat.dispatchApplyWindowInsets(content, keyboard);
        assertEquals("above the keyboard", 900, content.getPaddingBottom());

        WindowInsetsCompat landscapeCutout = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 0, 0, 0))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(96, 0, 0, 0)).build();
        ViewCompat.dispatchApplyWindowInsets(content, landscapeCutout);
        assertEquals("clear of a camera cut-out on the side", 96, content.getPaddingLeft());
        assertEquals(0, content.getPaddingBottom());
    }

    @Test
    public void oemForceDark_isOff_inBothThemes() throws Exception {
        assertTrue(read("src/main/res/values/themes.xml").contains("<item name=\"android:forceDarkAllowed\">false</item>"));
        assertTrue(read("src/main/res/values-night/themes.xml").contains("<item name=\"android:forceDarkAllowed\">false</item>"));
    }

    @Test
    public void noScreenIsLockedToPortrait_andTheShared_handlerIsInstalledByTheApplication() throws Exception {
        String manifest = read("src/main/AndroidManifest.xml");
        assertFalse("tablets and landscape are supported", manifest.contains("screenOrientation=\"portrait\""));
        assertTrue(read("src/main/java/com/example/velocitysuites/VelocitySuitesApp.java").contains("registerActivityLifecycleCallbacks(new ResponsiveWindow())"));
    }

    @Test
    public void phonesLockToPortrait_tabletsRotate() {
        org.robolectric.RuntimeEnvironment.setQualifiers("w393dp-h851dp-xxhdpi");
        Activity phone = Robolectric.buildActivity(TransactionHistoryActivity.class).setup().get();
        ResponsiveWindow.applyOrientation(phone);
        assertFalse(ResponsiveWindow.isTablet(phone));
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, phone.getRequestedOrientation());

        org.robolectric.RuntimeEnvironment.setQualifiers("w600dp-h960dp-xxhdpi");
        Activity tablet = Robolectric.buildActivity(TransactionHistoryActivity.class).setup().get();
        ResponsiveWindow.applyOrientation(tablet);
        assertTrue(ResponsiveWindow.isTablet(tablet));
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, tablet.getRequestedOrientation());
        org.robolectric.RuntimeEnvironment.setQualifiers("w360dp-h800dp-xxhdpi");
    }

    @Test
    public void tabletScreensSurviveRotation_recreateWithSavedState() {
        android.content.Context app = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        com.example.velocitysuites.network.SessionManager.saveSession(app, "t", 1L, "Ana", "Reyes", "", "Ana Reyes", "a@b.c", "09171234567", "Female", "Jan 01, 1990", "");
        org.robolectric.RuntimeEnvironment.setQualifiers("w840dp-h1280dp-xxhdpi");
        Class<?>[] screens = {TransactionHistoryActivity.class, com.example.velocitysuites.NotificationActivity.class,
                com.example.velocitysuites.DashboardActivity.class, com.example.velocitysuites.BookingAndReservationActivity.class};
        for (Class<?> type : screens) {
            @SuppressWarnings("unchecked")
            org.robolectric.android.controller.ActivityController<? extends Activity> c =
                    Robolectric.buildActivity((Class<? extends Activity>) type).setup();
            org.robolectric.RuntimeEnvironment.setQualifiers("w1280dp-h800dp-land-xxhdpi");
            c.configurationChange();
            c.recreate();
            shadowOf(Looper.getMainLooper()).idle();
            assertFalse(type.getSimpleName(), c.get().isFinishing());
            org.robolectric.RuntimeEnvironment.setQualifiers("w840dp-h1280dp-port-xxhdpi");
        }
        org.robolectric.RuntimeEnvironment.setQualifiers("w360dp-h800dp-xxhdpi");
    }
}
