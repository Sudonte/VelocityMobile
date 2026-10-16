package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ProfileManagementActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.ApiMessage;
import com.example.velocitysuites.network.dto.ChangePasswordTokenRequest;
import com.example.velocitysuites.network.dto.ResetTokenResponse;
import com.example.velocitysuites.ui.ScreenTestSupport.FakeApi;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

/** Change Password: the password fields do not exist for the guest until the SERVER has accepted the emailed code. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h640dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ChangePasswordFlowTest {

    private FakeApi api;
    private ProfileManagementActivity activity;
    private AlertDialog dialog;

    @Before
    public void setUp() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        api = new FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        setApiClientService(api.proxy());
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com",
                "09171234567", "Female", "Jan 01, 1990", "");
        activity = Robolectric.buildActivity(ProfileManagementActivity.class).setup().get();
        idle();

        activity.findViewById(R.id.changePasswordButton).performClick();
        idle();
        dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertEquals("opening the dialog sends nothing: asking for the code is its first step", 0, api.count("forgotPassword"));
        // the tests below start on the code step, so ask for the code the way the guest does
        dialog.findViewById(R.id.btnConfirmChangePasswordOtp).performClick();
        api.<ApiMessage>last("forgotPassword").succeed(new ApiMessage());
        idle();
    }

    /** A fresh dialog still on its first (request) step. */
    private AlertDialog freshRequestStep() {
        dialog.dismiss();
        idle();
        activity.findViewById(R.id.changePasswordButton).performClick();
        idle();
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }

    @After
    public void tearDown() throws Exception {
        setApiClientService(null);
    }

    private static void setApiClientService(Object service) throws Exception {
        java.lang.reflect.Field field = com.example.velocitysuites.network.ApiClient.class.getDeclaredField("service");
        field.setAccessible(true);
        field.set(null, service);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private View v(int id) {
        return dialog.findViewById(id);
    }

    private EditText firstBox() {
        return (EditText) ((LinearLayout) v(R.id.changePasswordOtpBoxes)).getChildAt(0);
    }

    private void enterCode(String code) {
        firstBox().setText(code);
        idle();
    }

    private void verifyWithServer() {
        enterCode("123456");
        ResetTokenResponse ok = new ResetTokenResponse();
        ok.reset_token = "tok-123";
        api.<ResetTokenResponse>last("verifyResetOtpForToken").succeed(ok);
        idle();
    }

    @Test
    public void thereAreThreeSteps_requestTheCode_enterIt_chooseThePassword() {
        AlertDialog d = freshRequestStep();
        assertEquals(View.VISIBLE, d.findViewById(R.id.changePasswordStepRequest).getVisibility());
        assertEquals(View.GONE, d.findViewById(R.id.changePasswordStepOtp).getVisibility());
        assertEquals(View.GONE, d.findViewById(R.id.changePasswordStepPassword).getVisibility());
        assertEquals("Step 1 of 3", ((TextView) d.findViewById(R.id.changePasswordStepText)).getText().toString());
        String desc = ((TextView) d.findViewById(R.id.changePasswordRequestDesc)).getText().toString();
        assertTrue(desc, desc.contains("a***@example.com"));
        assertEquals("Send Code", ((android.widget.Button) d.findViewById(R.id.btnConfirmChangePasswordOtp)).getText().toString());

        d.findViewById(R.id.btnConfirmChangePasswordOtp).performClick();
        api.<ApiMessage>last("forgotPassword").succeed(new ApiMessage());
        idle();
        assertEquals(View.VISIBLE, d.findViewById(R.id.changePasswordStepOtp).getVisibility());
        assertEquals("entering the code is still the Verify stage", "Step 1 of 3", ((TextView) d.findViewById(R.id.changePasswordStepText)).getText().toString());
        assertEquals("Verify OTP", ((android.widget.Button) d.findViewById(R.id.btnConfirmChangePasswordOtp)).getText().toString());
    }

    @Test
    public void aFailedSendStaysOnTheRequestStep_withTheReason_andCanBeRetried() {
        AlertDialog d = freshRequestStep();
        d.findViewById(R.id.btnConfirmChangePasswordOtp).performClick();
        api.<ApiMessage>last("forgotPassword").httpWithBody(500, "{\"message\":\"Mail is down.\"}");
        idle();

        assertEquals(View.VISIBLE, d.findViewById(R.id.changePasswordStepRequest).getVisibility());
        assertEquals(View.VISIBLE, d.findViewById(R.id.changePasswordRequestError).getVisibility());
        assertTrue(d.findViewById(R.id.btnConfirmChangePasswordOtp).isEnabled());
    }

    @Test
    public void aCodeSentMomentsAgo_429_counts_asSent() {
        AlertDialog d = freshRequestStep();
        d.findViewById(R.id.btnConfirmChangePasswordOtp).performClick();
        api.<ApiMessage>last("forgotPassword").http(429);
        idle();
        assertEquals(View.VISIBLE, d.findViewById(R.id.changePasswordStepOtp).getVisibility());
    }

    @Test
    public void theThirdStepShowsStepThreeOfThree() {
        verifyWithServer();
        assertEquals("Step 2 of 3", ((TextView) v(R.id.changePasswordStepText)).getText().toString());
        assertEquals(View.GONE, v(R.id.changePasswordStepRequest).getVisibility());
    }

    @Test
    public void opensOnTheCodeStepWithTheAddressMaskedAndNoPasswordFields() {
        assertEquals(View.VISIBLE, v(R.id.changePasswordStepOtp).getVisibility());
        assertEquals(View.GONE, v(R.id.changePasswordStepPassword).getVisibility());
        String desc = ((TextView) v(R.id.changePasswordOtpDesc)).getText().toString();
        assertTrue(desc, desc.contains("a***@example.com"));
        assertFalse(desc, desc.contains("ana@"));
        assertFalse("resend is locked during the countdown", v(R.id.resendChangePasswordOtpLink).isEnabled());
        assertTrue(((TextView) v(R.id.resendChangePasswordOtpLink)).getText().toString().contains("30"));
    }

    @Test
    public void passwordFieldsStayHiddenWhenTheServerRejectsTheCode() {
        enterCode("000000");
        assertEquals(1, api.count("verifyResetOtpForToken"));
        api.<ResetTokenResponse>last("verifyResetOtpForToken").httpWithBody(422, "{\"message\":\"Invalid or expired code.\"}");
        idle();

        assertEquals(View.GONE, v(R.id.changePasswordStepPassword).getVisibility());
        assertEquals(View.VISIBLE, v(R.id.changePasswordOtpError).getVisibility());
        assertEquals("Invalid or expired code.", ((TextView) v(R.id.changePasswordOtpError)).getText().toString());
        assertEquals("the boxes are cleared for another try", "", firstBox().getText().toString());
    }

    @Test
    public void tooManyWrongCodesShowsTheLockoutMessage() {
        enterCode("000000");
        api.<ResetTokenResponse>last("verifyResetOtpForToken").http(429);
        idle();
        assertEquals(View.GONE, v(R.id.changePasswordStepPassword).getVisibility());
        assertTrue(((TextView) v(R.id.changePasswordOtpError)).getText().toString().contains("Too many"));
    }

    @Test
    public void aCodeTheServerAcceptsRevealsOnlyThePasswordStep() {
        verifyWithServer();
        assertEquals(View.GONE, v(R.id.changePasswordStepOtp).getVisibility());
        assertEquals(View.VISIBLE, v(R.id.changePasswordStepPassword).getVisibility());
        assertEquals(0, api.count("changePasswordWithToken"));
    }

    @Test
    public void mismatchedOrShortPasswordsAreNotSubmitted() {
        verifyWithServer();
        ((EditText) v(R.id.changePasswordNew)).setText("short");
        ((EditText) v(R.id.changePasswordConfirmNew)).setText("short");
        v(R.id.btnConfirmChangePasswordOtp).performClick();
        assertNotNull(((TextInputLayout) v(R.id.changePasswordNewLayout)).getError());

        ((EditText) v(R.id.changePasswordNew)).setText("LongEnough1");
        ((EditText) v(R.id.changePasswordConfirmNew)).setText("LongEnough2");
        v(R.id.btnConfirmChangePasswordOtp).performClick();
        assertNotNull(((TextInputLayout) v(R.id.changePasswordConfirmNewLayout)).getError());
        assertEquals("nothing reached the server", 0, api.count("changePasswordWithToken"));
    }

    @Test
    public void theChangeIsSentWithTheVerifiedToken() {
        verifyWithServer();
        ((EditText) v(R.id.changePasswordNew)).setText("LongEnough1");
        ((EditText) v(R.id.changePasswordConfirmNew)).setText("LongEnough1");
        v(R.id.btnConfirmChangePasswordOtp).performClick();

        assertEquals(1, api.count("changePasswordWithToken"));
        ChangePasswordTokenRequest sent = (ChangePasswordTokenRequest) api.lastArgs.get("changePasswordWithToken")[0];
        assertEquals("tok-123", sent.reset_token);
        assertEquals("LongEnough1", sent.password);
        assertEquals("LongEnough1", sent.password_confirmation);
    }

    @Test
    public void aSuccessfulChangeShowsTheDoneScreen_andLogsOutOnlyWhenTheGuestLeavesIt() {
        verifyWithServer();
        ((EditText) v(R.id.changePasswordNew)).setText("LongEnough1!");
        ((EditText) v(R.id.changePasswordConfirmNew)).setText("LongEnough1!");
        v(R.id.btnConfirmChangePasswordOtp).performClick();
        api.<ApiMessage>last("changePasswordWithToken").succeed(new ApiMessage());
        idle();

        assertEquals(View.VISIBLE, v(R.id.changePasswordStepDone).getVisibility());
        assertEquals(View.GONE, v(R.id.changePasswordStepPassword).getVisibility());
        assertEquals("Log in again", ((android.widget.Button) v(R.id.btnConfirmChangePasswordOtp)).getText().toString());
        assertEquals("nothing to cancel any more", View.GONE, v(R.id.btnCancelChangePasswordOtp).getVisibility());
        assertTrue("still signed in until they leave the success screen", SessionManager.isLoggedIn(ApplicationProvider.getApplicationContext()));

        v(R.id.btnConfirmChangePasswordOtp).performClick();
        idle();
        assertFalse(dialog.isShowing());
        assertFalse("signed out so they log in with the new password", SessionManager.isLoggedIn(ApplicationProvider.getApplicationContext()));
    }

    @Test
    public void theStrengthMeterFollowsWhatIsTyped() {
        verifyWithServer();
        TextView text = (TextView) v(R.id.changePasswordStrengthText);
        EditText pw = (EditText) v(R.id.changePasswordNew);
        assertEquals("Choose a strong password", text.getText().toString());
        pw.setText("abc");
        assertEquals("Strength: Weak", text.getText().toString());
        pw.setText("abcdefgh");
        assertEquals("Strength: Weak", text.getText().toString());
        pw.setText("Abcdefgh1");
        assertEquals("Strength: Good", text.getText().toString());
        pw.setText("Abcdefgh1!");
        assertEquals("Strength: Strong", text.getText().toString());
        assertEquals(4, ((com.google.android.material.progressindicator.LinearProgressIndicator) v(R.id.changePasswordStrengthBar)).getProgress());
    }

    @Test
    public void anExpiredVerificationSendsTheGuestBackToTheCodeStep() {
        verifyWithServer();
        ((EditText) v(R.id.changePasswordNew)).setText("LongEnough1");
        ((EditText) v(R.id.changePasswordConfirmNew)).setText("LongEnough1");
        v(R.id.btnConfirmChangePasswordOtp).performClick();
        api.<ApiMessage>last("changePasswordWithToken").httpWithBody(422,
                "{\"message\":\"Your verification has expired. Please verify a new code to change your password.\"}");
        idle();

        assertEquals(View.VISIBLE, v(R.id.changePasswordStepOtp).getVisibility());
        assertEquals(View.GONE, v(R.id.changePasswordStepPassword).getVisibility());
        assertEquals("", ((EditText) v(R.id.changePasswordNew)).getText().toString());
    }

    @Test
    public void cancelAfterEnteringSomethingAsksBeforeDiscarding() {
        verifyWithServer();
        v(R.id.btnCancelChangePasswordOtp).performClick();
        idle();

        assertTrue("the password dialog is still open", dialog.isShowing());
        AlertDialog confirm = (AlertDialog) ShadowDialog.getLatestDialog();
        assertTrue(confirm != dialog && confirm.isShowing());
        confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertFalse(dialog.isShowing());
    }

    @Test
    public void cancelWithNothingEnteredClosesWithoutAsking() {
        v(R.id.btnCancelChangePasswordOtp).performClick();
        idle();
        assertFalse(dialog.isShowing());
    }
}
