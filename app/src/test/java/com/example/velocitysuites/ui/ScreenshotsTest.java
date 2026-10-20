package com.example.velocitysuites.ui;

import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ProfileManagementActivity;
import com.example.velocitysuites.UpcomingTransactionsActivity;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.ReservationDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Profile Management and Upcoming Transactions screens to PNG on the JVM (Robolectric native graphics,
 * no device or emulator), in light and dark. Files go to app/build/screenshots/&lt;tag&gt;/ where the tag is the
 * SHOT_TAG environment variable ("before" / "after"; default "after"). This is a viewing aid, not an assertion.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenshotsTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static final String TAG = System.getenv("SHOT_TAG") != null ? System.getenv("SHOT_TAG") : "after";

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        // Opt-in: only renders when SHOT_TAG is set (SHOT_TAG=before|after ./gradlew testDebugUnitTest --tests "*ScreenshotsTest").
        org.junit.Assume.assumeTrue(System.getenv("SHOT_TAG") != null);
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com",
                "09171234567", "Female", "Jan 01, 1990", "");
    }

    private static void render(Activity activity, String name, int heightPx) throws Exception {
        View decor = activity.getWindow().getDecorView();
        int w = activity.getResources().getDisplayMetrics().widthPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, heightPx);
        Bitmap bitmap = Bitmap.createBitmap(w, heightPx, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        // trim the empty tail: rows identical to the bottom-left pixel
        int bg = bitmap.getPixel(2, heightPx - 1);
        int last = heightPx - 1;
        while (last > 400) {
            boolean blank = true;
            for (int x = 0; x < w; x += 7) if (bitmap.getPixel(x, last) != bg) { blank = false; break; }
            if (!blank) break;
            last--;
        }
        Bitmap trimmed = Bitmap.createBitmap(bitmap, 0, 0, w, Math.min(heightPx, last + 40));
        File dir = new File("build/screenshots/" + TAG);
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            trimmed.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private static String day(int plus) {
        return LocalDate.now(java.time.ZoneId.of("Asia/Manila")).plusDays(plus).toString();
    }

    private void profile(String mode) throws Exception {
        com.example.velocitysuites.ThemePreferences.setMode(app, "dark".equals(mode) ? com.example.velocitysuites.ThemePreferences.MODE_DARK : com.example.velocitysuites.ThemePreferences.MODE_LIGHT);
        org.robolectric.android.controller.ActivityController<ProfileManagementActivity> c = Robolectric.buildActivity(ProfileManagementActivity.class).setup();
        ProfileManagementActivity a = c.get();
        shadowOf(Looper.getMainLooper()).idle();
        render(a, "profile_" + mode, 5200);
        c.pause().stop().destroy();
    }

    private void upcoming(String mode) throws Exception {
        com.example.velocitysuites.ThemePreferences.setMode(app, "dark".equals(mode) ? com.example.velocitysuites.ThemePreferences.MODE_DARK : com.example.velocitysuites.ThemePreferences.MODE_LIGHT);
        org.robolectric.android.controller.ActivityController<UpcomingTransactionsActivity> c = Robolectric.buildActivity(UpcomingTransactionsActivity.class).setup();
        UpcomingTransactionsActivity a = c.get();
        shadowOf(Looper.getMainLooper()).idle();

        ReservationDto pending = ScreenTestSupport.reservation(588, 3600);
        pending.check_in = day(3);
        pending.check_out = day(5);
        DirectBookingResponseDto confirmed = ScreenTestSupport.directBooking(338, 6000);
        confirmed.check_in = day(1);
        confirmed.check_out = day(3);
        confirmed.rooms_requested = 2;
        DirectBookingResponseDto sameDay = ScreenTestSupport.directBooking(339, 3000);
        sameDay.check_in = day(1);
        sameDay.check_out = day(2);
        DirectBookingResponseDto later = ScreenTestSupport.directBooking(340, 9000);
        later.check_in = day(20);
        later.check_out = day(23);
        List<ReservationDto> res = ScreenTestSupport.reservations(pending);
        List<DirectBookingResponseDto> direct = new ArrayList<>(java.util.Arrays.asList(confirmed, sameDay, later));
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1, res, direct);
        shadowOf(Looper.getMainLooper()).idle();
        render(a, "upcoming_" + mode, 4200);
        c.pause().stop().destroy();
    }

    private void dashboard(String mode) throws Exception {
        com.example.velocitysuites.ThemePreferences.setMode(app, "dark".equals(mode) ? com.example.velocitysuites.ThemePreferences.MODE_DARK : com.example.velocitysuites.ThemePreferences.MODE_LIGHT);
        org.robolectric.android.controller.ActivityController<com.example.velocitysuites.DashboardActivity> c =
                Robolectric.buildActivity(com.example.velocitysuites.DashboardActivity.class).setup();
        com.example.velocitysuites.DashboardActivity a = c.get();
        shadowOf(Looper.getMainLooper()).idle();
        ReservationDto next = ScreenTestSupport.reservation(588, 3600);
        next.check_in = day(2);
        next.check_out = day(4);
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1, ScreenTestSupport.reservations(next), new ArrayList<>());
        shadowOf(Looper.getMainLooper()).idle();
        render(a, "dashboard_" + mode, 6500);
        c.pause().stop().destroy();
    }

    @Test
    public void dashboardLight() throws Exception {
        dashboard("light");
    }

    @Test
    public void dashboardDark() throws Exception {
        dashboard("dark");
    }

    @Test
    public void profileLight() throws Exception {
        profile("light");
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-xxhdpi")
    public void profileDark() throws Exception {
        profile("dark");
    }

    @Test
    public void upcomingLight() throws Exception {
        upcoming("light");
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-xxhdpi")
    public void upcomingDark() throws Exception {
        upcoming("dark");
    }
}
