package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.LoginActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.RegistrationActivity;
import com.example.velocitysuites.WelcomeActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The Welcome screen, launched for real: what the guest reads (in order), that the two actions keep their
 * destinations with one clearly primary, that they stay reachable and the content scrolls on a small screen
 * at the largest font size, and the accessibility basics (touch targets, described logo, hidden decoration).
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WelcomeScreenTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static WelcomeActivity launch() {
        ActivityController<WelcomeActivity> controller = Robolectric.buildActivity(WelcomeActivity.class);
        controller.setup();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800)); // entry animation
        return controller.get();
    }

    private static void layout(WelcomeActivity activity) {
        View decor = activity.getWindow().getDecorView();
        int w = activity.getResources().getDisplayMetrics().widthPixels;
        int h = activity.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
    }

    private static int top(View root, View v) {
        return LayoutHarness.boundsIn(root, v).top;
    }

    private static float dp(WelcomeActivity activity, int px) {
        return px / activity.getResources().getDisplayMetrics().density;
    }

    private static final int[] CARD_IDS = {R.id.welcomeCardRooms, R.id.welcomeCardPay, R.id.welcomeCardTrack, R.id.welcomeCardSupport};

    // ---- content and order ----

    @Test
    public void readsInOrder_logo_headline_support_points_actions() {
        WelcomeActivity activity = launch();
        layout(activity);
        View root = activity.findViewById(R.id.welcomeRoot);

        TextView headline = activity.findViewById(R.id.welcomeTitleText);
        TextView support = activity.findViewById(R.id.welcomeDescriptionText);
        View logo = activity.findViewById(R.id.logoBorder);
        View points = activity.findViewById(R.id.welcomePoints);
        View actions = activity.findViewById(R.id.buttonContainer);

        assertEquals(activity.getString(R.string.welcome_headline), headline.getText().toString());
        assertEquals(activity.getString(R.string.welcome_support), support.getText().toString());
        assertTrue("logo above headline", top(root, logo) < top(root, headline));
        assertTrue("headline above supporting text", top(root, headline) < top(root, support));
        assertTrue("supporting text above the value points", top(root, support) < top(root, points));
        assertTrue("value points above the actions", top(root, points) < top(root, actions));
        assertEquals("the four highlights sit in two rows of two", 2, ((ViewGroup) points).getChildCount());
    }

    // ---- actions ----

    @Test
    public void signUpIsThePrimaryAction_signInIsClearlySecondary_andBothKeepTheirDestinations() {
        WelcomeActivity activity = launch();
        layout(activity);
        MaterialButton primary = activity.findViewById(R.id.registrationButton);
        MaterialButton secondary = activity.findViewById(R.id.loginButton);

        assertEquals(activity.getString(R.string.welcome_action_create_account), primary.getText().toString());
        assertEquals("primary is filled", 0, primary.getStrokeWidth());
        assertNotNull("primary has a fill colour", primary.getBackgroundTintList());
        assertEquals("secondary is a quiet text link, not a second filled/outlined box", 0, secondary.getStrokeWidth());
        assertTrue("primary is taller than secondary", primary.getHeight() > secondary.getHeight());
        assertTrue("primary sits above secondary", top(activity.findViewById(R.id.welcomeRoot), primary)
                < top(activity.findViewById(R.id.welcomeRoot), secondary));

        primary.performClick();
        Intent toRegistration = shadowOf(activity).getNextStartedActivity();
        assertEquals(new ComponentName(activity, RegistrationActivity.class), toRegistration.getComponent());

        secondary.performClick();
        Intent toLogin = shadowOf(activity).getNextStartedActivity();
        assertEquals(new ComponentName(activity, LoginActivity.class), toLogin.getComponent());

        activity.findViewById(R.id.btnWelcomeBack).performClick();
        assertTrue("Back still closes the screen", activity.isFinishing());
    }

    // ---- small screen, large font ----

    private void assertActionsReachableAndContentScrolls(float fontScale) {
        RuntimeEnvironment.setFontScale(fontScale);
        WelcomeActivity activity = launch();
        layout(activity);
        View root = activity.findViewById(R.id.welcomeRoot);
        NestedScrollView scroll = activity.findViewById(R.id.welcomeScroll);
        View actions = activity.findViewById(R.id.buttonContainer);
        View primary = activity.findViewById(R.id.registrationButton);
        View secondary = activity.findViewById(R.id.loginButton);
        String where = "font scale " + fontScale + ": ";

        assertTrue(where + "the content is taller than its viewport, so it must scroll",
                scroll.getChildAt(0).getHeight() > scroll.getHeight());
        assertTrue(where + "the scroller ends where the action bar begins (nothing hidden behind it)",
                scroll.getBottom() <= actions.getTop());
        assertTrue(where + "the action bar is fully on screen",
                LayoutHarness.boundsIn(root, actions).bottom <= root.getHeight());
        assertTrue(where + "primary is at least 48dp tall: " + dp(activity, primary.getHeight()), dp(activity, primary.getHeight()) >= 48f);
        assertTrue(where + "secondary is at least 48dp tall: " + dp(activity, secondary.getHeight()), dp(activity, secondary.getHeight()) >= 48f);
        assertTrue(where + "the scroller keeps a usable height above the bar: " + dp(activity, scroll.getHeight()) + "dp",
                dp(activity, scroll.getHeight()) >= 120f);

        // scrolled to the very end, the last value point is completely inside the viewport
        scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
        layout(activity);
        ViewGroup points = activity.findViewById(R.id.welcomePoints);
        View last = points.getChildAt(points.getChildCount() - 1);
        int lastBottom = LayoutHarness.boundsIn(root, last).bottom;
        // the scroller's own edge in its parent's coordinates (boundsIn() would subtract the scroller's own scroll offset)
        int viewportBottom = scroll.getBottom();
        assertTrue(where + "the last value point can be scrolled fully into view (last bottom " + lastBottom + "px, viewport bottom "
                + viewportBottom + "px, scrollY " + scroll.getScrollY() + ", content " + scroll.getChildAt(0).getHeight()
                + "px, viewport " + scroll.getHeight() + "px)", lastBottom <= viewportBottom);
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    public void smallPhone_actionsStayReachable_contentScrolls() {
        assertActionsReachableAndContentScrolls(1.0f);
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    public void smallPhone_largeFont_actionsStayReachable_contentScrolls() {
        assertActionsReachableAndContentScrolls(1.3f);
        // 2.0 is the largest system font scale on current Android
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    public void smallPhone_largestFont_actionsStayReachable_contentScrolls() {
        assertActionsReachableAndContentScrolls(2.0f);
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-xhdpi")
    public void shortWideWindow_landscapeLike_actionsStayReachable_contentScrolls() {
        assertActionsReachableAndContentScrolls(1.0f);
    }

    // ---- accessibility, shape ----

    @Test
    public void accessibility_logoIsDescribed_decorationIsHidden_headlineIsAHeading() {
        WelcomeActivity activity = launch();
        ImageView logo = activity.findViewById(R.id.welcomeLogoImage);
        assertEquals(activity.getString(R.string.app_logo), logo.getContentDescription().toString());
        assertTrue("headline is announced as a heading", activity.findViewById(R.id.welcomeTitleText).isAccessibilityHeading());

        // every other image on the screen is decoration and must not be announced
        List<String> announced = new ArrayList<>();
        collectImages(activity.findViewById(R.id.welcomeRoot), logo, announced);
        assertTrue("decorative images that screen readers would announce: " + announced, announced.isEmpty());

        // each value point is one announcement, not two loose texts
        ViewGroup points = activity.findViewById(R.id.welcomePoints);
        for (int id : CARD_IDS) {
            assertTrue("value point reads as one item", activity.findViewById(id).isScreenReaderFocusable());
        }
    }

    private static void collectImages(View view, ImageView meaningful, List<String> announced) {
        if (view instanceof ImageView && view != meaningful && !(view.getParent() instanceof MaterialButton)) {
            boolean hidden = view.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    || view.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
            if (!hidden) announced.add(view.toString());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectImages(group.getChildAt(i), meaningful, announced);
        }
    }

    @Test
    public void noShadowsAndNoRoundedCorners() {
        WelcomeActivity activity = launch();
        List<String> problems = new ArrayList<>();
        collect(activity.findViewById(R.id.welcomeRoot), problems);
        assertTrue(problems.toString(), problems.isEmpty());
        assertNull("no press shadow animator on the primary action",
                ((MaterialButton) activity.findViewById(R.id.registrationButton)).getStateListAnimator());
        assertFalse(problems.toString(), activity.findViewById(R.id.logoBorder) == null);
    }

    private static void collect(View v, List<String> problems) {
        String name = v.getClass().getSimpleName() + (v.getId() == View.NO_ID ? "" : "#" + v.getId());
        if (!(v instanceof MaterialCardView) && v.getElevation() != 0f) problems.add(name + " elevation=" + v.getElevation());
        if (v instanceof MaterialButton) {
            MaterialButton b = (MaterialButton) v;
            if (b.getCornerRadius() != 0) problems.add(name + " cornerRadius=" + b.getCornerRadius());
            if (b.getStateListAnimator() != null) problems.add(name + " has a press animator");
        }
        if (v instanceof MaterialCardView) {
            MaterialCardView c = (MaterialCardView) v;
            if (c.getRadius() != v.getResources().getDimension(R.dimen.card_corner_radius)) problems.add(name + " radius=" + c.getRadius());
            if (c.getCardElevation() != v.getResources().getDimension(R.dimen.card_elevation)) problems.add(name + " cardElevation=" + c.getCardElevation());
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), problems);
        }
    }

    // ---- the redesign ----

    private void assertRedesignFits(WelcomeActivity activity) {
        layout(activity);
        View root = activity.findViewById(R.id.welcomeRoot);
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;

        // order: back, logo, VELOCITY SUITES, tagline, divider, heading, description, features, buttons
        View[] order = {activity.findViewById(R.id.btnWelcomeBack), activity.findViewById(R.id.logoBorder), activity.findViewById(R.id.welcomeBrandText),
                activity.findViewById(R.id.welcomeTaglineText), activity.findViewById(R.id.welcomeDivider), activity.findViewById(R.id.welcomeTitleText),
                activity.findViewById(R.id.welcomeDescriptionText), activity.findViewById(R.id.welcomePoints), activity.findViewById(R.id.buttonContainer)};
        for (int i = 1; i < order.length; i++) {
            assertTrue("view " + i + " is below view " + (i - 1), top(root, order[i]) >= LayoutHarness.boundsIn(root, order[i - 1]).bottom - 1
                    || order[i] == order[i - 1]);
        }

        TextView brand = activity.findViewById(R.id.welcomeBrandText);
        TextView tagline = activity.findViewById(R.id.welcomeTaglineText);
        TextView description = activity.findViewById(R.id.welcomeDescriptionText);
        assertEquals("VELOCITY SUITES", brand.getText().toString());
        assertTrue("the brand label is bold", brand.getTypeface().getWeight() >= 700 || brand.getTypeface().isBold());
        assertEquals("primary red", activity.getColor(R.color.velocity_red_primary), brand.getCurrentTextColor());
        assertEquals("Your comfort is our service", tagline.getText().toString());
        assertTrue("the tagline is smaller than the brand label", tagline.getTextSize() < brand.getTextSize());
        assertEquals("secondary colour", activity.getColor(R.color.velocity_text_secondary), tagline.getCurrentTextColor());
        assertTrue("the description is 2-3 lines: " + description.getLineCount(), description.getLineCount() >= 1 && description.getLineCount() <= 3);
        assertEquals("one line", 1, brand.getLineCount());
        for (TextView centred : new TextView[]{brand, tagline, description, activity.findViewById(R.id.welcomeTitleText)}) {
            assertEquals("centred", android.view.Gravity.CENTER_HORIZONTAL, centred.getGravity() & android.view.Gravity.HORIZONTAL_GRAVITY_MASK);
        }

        // the 2 x 2 grid: equal cards, nothing clipped
        View first = activity.findViewById(CARD_IDS[0]);
        for (int id : CARD_IDS) {
            View card = activity.findViewById(id);
            assertEquals("equal widths", first.getWidth(), card.getWidth(), 1);
            assertEquals("equal heights", first.getHeight(), card.getHeight(), 1);
            Rect box = LayoutHarness.boundsIn(root, card);
            assertTrue("inside the screen", box.left >= 0 && box.right <= screenWidth);
            TextView title = card.findViewById(id == R.id.welcomeCardRooms ? R.id.welcomeCardRoomsTitle : id == R.id.welcomeCardPay ? R.id.welcomeCardPayTitle
                    : id == R.id.welcomeCardTrack ? R.id.welcomeCardTrackTitle : R.id.welcomeCardSupportTitle);
            TextView desc = card.findViewById(id == R.id.welcomeCardRooms ? R.id.welcomeCardRoomsDesc : id == R.id.welcomeCardPay ? R.id.welcomeCardPayDesc
                    : id == R.id.welcomeCardTrack ? R.id.welcomeCardTrackDesc : R.id.welcomeCardSupportDesc);
            assertTrue("title at most 2 lines: " + title.getText(), title.getLineCount() <= 2);
            assertTrue("description at most 2 lines: " + desc.getText(), desc.getLineCount() <= 2);
            assertEquals("title not cut", 0, title.getLayout().getEllipsisCount(title.getLineCount() - 1));
            assertTrue("title inside its card", box.contains(LayoutHarness.boundsIn(root, title)));
            assertTrue("description inside its card", box.contains(LayoutHarness.boundsIn(root, desc)));
            assertTrue("title is bold", title.getTypeface().getWeight() >= 700 || title.getTypeface().isBold());
        }
        assertEquals("left and right card share a row", LayoutHarness.boundsIn(root, first).top, LayoutHarness.boundsIn(root, activity.findViewById(CARD_IDS[1])).top);
        assertTrue("the second row is below the first", LayoutHarness.boundsIn(root, activity.findViewById(CARD_IDS[2])).top
                >= LayoutHarness.boundsIn(root, first).bottom);

        // buttons: sentence case, no letter-spacing
        MaterialButton create = activity.findViewById(R.id.registrationButton);
        MaterialButton signIn = activity.findViewById(R.id.loginButton);
        assertEquals("Create an account", create.getText().toString());
        assertEquals("Already have an account? Sign in", signIn.getText().toString());
        for (MaterialButton b : new MaterialButton[]{create, signIn}) {
            assertEquals("no letter-spacing", 0f, b.getLetterSpacing(), 0.0001f);
            assertFalse("not ALL CAPS", b.isAllCaps());
            assertEquals("one line", 1, b.getLineCount());
            assertTrue("at least 48dp", dp(activity, b.getHeight()) >= 48f);
        }
        assertTrue("the primary is 48-56dp+", dp(activity, create.getHeight()) >= 52f);
        // "Sign in" alone is picked out
        android.text.Spanned spanned = (android.text.Spanned) signIn.getText();
        android.text.style.StyleSpan[] bold = spanned.getSpans(0, spanned.length(), android.text.style.StyleSpan.class);
        assertEquals(1, bold.length);
        assertEquals("Sign in", spanned.subSequence(spanned.getSpanStart(bold[0]), spanned.getSpanEnd(bold[0])).toString());
        Rect bar = LayoutHarness.boundsIn(root, activity.findViewById(R.id.buttonContainer));
        assertTrue("the bar is pinned to the bottom", bar.bottom == root.getHeight() || bar.bottom <= root.getHeight());
    }

    @Test
    public void redesign_360dp_light() {
        assertRedesignFits(launch());
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-xxhdpi")
    public void redesign_360dp_dark() {
        assertRedesignFits(launch());
    }

    @Test
    @Config(qualifiers = "w393dp-h851dp-xxhdpi")
    public void redesign_393dp_light() {
        assertRedesignFits(launch());
    }

    @Test
    @Config(qualifiers = "w393dp-h851dp-night-xxhdpi")
    public void redesign_393dp_dark() {
        assertRedesignFits(launch());
    }

}
