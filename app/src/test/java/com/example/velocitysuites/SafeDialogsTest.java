package com.example.velocitysuites;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ui.TestApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** A network callback that lands after the screen is gone must not crash the app by opening a dialog on it. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class SafeDialogsTest {

    @Test
    public void dialogOnALiveScreenIsShown() {
        Activity activity = Robolectric.buildActivity(WelcomeActivity.class).setup().get();
        assertTrue(SafeDialogs.canShow(activity));
        androidx.appcompat.app.AlertDialog dialog = new SafeDialogs.Material(activity).setMessage("hi").show();
        assertTrue(dialog.isShowing());
    }

    @Test
    public void dialogOnAFinishedScreenIsSkippedWithoutCrashing() {
        ActivityController<WelcomeActivity> controller = Robolectric.buildActivity(WelcomeActivity.class).setup();
        Activity activity = controller.get();
        activity.finish();
        assertFalse(SafeDialogs.canShow(activity));
        androidx.appcompat.app.AlertDialog dialog = new SafeDialogs.Material(activity).setMessage("late").show();
        assertNotNull(dialog);
        assertFalse(dialog.isShowing());
        androidx.appcompat.app.AlertDialog plain = new SafeDialogs.Plain(activity).setMessage("late").show();
        assertFalse(plain.isShowing());
    }

    @Test
    public void dialogOnADestroyedScreenIsSkippedWithoutCrashing() {
        ActivityController<WelcomeActivity> controller = Robolectric.buildActivity(WelcomeActivity.class).setup();
        Activity activity = controller.get();
        controller.pause().stop().destroy();
        assertFalse(SafeDialogs.canShow(activity));
        assertFalse(new SafeDialogs.Material(activity).setMessage("late").show().isShowing());
    }
}
