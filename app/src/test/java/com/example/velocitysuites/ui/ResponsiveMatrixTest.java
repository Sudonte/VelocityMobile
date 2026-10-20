package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Looper;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingAndReservationActivity;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.BookingWizardActivity;
import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.DashboardActivity;
import com.example.velocitysuites.NotificationActivity;
import com.example.velocitysuites.ResponsiveWindow;
import com.example.velocitysuites.TransactionHistoryActivity;
import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.SessionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Every main screen at phone and tablet sizes, portrait and landscape, font scale 1.0 to 2.0, light and dark: nothing runs off the
 * window, nothing with a fixed height cuts its text, and on a wide window the content stays at a comfortable width, centred.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ResponsiveMatrixTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static final class Size {
        final String qualifiers;
        final int widthDp;
        final int heightDp;

        Size(int w, int h) {
            this.widthDp = w;
            this.heightDp = h;
            this.qualifiers = "w" + w + "dp-h" + h + "dp-" + (w > h ? "land-" : "port-") + "xxhdpi";
        }
    }

    private static final Size[] PHONES = {new Size(320, 568), new Size(360, 800), new Size(393, 851), new Size(412, 915)};
    private static final Size[] TABLETS = {new Size(600, 960), new Size(840, 1280)};
    private static final Size[] LANDSCAPE = {new Size(640, 360), new Size(960, 600), new Size(1280, 800)};
    private static final float[] FONTS = {1.3f, 1.5f, 2.0f};

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.FakeApi api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com",
                "09171234567", "Female", "Jan 01, 1990", "");
    }

    @After
    public void reset() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        RuntimeEnvironment.setFontScale(1.0f);
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-xxhdpi");
    }

    private void configure(Size size, boolean night, float font) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        RuntimeEnvironment.setQualifiers(size.qualifiers.replace("-xxhdpi", night ? "-night-xxhdpi" : "-xxhdpi"));
        RuntimeEnvironment.setFontScale(font);
        app = ApplicationProvider.getApplicationContext();
    }

    // ---- screens

    private interface Launcher {
        Activity launch();
    }

    private Launcher[] screens() {
        return new Launcher[]{
                () -> Robolectric.buildActivity(DashboardActivity.class).setup().get(),
                () -> Robolectric.buildActivity(BookingAndReservationActivity.class).setup().get(),
                () -> Robolectric.buildActivity(TransactionHistoryActivity.class).setup().get(),
                () -> Robolectric.buildActivity(NotificationActivity.class).setup().get(),
                () -> {
                    Booking b = ApiMapper.toBooking(ScreenTestSupport.directBooking(569, 2000));
                    Intent i = new Intent(app, BookingDetailsActivity.class).putExtra(BookingDetailsActivity.EXTRA_BOOKING, b);
                    return Robolectric.buildActivity(BookingDetailsActivity.class, i).setup().get();
                },
                () -> {
                    BookingWizardActivity a = Robolectric.buildActivity(BookingWizardActivity.class,
                            BookingWizardActivity.newIntent(app, BookingWizardState.Mode.RESERVATION)).setup().get();
                    return a;
                },
        };
    }

    private void layOut(Activity a) {
        View decor = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels;
        int h = a.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
    }

    private static void collect(View v, List<TextView> out) {
        if (v instanceof TextView && v.isShown()) out.add((TextView) v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), out);
    }

    /** What the guest would see wrong on this screen at this size, or "" when nothing is. */
    private String problems(Activity a, Size size, float font) {
        layOut(a);
        View decor = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels;
        float density = a.getResources().getDisplayMetrics().density;
        StringBuilder out = new StringBuilder();
        FrameLayout content = a.findViewById(android.R.id.content);
        View root = content.getChildAt(0);

        if (size.widthDp > ResponsiveWindow.MAX_CONTENT_DP) {
            Rect r = new Rect();
            r.set(root.getLeft(), root.getTop(), root.getRight(), root.getBottom());
            if (r.width() > Math.round(ResponsiveWindow.MAX_CONTENT_DP * density) + 1) out.append("content is ").append(r.width() / density).append("dp wide\n");
            if (Math.abs((r.left + r.right) / 2 - w / 2) > 2) out.append("content is not centred\n");
        } else if (root.getWidth() != w) {
            out.append("content is ").append(root.getWidth() / density).append("dp, not the whole window\n");
        }

        List<TextView> texts = new ArrayList<>();
        collect(root, texts);
        for (TextView t : texts) {
            Rect r = new Rect(0, 0, t.getWidth(), t.getHeight());
            content.offsetDescendantRectToMyCoords(t, r);
            String name = nameOf(a, t);
            if (r.width() > 0 && (r.left < -1 || r.right > w + 1)) {
                // a view inside a horizontal scroller (chips, day strip) may leave the window on purpose
                boolean scrolls = false;
                for (android.view.ViewParent p = t.getParent(); p != null; p = p.getParent()) {
                    if (p instanceof android.widget.HorizontalScrollView || p instanceof androidx.recyclerview.widget.RecyclerView) scrolls = true;
                }
                if (!scrolls) out.append(name).append(" runs off the window (").append(r.left).append("..").append(r.right).append(" of ").append(w).append(")\n");
            }
            Layout layout = t.getLayout();
            ViewGroup.LayoutParams lp = t.getLayoutParams();
            if (layout != null && lp != null && lp.height > 0 && t.getMaxLines() > 0 && t.getMaxLines() < Integer.MAX_VALUE) {
                // fixed height, text that is allowed to be taller than it
                int needed = layout.getLineBottom(Math.min(layout.getLineCount(), t.getMaxLines()) - 1) + t.getCompoundPaddingTop() + t.getCompoundPaddingBottom();
                if (layout.getLineCount() > 0 && needed > t.getHeight() + 2 && t.getEllipsize() == null) {
                    out.append(name).append(" is cut off by its fixed height (").append(needed).append(" > ").append(t.getHeight()).append(")\n");
                }
            }
        }
        return out.toString();
    }

    private static String nameOf(Activity a, TextView t) {
        try {
            if (t.getId() != View.NO_ID) return a.getResources().getResourceEntryName(t.getId());
        } catch (android.content.res.Resources.NotFoundException generated) {
            // a view id made at run time
        }
        return "\"" + t.getText() + "\"";
    }

    private void check(Size size, boolean night, float font) {
        configure(size, night, font);
        for (Launcher l : screens()) {
            Activity a = l.launch();
            shadowOf(Looper.getMainLooper()).idle();
            ResponsiveWindow.apply(a); // the Application does this after onCreate; the test Application is bare
            assertEquals("the size really applied", size.widthDp, a.getResources().getConfiguration().screenWidthDp);
            assertEquals("the font scale really applied", font, a.getResources().getConfiguration().fontScale, 0.001f);
            assertEquals("dark/light really applied", night, (a.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES);
            assertEquals("landscape/portrait", size.widthDp > size.heightDp, a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
            String p = problems(a, size, font);
            assertEquals(a.getClass().getSimpleName() + " at " + size.qualifiers + " font " + font + (night ? " dark" : " light") + "\n", "", p);
            a.finish();
        }
    }

    // ---- the matrix

    @Test
    public void phones_portrait_light_font1() {
        for (Size s : PHONES) check(s, false, 1.0f);
    }

    @Test
    public void phones_portrait_light_largerFonts() {
        for (Size s : new Size[]{PHONES[0], PHONES[1]}) for (float f : FONTS) check(s, false, f);
    }

    @Test
    public void phones_portrait_dark() {
        for (Size s : new Size[]{PHONES[1], PHONES[2]}) check(s, true, 1.0f);
        check(PHONES[0], true, 1.5f);
    }

    @Test
    public void tablets_andLandscape_light_andDark() {
        for (Size s : TABLETS) {
            check(s, false, 1.0f);
            check(s, true, 1.0f);
        }
        for (Size s : LANDSCAPE) check(s, false, 1.0f);
        check(LANDSCAPE[0], true, 1.3f);
        check(TABLETS[0], false, 1.5f);
    }

    @Test
    public void wideContentIsCapped_andCentred() {
        configure(TABLETS[1], false, 1.0f);
        Activity a = Robolectric.buildActivity(TransactionHistoryActivity.class).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        ResponsiveWindow.apply(a);
        layOut(a);
        FrameLayout content = a.findViewById(android.R.id.content);
        View root = content.getChildAt(0);
        float density = a.getResources().getDisplayMetrics().density;
        assertEquals(Math.round(680 * density), root.getWidth());
        assertEquals((a.getResources().getDisplayMetrics().widthPixels - root.getWidth()) / 2, root.getLeft(), 2);
        assertEquals(680, ResponsiveWindow.contentWidthDp(1280));
        assertEquals(360, ResponsiveWindow.contentWidthDp(360));
        assertFalse(root.getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT);

        // a phone is untouched
        configure(PHONES[1], false, 1.0f);
        Activity phone = Robolectric.buildActivity(TransactionHistoryActivity.class).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        ResponsiveWindow.apply(phone);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, ((FrameLayout) phone.findViewById(android.R.id.content)).getChildAt(0).getLayoutParams().width);
        assertTrue(true);
    }
}
