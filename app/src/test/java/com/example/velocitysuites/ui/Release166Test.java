package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.LoginActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.ApiClient;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.ApiMessage;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowToast;

import java.time.Duration;

/**
 * 1.66.0: the sign-in screen never says whether an email has an account (an unknown email and a wrong password get the same words in the
 * same place), Forgot password gives one answer either way, and the forgot-password reset uses the same live password checklist and rule
 * (8+ characters, a letter, a number) as sign-up and change password.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Release166Test {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static final String GENERIC = "Incorrect email or password.";
    private static final String SENT = "If an account exists for that email, we've sent a code.";

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
        RuntimeEnvironment.setQualifiers("+w360dp-h800dp-notnight");
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
    }

    private static String text(android.app.Activity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private static void type(android.app.Activity a, int id, String value) {
        ((TextView) a.findViewById(id)).setText(value);
        idle();
    }

    private LoginActivity signIn() {
        LoginActivity a = Robolectric.buildActivity(LoginActivity.class).setup().get();
        settle();
        return a;
    }

    private LoginActivity attempt(String email) {
        LoginActivity a = signIn();
        type(a, R.id.emailEditText, email);
        type(a, R.id.passwordEditText, "Secret123");
        a.findViewById(R.id.signInButton).performClick();
        idle();
        return a;
    }

    private static String errorOf(android.app.Activity a, int layoutId) {
        CharSequence e = ((TextInputLayout) a.findViewById(layoutId)).getError();
        return e == null ? null : e.toString();
    }

    // ================================================================ 1. sign-in messages

    @Test
    public void anUnknownEmail_andAWrongPassword_getTheSameWords_inTheSamePlace() {
        String body = "{\"code\":\"INVALID_CREDENTIALS\",\"message\":\"Incorrect email or password.\"}";

        LoginActivity unknown = attempt("nobody@example.com");
        api.<Object>last("login").httpWithBody(401, body);
        idle();
        LoginActivity wrong = attempt("ana@example.com");
        api.<Object>last("login").httpWithBody(401, body);
        idle();

        for (LoginActivity a : new LoginActivity[]{unknown, wrong}) {
            assertEquals(GENERIC, text(a, R.id.loginError));
            assertEquals(View.VISIBLE, a.findViewById(R.id.loginError).getVisibility());
            assertNull("not under the email field", errorOf(a, R.id.emailLayout));
            assertNull("not under the password field", errorOf(a, R.id.passwordLayout));
            assertEquals(View.GONE, a.findViewById(R.id.loginResendCode).getVisibility());
            assertTrue("the button is back", a.findViewById(R.id.signInButton).isEnabled());
        }
        assertEquals("a popup is never used", 0, ShadowToast.shownToastCount());
    }

    @Test
    public void anOlderServersTwoSpecificAnswers_areShownAsTheSameGenericMessage() {
        LoginActivity a = attempt("nobody@example.com");
        api.<Object>last("login").httpWithBody(401, "{\"code\":\"ACCOUNT_NOT_FOUND\",\"message\":\"Account does not exist.\"}");
        idle();
        assertEquals(GENERIC, text(a, R.id.loginError));

        LoginActivity b = attempt("ana@example.com");
        api.<Object>last("login").httpWithBody(401, "{\"code\":\"WRONG_PASSWORD\",\"message\":\"Incorrect password. One more failed attempt will require you to verify.\"}");
        idle();
        assertEquals("the server's hint about attempts is never shown", GENERIC, text(b, R.id.loginError));
    }

    @Test
    public void aBadAnswerWithNoBody_isStillTheGenericMessage_notAFieldError() {
        LoginActivity a = attempt("ana@example.com");
        api.<Object>last("login").httpWithBody(401, "{\"message\":\"Invalid credentials.\"}");
        idle();
        assertEquals("Invalid credentials.", text(a, R.id.loginError));
        assertNull(errorOf(a, R.id.passwordLayout));
    }

    @Test
    public void theSpecificMessages_stayWhereTheyAreSafe_afterACorrectPassword() {
        LoginActivity locked = attempt("ana@example.com");
        api.<Object>last("login").httpWithBody(423, "{\"code\":\"ACCOUNT_LOCKED\",\"message\":\"Account locked due to multiple failed login attempts. Try again in 15 minutes or reset your password.\"}");
        idle();
        assertTrue(text(locked, R.id.loginError).startsWith("Account locked"));
        assertEquals("the form is still there (the guest can reset the password from it)", View.VISIBLE, locked.findViewById(R.id.loginCard).getVisibility());

        LoginActivity blocked = attempt("blocked@example.com");
        api.<Object>last("login").httpWithBody(403, "{\"code\":\"ACCOUNT_BLOCKED\",\"message\":\"This account is blocked. Please contact the hotel.\"}");
        idle();
        assertEquals("This account is blocked. Please contact the hotel.", text(blocked, R.id.loginError));

        LoginActivity unverified = attempt("pending@example.com");
        api.<Object>last("login").httpWithBody(401, "{\"code\":\"ACCOUNT_NOT_VERIFIED\",\"message\":\"This account is not verified yet. Enter the code we sent to your email, or ask for a new one.\"}");
        idle();
        assertTrue(text(unverified, R.id.loginError).contains("not verified yet"));
        assertEquals("Resend code is offered", View.VISIBLE, unverified.findViewById(R.id.loginResendCode).getVisibility());

        LoginActivity offline = attempt("ana@example.com");
        api.<Object>last("login").offline();
        idle();
        assertEquals(View.VISIBLE, offline.findViewById(R.id.loginError).getVisibility());
        assertFalse("not the generic wrong-password words", text(offline, R.id.loginError).equals(GENERIC));
        assertEquals(0, ShadowToast.shownToastCount());
    }

    @Test
    public void theMessageClearsAsSoonAsTheGuestTypesAgain() {
        LoginActivity a = attempt("ana@example.com");
        api.<Object>last("login").httpWithBody(401, "{\"code\":\"INVALID_CREDENTIALS\",\"message\":\"Incorrect email or password.\"}");
        idle();
        assertEquals(View.VISIBLE, a.findViewById(R.id.loginError).getVisibility());
        type(a, R.id.passwordEditText, "Another1");
        assertEquals(View.GONE, a.findViewById(R.id.loginError).getVisibility());
    }

    // ================================================================ 1b. forgot password: one answer

    private LoginActivity forgotCard() {
        LoginActivity a = signIn();
        a.findViewById(R.id.forgotPasswordText).performClick();
        settle();
        assertEquals(View.VISIBLE, a.findViewById(R.id.forgotPasswordCard).getVisibility());
        return a;
    }

    private void askForCode(LoginActivity a, String email) {
        type(a, R.id.forgotEmailEditText, email);
        a.findViewById(R.id.forgotActionButton).performClick();
        idle();
        assertEquals(1, api.count("forgotPassword"));
    }

    @Test
    public void forgotPassword_sayWhetherOrNot_theSameWords() {
        String serverAnswer = "If an account exists for that email, we've sent a code.";
        for (String email : new String[]{"ana@example.com", "nobody@example.com"}) {
            api = new ScreenTestSupport.FakeApi();
            ApiClient.setServiceForTests(api.proxy());
            LoginActivity a = forgotCard();
            askForCode(a, email);
            ApiMessage ok = new ApiMessage();
            ok.message = serverAnswer;
            api.<ApiMessage>last("forgotPassword").succeed(ok);
            idle();
            assertEquals(email, SENT, text(a, R.id.forgotPasswordDesc));
            assertEquals("the code step is next, for every address", View.VISIBLE, a.findViewById(R.id.loginOtpLayout).getVisibility());
            assertFalse("the address is not echoed back as if it existed", text(a, R.id.forgotPasswordDesc).contains(email));
            assertEquals("no popup", 0, ShadowToast.shownToastCount());
        }
    }

    @Test
    public void forgotPassword_theWaitAMomentAnswer_isInlineUnderTheEmail() {
        LoginActivity a = forgotCard();
        askForCode(a, "ana@example.com");
        api.<ApiMessage>last("forgotPassword").httpWithBody(429, "{\"message\":\"Please wait 60 seconds before requesting another code.\"}");
        idle();
        assertEquals("Please wait 60 seconds before requesting another code.", errorOf(a, R.id.forgotEmailLayout));
        assertEquals(View.VISIBLE, a.findViewById(R.id.forgotEmailLayout).getVisibility());
        assertEquals(0, ShadowToast.shownToastCount());
    }

    // ================================================================ 2. one password rule in the reset

    private LoginActivity atNewPassword() {
        LoginActivity a = forgotCard();
        askForCode(a, "ana@example.com");
        api.<ApiMessage>last("forgotPassword").succeed(new ApiMessage());
        idle();
        type(a, R.id.loginOtpEditText, "123456");
        a.findViewById(R.id.forgotActionButton).performClick();
        idle();
        api.<ApiMessage>last("verifyResetOtp").succeed(new ApiMessage());
        idle();
        assertEquals(View.VISIBLE, a.findViewById(R.id.newPasswordLayout).getVisibility());
        return a;
    }

    @Test
    public void theResetShowsTheSameChecklist_andOnlyAGoodPasswordCanBeSent() {
        LoginActivity a = atNewPassword();
        assertEquals(View.VISIBLE, a.findViewById(R.id.forgotPasswordRules).getVisibility());
        for (int id : new int[]{R.id.ruleLength, R.id.ruleLetter, R.id.ruleNumber, R.id.ruleMatch, R.id.passwordStrengthBar, R.id.passwordStrengthText}) {
            assertEquals(View.VISIBLE, a.findViewById(id).getVisibility());
        }
        assertFalse("nothing typed yet: the button waits", a.findViewById(R.id.forgotActionButton).isEnabled());

        for (String weak : new String[]{"abcdefgh", "12345678", "abc12", "short1"}) {
            type(a, R.id.newPasswordEditText, weak);
            type(a, R.id.confirmNewPasswordEditText, weak);
            assertFalse(weak + " cannot be sent", a.findViewById(R.id.forgotActionButton).isEnabled());
        }
        type(a, R.id.newPasswordEditText, "abcdefg1");
        type(a, R.id.confirmNewPasswordEditText, "abcdefg2");
        assertFalse("the confirmation must match", a.findViewById(R.id.forgotActionButton).isEnabled());
        assertTrue(text(a, R.id.ruleMatch).startsWith("○"));

        type(a, R.id.confirmNewPasswordEditText, "abcdefg1");
        assertTrue(text(a, R.id.ruleLength).startsWith("✓") && text(a, R.id.ruleLetter).startsWith("✓") && text(a, R.id.ruleNumber).startsWith("✓") && text(a, R.id.ruleMatch).startsWith("✓"));
        assertEquals("Strength: Good", text(a, R.id.passwordStrengthText));
        assertTrue(a.findViewById(R.id.forgotActionButton).isEnabled());
        assertEquals("nothing weak ever reached the server", 0, api.count("resetPassword"));

        a.findViewById(R.id.forgotActionButton).performClick();
        idle();
        assertEquals(1, api.count("resetPassword"));
        com.example.velocitysuites.network.dto.ResetPasswordRequest sent = (com.example.velocitysuites.network.dto.ResetPasswordRequest) api.lastArgs.get("resetPassword")[0];
        assertEquals("abcdefg1", sent.password);
    }

    @Test
    public void theChecklistIsReadable_inLightAndDark() {
        for (String q : new String[]{"w360dp-h800dp-notnight", "w393dp-h851dp-night"}) {
            RuntimeEnvironment.setQualifiers("+" + q);
            api = new ScreenTestSupport.FakeApi();
            ApiClient.setServiceForTests(api.proxy());
            LoginActivity a = atNewPassword();
            type(a, R.id.newPasswordEditText, "abcdefg1");
            Release160Test.assertReadable(q + " reset", a, a.getWindow().getDecorView());
        }
    }
}
