package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.LoginActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.RegistrationActivity;
import com.example.velocitysuites.network.ApiClient;
import com.example.velocitysuites.network.SessionManager;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.65.0: Sign in and Create account. Sign in: a shorter header with a plain back arrow, floating labels, no sign-up helper on the email
 * field, no footer promises, inline validation and inline server errors (never a popup), "Resend code" for an unverified account. Create
 * account: six short steps under one compact header with Next always on screen, a code screen that names the (masked) email, six code boxes,
 * and everything readable at 360dp and 393dp, light and dark, and at 1.3x and 2.0x font size.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Release165Test {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static final String[] CONFIGS = {"w360dp-h800dp-notnight", "w360dp-h800dp-night", "w393dp-h851dp-notnight", "w393dp-h851dp-night"};
    private static final String[] STEP_TITLES = {"Your name", "About you", "Contact", "Address", "Password and photo", "Review and terms"};

    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(ApplicationProvider.getApplicationContext(), api);
        ApiClient.setServiceForTests(api.proxy());
        SessionManager.clear(ApplicationProvider.getApplicationContext());
    }

    @After
    public void tearDown() {
        ApiClient.setServiceForTests(null);
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

    private static void texts(View v, List<String> out) {
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE && ((TextView) v).getText().length() > 0) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup && v.getVisibility() == View.VISIBLE) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) texts(((ViewGroup) v).getChildAt(i), out);
        }
    }

    private static List<String> textsOf(View root) {
        List<String> out = new ArrayList<>();
        texts(root, out);
        return out;
    }

    private static void type(android.app.Activity a, int id, String text) {
        ((TextView) a.findViewById(id)).setText(text);
        idle();
    }

    private static String text(android.app.Activity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private static String errorOf(android.app.Activity a, int layoutId) {
        CharSequence e = ((TextInputLayout) a.findViewById(layoutId)).getError();
        return e == null ? null : e.toString();
    }

    private static int dp(android.app.Activity a, int px) {
        return Math.round(px / a.getResources().getDisplayMetrics().density);
    }

    // ================================================================ SIGN IN

    private LoginActivity signIn() {
        LoginActivity a = Robolectric.buildActivity(LoginActivity.class).setup().get();
        settle();
        return a;
    }

    @Test
    public void signIn_hasAShorterHeader_aPlainBackArrow_andNoFooterPromises() {
        RuntimeEnvironment.setQualifiers("+w393dp-h851dp-notnight");
        LoginActivity a = signIn();
        layout(a);
        assertTrue("the header is about 30% shorter than the old 260dp: " + dp(a, a.findViewById(R.id.curvedHeaderView).getHeight()),
                dp(a, a.findViewById(R.id.curvedHeaderView).getHeight()) <= 190);
        assertTrue("a smaller logo", dp(a, a.findViewById(R.id.logoContainer).getWidth()) <= 70);

        View back = a.findViewById(R.id.btnBack);
        assertTrue("a plain arrow icon, not a white square", back instanceof ImageButton);
        assertTrue("48dp touch target", dp(a, back.getWidth()) >= 48 && dp(a, back.getHeight()) >= 48);

        List<String> t = textsOf(a.getWindow().getDecorView());
        String all = String.join(" | ", t);
        assertFalse(all, all.contains("Secure & Safe") || all.contains("24/7 Support") || all.contains("Best Rate Guarantee"));
        assertFalse("the sign-up helper is not on the sign-in field", all.contains("Use an email address you can open"));
        assertTrue(all, t.contains("Welcome back") && t.contains("Velocity Suites") && t.contains("Your comfort is our service"));
        assertTrue(all, t.contains("Remember me") && t.contains("Forgot password?") && t.contains("Sign in") && t.contains("Sign up"));
        assertEquals("Welcome back", ((TextView) a.findViewById(R.id.loginHeading)).getText().toString());
        assertEquals(24f, ((TextView) a.findViewById(R.id.loginHeading)).getTextSize() / a.getResources().getDisplayMetrics().scaledDensity, 0.3f);
    }

    @Test
    public void signIn_fieldsAreOutlinedWithFloatingLabels_andIcons() {
        LoginActivity a = signIn();
        TextInputLayout email = a.findViewById(R.id.emailLayout);
        TextInputLayout password = a.findViewById(R.id.passwordLayout);
        assertTrue(email.isHintEnabled() && password.isHintEnabled());
        assertEquals("Email", email.getHint().toString());
        assertEquals("Password", password.getHint().toString());
        assertNull("no placeholder repeating the label", ((EditText) a.findViewById(R.id.emailEditText)).getHint() == null ? null : null);
        assertNotNull(email.getStartIconDrawable());
        assertNotNull(password.getStartIconDrawable());
        assertEquals(TextInputLayout.END_ICON_PASSWORD_TOGGLE, password.getEndIconMode());
        assertNull("no helper under the email field", email.getHelperText());
    }

    @Test
    public void signIn_rememberMeAndForgotPassword_shareOneRow_centeredTogether() {
        RuntimeEnvironment.setQualifiers("+w393dp-h851dp-notnight");
        LoginActivity a = signIn();
        layout(a);
        View remember = a.findViewById(R.id.rememberMeCheckbox);
        View forgot = a.findViewById(R.id.forgotPasswordText);
        int rememberMid = LayoutHarness.boundsIn(a.getWindow().getDecorView(), remember).centerY();
        int forgotMid = LayoutHarness.boundsIn(a.getWindow().getDecorView(), forgot).centerY();
        assertEquals("one row, vertically centered", rememberMid, forgotMid, a.getResources().getDisplayMetrics().density * 2);
        assertTrue("Remember me is on the left, Forgot password on the right", remember.getLeft() < forgot.getLeft());
        assertTrue(dp(a, remember.getHeight()) >= 48 && dp(a, forgot.getHeight()) >= 48);
    }

    @Test
    public void signIn_fitsOneScreenOn393dp() {
        RuntimeEnvironment.setQualifiers("+w393dp-h851dp-notnight");
        LoginActivity a = signIn();
        layout(a);
        androidx.core.widget.NestedScrollView scroll = (androidx.core.widget.NestedScrollView) ((ViewGroup) a.findViewById(R.id.loginRoot)).getChildAt(2);
        View signUp = a.findViewById(R.id.signupRedirect);
        int bottom = LayoutHarness.boundsIn(a.getWindow().getDecorView(), signUp).bottom;
        assertTrue("everything down to 'Sign up' is on the first screen (" + bottom + " of " + a.getResources().getDisplayMetrics().heightPixels + ")",
                bottom <= a.getResources().getDisplayMetrics().heightPixels);
        assertNotNull(scroll);
    }

    @Test
    public void signIn_validatesBeforeCallingTheServer_inline() {
        LoginActivity a = signIn();
        a.findViewById(R.id.signInButton).performClick();
        idle();
        assertEquals("Email is required", errorOf(a, R.id.emailLayout));
        assertEquals("Password is required", errorOf(a, R.id.passwordLayout));
        assertEquals("no network call", 0, api.count("login"));
        assertEquals("never a popup", 0, ShadowToast.shownToastCount());

        type(a, R.id.emailEditText, "not-an-email");
        type(a, R.id.passwordEditText, "x");
        a.findViewById(R.id.signInButton).performClick();
        idle();
        assertEquals(a.getString(R.string.valid_email_required), errorOf(a, R.id.emailLayout));
        assertEquals(0, api.count("login"));

        type(a, R.id.emailEditText, "ana@example.com");
        assertNull("the error clears while the guest fixes the field", errorOf(a, R.id.emailLayout));
    }

    private LoginActivity signInWith(String email) {
        LoginActivity a = signIn();
        type(a, R.id.emailEditText, email);
        type(a, R.id.passwordEditText, "Secret123");
        a.findViewById(R.id.signInButton).performClick();
        idle();
        return a;
    }

    @Test
    public void signIn_serverErrors_areInline_inPlainWords() {
        LoginActivity a = signInWith("ana@example.com");
        assertFalse("loading state", a.findViewById(R.id.signInButton).isEnabled());
        api.<Object>last("login").httpWithBody(401, "{\"code\":\"INVALID_CREDENTIALS\",\"message\":\"Incorrect email or password.\"}");
        idle();
        assertEquals("Incorrect email or password.", text(a, R.id.loginError));
        assertTrue(a.findViewById(R.id.signInButton).isEnabled());

        a = signInWith("blocked@example.com");
        api.<Object>last("login").httpWithBody(401, "{\"message\":\"Account suspended or credentials invalid.\"}");
        idle();
        assertEquals("a blocked account shows its reason under the form", "Account suspended or credentials invalid.", text(a, R.id.loginError));
        assertEquals(View.VISIBLE, a.findViewById(R.id.loginError).getVisibility());

        a = signInWith("staff@example.com");
        api.<Object>last("login").httpWithBody(403, "{\"message\":\"This app is for guest accounts only. Staff should use the website.\"}");
        idle();
        assertTrue(text(a, R.id.loginError).contains("guest accounts only"));

        a = signInWith("offline@example.com");
        api.<Object>last("login").offline();
        idle();
        assertEquals(View.VISIBLE, a.findViewById(R.id.loginError).getVisibility());
        assertEquals("no popups for any of it", 0, ShadowToast.shownToastCount());
    }

    @Test
    public void signIn_anUnverifiedAccount_offersResendCode_andLandsOnTheVerifyScreen() {
        LoginActivity a = signInWith("pending@example.com");
        api.<Object>last("login").httpWithBody(401, "{\"code\":\"ACCOUNT_NOT_VERIFIED\",\"message\":\"This account is not verified yet. Enter the code we sent to your email, or ask for a new one.\"}");
        idle();
        assertTrue(text(a, R.id.loginError).contains("not verified yet"));
        View resend = a.findViewById(R.id.loginResendCode);
        assertEquals(View.VISIBLE, resend.getVisibility());

        resend.performClick();
        idle();
        assertEquals(1, api.count("resendOtp"));
        api.<Object>last("resendOtp").succeed(new com.example.velocitysuites.network.dto.ApiMessage());
        idle();
        Intent next = shadowOf(a).getNextStartedActivity();
        assertEquals(RegistrationActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("pending@example.com", next.getStringExtra(RegistrationActivity.EXTRA_VERIFY_EMAIL));

        // typing again hides the old message and the resend button
        type(a, R.id.emailEditText, "other@example.com");
        assertEquals(View.GONE, a.findViewById(R.id.loginError).getVisibility());
        assertEquals(View.GONE, a.findViewById(R.id.loginResendCode).getVisibility());
    }

    @Test
    public void signIn_keepsForgotPassword_andTheBackAndSignUpLinks() {
        LoginActivity a = signIn();
        a.findViewById(R.id.forgotPasswordText).performClick();
        settle();
        assertEquals(View.VISIBLE, a.findViewById(R.id.forgotPasswordCard).getVisibility());
        a.findViewById(R.id.backToLoginLink).performClick();
        settle();
        assertEquals(View.VISIBLE, a.findViewById(R.id.loginCard).getVisibility());
        a.findViewById(R.id.signUpLink).performClick();
        assertEquals(RegistrationActivity.class.getName(), shadowOf(a).getNextStartedActivity().getComponent().getClassName());
    }

    @Test
    public void signIn_repeatTapsCallTheServerOnce() {
        LoginActivity a = signIn();
        type(a, R.id.emailEditText, "ana@example.com");
        type(a, R.id.passwordEditText, "Secret123");
        for (int i = 0; i < 3; i++) a.findViewById(R.id.signInButton).performClick();
        assertEquals(1, api.count("login"));
    }

    @Test
    public void signIn_isReadable_at360And393_lightAndDark_andAtLargeFonts() {
        for (String q : CONFIGS) {
            RuntimeEnvironment.setQualifiers("+" + q);
            LoginActivity a = signIn();
            a.findViewById(R.id.signInButton).performClick(); // with the inline errors showing
            idle();
            Release160Test.assertReadable(q + " sign in", a, a.getWindow().getDecorView());
        }
        for (float scale : new float[]{1.3f, 2.0f}) {
            RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
            RuntimeEnvironment.setFontScale(scale);
            LoginActivity a = signIn();
            Release160Test.assertReadable(scale + "x sign in", a, a.getWindow().getDecorView());
        }
    }

    // ================================================================ CREATE ACCOUNT

    private RegistrationActivity signUp() {
        RegistrationActivity a = Robolectric.buildActivity(RegistrationActivity.class).setup().get();
        idle();
        return a;
    }

    private static boolean nextEnabled(RegistrationActivity a) {
        View primary = a.findViewById(R.id.signUpButton).getVisibility() == View.VISIBLE ? a.findViewById(R.id.signUpButton) : a.findViewById(R.id.nextButton);
        return primary.isEnabled();
    }

    private static void next(RegistrationActivity a) {
        View primary = a.findViewById(R.id.signUpButton).getVisibility() == View.VISIBLE ? a.findViewById(R.id.signUpButton) : a.findViewById(R.id.nextButton);
        primary.performClick();
        idle();
        settle();
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Manila"));
    }

    /** Fills steps 1-5 with good values and stops on step 6. */
    private RegistrationActivity upToReview() {
        RegistrationActivity a = signUp();
        type(a, R.id.firstNameEditText, "Juan");
        type(a, R.id.lastNameEditText, "Dela Cruz");
        next(a);
        ((AutoCompleteTextView) a.findViewById(R.id.genderDropdown)).setText("Male", false);
        a.setDateOfBirthForTest(today().minusYears(23).minusDays(3));
        next(a);
        type(a, R.id.emailEditText, "juan@example.com");
        type(a, R.id.mobileEditText, "09171234567");
        next(a);
        a.markAddressCompleteForTest();
        next(a);
        type(a, R.id.passwordEditText, "Sunrise2026");
        type(a, R.id.confirmPasswordEditText, "Sunrise2026");
        next(a);
        assertEquals(6, a.currentStepForTest());
        return a;
    }

    @Test
    public void everyStep_hasTheSameCompactHeader_andNextIsAlwaysOnScreen() {
        for (String q : new String[]{"w360dp-h800dp-notnight", "w393dp-h851dp-night"}) {
            RuntimeEnvironment.setQualifiers("+" + q);
            RegistrationActivity a = upToReview();
            assertEquals(q + " title", "Create account", text(a, R.id.registrationTitle));
            for (int step = 6; step >= 1; step--) {
                String line = text(a, R.id.stepText);
                assertEquals(q + " step " + step, "Step " + step + " of 6 · " + STEP_TITLES[step - 1], line);
                layout(a);
                View primary = step == 6 ? a.findViewById(R.id.signUpButton) : a.findViewById(R.id.nextButton);
                assertEquals(View.VISIBLE, primary.getVisibility());
                assertTrue(q + " step " + step + " Next/Create is on screen", LayoutHarness.boundsIn(a.getWindow().getDecorView(), primary).bottom <= a.getResources().getDisplayMetrics().heightPixels);
                assertTrue(dp(a, primary.getHeight()) >= 48);
                assertEquals("one progress bar", Math.round(100f * step / 6), ((com.google.android.material.progressindicator.LinearProgressIndicator) a.findViewById(R.id.stepProgressBar)).getProgress());
                if (step > 1) {
                    a.findViewById(R.id.btnPrevious).performClick();
                    idle();
                    settle();
                }
            }
        }
    }

    @Test
    public void step1_nextWaitsForTheNames_andSaysWhy() {
        RegistrationActivity a = signUp();
        assertFalse(nextEnabled(a));
        assertEquals(a.getString(R.string.reg_reason_1), text(a, R.id.nextReason));
        type(a, R.id.firstNameEditText, "Juan");
        assertFalse("the last name is needed too", nextEnabled(a));
        type(a, R.id.lastNameEditText, "Dela Cruz");
        assertTrue(nextEnabled(a));
        assertEquals("the reason is gone", View.GONE, a.findViewById(R.id.nextReason).getVisibility());
        assertTrue("the middle name stays optional", ((TextInputLayout) a.findViewById(R.id.middleNameLayout)).getHint().toString().contains("optional"));
    }

    @Test
    public void step1_labelsFloat_andNoPlaceholderRepeatsThem() {
        RegistrationActivity a = signUp();
        for (int id : new int[]{R.id.firstNameLayout, R.id.lastNameLayout, R.id.middleNameLayout}) {
            TextInputLayout l = a.findViewById(id);
            assertTrue(l.isHintEnabled());
            assertNotNull(l.getHint());
        }
    }

    @Test
    public void step2_age_isReadOnlyText_calculatedFromTheDateOfBirth_and18IsEnforced() {
        RegistrationActivity a = signUp();
        type(a, R.id.firstNameEditText, "Juan");
        type(a, R.id.lastNameEditText, "Dela Cruz");
        next(a);
        assertEquals(2, a.currentStepForTest());
        assertFalse(nextEnabled(a));
        assertFalse("the date of birth is picked, never typed", a.findViewById(R.id.dobEditText).isFocusable());
        assertTrue(a.findViewById(R.id.dobEditText).hasOnClickListeners());
        assertFalse("age is plain text, not a field", a.findViewById(R.id.ageText) instanceof EditText);

        ((AutoCompleteTextView) a.findViewById(R.id.genderDropdown)).setText("Female", false);
        a.setDateOfBirthForTest(today().minusYears(23).minusDays(3));
        assertEquals("Age: 23", text(a, R.id.ageText));
        assertEquals(View.GONE, a.findViewById(R.id.ageValidationErrorText).getVisibility());
        assertTrue(nextEnabled(a));

        a.setDateOfBirthForTest(today().minusYears(17).minusDays(1));
        assertEquals("Age: 17", text(a, R.id.ageText));
        assertEquals("You must be 18 or older to create an account.", text(a, R.id.ageValidationErrorText));
        assertEquals(View.VISIBLE, a.findViewById(R.id.ageValidationErrorText).getVisibility());
        assertFalse("under 18 cannot continue", nextEnabled(a));

        a.setDateOfBirthForTest(today().minusYears(18));
        assertEquals("turning 18 today is allowed", "Age: 18", text(a, R.id.ageText));
        assertTrue(nextEnabled(a));
        assertNotNull(((TextInputLayout) a.findViewById(R.id.genderLayout)).getStartIconDrawable());
    }

    @Test
    public void step3_email_and_philippineMobile_areValidatedInline_andTheHelperIsGreyUntilItIsWrong() {
        RegistrationActivity a = signUp();
        type(a, R.id.firstNameEditText, "Juan");
        type(a, R.id.lastNameEditText, "Dela Cruz");
        next(a);
        ((AutoCompleteTextView) a.findViewById(R.id.genderDropdown)).setText("Male", false);
        a.setDateOfBirthForTest(today().minusYears(30));
        next(a);
        assertEquals(3, a.currentStepForTest());

        TextInputLayout email = a.findViewById(R.id.emailLayout);
        TextInputLayout mobile = a.findViewById(R.id.mobileInputLayout);
        assertEquals("We'll send your verification code here", email.getHelperText().toString());
        assertEquals("Starts with 09 or +639, e.g. 09171234567", mobile.getHelperText().toString());
        assertNull("not red before anything is typed", mobile.getError());
        assertNull(email.getError());
        assertFalse(nextEnabled(a));

        type(a, R.id.emailEditText, "juan@example");
        type(a, R.id.mobileEditText, "12345");
        a.findViewById(R.id.emailEditText).requestFocus();
        a.findViewById(R.id.mobileEditText).requestFocus();
        a.findViewById(R.id.emailEditText).requestFocus();
        idle();
        assertNotNull("the bad number shows its error once the field is left", mobile.getError());
        assertFalse(nextEnabled(a));

        type(a, R.id.emailEditText, "juan@example.com");
        type(a, R.id.mobileEditText, "+639171234567");
        assertTrue("+639... is accepted", nextEnabled(a));
        type(a, R.id.mobileEditText, "09171234567");
        assertTrue("09... is accepted", nextEnabled(a));
        type(a, R.id.mobileEditText, "0917123456");
        assertFalse("a short number is not", nextEnabled(a));
    }

    @Test
    public void step4_address_zipIs4Digits_numeric_withALocationIcon_andNextWaitsForTheWholeAddress() {
        RegistrationActivity a = signUp();
        type(a, R.id.firstNameEditText, "Juan");
        type(a, R.id.lastNameEditText, "Dela Cruz");
        next(a);
        ((AutoCompleteTextView) a.findViewById(R.id.genderDropdown)).setText("Male", false);
        a.setDateOfBirthForTest(today().minusYears(30));
        next(a);
        type(a, R.id.emailEditText, "juan@example.com");
        type(a, R.id.mobileEditText, "09171234567");
        next(a);
        assertEquals(4, a.currentStepForTest());
        assertFalse(nextEnabled(a));
        assertEquals(a.getString(R.string.reg_reason_4), text(a, R.id.nextReason));

        EditText zip = a.findViewById(R.id.addrZipField);
        assertEquals(android.text.InputType.TYPE_CLASS_NUMBER, zip.getInputType() & android.text.InputType.TYPE_MASK_CLASS);
        boolean max4 = false;
        for (android.text.InputFilter f : zip.getFilters()) if (f instanceof android.text.InputFilter.LengthFilter && ((android.text.InputFilter.LengthFilter) f).getMax() == 4) max4 = true;
        assertTrue("ZIP holds 4 digits", max4);
        TextInputLayout zipLayout = a.findViewById(R.id.addrZipLayout);
        assertNotNull("a location pin, not a flag", zipLayout.getStartIconDrawable());
        assertEquals("ZIP code", zipLayout.getHint().toString());
        for (int id : new int[]{R.id.addrCountryLayout, R.id.addrRegionLayout, R.id.addrProvinceLayout, R.id.addrCityLayout, R.id.addrBarangayLayout, R.id.addrStreetLayout}) {
            assertNotNull(((TextInputLayout) a.findViewById(id)).getHint());
        }
        a.markAddressCompleteForTest();
        assertTrue(nextEnabled(a));
    }

    @Test
    public void step5_passwordChecklist_strength_andTheSameRuleAsTheServer() {
        RegistrationActivity a = upToReview();
        a.findViewById(R.id.btnPrevious).performClick();
        idle();
        settle();
        assertEquals(5, a.currentStepForTest());

        String[] weak = {"short1", "allletters", "12345678", "abc12"};
        for (String w : weak) {
            type(a, R.id.passwordEditText, w);
            type(a, R.id.confirmPasswordEditText, w);
            assertFalse(w + " is refused", nextEnabled(a));
        }
        type(a, R.id.passwordEditText, "abcdefg1");
        type(a, R.id.confirmPasswordEditText, "abcdefg2");
        assertFalse("the confirmation must match", nextEnabled(a));
        assertTrue(text(a, R.id.ruleMatch).startsWith("○"));
        assertTrue(text(a, R.id.ruleLength).startsWith("✓") && text(a, R.id.ruleLetter).startsWith("✓") && text(a, R.id.ruleNumber).startsWith("✓"));

        type(a, R.id.confirmPasswordEditText, "abcdefg1");
        assertTrue(nextEnabled(a));
        assertTrue(text(a, R.id.ruleMatch).startsWith("✓"));
        assertEquals("Strength: Good", text(a, R.id.passwordStrengthText));
        type(a, R.id.passwordEditText, "Str0ngPass!");
        type(a, R.id.confirmPasswordEditText, "Str0ngPass!");
        assertEquals("Strength: Strong", text(a, R.id.passwordStrengthText));
        assertEquals(4, ((android.widget.ProgressBar) a.findViewById(R.id.passwordStrengthBar)).getProgress());
        assertTrue("the photo is optional: Next works without one", nextEnabled(a));
        assertTrue(textsOf(a.findViewById(R.id.step5Layout)).contains("Profile picture (optional)"));
    }

    @Test
    public void step6_summaryHasEditLinks_termsGateTheButton_andTheConfirmationNamesTheEmail() {
        RegistrationActivity a = upToReview();
        List<String> t = textsOf(a.findViewById(R.id.reviewSummary));
        String all = String.join(" | ", t);
        assertTrue(all, all.contains("Juan Dela Cruz") && all.contains("juan@example.com") && all.contains("09171234567") && all.contains("Male"));
        assertTrue(all, all.contains("Age: 23"));
        int edits = 0;
        for (String s : t) if (s.equals("Edit")) edits++;
        assertEquals("every section has an Edit link", 5, edits);
        assertFalse("Create account waits for the terms", nextEnabled(a));
        assertEquals(a.getString(R.string.terms_missing_open), text(a, R.id.nextReason));

        a.acceptTermsForTest();
        assertTrue(nextEnabled(a));

        a.findViewById(R.id.signUpButton).performClick();
        idle();
        Dialog d = ShadowDialog.getLatestDialog();
        assertTrue(d.isShowing());
        assertEquals("Create your account with juan@example.com?", ((TextView) d.findViewById(android.R.id.message)).getText().toString());
        assertEquals("nothing sent yet", 0, api.count("register"));
        ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals(1, api.count("register"));
        Object[] sent = api.lastArgs.get("register");
        com.example.velocitysuites.network.dto.RegisterRequest request = (com.example.velocitysuites.network.dto.RegisterRequest) sent[0];
        assertEquals("juan@example.com", request.email);
        assertEquals("male", request.gender);
        assertEquals(23, request.age);
        assertEquals(today().minusYears(23).minusDays(3).toString(), request.date_of_birth);
        assertEquals("Sunrise2026", request.password);
    }

    @Test
    public void step6_editJumpsBackToThatStep_withTheValuesKept() {
        RegistrationActivity a = upToReview();
        LinearLayout box = a.findViewById(R.id.reviewSummary);
        // the third section is Contact
        View edit = ((LinearLayout) ((LinearLayout) box.getChildAt(2)).getChildAt(0)).getChildAt(1);
        edit.performClick();
        idle();
        settle();
        assertEquals(3, a.currentStepForTest());
        assertEquals("juan@example.com", text(a, R.id.emailEditText));
    }

    private RegistrationActivity toVerify() {
        RegistrationActivity a = upToReview();
        a.acceptTermsForTest();
        a.findViewById(R.id.signUpButton).performClick();
        idle();
        ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        api.<Object>last("register").succeed(new com.example.velocitysuites.network.dto.ApiMessage());
        idle();
        settle();
        return a;
    }

    private static List<EditText> boxes(RegistrationActivity a) {
        LinearLayout c = a.findViewById(R.id.otpBoxesContainer);
        List<EditText> out = new ArrayList<>();
        for (int i = 0; i < c.getChildCount(); i++) out.add((EditText) c.getChildAt(i));
        return out;
    }

    @Test
    public void verifyScreen_namesTheMaskedEmail_hasSixBoxes_andAutoSubmits() {
        RegistrationActivity a = toVerify();
        assertEquals("Verify your email", text(a, R.id.verifyTitle));
        assertEquals("Enter the 6-digit code we sent to j***@example.com", text(a, R.id.otpVerifyDescText));
        assertFalse("the email is never missing", text(a, R.id.otpVerifyDescText).endsWith("to"));
        assertEquals("the compact header is still there", "Create account", text(a, R.id.registrationTitle));
        assertEquals(6, boxes(a).size());
        for (EditText b : boxes(a)) assertEquals("numeric keyboard", android.text.InputType.TYPE_CLASS_NUMBER, b.getInputType() & android.text.InputType.TYPE_MASK_CLASS);

        List<EditText> b = boxes(a);
        for (int i = 0; i < 5; i++) {
            b.get(i).setText(String.valueOf(i + 1));
            idle();
            assertTrue("auto-advance to box " + (i + 2), b.get(i + 1).hasFocus());
        }
        assertEquals("not submitted until complete", 0, api.count("verifyOtp"));
        b.get(5).setText("6");
        idle();
        assertEquals("auto-submits when complete", 1, api.count("verifyOtp"));
        com.example.velocitysuites.network.dto.VerifyOtpRequest sent = (com.example.velocitysuites.network.dto.VerifyOtpRequest) api.lastArgs.get("verifyOtp")[0];
        assertEquals("123456", sent.otp);
        assertEquals("juan@example.com", sent.email);
        assertEquals("one submit, even if the last box changes again", 1, api.count("verifyOtp"));
    }

    @Test
    public void verifyScreen_pasteFillsAllBoxes_backspaceMovesBack_andAWrongCodeShowsInline() {
        RegistrationActivity a = toVerify();
        int toastsBefore = ShadowToast.shownToastCount();
        List<EditText> b = boxes(a);
        b.get(0).setText("482913"); // a pasted code lands in the first box and spreads
        idle();
        StringBuilder code = new StringBuilder();
        for (EditText box : b) code.append(box.getText());
        assertEquals("482913", code.toString());
        assertEquals(1, api.count("verifyOtp"));

        api.<Object>last("verifyOtp").httpWithBody(422, "{\"message\":\"The code is invalid or expired.\"}");
        idle();
        assertEquals("That code isn't right. Check it and try again.", text(a, R.id.otpErrorText));
        assertEquals(View.VISIBLE, a.findViewById(R.id.otpErrorText).getVisibility());
        assertEquals("boxes are cleared for another try", "", boxes(a).get(0).getText().toString());
        assertTrue(boxes(a).get(0).isEnabled());
        assertEquals("no popup for the wrong code", toastsBefore, ShadowToast.shownToastCount());

        // Backspace on an empty box clears the one before it and moves back
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
    public void verifyScreen_resendCountsDown_thenOffersResend_andChangeEmailGoesBackToContact() {
        RegistrationActivity a = toVerify();
        TextView resend = a.findViewById(R.id.resendOtpLink);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Resend code in ([0-9]+)s").matcher(resend.getText());
        assertTrue(resend.getText().toString(), m.matches());
        int left = Integer.parseInt(m.group(1));
        assertTrue("about a minute (" + left + "s)", left >= 50 && left <= 60);
        assertFalse(resend.isEnabled());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertEquals("Resend code in " + (left - 3) + "s", resend.getText().toString());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(left));
        assertEquals("Resend code", resend.getText().toString());
        assertTrue(resend.isEnabled());

        resend.performClick();
        idle();
        assertEquals(1, api.count("resendOtp"));
        api.<Object>last("resendOtp").succeed(new com.example.velocitysuites.network.dto.ApiMessage());
        idle();
        assertTrue("the countdown starts again", resend.getText().toString().startsWith("Resend code in"));

        a.findViewById(R.id.changeEmailLink).performClick();
        idle();
        settle();
        assertEquals(3, a.currentStepForTest());
        assertEquals("juan@example.com", text(a, R.id.emailEditText));
    }

    @Test
    public void verifyScreen_aGoodCode_showsAccountCreated_thenContinues() {
        RegistrationActivity a = toVerify();
        for (int i = 0; i < 6; i++) boxes(a).get(i).setText(String.valueOf(i + 1));
        idle();
        com.example.velocitysuites.network.dto.AuthResponse auth = new com.google.gson.Gson().fromJson(
                "{\"token\":\"t\",\"user\":{\"id\":9,\"first_name\":\"Juan\",\"last_name\":\"Dela Cruz\",\"email\":\"juan@example.com\",\"guest\":{\"mobile_number\":\"09171234567\"}}}",
                com.example.velocitysuites.network.dto.AuthResponse.class);
        api.<Object>last("verifyOtp").succeed(auth);
        idle();
        settle();
        assertEquals(102, a.currentStepForTest());
        assertEquals("Account created", text(a, R.id.successTitle));
        assertTrue(SessionManager.isLoggedIn(a));
        a.findViewById(R.id.successContinueButton).performClick();
        idle();
        assertNotNull("heads into the app", shadowOf(a).getNextStartedActivity());
    }

    @Test
    public void leavingMidWay_asksToDiscard_butAnEmptyFormJustLeaves() {
        RegistrationActivity empty = signUp();
        empty.findViewById(R.id.btnBack).performClick();
        idle();
        assertTrue("nothing typed: it simply leaves", empty.isFinishing());

        RegistrationActivity a = signUp();
        type(a, R.id.firstNameEditText, "Juan");
        a.findViewById(R.id.btnBack).performClick();
        idle();
        Dialog d = ShadowDialog.getLatestDialog();
        assertTrue(d.isShowing());
        assertEquals("Discard your sign-up?", ((TextView) d.findViewById(androidx.appcompat.R.id.alertTitle)).getText().toString());
        assertFalse(a.isFinishing());
        ((AlertDialog) d).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        idle();
        assertFalse("Keep editing stays", a.isFinishing());
        assertEquals("Juan", text(a, R.id.firstNameEditText));
    }

    @Test
    public void repeatTapOnCreateAccount_sendsOneRequest() {
        RegistrationActivity a = upToReview();
        a.acceptTermsForTest();
        a.findViewById(R.id.signUpButton).performClick();
        idle();
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        a.findViewById(R.id.signUpButton).performClick();
        a.findViewById(R.id.signUpButton).performClick();
        idle();
        assertEquals(1, api.count("register"));
        assertFalse("loading state: the button is off while it works", a.findViewById(R.id.signUpButton).isEnabled());
    }

    @Test
    public void aServerRefusal_showsInlineOnTheReviewStep() {
        RegistrationActivity a = upToReview();
        a.acceptTermsForTest();
        a.findViewById(R.id.signUpButton).performClick();
        idle();
        ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        int toastsBefore = ShadowToast.shownToastCount();
        api.<Object>last("register").httpWithBody(422, "{\"message\":\"The email has already been taken.\",\"errors\":{\"email\":[\"This email address is already registered. Please login instead.\"]}}");
        idle();
        assertEquals("This email address is already registered. Please login instead.", text(a, R.id.regSubmitError));
        assertEquals(6, a.currentStepForTest());
        assertEquals("no popup for the refusal", toastsBefore, ShadowToast.shownToastCount());
        assertTrue("the button works again", a.findViewById(R.id.signUpButton).isEnabled());
    }

    @Test
    public void rotation_keepsTheStep_theValues_andTheVerifyScreen() {
        ActivityController<RegistrationActivity> controller = Robolectric.buildActivity(RegistrationActivity.class).setup();
        RegistrationActivity a = controller.get();
        idle();
        type(a, R.id.firstNameEditText, "Juan");
        type(a, R.id.lastNameEditText, "Dela Cruz");
        next(a);
        ((AutoCompleteTextView) a.findViewById(R.id.genderDropdown)).setText("Male", false);
        a.setDateOfBirthForTest(LocalDate.of(2000, 5, 17));
        controller.recreate();
        idle();
        RegistrationActivity b = controller.get();
        assertEquals(2, b.currentStepForTest());
        assertEquals("Juan", text(b, R.id.firstNameEditText));
        assertEquals("Male", text(b, R.id.genderDropdown));
        assertEquals("the date of birth survives", "May 17, 2000", text(b, R.id.dobEditText));

        // the verify screen comes back too, with its masked email
        ActivityController<RegistrationActivity> v = Robolectric.buildActivity(RegistrationActivity.class,
                new Intent(ApplicationProvider.getApplicationContext(), RegistrationActivity.class).putExtra(RegistrationActivity.EXTRA_VERIFY_EMAIL, "ana@example.com")).setup();
        idle();
        assertEquals("Enter the 6-digit code we sent to a***@example.com", text(v.get(), R.id.otpVerifyDescText));
        v.recreate();
        idle();
        assertEquals(101, v.get().currentStepForTest());
        assertEquals("Enter the 6-digit code we sent to a***@example.com", text(v.get(), R.id.otpVerifyDescText));
    }

    @Test
    public void everyScreen_isReadable_at360And393_lightAndDark_andAtLargeFonts() {
        for (String q : CONFIGS) {
            RuntimeEnvironment.setQualifiers("+" + q);
            RegistrationActivity a = upToReview();
            for (int step = 6; step >= 1; step--) {
                Release160Test.assertReadable(q + " sign-up step " + step, a, a.getWindow().getDecorView());
                if (step > 1) {
                    a.findViewById(R.id.btnPrevious).performClick();
                    idle();
                    settle();
                }
            }
            RegistrationActivity v = Robolectric.buildActivity(RegistrationActivity.class,
                    new Intent(ApplicationProvider.getApplicationContext(), RegistrationActivity.class).putExtra(RegistrationActivity.EXTRA_VERIFY_EMAIL, "juan@example.com")).setup().get();
            idle();
            Release160Test.assertReadable(q + " verify", v, v.getWindow().getDecorView());
        }
        for (float scale : new float[]{1.3f, 2.0f}) {
            RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
            RuntimeEnvironment.setFontScale(scale);
            RegistrationActivity a = upToReview();
            String tag = scale + "x";
            Release160Test.assertReadable(tag + " review", a, a.getWindow().getDecorView());
            layout(a);
            View primary = a.findViewById(R.id.signUpButton);
            assertTrue(tag + " the button stays on screen", LayoutHarness.boundsIn(a.getWindow().getDecorView(), primary).bottom <= a.getResources().getDisplayMetrics().heightPixels);
            a.findViewById(R.id.btnPrevious).performClick();
            idle();
            settle();
            Release160Test.assertReadable(tag + " password step", a, a.getWindow().getDecorView());
        }
    }
}
