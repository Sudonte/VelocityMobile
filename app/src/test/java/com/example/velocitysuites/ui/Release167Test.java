package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.DashboardActivity;
import com.example.velocitysuites.FailedSignIns;
import com.example.velocitysuites.LoginActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.ResetPasswordActivity;
import com.example.velocitysuites.network.ApiClient;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.ApiMessage;
import com.example.velocitysuites.network.dto.AuthResponse;
import com.example.velocitysuites.network.dto.ResetPasswordRequest;
import com.example.velocitysuites.network.dto.UserDto;
import com.example.velocitysuites.network.dto.VerifyResetOtpRequest;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 1.67.0: Reset password in three short steps under the same header as Sign in (the logo is never overlapped), the same answer for every
 * address, six code boxes, the shared password checklist, signing in and opening the Dashboard on success; and the redirect to Reset
 * password after the third failed sign-in for the same email (counted on this device, the same for an address with no account).
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Release167Test {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static final String SENT = "If an account exists for that email, we've sent a 6-digit code.";
    private static final String INVALID = "{\"code\":\"INVALID_CREDENTIALS\",\"message\":\"Incorrect email or password.\"}";

    private ScreenTestSupport.FakeApi api;
    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ApiClient.setServiceForTests(api.proxy());
        SessionManager.clear(app);
        FailedSignIns.setClockForTests(null);
        for (String email : new String[]{"ana@example.com", "nobody@example.com", "other@example.com"}) FailedSignIns.clear(app, email);
    }

    @After
    public void tearDown() {
        ApiClient.setServiceForTests(null);
        FailedSignIns.setClockForTests(null);
        RuntimeEnvironment.setFontScale(1.0f);
        RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
    }

    // ---- helpers ----

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
    }

    private static void layout(android.app.Activity a) {
        View decor = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels;
        int h = a.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
    }

    private static String text(android.app.Activity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private static void type(android.app.Activity a, int id, String value) {
        ((TextView) a.findViewById(id)).setText(value);
        idle();
    }

    private static int vis(android.app.Activity a, int id) {
        return a.findViewById(id).getVisibility();
    }

    private static String errorOf(android.app.Activity a, int layoutId) {
        CharSequence e = ((TextInputLayout) a.findViewById(layoutId)).getError();
        return e == null ? null : e.toString();
    }

    private static void collectText(View v, List<String> out) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof TextView && ((TextView) v).getText().length() > 0) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collectText(((ViewGroup) v).getChildAt(i), out);
        }
    }

    private static String allText(android.app.Activity a) {
        List<String> out = new ArrayList<>();
        collectText(a.getWindow().getDecorView(), out);
        return String.join(" | ", out);
    }

    private ResetPasswordActivity reset() {
        return reset(null, false);
    }

    private ResetPasswordActivity reset(String email, boolean trouble) {
        Intent intent = new Intent(app, ResetPasswordActivity.class);
        if (email != null) intent.putExtra(ResetPasswordActivity.EXTRA_EMAIL, email);
        if (trouble) intent.putExtra(ResetPasswordActivity.EXTRA_TROUBLE, true);
        ResetPasswordActivity a = Robolectric.buildActivity(ResetPasswordActivity.class, intent).setup().get();
        settle();
        return a;
    }

    private ResetPasswordActivity atCodeStep(String email) {
        ResetPasswordActivity a = reset();
        type(a, R.id.resetEmailEditText, email);
        a.findViewById(R.id.sendCodeButton).performClick();
        idle();
        api.<ApiMessage>last("forgotPassword").succeed(new ApiMessage());
        idle();
        assertEquals(View.VISIBLE, vis(a, R.id.resetStep2));
        return a;
    }

    private static List<EditText> boxes(android.app.Activity a) {
        LinearLayout c = a.findViewById(R.id.resetOtpBoxes);
        List<EditText> out = new ArrayList<>();
        for (int i = 0; i < c.getChildCount(); i++) out.add((EditText) c.getChildAt(i));
        return out;
    }

    private static void enterCode(android.app.Activity a, String code) {
        List<EditText> b = boxes(a);
        for (int i = 0; i < code.length(); i++) {
            b.get(i).setText(String.valueOf(code.charAt(i)));
            idle();
        }
    }

    private ResetPasswordActivity atNewPassword(String email) {
        ResetPasswordActivity a = atCodeStep(email);
        enterCode(a, "123456");
        api.<ApiMessage>last("verifyResetOtp").succeed(new ApiMessage());
        idle();
        assertEquals(View.VISIBLE, vis(a, R.id.resetStep3));
        return a;
    }

    private static AuthResponse signedIn(String email) {
        AuthResponse r = new AuthResponse();
        r.token = "new-token";
        r.user = new UserDto();
        r.user.id = 7L;
        r.user.first_name = "Ana";
        r.user.last_name = "Reyes";
        r.user.full_name = "Ana Reyes";
        r.user.email = email;
        r.user.account_status = "active";
        return r;
    }

    // ================================================================ 1. the logo is never overlapped

    private static Rect boundsOf(android.app.Activity a, int id) {
        return LayoutHarness.boundsIn(a.getWindow().getDecorView(), a.findViewById(id));
    }

    private static void assertLogoClear(String tag, android.app.Activity a) {
        layout(a);
        Rect logo = boundsOf(a, R.id.logoContainer);
        Rect curve = boundsOf(a, R.id.curvedHeaderView);
        Rect screen = new Rect(0, 0, a.getResources().getDisplayMetrics().widthPixels, a.getResources().getDisplayMetrics().heightPixels);
        assertTrue(tag + ": the logo has a real size " + logo, logo.width() > 0 && logo.height() > 0);
        assertTrue(tag + ": the whole logo is on the screen " + logo, screen.contains(logo));
        assertTrue(tag + ": the whole logo is inside the red header " + logo + " in " + curve, curve.contains(logo));
        View column = a.findViewById(R.id.resetColumn);
        ViewGroup col = (ViewGroup) column;
        View header = a.findViewById(R.id.resetHeader);
        for (int i = 0; i < col.getChildCount(); i++) {
            View child = col.getChildAt(i);
            if (child == header || child.getVisibility() != View.VISIBLE) continue;
            Rect r = LayoutHarness.boundsIn(a.getWindow().getDecorView(), child);
            assertFalse(tag + ": " + a.getResources().getResourceEntryName(child.getId()) + " " + r + " overlaps the logo " + logo, Rect.intersects(r, logo));
            assertTrue(tag + ": nothing starts above the header's end", r.top >= boundsOf(a, R.id.taglineLabel).bottom - 1);
        }
        assertFalse(tag + ": the back arrow is not on the logo", Rect.intersects(boundsOf(a, R.id.btnBack), logo));
    }

    @Test
    public void theLogo_isFullyVisibleAndNotOverlapped_atEverySizeThemeAndFontScale_onEveryStep() {
        String[] sizes = {"w360dp-h800dp", "w393dp-h851dp"};
        String[] themes = {"notnight", "night"};
        float[] fonts = {1.0f, 2.0f};
        for (String size : sizes) {
            for (String theme : themes) {
                for (float font : fonts) {
                    RuntimeEnvironment.setQualifiers("+" + size + "-" + theme);
                    RuntimeEnvironment.setFontScale(font);
                    api = new ScreenTestSupport.FakeApi();
                    ApiClient.setServiceForTests(api.proxy());
                    String tag = size + " " + theme + " x" + font;

                    ResetPasswordActivity a = atNewPasswordStepsChecked(tag);
                    assertNotNull(a);
                }
            }
        }
    }

    private ResetPasswordActivity atNewPasswordStepsChecked(String tag) {
        ResetPasswordActivity a = reset();
        assertLogoClear(tag + " step 1", a);
        type(a, R.id.resetEmailEditText, "ana@example.com");
        a.findViewById(R.id.sendCodeButton).performClick();
        idle();
        api.<ApiMessage>last("forgotPassword").succeed(new ApiMessage());
        idle();
        assertLogoClear(tag + " step 2", a);
        enterCode(a, "123456");
        api.<ApiMessage>last("verifyResetOtp").succeed(new ApiMessage());
        idle();
        assertLogoClear(tag + " step 3", a);
        return a;
    }

    @Test
    public void theHeader_isTheSameAsSignIn_sameHeightLogoSizeAndBackArrow() {
        for (String q : new String[]{"w360dp-h800dp-notnight", "w393dp-h851dp-night"}) {
            RuntimeEnvironment.setQualifiers("+" + q);
            LoginActivity in = Robolectric.buildActivity(LoginActivity.class).setup().get();
            settle();
            layout(in);
            ResetPasswordActivity re = reset();
            layout(re);
            for (int id : new int[]{R.id.curvedHeaderView, R.id.logoContainer, R.id.btnBack, R.id.appNameLabel, R.id.taglineLabel}) {
                Rect x = boundsOf(in, id);
                Rect y = boundsOf(re, id);
                assertEquals(q + " " + id + " width", x.width(), y.width());
                assertEquals(q + " " + id + " height", x.height(), y.height());
                assertEquals(q + " " + id + " left", x.left, y.left);
                assertEquals(q + " " + id + " top", x.top, y.top);
            }
        }
    }

    @Test
    public void everyStep_isReadable_inLightAndDark_at360And393() {
        String[] qs = {"w393dp-h851dp-notnight", "w360dp-h800dp-night"};
        for (String q : qs) {
            RuntimeEnvironment.setQualifiers("+" + q);
            api = new ScreenTestSupport.FakeApi();
            ApiClient.setServiceForTests(api.proxy());
            ResetPasswordActivity a = reset();
            Release160Test.assertReadable(q + " step 1", a, a.getWindow().getDecorView());
            type(a, R.id.resetEmailEditText, "ana@example.com");
            a.findViewById(R.id.sendCodeButton).performClick();
            idle();
            api.<ApiMessage>last("forgotPassword").succeed(new ApiMessage());
            idle();
            Release160Test.assertReadable(q + " step 2", a, a.getWindow().getDecorView());
            enterCode(a, "123456");
            api.<ApiMessage>last("verifyResetOtp").succeed(new ApiMessage());
            idle();
            type(a, R.id.resetNewPasswordEditText, "abcdefg1");
            Release160Test.assertReadable(q + " step 3", a, a.getWindow().getDecorView());
        }
    }

    // ================================================================ 2. step 1: your email

    @Test
    public void step1_hasTheNewWords_aLockIcon_andNoSignUpOrOtpText() {
        ResetPasswordActivity a = reset();
        assertEquals("Reset your password", text(a, R.id.resetHeading));
        assertEquals("Step 1 of 3 · Your email", text(a, R.id.resetStepLine));
        TextInputLayout email = a.findViewById(R.id.resetEmailLayout);
        assertEquals("Email", String.valueOf(email.getHint()));
        assertEquals("Enter the email you used for your Velocity Suites account", String.valueOf(email.getHelperText()));
        assertEquals("Send code", text(a, R.id.sendCodeButton));
        assertEquals("Back to sign in", text(a, R.id.backToSignInLink));
        String all = allText(a);
        assertFalse(all, all.contains("Use an email address you can open"));
        assertFalse("guests read 'code', never 'OTP': " + all, all.contains("OTP"));
        assertEquals("the red info icon is gone: a lock is there", View.VISIBLE, vis(a, R.id.resetLockIcon));
        assertEquals(R.drawable.ic_lock, shadowOf(((android.widget.ImageView) a.findViewById(R.id.resetLockIcon)).getDrawable()).getCreatedFromResId());
        assertEquals("no banner when the guest came on their own", View.GONE, vis(a, R.id.resetBanner));
    }

    @Test
    public void step1_checksTheEmailBeforeAnythingIsSent() {
        ResetPasswordActivity a = reset();
        a.findViewById(R.id.sendCodeButton).performClick();
        idle();
        assertEquals("Email is required", errorOf(a, R.id.resetEmailLayout));
        type(a, R.id.resetEmailEditText, "not-an-email");
        a.findViewById(R.id.sendCodeButton).performClick();
        idle();
        assertNotNull(errorOf(a, R.id.resetEmailLayout));
        assertEquals("nothing was sent", 0, api.count("forgotPassword"));
    }

    @Test
    public void step1_backToSignIn_returnsToSignIn() {
        ResetPasswordActivity a = reset();
        a.findViewById(R.id.backToSignInLink).performClick();
        assertTrue(a.isFinishing());
        ResetPasswordActivity b = reset();
        b.getOnBackPressedDispatcher().onBackPressed();
        assertTrue("the back arrow leaves without asking on step 1", b.isFinishing());
        assertEquals(0, ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void step1_cameFromThreeFailures_showsWhy_andTheEmailIsFilledIn() {
        ResetPasswordActivity a = reset("ana@example.com", true);
        assertEquals(View.VISIBLE, vis(a, R.id.resetBanner));
        assertEquals("Having trouble signing in? Let's reset your password.", text(a, R.id.resetBanner));
        assertEquals("ana@example.com", text(a, R.id.resetEmailEditText));
    }

    // ================================================================ 3. the same answer for every address

    @Test
    public void asking_forACode_getsTheSameWords_forAKnownAndAnUnknownAddress() {
        for (String email : new String[]{"ana@example.com", "nobody@example.com"}) {
            api = new ScreenTestSupport.FakeApi();
            ApiClient.setServiceForTests(api.proxy());
            ResetPasswordActivity a = atCodeStep(email);
            assertEquals(email, SENT, text(a, R.id.resetSentNote));
            assertEquals(email, "We sent a 6-digit code to " + email.charAt(0) + "***" + email.substring(email.indexOf('@')), text(a, R.id.resetCodeDesc));
            assertFalse("the full address is never shown: " + allText(a), allText(a).contains(email));
            assertEquals("Step 2 of 3 · Check your email", text(a, R.id.resetStepLine));
            assertEquals("Enter the code", text(a, R.id.resetHeading));
            assertEquals(View.GONE, vis(a, R.id.resetStep1));
            assertEquals("no popup", 0, ShadowToast.shownToastCount());
        }
    }

    @Test
    public void theWaitAMomentAnswer_isShownUnderTheEmail() {
        ResetPasswordActivity a = reset();
        type(a, R.id.resetEmailEditText, "ana@example.com");
        a.findViewById(R.id.sendCodeButton).performClick();
        idle();
        api.<ApiMessage>last("forgotPassword").httpWithBody(429, "{\"message\":\"Please wait 30 seconds before requesting another code.\"}");
        idle();
        assertEquals("Please wait 30 seconds before requesting another code.", text(a, R.id.resetEmailError));
        assertEquals(View.VISIBLE, vis(a, R.id.resetStep1));
        assertTrue(a.findViewById(R.id.sendCodeButton).isEnabled());
        type(a, R.id.resetEmailEditText, "ana2@example.com");
        assertEquals("typing clears it", View.GONE, vis(a, R.id.resetEmailError));
    }

    @Test
    public void noConnection_isSaidInPlainWords_underTheEmail() {
        ResetPasswordActivity a = reset();
        type(a, R.id.resetEmailEditText, "ana@example.com");
        a.findViewById(R.id.sendCodeButton).performClick();
        idle();
        api.<ApiMessage>last("forgotPassword").offline();
        idle();
        assertEquals(View.VISIBLE, vis(a, R.id.resetEmailError));
        assertEquals(View.VISIBLE, vis(a, R.id.resetStep1));
        assertEquals(0, ShadowToast.shownToastCount());
    }

    // ================================================================ 4. step 2: the code

    @Test
    public void step2_hasSixNumericBoxes_thatAutoAdvance_andSubmitWhenFull() {
        ResetPasswordActivity a = atCodeStep("ana@example.com");
        List<EditText> b = boxes(a);
        assertEquals(6, b.size());
        for (EditText box : b) assertEquals("numeric keyboard", android.text.InputType.TYPE_CLASS_NUMBER, box.getInputType() & android.text.InputType.TYPE_MASK_CLASS);
        for (int i = 0; i < 5; i++) {
            b.get(i).setText(String.valueOf(i + 1));
            idle();
            assertTrue("auto-advance to box " + (i + 2), b.get(i + 1).hasFocus());
        }
        assertEquals("not submitted until complete", 0, api.count("verifyResetOtp"));
        b.get(5).setText("6");
        idle();
        assertEquals("auto-submits when complete", 1, api.count("verifyResetOtp"));
        VerifyResetOtpRequest sent = (VerifyResetOtpRequest) api.lastArgs.get("verifyResetOtp")[0];
        assertEquals("123456", sent.otp);
        assertEquals("ana@example.com", sent.email);
    }

    @Test
    public void step2_pasteFillsAllBoxes_andBackspaceMovesBack() {
        ResetPasswordActivity a = atCodeStep("ana@example.com");
        List<EditText> b = boxes(a);
        b.get(0).setText("654321"); // a pasted code lands in the focused box
        idle();
        StringBuilder got = new StringBuilder();
        for (EditText box : b) got.append(box.getText());
        assertEquals("654321", got.toString());
        assertEquals("the pasted code is checked", 1, api.count("verifyResetOtp"));

        // Backspace on an empty box clears the one before it and moves back
        api.<ApiMessage>last("verifyResetOtp").httpWithBody(422, "{\"message\":\"Invalid or expired code.\"}");
        idle();
        List<EditText> again = boxes(a);
        again.get(0).setText("1");
        again.get(1).setText("2");
        idle();
        assertTrue(again.get(2).hasFocus());
        again.get(2).dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL));
        idle();
        assertTrue("moved back to box 2", again.get(1).hasFocus());
        assertEquals("", again.get(1).getText().toString());
    }

    @Test
    public void step2_aWrongCode_isShownInline_theBoxesEmpty_andTheGuestCanTryAgain() {
        ResetPasswordActivity a = atCodeStep("ana@example.com");
        enterCode(a, "111111");
        api.<ApiMessage>last("verifyResetOtp").httpWithBody(422, "{\"message\":\"Invalid or expired code.\"}");
        idle();
        assertEquals("That code isn't right or has expired. Check it and try again, or ask for a new one.", text(a, R.id.resetCodeError));
        assertEquals(View.VISIBLE, vis(a, R.id.resetCodeError));
        for (EditText box : boxes(a)) {
            assertEquals("", box.getText().toString());
            assertTrue(box.isEnabled());
        }
        assertEquals(View.VISIBLE, vis(a, R.id.resetStep2));
        assertEquals(0, ShadowToast.shownToastCount());
        enterCode(a, "222222");
        assertEquals("a second try goes out", 2, api.count("verifyResetOtp"));
        assertEquals("the old message is gone while typing", View.GONE, vis(a, R.id.resetCodeError));
    }

    @Test
    public void step2_tooManyWrongCodes_showsTheServersWords_inline() {
        ResetPasswordActivity a = atCodeStep("ana@example.com");
        enterCode(a, "111111");
        api.<ApiMessage>last("verifyResetOtp").httpWithBody(429, "{\"message\":\"Too many incorrect codes. Please request a new code.\"}");
        idle();
        assertEquals("Too many incorrect codes. Please request a new code.", text(a, R.id.resetCodeError));
    }

    @Test
    public void step2_resendHasACountdown_andAsksAgainWhenItEnds() {
        ResetPasswordActivity a = atCodeStep("ana@example.com");
        TextView resend = a.findViewById(R.id.resetResendLink);
        assertFalse(resend.isEnabled());
        assertTrue(resend.getText().toString(), resend.getText().toString().startsWith("Resend code in "));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31));
        assertTrue(resend.isEnabled());
        assertEquals("Resend code", resend.getText().toString());
        resend.performClick();
        idle();
        assertEquals("a new code was asked for", 2, api.count("forgotPassword"));
        api.<ApiMessage>last("forgotPassword").succeed(new ApiMessage());
        idle();
        assertFalse("the wait starts again", resend.isEnabled());
        assertTrue(resend.getText().toString().startsWith("Resend code in "));
    }

    @Test
    public void step2_changeEmail_goesBackToStep1_keepingTheAddress() {
        ResetPasswordActivity a = atCodeStep("ana@example.com");
        a.findViewById(R.id.resetChangeEmailLink).performClick();
        idle();
        assertEquals(View.VISIBLE, vis(a, R.id.resetStep1));
        assertEquals(View.GONE, vis(a, R.id.resetStep2));
        assertEquals("ana@example.com", text(a, R.id.resetEmailEditText));
        assertEquals("Reset your password", text(a, R.id.resetHeading));
    }

    @Test
    public void step2_aNetworkFailure_isSaidInline_andTheBoxesStayUsable() {
        ResetPasswordActivity a = atCodeStep("ana@example.com");
        enterCode(a, "123456");
        api.<ApiMessage>last("verifyResetOtp").offline();
        idle();
        assertEquals(View.VISIBLE, vis(a, R.id.resetCodeError));
        for (EditText box : boxes(a)) assertTrue(box.isEnabled());
    }

    // ================================================================ 5. step 3: a new password

    @Test
    public void step3_usesTheSharedChecklist_andOnlyAGoodMatchingPasswordCanBeSaved() {
        ResetPasswordActivity a = atNewPassword("ana@example.com");
        assertEquals("Create a new password", text(a, R.id.resetHeading));
        assertEquals("Step 3 of 3 · New password", text(a, R.id.resetStepLine));
        assertEquals("Save and sign in", text(a, R.id.saveAndSignInButton));
        for (int id : new int[]{R.id.ruleLength, R.id.ruleLetter, R.id.ruleNumber, R.id.ruleMatch, R.id.passwordStrengthBar, R.id.passwordStrengthText}) {
            assertEquals(View.VISIBLE, vis(a, id));
        }
        assertFalse("nothing typed yet", a.findViewById(R.id.saveAndSignInButton).isEnabled());
        for (String weak : new String[]{"abcdefgh", "12345678", "abc12", "short1"}) {
            type(a, R.id.resetNewPasswordEditText, weak);
            type(a, R.id.resetConfirmPasswordEditText, weak);
            assertFalse(weak + " cannot be saved", a.findViewById(R.id.saveAndSignInButton).isEnabled());
        }
        type(a, R.id.resetNewPasswordEditText, "abcdefg1");
        type(a, R.id.resetConfirmPasswordEditText, "abcdefg2");
        assertFalse("both must match", a.findViewById(R.id.saveAndSignInButton).isEnabled());
        assertTrue(text(a, R.id.ruleMatch).startsWith("○"));
        type(a, R.id.resetConfirmPasswordEditText, "abcdefg1");
        assertTrue(text(a, R.id.ruleLength).startsWith("✓") && text(a, R.id.ruleLetter).startsWith("✓") && text(a, R.id.ruleNumber).startsWith("✓") && text(a, R.id.ruleMatch).startsWith("✓"));
        assertTrue(a.findViewById(R.id.saveAndSignInButton).isEnabled());
        assertEquals("nothing weak ever reached the server", 0, api.count("resetPassword"));
    }

    @Test
    public void saving_signsTheGuestIn_opensTheDashboard_andClearsTheFailedCount() {
        FailedSignIns.record(app, "ana@example.com");
        FailedSignIns.record(app, "ana@example.com");
        ResetPasswordActivity a = atNewPassword("ana@example.com");
        type(a, R.id.resetNewPasswordEditText, "abcdefg1");
        type(a, R.id.resetConfirmPasswordEditText, "abcdefg1");
        a.findViewById(R.id.saveAndSignInButton).performClick();
        idle();
        assertFalse("one tap, one request: the button waits", a.findViewById(R.id.saveAndSignInButton).isEnabled());
        a.findViewById(R.id.saveAndSignInButton).performClick();
        assertEquals(1, api.count("resetPassword"));
        ResetPasswordRequest sent = (ResetPasswordRequest) api.lastArgs.get("resetPassword")[0];
        assertEquals("ana@example.com", sent.email);
        assertEquals("123456", sent.otp);
        assertEquals("abcdefg1", sent.password);
        assertEquals("abcdefg1", sent.password_confirmation);

        api.<AuthResponse>last("resetPassword").succeed(signedIn("ana@example.com"));
        idle();
        assertTrue("signed in with the session the server returned", SessionManager.isLoggedIn(app));
        assertEquals("new-token", SessionManager.getToken(app));
        Intent next = shadowOf(a).getNextStartedActivity();
        assertEquals(DashboardActivity.class.getName(), next.getComponent().getClassName());
        assertTrue(a.isFinishing());
        assertEquals("the failed-attempt count is gone", 0, FailedSignIns.count(app, "ana@example.com"));
    }

    @Test
    public void afterSaving_theDashboardShowsThePasswordChangedLine_once() {
        SessionManager.saveSession(app, "old-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com", "09171234567", "Female", "Jan 01, 1990", "");
        ScreenTestSupport.goOnline(app);
        DashboardActivity d = Robolectric.buildActivity(DashboardActivity.class).setup().get();
        idle();
        ResetPasswordActivity r = atNewPassword("ana@example.com");
        type(r, R.id.resetNewPasswordEditText, "abcdefg1");
        type(r, R.id.resetConfirmPasswordEditText, "abcdefg1");
        r.findViewById(R.id.saveAndSignInButton).performClick();
        idle();
        api.<AuthResponse>last("resetPassword").succeed(signedIn("ana@example.com"));
        idle();
        new com.example.velocitysuites.Feedback().onActivityResumed(d); // what the app does when the Dashboard comes to the front
        idle();
        TextView line = d.findViewById(com.google.android.material.R.id.snackbar_text);
        assertNotNull("the line is on the screen", line);
        assertEquals("Password changed. Welcome back!", line.getText().toString());

        new com.example.velocitysuites.Feedback().onActivityResumed(d);
        idle();
        assertEquals("it is shown once", 1, countSnackbars(d));
    }

    private static int countSnackbars(android.app.Activity a) {
        ViewGroup content = a.findViewById(android.R.id.content);
        int n = 0;
        for (int i = 0; i < content.getChildCount(); i++) {
            if (content.getChildAt(i) instanceof com.google.android.material.snackbar.Snackbar.SnackbarLayout) n++;
        }
        return n;
    }

    @Test
    public void saving_aServerRefusal_isShownUnderTheButton_andTheGuestCanRetry() {
        ResetPasswordActivity a = atNewPassword("ana@example.com");
        type(a, R.id.resetNewPasswordEditText, "abcdefg1");
        type(a, R.id.resetConfirmPasswordEditText, "abcdefg1");
        a.findViewById(R.id.saveAndSignInButton).performClick();
        idle();
        api.<AuthResponse>last("resetPassword").httpWithBody(500, "{\"message\":\"Something went wrong.\"}");
        idle();
        assertEquals("Something went wrong.", text(a, R.id.resetSaveError));
        assertTrue(a.findViewById(R.id.saveAndSignInButton).isEnabled());
        assertFalse(SessionManager.isLoggedIn(app));
        assertEquals("nothing started", null, shadowOf(a).getNextStartedActivity());
    }

    @Test
    public void saving_aCodeThatExpiredMeanwhile_goesBackToStep2_withAMessage() {
        ResetPasswordActivity a = atNewPassword("ana@example.com");
        type(a, R.id.resetNewPasswordEditText, "abcdefg1");
        type(a, R.id.resetConfirmPasswordEditText, "abcdefg1");
        a.findViewById(R.id.saveAndSignInButton).performClick();
        idle();
        api.<AuthResponse>last("resetPassword").httpWithBody(422, "{\"message\":\"Invalid or expired code.\"}");
        idle();
        assertEquals(View.VISIBLE, vis(a, R.id.resetStep2));
        assertEquals(View.VISIBLE, vis(a, R.id.resetCodeError));
        assertFalse(SessionManager.isLoggedIn(app));
    }

    // ================================================================ 6. leaving

    private static void backPressed(ResetPasswordActivity a) {
        a.getOnBackPressedDispatcher().onBackPressed();
        idle();
    }

    @Test
    public void back_fromStep2And3_asksToStop_andKeepGoingStays() {
        for (int step = 2; step <= 3; step++) {
            api = new ScreenTestSupport.FakeApi();
            ApiClient.setServiceForTests(api.proxy());
            ResetPasswordActivity a = step == 2 ? atCodeStep("ana@example.com") : atNewPassword("ana@example.com");
            backPressed(a);
            AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
            assertTrue(d.isShowing());
            assertEquals("Stop resetting your password?", ((TextView) d.findViewById(androidx.appcompat.R.id.alertTitle)).getText().toString());
            d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            idle();
            assertFalse("Keep going stays on the step", a.isFinishing());
            assertEquals(View.VISIBLE, vis(a, step == 2 ? R.id.resetStep2 : R.id.resetStep3));
        }
    }

    @Test
    public void back_fromStep3_thenStop_leavesWithoutSavingAnything() {
        ResetPasswordActivity a = atNewPassword("ana@example.com");
        a.findViewById(R.id.btnBack).performClick();
        idle();
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        idle();
        assertTrue(a.isFinishing());
        assertEquals(0, api.count("resetPassword"));
        assertFalse(SessionManager.isLoggedIn(app));
    }

    @Test
    public void rotation_keepsTheStep_theEmailAndTheVerifiedCode() {
        ActivityController<ResetPasswordActivity> controller = Robolectric.buildActivity(ResetPasswordActivity.class).setup();
        ResetPasswordActivity a = controller.get();
        settle();
        type(a, R.id.resetEmailEditText, "ana@example.com");
        a.findViewById(R.id.sendCodeButton).performClick();
        idle();
        api.<ApiMessage>last("forgotPassword").succeed(new ApiMessage());
        idle();
        enterCode(a, "123456");
        api.<ApiMessage>last("verifyResetOtp").succeed(new ApiMessage());
        idle();
        controller.recreate();
        idle();
        ResetPasswordActivity b = controller.get();
        assertEquals(View.VISIBLE, vis(b, R.id.resetStep3));
        type(b, R.id.resetNewPasswordEditText, "abcdefg1");
        type(b, R.id.resetConfirmPasswordEditText, "abcdefg1");
        b.findViewById(R.id.saveAndSignInButton).performClick();
        idle();
        ResetPasswordRequest sent = (ResetPasswordRequest) api.lastArgs.get("resetPassword")[0];
        assertEquals("ana@example.com", sent.email);
        assertEquals("123456", sent.otp);
    }

    // ================================================================ 7. the redirect after three failed sign-ins

    private LoginActivity signIn() {
        LoginActivity a = Robolectric.buildActivity(LoginActivity.class).setup().get();
        settle();
        return a;
    }

    private static void failOnce(LoginActivity a, ScreenTestSupport.FakeApi api, String email, String body) {
        type(a, R.id.emailEditText, email);
        type(a, R.id.passwordEditText, "Wrong123");
        a.findViewById(R.id.signInButton).performClick();
        idle();
        api.<Object>last("login").httpWithBody(401, body);
        idle();
    }

    private static Intent started(LoginActivity a) {
        return shadowOf(a).getNextStartedActivity();
    }

    @Test
    public void theThirdFailure_forTheSameEmail_opensResetPassword_withTheEmailFilledIn() {
        LoginActivity a = signIn();
        failOnce(a, api, "ana@example.com", INVALID);
        assertNull("the first failure stays on Sign in", started(a));
        failOnce(a, api, "ana@example.com", INVALID);
        assertNull("so does the second", started(a));
        assertEquals("Incorrect email or password.", text(a, R.id.loginError));

        failOnce(a, api, "ana@example.com", INVALID);
        Intent next = started(a);
        assertNotNull("the third opens Reset password", next);
        assertEquals(ResetPasswordActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("ana@example.com", next.getStringExtra(ResetPasswordActivity.EXTRA_EMAIL));
        assertTrue(next.getBooleanExtra(ResetPasswordActivity.EXTRA_TROUBLE, false));

        // and that screen says why, with the email filled in, and can go back to Sign in
        ResetPasswordActivity r = Robolectric.buildActivity(ResetPasswordActivity.class, next).setup().get();
        settle();
        assertEquals("Having trouble signing in? Let's reset your password.", text(r, R.id.resetBanner));
        assertEquals("ana@example.com", text(r, R.id.resetEmailEditText));
        r.findViewById(R.id.backToSignInLink).performClick();
        assertTrue(r.isFinishing());
    }

    @Test
    public void anAddressWithNoAccount_behavesExactlyTheSame() {
        LoginActivity known = signIn();
        for (int i = 0; i < 3; i++) failOnce(known, api, "ana@example.com", INVALID);
        Intent a = started(known);
        LoginActivity unknown = signIn();
        for (int i = 0; i < 3; i++) failOnce(unknown, api, "nobody@example.com", INVALID);
        Intent b = started(unknown);
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(a.getComponent(), b.getComponent());
        assertEquals("nobody@example.com", b.getStringExtra(ResetPasswordActivity.EXTRA_EMAIL));
        assertEquals(a.getBooleanExtra(ResetPasswordActivity.EXTRA_TROUBLE, false), b.getBooleanExtra(ResetPasswordActivity.EXTRA_TROUBLE, false));
    }

    @Test
    public void failuresAreCounted_perEmail_notAcrossEmails() {
        LoginActivity a = signIn();
        failOnce(a, api, "ana@example.com", INVALID);
        failOnce(a, api, "other@example.com", INVALID);
        failOnce(a, api, "ana@example.com", INVALID);
        failOnce(a, api, "other@example.com", INVALID);
        assertNull("two for each address is not three for either", started(a));
        failOnce(a, api, "ANA@example.com ", INVALID);
        assertNotNull("the same address in other letters is the same address", started(a));
    }

    private static void signInAndAnswer(LoginActivity a, ScreenTestSupport.FakeApi api, int status, String body) {
        type(a, R.id.emailEditText, "ana@example.com");
        type(a, R.id.passwordEditText, "Wrong123");
        a.findViewById(R.id.signInButton).performClick();
        idle();
        if (status == 0) {
            api.<Object>last("login").offline();
        } else {
            api.<Object>last("login").httpWithBody(status, body);
        }
        idle();
    }

    @Test
    public void networkErrors_lockedAndBlockedAccounts_neverCount() {
        LoginActivity a = signIn();
        for (int i = 0; i < 4; i++) signInAndAnswer(a, api, 0, null);
        for (int i = 0; i < 4; i++) signInAndAnswer(a, api, 423, "{\"code\":\"ACCOUNT_LOCKED\",\"message\":\"Account locked.\"}");
        for (int i = 0; i < 4; i++) signInAndAnswer(a, api, 403, "{\"code\":\"ACCOUNT_BLOCKED\",\"message\":\"This account is blocked. Please contact the hotel.\"}");
        for (int i = 0; i < 4; i++) signInAndAnswer(a, api, 401, "{\"code\":\"ACCOUNT_NOT_VERIFIED\",\"message\":\"This account is not verified yet.\"}");
        assertEquals("nothing was counted", 0, FailedSignIns.count(app, "ana@example.com"));
        assertNull(started(a));
        // two real failures after all that are still only two
        failOnce(a, api, "ana@example.com", INVALID);
        failOnce(a, api, "ana@example.com", INVALID);
        assertNull(started(a));
    }

    @Test
    public void aSuccessfulSignIn_resetsTheCount() {
        LoginActivity a = signIn();
        failOnce(a, api, "ana@example.com", INVALID);
        failOnce(a, api, "ana@example.com", INVALID);
        assertEquals(2, FailedSignIns.count(app, "ana@example.com"));

        type(a, R.id.emailEditText, "ana@example.com");
        type(a, R.id.passwordEditText, "Right123");
        a.findViewById(R.id.signInButton).performClick();
        idle();
        api.<AuthResponse>last("login").succeed(signedIn("ana@example.com"));
        idle();
        assertEquals(0, FailedSignIns.count(app, "ana@example.com"));
        assertEquals(DashboardActivity.class.getName(), started(a).getComponent().getClassName());

        SessionManager.clear(app);
        LoginActivity again = signIn();
        failOnce(again, api, "ana@example.com", INVALID);
        failOnce(again, api, "ana@example.com", INVALID);
        assertNull("two more is two, not four", started(again));
    }

    @Test
    public void aCompletedReset_resetsTheCount() {
        FailedSignIns.record(app, "ana@example.com");
        FailedSignIns.record(app, "ana@example.com");
        ResetPasswordActivity r = atNewPassword("ana@example.com");
        type(r, R.id.resetNewPasswordEditText, "abcdefg1");
        type(r, R.id.resetConfirmPasswordEditText, "abcdefg1");
        r.findViewById(R.id.saveAndSignInButton).performClick();
        idle();
        api.<AuthResponse>last("resetPassword").succeed(signedIn("ana@example.com"));
        idle();
        assertEquals(0, FailedSignIns.count(app, "ana@example.com"));
    }

    @Test
    public void theCountForgetsItself_after15Minutes() {
        AtomicLong now = new AtomicLong(1_000_000L);
        FailedSignIns.setClockForTests(now::get);
        LoginActivity a = signIn();
        failOnce(a, api, "ana@example.com", INVALID);
        failOnce(a, api, "ana@example.com", INVALID);
        now.addAndGet(FailedSignIns.WINDOW_MS - 1);
        assertEquals("just inside the window", 2, FailedSignIns.count(app, "ana@example.com"));
        now.addAndGet(1);
        assertEquals("15 minutes later it is forgotten", 0, FailedSignIns.count(app, "ana@example.com"));
        failOnce(a, api, "ana@example.com", INVALID);
        assertNull("so the next failure is the first again", started(a));
        assertEquals(1, FailedSignIns.count(app, "ana@example.com"));
    }

    @Test
    public void afterTheRedirect_theGuestStartsFromZero_whenTheyComeBackToSignIn() {
        LoginActivity a = signIn();
        for (int i = 0; i < 3; i++) failOnce(a, api, "ana@example.com", INVALID);
        assertNotNull(started(a));
        assertEquals(0, FailedSignIns.count(app, "ana@example.com"));
        failOnce(a, api, "ana@example.com", INVALID);
        failOnce(a, api, "ana@example.com", INVALID);
        assertNull("the redirect does not fire on every failure after it", started(a));
    }

    @Test
    public void forgotPasswordOnSignIn_opensResetPassword_withTheTypedEmail() {
        LoginActivity a = signIn();
        type(a, R.id.emailEditText, "ana@example.com");
        a.findViewById(R.id.forgotPasswordText).performClick();
        Intent next = started(a);
        assertEquals(ResetPasswordActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("ana@example.com", next.getStringExtra(ResetPasswordActivity.EXTRA_EMAIL));
        assertFalse("no 'having trouble' line: the guest chose this", next.getBooleanExtra(ResetPasswordActivity.EXTRA_TROUBLE, false));
    }

    @Test
    public void aResetThatNeedsRestoreOrReactivation_sendsTheGuestToSignInWithANotice() {
        ResetPasswordActivity a = atNewPassword("ana@example.com");
        type(a, R.id.resetNewPasswordEditText, "abcdefg1");
        type(a, R.id.resetConfirmPasswordEditText, "abcdefg1");
        a.findViewById(R.id.saveAndSignInButton).performClick();
        idle();
        AuthResponse r = signedIn("ana@example.com");
        r.user.account_status = "pending_deletion";
        api.<AuthResponse>last("resetPassword").succeed(r);
        idle();
        assertFalse("no session is kept for an account waiting to be restored", SessionManager.isLoggedIn(app));
        Intent next = shadowOf(a).getNextStartedActivity();
        assertEquals(LoginActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("Your password was changed. Sign in with it to continue.", next.getStringExtra(LoginActivity.EXTRA_NOTICE));
        LoginActivity in = Robolectric.buildActivity(LoginActivity.class, next).setup().get();
        settle();
        assertEquals("Your password was changed. Sign in with it to continue.", text(in, R.id.loginError));
    }
}
