package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Looper;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.BookingWizardActivity;
import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.CheckInWindow;
import com.example.velocitysuites.R;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomAmenity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Calendar;
import java.util.Collections;

/**
 * The wizard screens that a group of stays changed - the merged Step 1 with one, two and three stays, the group summary bar, the discount
 * note (the ID step) and the review step - fit the screen at 360dp and 393dp in light and dark: nothing runs past the screen edge, no text
 * is cut off (ellipsized, or taller than its box) and no two text views or buttons that share a parent sit on top of each other.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WizardGroupLayoutTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static final String[] CONFIGS = {"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"};

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static Room room(int n, double price) {
        return new Room(String.valueOf(n), "Room " + n, "Deluxe", 2, price, "desc", 0, true, Collections.<RoomAmenity>emptyList(), "Queen", "24 sqm", "No smoking");
    }

    private static Calendar day(int days) {
        return CheckInWindow.toDeviceMidnight(CheckInWindow.today().plusDays(days));
    }

    private static void fill(BookingWizardState state, int checkInDays, int nights, Room room) {
        state.checkIn = day(checkInDays);
        state.checkOut = day(checkInDays + nights);
        state.selectedRooms.clear();
        state.selectedRooms.add(room);
    }

    private static Fragment step(BookingWizardActivity a) {
        return a.getSupportFragmentManager().findFragmentById(R.id.wizardFragmentContainer);
    }

    /** A wizard with {@code stays} confirmed stays (one room each), sitting on the merged Step 1. */
    private BookingWizardActivity wizard(int stays) {
        BookingWizardActivity activity = WizardDriver.onConfirmedStay(app, 5, 2, WizardDriver.room("1", 12500), 1);
        for (int i = 2; i <= stays; i++) {
            WizardDriver.addConfirmedStay(activity, 5 + (i - 1) * 5, 2, WizardDriver.room(String.valueOf(i), 12500), 1);
        }
        return activity;
    }

    /** Measures {@code root} at the screen width and checks every visible view. */
    private static void assertFits(String tag, View root, int screenWidth) {
        root.measure(View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.layout(0, 0, screenWidth, root.getMeasuredHeight());
        check(tag, root, 0, 0, screenWidth);
    }

    private static String idName(View v) {
        if (v.getId() == View.NO_ID) return "";
        try {
            return "#" + v.getResources().getResourceEntryName(v.getId());
        } catch (android.content.res.Resources.NotFoundException e) {
            return "#generated" + v.getId(); // an id made at run time, not in the app's resources
        }
    }

    private static void check(String tag, View v, int offX, int offY, int screenWidth) {
        if (v.getVisibility() != View.VISIBLE) return;
        int left = offX + v.getLeft();
        int right = offX + v.getRight();
        String name = v.getClass().getSimpleName() + idName(v);
        // Inside a horizontal scroller a row may run on by design
        assertTrue(tag + ": " + name + " runs past the screen edge (" + left + ".." + right + " of " + screenWidth + ")", left >= 0 && right <= screenWidth);
        if (v instanceof TextView && v.getWidth() > 0) {
            TextView t = (TextView) v;
            Layout layout = t.getLayout();
            if (layout != null && t.getText().length() > 0) {
                for (int line = 0; line < layout.getLineCount(); line++) {
                    assertEquals(tag + ": " + name + " has cut-off text '" + t.getText() + "'", 0, layout.getEllipsisCount(line));
                }
                int needed = layout.getHeight() + t.getCompoundPaddingTop() + t.getCompoundPaddingBottom();
                assertTrue(tag + ": " + name + " is shorter (" + t.getHeight() + ") than its text (" + needed + "): '" + t.getText() + "'", t.getHeight() >= needed - 1);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            boolean flows = g instanceof android.widget.LinearLayout || g instanceof android.widget.ScrollView
                    || g instanceof androidx.core.widget.NestedScrollView || g instanceof androidx.recyclerview.widget.RecyclerView;
            for (int i = 0; i < g.getChildCount(); i++) {
                View a = g.getChildAt(i);
                if (a.getVisibility() != View.VISIBLE) continue;
                check(tag, a, left - g.getScrollX(), offY + v.getTop() - g.getScrollY(), screenWidth);
                if (flows || !(a instanceof TextView)) continue;
                for (int j = i + 1; j < g.getChildCount(); j++) {
                    View b = g.getChildAt(j);
                    if (b.getVisibility() != View.VISIBLE || !(b instanceof TextView)) continue;
                    Rect ra = new Rect(a.getLeft(), a.getTop(), a.getRight(), a.getBottom());
                    Rect rb = new Rect(b.getLeft(), b.getTop(), b.getRight(), b.getBottom());
                    assertTrue(tag + ": text views overlap inside " + name, !Rect.intersects(ra, rb));
                }
            }
        }
    }

    private static void inEachConfig(Runnable body) {
        try {
            for (String q : CONFIGS) {
                RuntimeEnvironment.setQualifiers("+" + q);
                body.run();
            }
        } finally {
            RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
        }
    }

    private static boolean isNight(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    @Test
    public void mergedStep1_withOneTwoAndThreeStays() {
        for (String q : CONFIGS) {
            RuntimeEnvironment.setQualifiers("+" + q);
            for (int stays = 1; stays <= 3; stays++) {
                BookingWizardActivity activity = wizard(stays);
                assertEquals(q, q.endsWith("-night"), isNight(activity));
                assertFits(q + " step 1, " + stays + " stay(s)", step(activity).getView(), activity.getResources().getDisplayMetrics().widthPixels);
            }
        }
        RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
    }

    @Test
    public void groupSummaryBar_withAHighTotal() {
        inEachConfig(() -> {
            BookingWizardActivity activity = wizard(3);
            activity.refreshSummaryBar();
            View bar = activity.findViewById(R.id.wizardSummaryBar);
            assertNotNull(bar);
            assertEquals(View.VISIBLE, bar.getVisibility());
            TextView left = activity.findViewById(R.id.wizardSummaryLeft);
            assertTrue(left.getText().toString(), left.getText().toString().contains("3 stays"));
            assertFits("summary bar", bar, activity.getResources().getDisplayMetrics().widthPixels);
        });
    }

    @Test
    public void idStep_isOneIdForAllStays_withNoDuplicateTitle() {
        inEachConfig(() -> {
            BookingWizardActivity activity = wizard(2);
            activity.goToStep(5);
            idle();
            View root = step(activity).getView();
            String all = collect(root);
            assertTrue(all, all.contains(app.getString(R.string.discount_step_intro_stays)));
            assertFalse("the discount list is not hidden any more", all.contains("can't be claimed"));
            // the step name is the header's job: the section title is not repeated inside the step
            assertEquals("ID Verification appears once in the step body", 0, countOccurrences(all, app.getString(R.string.id_verification_title)));
            assertFits("ID step with a group", root, activity.getResources().getDisplayMetrics().widthPixels);
        });
    }

    @Test
    public void reviewStep_listsEveryStay() {
        inEachConfig(() -> {
            BookingWizardActivity activity = wizard(3);
            BookingWizardState state = activity.getState();
            state.termsViewed = true;
            state.termsAccepted = true;
            activity.goToStep(6);
            idle();
            View root = step(activity).getView();
            String all = collect(root);
            assertTrue(all, countOccurrences(all, "→") >= 3 && !all.contains("Stay 3"));
            assertFits("review with 3 stays", root, activity.getResources().getDisplayMetrics().widthPixels);
        });
    }

    private static int countOccurrences(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) n++;
        return n;
    }

    private static String collect(View v) {
        StringBuilder sb = new StringBuilder();
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE) sb.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) sb.append(collect(((ViewGroup) v).getChildAt(i)));
        }
        return sb.toString();
    }
}
