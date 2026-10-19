package com.example.velocitysuites.ui;

import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingAndReservationActivity;
import com.example.velocitysuites.DashboardActivity;
import com.example.velocitysuites.NotificationActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.RoomBrowsingActivity;
import com.example.velocitysuites.TransactionHistoryActivity;
import com.example.velocitysuites.network.SessionManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * What the guest actually SEES, not what the sources say: the main screens are inflated and every visible TextView (buttons,
 * tabs, segmented buttons, chips and field labels included) must have no ALL CAPS transformation and no letter-spacing. This
 * catches the theme-level cause the source scan cannot see: the Material default button / tab text appearance is ALL CAPS
 * with letter-spacing, and reaches every button through ?attr/textAppearanceButton (replaced in the theme by
 * TextAppearance.Velocity.Button). The only allowed exceptions are the brand label and the receipt stamps.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NoCapsOnScreensTest {

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com",
                "09171234567", "Female", "Jan 01, 1990", "");
    }

    private boolean allowed(TextView tv) {
        String text = tv.getText().toString();
        return text.equals(app.getString(R.string.welcome_brand))
                || text.equals(app.getString(R.string.receipt_bd_status_paid))
                || text.equals(app.getString(R.string.receipt_bd_status_partial))
                || text.equals(app.getString(R.string.receipt_status_official_paid));
    }

    private static boolean visible(View v) {
        for (View c = v; c != null; ) {
            if (c.getVisibility() != View.VISIBLE) return false;
            c = c.getParent() instanceof View ? (View) c.getParent() : null;
        }
        return true;
    }

    private void walk(View v, String screen, List<String> bad) {
        if (v instanceof TextView && visible(v)) {
            TextView tv = (TextView) v;
            if (tv.getText().length() > 0 && !allowed(tv)) {
                if (tv.isAllCaps()) bad.add(screen + ": \"" + tv.getText() + "\" is ALL CAPS");
                if (tv.getLetterSpacing() != 0f) bad.add(screen + ": \"" + tv.getText() + "\" letter-spacing " + tv.getLetterSpacing());
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), screen, bad);
        }
    }

    private void check(Class<? extends Activity> screen) {
        Activity a = Robolectric.buildActivity(screen, new Intent(app, screen)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        List<String> bad = new ArrayList<>();
        walk(a.getWindow().getDecorView(), screen.getSimpleName(), bad);
        assertTrue(bad.toString(), bad.isEmpty());
    }

    @Test
    public void dashboard() {
        check(DashboardActivity.class);
    }

    @Test
    public void bookingAndReservation() {
        check(BookingAndReservationActivity.class);
    }

    @Test
    public void roomBrowsing() {
        check(RoomBrowsingActivity.class);
    }

    @Test
    public void notifications() {
        check(NotificationActivity.class);
    }

    @Test
    public void transactionHistory() {
        check(TransactionHistoryActivity.class);
    }
}
