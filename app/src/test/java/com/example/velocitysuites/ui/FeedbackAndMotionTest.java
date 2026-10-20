package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Feedback;
import com.example.velocitysuites.Motion;
import com.example.velocitysuites.TransactionHistoryActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.nio.file.Files;

/** Success messages as a Snackbar (now, or on the next screen), and animations that switch off with "Remove animations". */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FeedbackAndMotionTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        ScreenTestSupport.goOnline(app);
        com.example.velocitysuites.network.SessionManager.saveSession(app, "t", 1L, "Ana", "Reyes", "", "Ana Reyes", "a@b.c",
                "09171234567", "Female", "Jan 01, 1990", "");
    }

    private static String snackbarText(Activity a) {
        TextView t = a.findViewById(com.google.android.material.R.id.snackbar_text);
        return t == null ? null : t.getText().toString();
    }

    @Test
    public void showAndLater_putTheMessageInASnackbar() {
        Activity a = Robolectric.buildActivity(TransactionHistoryActivity.class).setup().get();
        Feedback.show(a, "Reservation cancelled");
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Reservation cancelled", snackbarText(a));

        Activity next = Robolectric.buildActivity(TransactionHistoryActivity.class).setup().get();
        Feedback.later("Payment submitted for verification");
        new Feedback().onActivityResumed(next);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Payment submitted for verification", snackbarText(next));

        Activity third = Robolectric.buildActivity(TransactionHistoryActivity.class).setup().get();
        new Feedback().onActivityResumed(third);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("a message is shown once", null, snackbarText(third));
    }

    @Test
    public void animations_followTheSystemSetting() {
        Settings.Global.putFloat(app.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        assertTrue(Motion.enabled(app));
        assertEquals(180L, Motion.duration(app, 180L));
        Settings.Global.putFloat(app.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        assertFalse("Remove animations", Motion.enabled(app));
        assertEquals(0L, Motion.duration(app, 180L));
        Settings.Global.putFloat(app.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        assertTrue("short, about 150-250 ms", Motion.SHORT_MS >= 150 && Motion.MEDIUM_MS <= 250);
    }

    @Test
    public void screenTransitions_exist_andAreShort() throws Exception {
        String themes = new String(Files.readAllBytes(new File("src/main/res/values/themes.xml").toPath()), "UTF-8");
        assertTrue(themes.contains("android:windowAnimationStyle\">@style/ScreenTransition"));
        for (String f : new String[]{"screen_enter", "screen_exit", "screen_pop_enter", "screen_pop_exit"}) {
            String xml = new String(Files.readAllBytes(new File("src/main/res/anim/" + f + ".xml").toPath()), "UTF-8");
            assertTrue(f, xml.contains("android:duration=\"200\""));
        }
        assertNotNull(new LinearLayout(app));
    }
}
